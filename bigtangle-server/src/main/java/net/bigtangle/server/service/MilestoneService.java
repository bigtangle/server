/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;

/*
 *  This service offers maintenance functions to update the local state of the Tangle
 */
@Service
public class MilestoneService {
	private static final Logger log = LoggerFactory.getLogger(MilestoneService.class);
	private static final int WARNING_MILESTONE_UPDATE_LOOPS = 20;

	@Autowired
	protected FullPrunedBlockGraph blockGraphService;
	@Autowired
	protected FullPrunedBlockStore store;
	@Autowired
	private BlockService blockService;
	@Autowired
	private TipService tipsService;
	@Autowired
	private ValidatorService validatorService;

	private final Semaphore lock = new Semaphore(1, true);

	/**
	 * Scheduled update function that updates the Tangle
	 * 
	 * @throws Exception
	 */
	public void update() throws Exception {
		if (!lock.tryAcquire()) {
			log.debug("Milestone Update already running. Returning...");
			return;
		}

		try {
			log.info("Milestone Update started");
			// clearCacheBlockEvaluations();

			Stopwatch watch = Stopwatch.createStarted();
			updateMaintained();
			log.info("Maintained update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

			watch.stop();
			watch = Stopwatch.createStarted();
			updateWeightAndDepth();
			log.info("Weight and depth update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

			watch.stop();
			watch = Stopwatch.createStarted();
			updateRating();
			log.info("Rating update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

			watch.stop();
			watch = Stopwatch.createStarted();
			updateMilestone();
			log.info("Milestone update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

			watch.stop();
			watch = Stopwatch.createStarted();
			updateMilestoneDepth();
			log.info("Milestonedepth update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

			watch.stop();
		} finally {
			lock.release();
		}
	}

	@CacheEvict(cacheNames = "BlockEvaluations", allEntries = true)
	private void clearCacheBlockEvaluations() throws Exception {
	}

	/**
	 * Update cumulative weight: the amount of blocks a block is approved by. Update
	 * depth: the longest chain of blocks to a tip
	 * 
	 * @throws BlockStoreException
	 */
	private void updateWeightAndDepth() throws BlockStoreException {
		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockWrap> blockQueue = blockService.getSolidTipsDescending();
		HashMap<Sha256Hash, HashSet<Sha256Hash>> approvers = new HashMap<>();
		HashMap<Sha256Hash, Long> depths = new HashMap<>();

		// Initialize weight and depth of tips
		for (BlockWrap tip : blockQueue) {
			approvers.put(tip.getBlockHash(), new HashSet<>());
			depths.put(tip.getBlockHash(), 0L);
		}

		BlockWrap currentBlock = null;
		while ((currentBlock = blockQueue.poll()) != null) {
			Sha256Hash currentBlockHash = currentBlock.getBlockHash();

			// Abort if unmaintained, since it will be irrelevant for any tip selections
			if (!currentBlock.getBlockEvaluation().isMaintained())
				continue;

			// Add your own hash to approver hashes of current approver hashes
			approvers.get(currentBlockHash).add(currentBlockHash);

			// Add all current references to both approved blocks
			Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
			subUpdateWeightAndDepth(blockQueue, approvers, depths, currentBlockHash, prevTrunk);

			Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
			subUpdateWeightAndDepth(blockQueue, approvers, depths, currentBlockHash, prevBranch);

			// Update and dereference
			blockService.updateWeightAndDepth(currentBlock.getBlockEvaluation(), approvers.get(currentBlockHash).size(),
					depths.get(currentBlockHash));
			approvers.remove(currentBlockHash);
			depths.remove(currentBlockHash);
		}
	}

	private void subUpdateWeightAndDepth(PriorityQueue<BlockWrap> blockQueue,
			HashMap<Sha256Hash, HashSet<Sha256Hash>> approvers, HashMap<Sha256Hash, Long> depths,
			Sha256Hash currentBlockHash, Sha256Hash approvedBlockHash) throws BlockStoreException {
		Long currentDepth = depths.get(currentBlockHash);
		HashSet<Sha256Hash> currentApprovers = approvers.get(currentBlockHash);
		if (!approvers.containsKey(approvedBlockHash)) {
			BlockWrap prevBlock = store.getBlockWrap(approvedBlockHash);
			if (prevBlock != null) {
				blockQueue.add(prevBlock);
				approvers.put(approvedBlockHash, new HashSet<>(currentApprovers));
				depths.put(approvedBlockHash, currentDepth + 1);
			}
		} else {
			approvers.get(approvedBlockHash).addAll(currentApprovers);
			if (currentDepth + 1 > depths.get(approvedBlockHash))
				depths.put(approvedBlockHash, currentDepth + 1);
		}
	}

	/**
	 * Update MilestoneDepth: the longest forward path to a milestone block
	 * 
	 * @throws BlockStoreException
	 */
	private void updateMilestoneDepth() throws BlockStoreException {
		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockWrap> blockQueue = blockService.getSolidTipsDescending();
		HashMap<Sha256Hash, Long> milestoneDepths = new HashMap<>();

		// Initialize milestone depths as -1
		for (BlockWrap tip : blockQueue) {
			milestoneDepths.put(tip.getBlockHash(), -1L);
		}

		BlockWrap currentBlock = null;
		while ((currentBlock = blockQueue.poll()) != null) {
			// Abort if unmaintained, since it will be irrelevant (new milestone blocks will
			// always be relevant since lower cut-off is set to zero
			if (!currentBlock.getBlockEvaluation().isMaintained())
				continue;

			// If depth is set to -1 and we are milestone, set to 0
			if (milestoneDepths.get(currentBlock.getBlockHash()) == -1L
					&& currentBlock.getBlockEvaluation().isMilestone())
				milestoneDepths.put(currentBlock.getBlockHash(), 0L);

			// Add all current references to both approved blocks
			Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
			subUpdateMilestoneDepth(blockQueue, milestoneDepths, currentBlock, prevTrunk);

			Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
			subUpdateMilestoneDepth(blockQueue, milestoneDepths, currentBlock, prevBranch);

			// Update and dereference
			blockService.updateMilestoneDepth(currentBlock.getBlockEvaluation(),
					milestoneDepths.get(currentBlock.getBlockHash()));
			milestoneDepths.remove(currentBlock.getBlockHash());
		}
	}

	private void subUpdateMilestoneDepth(PriorityQueue<BlockWrap> blockQueue,
			HashMap<Sha256Hash, Long> milestoneDepths, BlockWrap currentBlock, Sha256Hash approvedBlock)
			throws BlockStoreException {
		boolean isMilestone = currentBlock.getBlockEvaluation().isMilestone();
		long milestoneDepth = milestoneDepths.get(currentBlock.getBlockHash());
		if (!milestoneDepths.containsKey(approvedBlock)) {
			BlockWrap prevBlock = store.getBlockWrap(approvedBlock);
			if (prevBlock != null) {
				blockQueue.add(prevBlock);
				milestoneDepths.put(prevBlock.getBlockHash(), isMilestone ? milestoneDepth + 1 : -1L);
			}
		} else {
			if (isMilestone)
				if (milestoneDepth + 1 > milestoneDepths.get(approvedBlock))
					milestoneDepths.put(approvedBlock, milestoneDepth + 1);
		}
	}

	/**
	 * Update rating: the percentage of times that tips selected by MCMC approve a
	 * block
	 * 
	 * @throws Exception
	 */
	private void updateRating() throws Exception {
		// Select #tipCount solid tips via MCMC
		HashMap<BlockWrap, HashSet<UUID>> selectedTipApprovers = new HashMap<BlockWrap, HashSet<UUID>>(
				NetworkParameters.MAX_RATING_TIP_COUNT);
		List<BlockWrap> selectedTips = tipsService.getRatingTips(NetworkParameters.MAX_RATING_TIP_COUNT);

		// Initialize all approvers
		for (BlockWrap selectedTip : selectedTips) {
			if (selectedTipApprovers.containsKey(selectedTip)) {
				HashSet<UUID> result = selectedTipApprovers.get(selectedTip);
				result.add(UUID.randomUUID());
			} else {
				HashSet<UUID> result = new HashSet<>();
				result.add(UUID.randomUUID());
				selectedTipApprovers.put(selectedTip, result);
			}
		}

		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockWrap> blockQueue = blockService.getSolidTipsDescending();
		HashMap<Sha256Hash, HashSet<UUID>> approvers = new HashMap<>();
		for (BlockWrap tip : blockQueue) {
			approvers.put(tip.getBlock().getHash(), new HashSet<>());
		}

		BlockWrap currentBlock = null;
		while ((currentBlock = blockQueue.poll()) != null) {
			// Abort if unmaintained and in milestone only (for now)
			if (!currentBlock.getBlockEvaluation().isMaintained() && currentBlock.getBlockEvaluation().isMilestone())
				continue;

			// Add your own hashes as reference if current block is one of the
			// selected tips
			if (selectedTipApprovers.containsKey(currentBlock))
				approvers.get(currentBlock.getBlockHash()).addAll(selectedTipApprovers.get(currentBlock));

			// Add all current references to both approved blocks (initialize if
			// not yet initialized)
			Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
			subUpdateRating(blockQueue, approvers, currentBlock, prevTrunk);

			Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
			subUpdateRating(blockQueue, approvers, currentBlock, prevBranch);

			// Update your rating
			blockService.updateRating(currentBlock.getBlockEvaluation(),
					approvers.get(currentBlock.getBlockHash()).size());
			approvers.remove(currentBlock.getBlockHash());
		}
	}

	private void subUpdateRating(PriorityQueue<BlockWrap> blockQueue,
			HashMap<Sha256Hash, HashSet<UUID>> approvers, BlockWrap currentBlock, Sha256Hash prevTrunk)
			throws BlockStoreException {
		if (!approvers.containsKey(prevTrunk)) {
			BlockWrap prevBlock = store.getBlockWrap(prevTrunk);
			if (prevBlock != null) {
				blockQueue.add(prevBlock);
				approvers.put(prevBlock.getBlockHash(), new HashSet<>(approvers.get(currentBlock.getBlockHash())));
			}
		} else {
			approvers.get(prevTrunk).addAll(approvers.get(currentBlock.getBlockHash()));
		}
	}

	/**
	 * Updates milestone field in block evaluation and changes output table
	 * correspondingly
	 * 
	 * @throws BlockStoreException
	 */
	private void updateMilestone() throws BlockStoreException {
		// First remove any blocks that should no longer be in the milestone
		HashSet<BlockEvaluation> blocksToRemove = blockService.getBlocksToRemoveFromMilestone();
		for (BlockEvaluation block : blocksToRemove)
			blockService.unconfirm(block);

		for (int i = 0; true; i++) {
			// Now try to find blocks that can be added to the milestone
			HashSet<BlockWrap> blocksToAdd = blockService.getBlocksToAddToMilestone();

			// VALIDITY CHECKS
			validatorService.resolveValidityConflicts(blocksToAdd, true);

			// Finally add the found new milestone blocks to the milestone
			for (BlockWrap block : blocksToAdd)
				blockService.confirm(block.getBlockEvaluation());

			// Exit condition: there are no more blocks to add
			if (blocksToAdd.isEmpty())
				break;

			if (i == WARNING_MILESTONE_UPDATE_LOOPS)
				log.warn("High amount of milestone updates per scheduled update. Can't keep up or reorganizing!");
		}
	}

	/**
	 * Updates maintained field in block evaluation Sets maintained to true if
	 * reachable by a rating entry point, else false.
	 * 
	 * @throws BlockStoreException
	 */
	private void updateMaintained() throws BlockStoreException {
		HashSet<Sha256Hash> maintainedBlockHashes = store.getMaintainedBlockHashes();
		HashSet<Sha256Hash> traversedBlockHashes = new HashSet<>();
		PriorityQueue<BlockWrap> blocks = getRatingEntryPointsAscendingAsPriorityQueue();
		HashSet<BlockWrap> blocksToTraverse = new HashSet<>(blocks);

		// Now set maintained in order of ascending height
		BlockWrap currentBlock = null;
		while ((currentBlock = blocks.poll()) != null) {
			blocksToTraverse.remove(currentBlock);
			traversedBlockHashes.add(currentBlock.getBlockHash());
			List<BlockWrap> solidApproverBlocks = blockService.getSolidApproverBlocks(currentBlock.getBlockHash());
			for (BlockWrap b : solidApproverBlocks) {
				if (blocksToTraverse.contains(b))
					continue;

				blocks.add(b);
				blocksToTraverse.add(b);
			}
		}

		// Unset no longer maintained blocks
		for (Sha256Hash hash : maintainedBlockHashes.stream().filter(h -> !traversedBlockHashes.contains(h))
				.collect(Collectors.toList()))
			store.updateBlockEvaluationMaintained(hash, false);

		// Set now maintained blocks
		for (Sha256Hash hash : traversedBlockHashes.stream().filter(h -> !maintainedBlockHashes.contains(h))
				.collect(Collectors.toList()))
			store.updateBlockEvaluationMaintained(hash, true);
	}

	/**
	 * Returns all rating entry point candidates ordered by ascending height
	 * 
	 * @return solid tips by ordered by descending height
	 * @throws BlockStoreException
	 */
	private PriorityQueue<BlockWrap> getRatingEntryPointsAscendingAsPriorityQueue() throws BlockStoreException {
		List<BlockWrap> candidates = blockService.getRatingEntryPointCandidates();
		if (candidates.isEmpty())
			throw new IllegalStateException("No rating entry point candidates were found!");

		PriorityQueue<BlockWrap> blocksByDescendingHeight = new PriorityQueue<BlockWrap>(candidates.size(),
				Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()));
		blocksByDescendingHeight.addAll(candidates);
		return blocksByDescendingHeight;
	}
}
