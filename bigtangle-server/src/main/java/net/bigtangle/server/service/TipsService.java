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

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockWrap;
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

	private static Random seed = new Random();

	public List<Sha256Hash> getRatingTips(int count) throws Exception {
		Stopwatch watch = Stopwatch.createStarted();

		List<Sha256Hash> entryPoints = getRatingEntryPoints(count);
		List<Sha256Hash> results = new ArrayList<>();
		long latestImportTime = store.getMaxImportTime();

		for (int i = 0; i < entryPoints.size(); i++) {
			results.add(randomWalk(entryPoints.get(i), latestImportTime));
		}

		watch.stop();
		log.info("getRatingTips time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return results;
	}

	public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair() throws Exception {
		// Init 
		Stopwatch watch = Stopwatch.createStarted();
		List<Sha256Hash> entryPoints = getValidationEntryPoints(2);
		BlockWrap left = store.getBlockWrap(entryPoints.get(0));
		BlockWrap right = store.getBlockWrap(entryPoints.get(1));
		HashSet<BlockWrap> currentApprovedNonMilestoneBlocks = new HashSet<>();

		// Find valid approvers to go to
		List<BlockWrap> validLeftApprovers = blockService.getSolidApproverBlocks(left.getBlock().getHash());
		List<BlockWrap> validRightApprovers = blockService.getSolidApproverBlocks(right.getBlock().getHash());
		validLeftApprovers.removeIf(b -> validatorService.isIneligible(b, currentApprovedNonMilestoneBlocks));
		validRightApprovers.removeIf(b -> validatorService.isIneligible(b, currentApprovedNonMilestoneBlocks));

		// Perform next steps
		BlockWrap nextLeft = performStep(left, validLeftApprovers);
		BlockWrap nextRight = performStep(right, validRightApprovers);

		// Repeat: Proceed on path to be included first (highest rating else right, which is
		// random)
		while (nextLeft != left && nextRight != right) {
			if (nextLeft.getBlockEvaluation().getRating() > nextRight.getBlockEvaluation().getRating()) {
				left = nextLeft;
				blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, nextLeft);
				validLeftApprovers = blockService.getSolidApproverBlocks(left.getBlock().getHash());
				validLeftApprovers.removeIf(b -> validatorService.isIneligible(b, currentApprovedNonMilestoneBlocks));
				nextLeft = performStep(left, validLeftApprovers);
			} else {
				right = nextRight;
				blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, nextRight);
				validRightApprovers = blockService.getSolidApproverBlocks(right.getBlock().getHash());
				validRightApprovers.removeIf(b -> validatorService.isIneligible(b, currentApprovedNonMilestoneBlocks));
				nextRight = performStep(right, validRightApprovers);
			}
		}

		// Go forward on the remaining paths
		while (nextLeft != left) {
			left = nextLeft;
			blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, nextLeft);
			validLeftApprovers = blockService.getSolidApproverBlocks(left.getBlock().getHash());
			validLeftApprovers.removeIf(b -> validatorService.isIneligible(b, currentApprovedNonMilestoneBlocks));
			nextLeft = performStep(left, validLeftApprovers);
		}
		while (nextRight != right) {
			right = nextRight;
			blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, nextRight);
			validRightApprovers = blockService.getSolidApproverBlocks(right.getBlock().getHash());
			validRightApprovers.removeIf(b -> validatorService.isIneligible(b, currentApprovedNonMilestoneBlocks));
			nextRight = performStep(right, validRightApprovers);
		}

		watch.stop();
		log.info("getValidatedBlockPairIteratively time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return Pair.of(left.getBlock().getHash(), right.getBlock().getHash());
	}

	private Sha256Hash randomWalk(Sha256Hash blockHash, long maxTime) throws Exception {
		// Repeatedly perform transitions until the final tip is found  
		BlockWrap currentBlock = store.getBlockWrap(blockHash);
		List<BlockWrap> approvers = blockService.getSolidApproverBlocks(blockHash);
		approvers.removeIf(b -> b.getBlockEvaluation().getInsertTime() > maxTime);      
		BlockWrap nextBlock = performStep(currentBlock, approvers);
		
		while (currentBlock != nextBlock) {
			currentBlock = nextBlock;
			approvers = blockService.getSolidApproverBlocks(currentBlock.getBlock().getHash());
			approvers.removeIf(b -> b.getBlockEvaluation().getInsertTime() > maxTime);       
			nextBlock = performStep(currentBlock, approvers);
		}
		return currentBlock.getBlock().getHash();
	}

	/**
	 * Performs one step of MCMC random walk by cumulative weight.
	 * 
	 * @param currentBlock the block to take a step from
	 * @param approvers all blocks approving the block that are allowed to go to
	 * @return currentBlock if no further steps possible, else a new block from approvers
	 */
	public BlockWrap performStep(BlockWrap currentBlock, List<BlockWrap> approvers) {
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
				double alpha = 0.5 * Math.exp(-0.05*
						Math.max(0.0, (currentBlock.getBlockEvaluation().getMilestoneDepth() - 30)));
				alpha = Math.max(0.0, alpha);
				alpha = Math.min(1.5, alpha);
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

	private List<Sha256Hash> getRatingEntryPoints(int count) throws Exception {
		List<BlockEvaluation> candidates = blockService.getRatingEntryPointCandidates();
		return pullByCumulativeWeight(candidates, count);
	}

	private List<Sha256Hash> getValidationEntryPoints(int count) throws Exception {
		List<BlockEvaluation> candidates = blockService.getValidationEntryPointCandidates();
		return pullByCumulativeWeight(candidates, count);
	}

	private List<Sha256Hash> pullByCumulativeWeight(List<BlockEvaluation> candidates, int count) {
		double maxBlockWeight = candidates.stream().mapToLong(e -> e.getCumulativeWeight()).max().orElse(1L);
		double normalizedBlockWeightSum = candidates.stream().mapToDouble(e -> e.getCumulativeWeight() / maxBlockWeight)
				.sum();
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
						results.add(selectedBlock.getBlockHash());
						break;
					}
				}
			}
		}

		return results;
	}
}
