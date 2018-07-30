/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class TipService {
	private final Logger log = LoggerFactory.getLogger(TipService.class);

	@Autowired
	protected FullPrunedBlockStore store;
	@Autowired
	private BlockService blockService;
	@Autowired
	protected NetworkParameters networkParameters;
	@Autowired
	private ValidatorService validatorService;

	private static Random seed = new Random();

	public List<BlockWrap> getRatingTips(int count) throws BlockStoreException {
		Stopwatch watch = Stopwatch.createStarted();

		List<BlockWrap> entryPoints = getRatingEntryPoints(count);
		List<BlockWrap> results = new ArrayList<>();
		long latestImportTime = store.getMaxImportTime();

		for (int i = 0; i < entryPoints.size(); i++) {
			results.add(getRatingTip(entryPoints.get(i), latestImportTime));
		}

		watch.stop();
		log.info("getRatingTips time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return results;
	}

	public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair() throws BlockStoreException {
		Stopwatch watch = Stopwatch.createStarted();
		List<BlockWrap> entryPoints = getValidationEntryPoints(2);
		BlockWrap left = entryPoints.get(0);
		BlockWrap right = entryPoints.get(1);
		HashSet<BlockWrap> currentApprovedNonMilestoneBlocks = new HashSet<>();
		
		//Unnecessary: left/right are never non-milestone initially
		//blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, left);
		//blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, right);

		// Perform next steps
		BlockWrap nextLeft = performValidatedStep(left, currentApprovedNonMilestoneBlocks);
		BlockWrap nextRight = performValidatedStep(right, currentApprovedNonMilestoneBlocks);

		// Repeat: Proceed on path to be included first (highest rating else random)
		while (nextLeft != left && nextRight != right) {
			if (nextLeft.getBlockEvaluation().getRating() > nextRight.getBlockEvaluation().getRating()) {
				// Go left
				left = nextLeft;
				blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, left);
				
				// Perform next steps 
				nextLeft = performValidatedStep(left, currentApprovedNonMilestoneBlocks);
				nextRight = validateOrPerformValidatedStep(right, currentApprovedNonMilestoneBlocks, nextRight);
			} else {
				// Go right
				right = nextRight;
				blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, right);
				
				// Perform next steps 
				nextRight = performValidatedStep(right, currentApprovedNonMilestoneBlocks);
				nextLeft = validateOrPerformValidatedStep(left, currentApprovedNonMilestoneBlocks, nextLeft);
			}
		}

		// Go forward on the remaining paths
		while (nextLeft != left) {
			left = nextLeft;
			blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, left);
			nextLeft = performValidatedStep(left, currentApprovedNonMilestoneBlocks);
		}
		while (nextRight != right) {
			right = nextRight;
			blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, right);
			nextRight = performValidatedStep(right, currentApprovedNonMilestoneBlocks);
		}

		watch.stop();
		log.info("getValidatedBlockPair (iteratively) time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return Pair.of(left.getBlock().getHash(), right.getBlock().getHash());
	}

	// Does not redo finding next step if next step was still valid
	private BlockWrap validateOrPerformValidatedStep(BlockWrap fromBlock, HashSet<BlockWrap> currentApprovedNonMilestoneBlocks,
			BlockWrap potentialNextBlock) throws BlockStoreException {
		if (!validatorService.isIneligibleForSelection(potentialNextBlock, currentApprovedNonMilestoneBlocks))
			return potentialNextBlock;
		else 
			return performValidatedStep(fromBlock, currentApprovedNonMilestoneBlocks);
	}

	// Finds a potential approver block to include given the currently approved blocks
	private BlockWrap performValidatedStep(BlockWrap fromBlock, HashSet<BlockWrap> currentApprovedNonMilestoneBlocks)
			throws BlockStoreException {
		List<BlockWrap> candidates = store.getSolidApproverBlocks(fromBlock.getBlock().getHash());
		BlockWrap result;
		do {
			// Find results until one is valid/eligible
			result = performTransition(fromBlock, candidates);
			candidates.remove(result);
		} while (validatorService.isIneligibleForSelection(result, currentApprovedNonMilestoneBlocks));
		return result;
	}

	private BlockWrap getRatingTip(BlockWrap currentBlock, long maxTime) throws BlockStoreException {
		// Repeatedly perform transitions until the final tip is found
		List<BlockWrap> approvers = store.getSolidApproverBlocks(currentBlock.getBlock().getHash());
		approvers.removeIf(b -> b.getBlockEvaluation().getInsertTime() > maxTime);
		BlockWrap nextBlock = performTransition(currentBlock, approvers);

		while (currentBlock != nextBlock) {
			currentBlock = nextBlock;
			approvers = store.getSolidApproverBlocks(currentBlock.getBlock().getHash());
			approvers.removeIf(b -> b.getBlockEvaluation().getInsertTime() > maxTime);
			nextBlock = performTransition(currentBlock, approvers);
		}
		return currentBlock;
	}

	@SuppressWarnings("unused")
	private BlockWrap getValidatedRatingBlock(BlockWrap currentBlock) throws BlockStoreException {
		HashSet<BlockWrap> currentApprovedNonMilestoneBlocks = new HashSet<>();

		// Perform next step
		BlockWrap nextLeft = performValidatedStep(currentBlock, currentApprovedNonMilestoneBlocks);
		
		// Go forward while validating
		while (nextLeft != currentBlock) {
			currentBlock = nextLeft;
			blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, currentBlock);
			nextLeft = performValidatedStep(currentBlock, currentApprovedNonMilestoneBlocks);
		}

		return currentBlock;
	}

	/**
	 * Performs one step of MCMC random walk by cumulative weight.
	 * 
	 * @param currentBlock
	 *            the block to take a step from
	 * @param approvers
	 *            all blocks approving the block that are allowed to go to
	 * @return currentBlock if no further steps possible, else a new block from
	 *         approvers
	 */
	public BlockWrap performTransition(BlockWrap currentBlock, List<BlockWrap> approvers) {
		if (approvers.size() == 0) {
			return currentBlock;
		} else if (approvers.size() == 1) {
			return approvers.get(0);
		} else {
			double[] transitionWeights = new double[approvers.size()];
			double transitionWeightSum = 0;
			long currentCumulativeWeight = currentBlock.getBlockEvaluation().getCumulativeWeight();

			// Calculate the unnormalized transition weights
			for (int i = 0; i < approvers.size(); i++) {
				// Aviv Zohar: Scale alpha up if very deep to prevent splitting attack
				double alpha = 0.1
						* Math.exp(0.05 * Math.max(0.0, (currentBlock.getBlockEvaluation().getMilestoneDepth() - 30)));
				
				// Clamp
				alpha = Math.max(0.001, alpha);
				alpha = Math.min(1.5, alpha);
				
				// Calculate transition weights
				transitionWeights[i] = Math.exp(-alpha
						* (currentCumulativeWeight - approvers.get(i).getBlockEvaluation().getCumulativeWeight()));
				transitionWeightSum += transitionWeights[i];
			}

			// Randomly select one of the approvers by transition probabilities
			double transitionRealization = seed.nextDouble() * transitionWeightSum;
			for (int i = 0; i < approvers.size(); i++) {
				transitionRealization -= transitionWeights[i];
				if (transitionRealization <= 0) {
					return approvers.get(i);
				}
			}

			log.warn("MCMC step failed");
			return currentBlock;
		}
	}

	private List<BlockWrap> getRatingEntryPoints(int count) throws BlockStoreException {
		List<BlockWrap> candidates = blockService.getRatingEntryPointCandidates();
		return pullRandomlyByCumulativeWeight(candidates, count);
	}

	/**
	 * Returns the specified amount of entry points for tip selection.
	 * 
	 * @param count
	 *            amount of entry points to get
	 * @return hashes of the entry points
	 * @throws Exception
	 */
	private List<BlockWrap> getValidationEntryPoints(int count) throws BlockStoreException {
		List<BlockWrap> candidates = blockService.getValidationEntryPointCandidates();
		return pullRandomlyByCumulativeWeight(candidates, count);
	}

	/**
	 * Randomly pulls with replacement the specified amount from the specified list.
	 * 
	 * @param candidates
	 *            List to pull from
	 * @param count
	 *            Amount to pull
	 * @return Random pulls from collection
	 */
	private List<BlockWrap> pullRandomlyByCumulativeWeight(List<BlockWrap> candidates, int count) {
		if (candidates.isEmpty())
			throw new IllegalArgumentException("Candidate list is empty.");

		double maxBlockWeight = candidates.stream().mapToLong(e -> e.getBlockEvaluation().getCumulativeWeight()).max()
				.orElse(1L);
		double normalizedBlockWeightSum = candidates.stream()
				.mapToDouble(e -> e.getBlockEvaluation().getCumulativeWeight() / maxBlockWeight).sum();
		List<BlockWrap> results = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			// Randomly select one of the candidates weighted by their
			// cumulative weights
			double selectionRealization = seed.nextDouble() * normalizedBlockWeightSum;
			for (int selection = 0; selection < candidates.size(); selection++) {
				BlockWrap selectedBlock = candidates.get(selection);
				selectionRealization -= selectedBlock.getBlockEvaluation().getCumulativeWeight() / maxBlockWeight;
				if (selectionRealization <= 0) {
					results.add(selectedBlock);
					break;
				}
			}
		}

		return results;
	}
}
