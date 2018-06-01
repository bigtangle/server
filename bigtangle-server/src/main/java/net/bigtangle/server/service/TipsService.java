/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class TipsService {
	private final Logger log = LoggerFactory.getLogger(TipsService.class);

	@Autowired
	protected FullPrunedBlockStore store;
	@Autowired
	private BlockService blockService;
	@Autowired
	protected NetworkParameters networkParameters;
	@Autowired
	private ValidatorService validatorService;

	public List<Sha256Hash> getRatingTips(int count) throws Exception {
	    // TODO include different time snapshots (max(time) - timeInterval) to be robust against attacks (low pass filter)
	    // overload getmcmc, stop when overtime or finished
	    
		Stopwatch watch = Stopwatch.createStarted();
		SecureRandom seed = new SecureRandom();

		List<Sha256Hash> entryPoints = getRatingEntryPoints(count, seed);
		List<Sha256Hash> results = new ArrayList<>();

		for (Sha256Hash entryPoint : entryPoints) {
			results.add(getMCMCResultBlock(entryPoint, seed).peek());
		}

		watch.stop();
		log.info("getRatingTips time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return results;
	}

	public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair() throws Exception {
		Stopwatch watch = Stopwatch.createStarted();
		List<Pair<Sha256Hash, Sha256Hash>> pairs = getValidatedBlockPairs(1);
		watch.stop();
		log.info("getValidatedBlockPair time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return pairs.get(0);
	}

	public List<Pair<Sha256Hash, Sha256Hash>> getValidatedBlockPairs(int count) throws Exception {
		Stopwatch watch = Stopwatch.createStarted();
		SecureRandom seed = new SecureRandom();

		List<Pair<Stack<Sha256Hash>, TreeSet<BlockEvaluation>>> blocks = getValidatedBlocks(2 * count, seed);
		List<Pair<Sha256Hash, Sha256Hash>> results = new ArrayList<>();

		for (int index = 0; index < count; index++) {
			Pair<Stack<Sha256Hash>, TreeSet<BlockEvaluation>> b1 = blocks.get(index);
			Pair<Stack<Sha256Hash>, TreeSet<BlockEvaluation>> b2 = blocks.get(count + index);
			BlockEvaluation e1 = blockService.getBlockEvaluation(b1.getLeft().peek());
			BlockEvaluation e2 = blockService.getBlockEvaluation(b2.getLeft().peek());
			Stack<Sha256Hash> selectedBlock1 = b1.getLeft();
			Stack<Sha256Hash> selectedBlock2 = b2.getLeft();

			// Get all blocks that are approved together
			TreeSet<BlockEvaluation> approvedNonMilestoneBlockEvaluations = new TreeSet<>(b1.getRight());
			approvedNonMilestoneBlockEvaluations.addAll(b2.getRight());
			List<Block> approvedNonMilestoneBlocks = blockService
					.getBlocks(approvedNonMilestoneBlockEvaluations.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));
			HashSet<Pair<BlockEvaluation, ConflictPoint>> conflictingOutPoints = new HashSet<Pair<BlockEvaluation, ConflictPoint>>();
			HashSet<BlockEvaluation> conflictingMilestoneBlocks = new HashSet<BlockEvaluation>();

			// Find all conflicts
			validatorService.findConflicts(approvedNonMilestoneBlocks, conflictingOutPoints, conflictingMilestoneBlocks);

			// Resolve all conflicts by grouping by UTXO ordered by descending
			// rating for most likely consensus
			Pair<HashSet<BlockEvaluation>, HashSet<BlockEvaluation>> conflictResolution = validatorService
					.resolveConflictsByDescendingRating(conflictingOutPoints);
			HashSet<BlockEvaluation> losingBlocks = conflictResolution.getRight();

			// If the selected blocks are in conflict, walk backwards until we
			// find a non-conflicting block
			Set<Sha256Hash> losingBlockHashes = losingBlocks.stream().map(e -> e.getBlockhash()).collect(Collectors.toSet());
			if (losingBlocks.contains(e1)) {
				selectedBlock1 = walkBackwardsUntilNotContained(b1.getLeft(), seed, losingBlockHashes);
			}
			if (losingBlocks.contains(e2)) {
				selectedBlock2 = walkBackwardsUntilNotContained(b2.getLeft(), seed, losingBlockHashes);
			}

			results.add(Pair.of(selectedBlock1.peek(), selectedBlock2.peek()));
		}

		watch.stop();
		log.info("getValidatedBlockPairs time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return results;
	}

	// Specifically, we check for milestone-candidate-conflicts +
	// candidate-candidate-conflicts and reverse until there are no such
	// conflicts
	// Also returns all approved non-milestone blocks in topological ordering
	private List<Pair<Stack<Sha256Hash>, TreeSet<BlockEvaluation>>> getValidatedBlocks(int count, Random seed) throws Exception {
		List<Pair<Stack<Sha256Hash>, TreeSet<BlockEvaluation>>> results = new ArrayList<>();
		List<Sha256Hash> entryPoints = getValidationEntryPoints(count, seed);

		for (Sha256Hash entryPoint : entryPoints) {
		    Stack<Sha256Hash> selectedBlock = getMCMCResultBlock(entryPoint, seed);
			BlockEvaluation selectedBlockEvaluation = blockService.getBlockEvaluation(selectedBlock.peek());

			// Get all non-milestone blocks that are to be approved by this
			// selection
			Comparator<BlockEvaluation> byDescendingHeightMultiple = Comparator.comparingLong((BlockEvaluation e) -> e.getHeight())
					.thenComparing((BlockEvaluation e) -> e.getBlockhash()).reversed();
			TreeSet<BlockEvaluation> approvedNonMilestoneBlockEvaluations = new TreeSet<>(byDescendingHeightMultiple);
			blockService.addApprovedNonMilestoneBlocksTo(approvedNonMilestoneBlockEvaluations, selectedBlockEvaluation);

			// Drop all approved blocks that cannot be added due to current
			// milestone
			validatorService.removeWhereInputNotFoundOrUnconfirmed(approvedNonMilestoneBlockEvaluations);
			validatorService.resolvePrunedConflicts(approvedNonMilestoneBlockEvaluations);

			List<Block> approvedNonMilestoneBlocks = blockService
					.getBlocks(approvedNonMilestoneBlockEvaluations.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));
			HashSet<Pair<BlockEvaluation, ConflictPoint>> conflictingOutPoints = new HashSet<Pair<BlockEvaluation, ConflictPoint>>();
			HashSet<BlockEvaluation> conflictingMilestoneBlocks = new HashSet<BlockEvaluation>();

			// Find all conflicts
			validatorService.findConflicts(approvedNonMilestoneBlocks, conflictingOutPoints, conflictingMilestoneBlocks);

			// Resolve all conflicts by grouping by UTXO ordered by descending
			// rating for most likely consensus
			Pair<HashSet<BlockEvaluation>, HashSet<BlockEvaluation>> conflictResolution = validatorService
					.resolveConflictsByDescendingRating(conflictingOutPoints);
			HashSet<BlockEvaluation> losingBlocks = conflictResolution.getRight();

			// If the selected block is in conflict, walk backwards until we
			// find a non-conflicting block
			if (losingBlocks.contains(selectedBlockEvaluation)) {
				selectedBlock = walkBackwardsUntilNotContained(selectedBlock, seed,
						losingBlocks.stream().map(e -> e.getBlockhash()).collect(Collectors.toSet()));
				selectedBlockEvaluation = blockService.getBlockEvaluation(selectedBlock.peek());
				approvedNonMilestoneBlockEvaluations.clear();
				blockService.addApprovedNonMilestoneBlocksTo(approvedNonMilestoneBlockEvaluations, selectedBlockEvaluation);
			}

			results.add(Pair.of(selectedBlock, approvedNonMilestoneBlockEvaluations));
		}

		return results;
	}

	private Stack<Sha256Hash> getMCMCResultBlock(Sha256Hash entryPoint, Random seed) throws Exception {
		return randomWalk(entryPoint, seed);
	}

	private List<Sha256Hash> getRatingEntryPoints(int count, Random seed) throws Exception {
		List<BlockEvaluation> candidates = blockService.getRatingEntryPointCandidates();
		return getRandomsByCumulativeWeight(candidates, count, seed);
	}

	private List<Sha256Hash> getValidationEntryPoints(int count, Random seed) throws Exception {
		List<BlockEvaluation> candidates = blockService.getValidationEntryPointCandidates();
		return getRandomsByCumulativeWeight(candidates, count, seed);
	}

	private List<Sha256Hash> getRandomsByCumulativeWeight(List<BlockEvaluation> candidates, int count, Random seed) {
		double maxBlockWeight = candidates.stream().mapToLong(e -> e.getCumulativeWeight()).max().orElse(1L);
		double normalizedBlockWeightSum = candidates.stream().mapToDouble(e -> e.getCumulativeWeight() / maxBlockWeight).sum();
		List<Sha256Hash> results = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			if (candidates.isEmpty()) {
				results.add(networkParameters.getGenesisBlock().getHash());
			} else {
				// Randomly select one of the candidates weighted by their
				// cumulative weights
				double selectionRealization = seed.nextDouble() * normalizedBlockWeightSum;
				for (int selection = 0; selection < candidates.size(); selection++) {
					BlockEvaluation selectedBlock = candidates.get(selection);
					selectionRealization -= selectedBlock.getCumulativeWeight() / maxBlockWeight;
					if (selectionRealization <= 0) {
						results.add(selectedBlock.getBlockhash());
						break;
					}
				}
			}
		}

		return results;
	}

	private Stack<Sha256Hash> randomWalk(Sha256Hash blockHash, Random seed) throws Exception {
		// Repeatedly perform transitions until the final tip is found
	    Stack<Sha256Hash> path = new Stack<Sha256Hash>();
		while (blockHash != null) {
            path.add(blockHash)
;			List<Sha256Hash> approverHashes = blockService.getSolidApproverBlockHashes(blockHash);
			if (approverHashes.size() == 0) {
				return path;
			} else if (approverHashes.size() == 1) {
				blockHash = approverHashes.get(0);
			} else {
				Sha256Hash[] blockApprovers = approverHashes.toArray(new Sha256Hash[approverHashes.size()]);
				double[] transitionWeights = new double[blockApprovers.length];
				double transitionWeightSum = 0;
				long currentCumulativeWeight = blockService.getBlockEvaluation(blockHash).getCumulativeWeight();

				// Calculate the unnormalized transition weights of all
				// approvers as ((Hx-Hy)^-3)
				// TODO set exp fct and alpha according to ideal orphan rates as found in parameter tuning simulations and papers
				for (int i = 0; i < blockApprovers.length; i++) {
					transitionWeights[i] = Math.pow(currentCumulativeWeight - blockService.getBlockEvaluation(blockApprovers[i]).getCumulativeWeight(), -3);
					transitionWeightSum += transitionWeights[i];
				}

				// Randomly select one of the approvers weighted by their
				// transition probabilities
				double transitionRealization = seed.nextDouble() * transitionWeightSum;
				for (int i = 0; i < blockApprovers.length; i++) {
					transitionRealization -= transitionWeights[i];
					if (transitionRealization <= 0) {
						blockHash = blockApprovers[i];
						break;
					}
				}
			}
		}

		return path;
	}

	private Stack<Sha256Hash> walkBackwardsUntilNotContained(Stack<Sha256Hash> blockHashes, Random seed, Set<Sha256Hash> losers) throws Exception {
	    // Pop stack until top hash is not contained anymore
	    while (blockHashes.size() > 1) {
	        if (losers.contains(blockHashes.peek()))
	            blockHashes.pop();
	        else 
	            return blockHashes;
	    }
	    
		// If there is only one left in stack, repeatedly perform transitions backwards
	    Sha256Hash blockHash;
		while ((blockHash = blockHashes.peek()) != null) {
			Block block = blockService.getBlock(blockHash);
			if (!losers.contains(blockHash)) {
				return blockHashes;
			} else {
				// Randomly select one of the two approved blocks to go to
				double transitionRealization = seed.nextDouble();
				blockHashes.pop();
				if (transitionRealization <= 0.5) {
				    blockHashes.push(block.getPrevBlockHash());
				} else {
                    blockHashes.push(block.getPrevBranchBlockHash());
				}
			}
		}

		throw new IllegalStateException("Failed to find non-losing blocks.");
	}
}
