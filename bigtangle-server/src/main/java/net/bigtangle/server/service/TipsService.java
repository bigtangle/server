/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.store.DatabaseFullPrunedBlockStore;

@Service
public class TipsService {
	private final Logger log = LoggerFactory.getLogger(TipsService.class);

	@Autowired
	protected DatabaseFullPrunedBlockStore store;
	@Autowired
	private BlockService blockService;
	@Autowired
	protected NetworkParameters networkParameters;
	@Autowired
	private ValidatorService validatorService;

	private static Random seed = new Random();

	/**
	 * Performs MCMC without walker restrictions.
	 * Note: We cannot disallow blocks conflicting with the
     * milestone, since reorgs must be allowed to happen. We cannot check if
     * given blocks are eligible without the milestone since that is not
     * efficiently computable.
     * 
	 * @param count The number of rating tips.
	 * @return A list of rating tips.
	 * @throws BlockStoreException
	 */
	public Collection<BlockWrap> getRatingTips(int count) throws BlockStoreException {
		Stopwatch watch = Stopwatch.createStarted();
		
		List<BlockWrap> entryPoints = getRatingEntryPoints(count);
		Collection<BlockWrap> results = new ConcurrentLinkedQueue<>();
//		long latestImportTime = store.getMaxImportTime();

	    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		try {
		    for (BlockWrap entryPoint : entryPoints) {
		        executor.submit(new Runnable() {
		            @Override
		            public void run() {
                        try {
                            BlockWrap ratingTip = getRatingTip(entryPoint, Long.MAX_VALUE);
                            results.add(ratingTip);
                            store.closeThread();
                        } catch (BlockStoreException e) {
                            log.error(e.getLocalizedMessage());
                        }
		            }
		        });
		    }
		} finally {
		    try {
		        executor.shutdown();
		        executor.awaitTermination(300, TimeUnit.SECONDS);
		    }
		    catch (InterruptedException e) {
		        System.err.println("Tasks interrupted");
		    }
		    finally {
		        if (!executor.isTerminated()) {
		            System.err.println("Cancel non-finished tasks");
		        }
		        executor.shutdownNow();
		        System.out.println("Shutdown finished");
		    }
		}

		watch.stop();
		log.info("getRatingTips with count {} time {} ms.",count, watch.elapsed(TimeUnit.MILLISECONDS));

		return results;
	}

    /**
     * Selects two blocks to approve via MCMC
     * 
     * @return Two blockhashes selected via MCMC
     * @throws BlockStoreException
     */
    public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair() throws BlockStoreException {
        return getValidatedBlockPair(new HashSet<>());
    }

    /**
     * Selects two blocks to approve via MCMC for the given prototype block such that the two approved blocks are not conflicting with the prototype block itself
     * 
     * @param prototype Invalid block that uses the contents of the 
     * @return Two blockhashes selected via MCMC
     * @throws VerificationException if the given prototype is not compatible with the current milestone
     */
    public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPairCompatibleWithPrototype(Block prototype) throws BlockStoreException {
        HashSet<BlockWrap> currentApprovedNonMilestoneBlocks = new HashSet<>();
        blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, store.getBlockWrap(prototype.getHash()));
        return getValidatedBlockPair(currentApprovedNonMilestoneBlocks);
    }

    /**
     * Selects two blocks to approve via MCMC for the given start block such that the two approved blocks are not conflicting with the prototype block itself
     * 
     * @param prototype One starting point for the MCMC walks
     * @return Two blockhashes selected via MCMC
     * @throws VerificationException if the given prototype is not compatible with the current milestone
     */
    public Pair<Sha256Hash, Sha256Hash> getValidatedBlockPairStartingFrom(BlockWrap prototype) throws BlockStoreException {
        HashSet<BlockWrap> currentApprovedNonMilestoneBlocks = new HashSet<>();
        blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, store.getBlockWrap(prototype.getBlockHash()));
        return getValidatedBlockPair(currentApprovedNonMilestoneBlocks, prototype);
    }

    private Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair(HashSet<BlockWrap> currentApprovedNonMilestoneBlocks) throws BlockStoreException {
        List<BlockWrap> entryPoints = getValidationEntryPoints(2);
        BlockWrap left = entryPoints.get(0);
        BlockWrap right = entryPoints.get(1);
        return getValidatedBlockPair(currentApprovedNonMilestoneBlocks, left, right);
    }

    private Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair(HashSet<BlockWrap> currentApprovedNonMilestoneBlocks, BlockWrap left) throws BlockStoreException {
        List<BlockWrap> entryPoints = getValidationEntryPoints(1);
        BlockWrap right = entryPoints.get(0);
        return getValidatedBlockPair(currentApprovedNonMilestoneBlocks, left, right);
    }

	private Pair<Sha256Hash, Sha256Hash> getValidatedBlockPair(HashSet<BlockWrap> currentApprovedNonMilestoneBlocks, BlockWrap left, BlockWrap right) throws BlockStoreException {
		Stopwatch watch = Stopwatch.createStarted();
		
		// Initialize approved blocks
		blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, left);
		blockService.addApprovedNonMilestoneBlocksTo(currentApprovedNonMilestoneBlocks, right);
        
        // Necessary: Initial test if the prototype's currentApprovedNonMilestoneBlocks are actually valid
        if (!validatorService.isEligibleForApprovalSelection(currentApprovedNonMilestoneBlocks))
            throw new VerificationException("The given prototype is invalid under the current milestone");

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
		log.info("getValidatedBlockPair iteration time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return Pair.of(left.getBlock().getHash(), right.getBlock().getHash());
	}

	// Does not redo finding next step if next step was still valid
	private BlockWrap validateOrPerformValidatedStep(BlockWrap fromBlock, HashSet<BlockWrap> currentApprovedNonMilestoneBlocks,
			BlockWrap potentialNextBlock) throws BlockStoreException {
		if (validatorService.isEligibleForApprovalSelection(potentialNextBlock, currentApprovedNonMilestoneBlocks))
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
		} while (!validatorService.isEligibleForApprovalSelection(result, currentApprovedNonMilestoneBlocks));
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

	/**
	 * Performs one step of MCMC random walk by cumulative weight.
	 * 
	 * @param currentBlock
	 *            the block to take a step from
	 * @param candidates
	 *            all blocks approving the block that are allowed to go to
	 * @return currentBlock if no further steps possible, else a new block from
	 *         approvers
	 */
	public BlockWrap performTransition(BlockWrap currentBlock, List<BlockWrap> candidates) {
		if (candidates.size() == 0) {
			return currentBlock;
		} else if (candidates.size() == 1) {
			return candidates.get(0);
		} else {
			double[] transitionWeights = new double[candidates.size()];
			double transitionWeightSum = 0;
			long currentCumulativeWeight = currentBlock.getBlockEvaluation().getCumulativeWeight();

			// Calculate the unnormalized transition weights
			for (int i = 0; i < candidates.size(); i++) {
				// Scale alpha up if very deep to prevent splitting attack
				double alpha = 0.1
						* Math.exp(0.05 * Math.max(0.0, (currentBlock.getBlockEvaluation().getMilestoneDepth() - NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF/2)));
				
				// Clamp
				alpha = Math.max(0.001, alpha);
				alpha = Math.min(1.5, alpha);
				
				// Calculate transition weights
				transitionWeights[i] = Math.exp(-alpha
						* (currentCumulativeWeight - candidates.get(i).getBlockEvaluation().getCumulativeWeight()));
				transitionWeightSum += transitionWeights[i];
			}

			// Randomly select one of the approvers by transition probabilities
			double transitionRealization = seed.nextDouble() * transitionWeightSum;
			for (int i = 0; i < candidates.size(); i++) {
				transitionRealization -= transitionWeights[i];
				if (transitionRealization <= 0) {
					return candidates.get(i);
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
			// Randomly select weighted by cumulative weights
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
