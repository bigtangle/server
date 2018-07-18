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

import net.bigtangle.core.Block;
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
	private TipsService tipsService;
	@Autowired
	private ValidatorService validatorService;

	private final Semaphore lock = new Semaphore(1, true);

	/**
	 * Scheduled update function that updates the Tangle
	 * 
	 * @throws Exception
	 */
	public void update() throws Exception {
		lock.acquire();

		try {
			log.info("Milestone Update started");
			// clearCacheBlockEvaluations();

			Stopwatch watch = Stopwatch.createStarted();
			updateMaintained();
			log.info("Maintained update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

			watch.stop();
			watch = Stopwatch.createStarted();
			updateCumulativeWeightAndDepth();
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
	 * Update cumulative weight, the amount of blocks a block is approved by
	 * 
	 * @throws BlockStoreException
	 */
	private void updateCumulativeWeightAndDepth() throws BlockStoreException {
		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockWrap> blocksByDescendingHeight = blockService.getSolidTipsDescending();
		HashMap<Sha256Hash, HashSet<Sha256Hash>> approverHashSets = new HashMap<>();
		HashMap<Sha256Hash, Long> depths = new HashMap<>();
		for (BlockWrap tip : blocksByDescendingHeight) {
			approverHashSets.put(tip.getBlockHash(), new HashSet<>());
			depths.put(tip.getBlockHash(), 0L);
		}

		BlockWrap currentBlock = null;
		while ((currentBlock = blocksByDescendingHeight.poll()) != null) {
			// Abort if unmaintained
			if (!currentBlock.getBlockEvaluation().isMaintained())
				continue;

			// Add your own hash to approver hashes of current approver hashes
			HashSet<Sha256Hash> approverHashes = approverHashSets.get(currentBlock.getBlockHash());
			approverHashes.add(currentBlock.getBlockHash());
			long depth = depths.get(currentBlock.getBlockHash());

			// Add all current references to both approved blocks (initialize if
			// not yet initialized)
			Block block = blockService.getBlock(currentBlock.getBlockHash());

			if (!approverHashSets.containsKey(block.getPrevBlockHash())) {
				BlockWrap prevBlockEvaluation = store.getBlockWrap(block.getPrevBlockHash());
				if (prevBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBlockEvaluation);
					approverHashSets.put(prevBlockEvaluation.getBlockHash(), new HashSet<>());
					depths.put(prevBlockEvaluation.getBlockHash(), 0L);
				}
			}

			if (!approverHashSets.containsKey(block.getPrevBranchBlockHash())) {
				BlockWrap prevBranchBlockEvaluation = store.getBlockWrap(block.getPrevBranchBlockHash());
				if (prevBranchBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBranchBlockEvaluation);
					approverHashSets.put(prevBranchBlockEvaluation.getBlockHash(), new HashSet<>());
					depths.put(prevBranchBlockEvaluation.getBlockHash(), 0L);
				}
			}

			if (approverHashSets.containsKey(block.getPrevBlockHash())) {
				approverHashSets.get(block.getPrevBlockHash()).addAll(approverHashes);
				if (depth + 1 > depths.get(block.getPrevBlockHash())) {
					depths.put(block.getPrevBlockHash(), depth + 1);
				}
			}

			if (approverHashSets.containsKey(block.getPrevBranchBlockHash())) {
				approverHashSets.get(block.getPrevBranchBlockHash()).addAll(approverHashes);
				if (depth + 1 > depths.get(block.getPrevBranchBlockHash())) {
					depths.put(block.getPrevBranchBlockHash(), depth + 1);
				}
			}

			// Update and dereference
			blockService.updateCumulativeWeight(currentBlock.getBlockEvaluation(), approverHashes.size());
			blockService.updateDepth(currentBlock.getBlockEvaluation(), depth);
			approverHashSets.remove(currentBlock.getBlockHash());
			depths.remove(currentBlock.getBlockHash());
		}
	}

	/**
	 * Update cumulative weight, the amount of blocks a block is approved by
	 * 
	 * @throws BlockStoreException
	 */
	private void updateMilestoneDepth() throws BlockStoreException {
		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockWrap> blocksByDescendingHeight = blockService.getSolidTipsDescending();
		HashMap<Sha256Hash, Long> milestoneDepths = new HashMap<>();
		for (BlockWrap tip : blocksByDescendingHeight) {
			milestoneDepths.put(tip.getBlockHash(), -1L);
		}

		BlockWrap currentBlock = null;
		while ((currentBlock = blocksByDescendingHeight.poll()) != null) {
			// Abort if unmaintained
			if (!currentBlock.getBlockEvaluation().isMaintained())
				continue;

			// Add all current references to both approved blocks (initialize if
			// not yet initialized)
			long milestoneDepth = milestoneDepths.get(currentBlock.getBlockHash());
			Block block = blockService.getBlock(currentBlock.getBlockHash());

			if (!milestoneDepths.containsKey(block.getPrevBlockHash())) {
				BlockWrap prevBlockEvaluation = store.getBlockWrap(block.getPrevBlockHash());
				if (prevBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBlockEvaluation);
					milestoneDepths.put(prevBlockEvaluation.getBlockHash(), -1L);
				}
			}

			if (!milestoneDepths.containsKey(block.getPrevBranchBlockHash())) {
				BlockWrap prevBranchBlockEvaluation = store.getBlockWrap(block.getPrevBranchBlockHash());
				if (prevBranchBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBranchBlockEvaluation);
					milestoneDepths.put(prevBranchBlockEvaluation.getBlockHash(), -1L);
				}
			}

			if (milestoneDepths.containsKey(block.getPrevBlockHash())) {
				if (currentBlock.getBlockEvaluation().isMilestone() && milestoneDepth + 1 > milestoneDepths.get(block.getPrevBlockHash())) {
					milestoneDepths.put(block.getPrevBlockHash(), milestoneDepth + 1);
				}
			}

			if (milestoneDepths.containsKey(block.getPrevBranchBlockHash())) {
				if (currentBlock.getBlockEvaluation().isMilestone()
						&& milestoneDepth + 1 > milestoneDepths.get(block.getPrevBranchBlockHash())) {
					milestoneDepths.put(block.getPrevBranchBlockHash(), milestoneDepth + 1);
				}
			}

			// Update and dereference
			blockService.updateMilestoneDepth(currentBlock.getBlockEvaluation(), milestoneDepth + 1);
			milestoneDepths.remove(currentBlock.getBlockHash());
		}
	}

	/**
	 * Update the percentage of times that tips selected by MCMC approve a block
	 * 
	 * @throws Exception
	 */
	private void updateRating() throws Exception {
		// Select #tipCount solid tips via MCMC
		HashMap<BlockWrap, HashSet<UUID>> selectedTips = new HashMap<BlockWrap, HashSet<UUID>>(
				NetworkParameters.MAX_RATING_TIP_COUNT);
		List<Sha256Hash> selectedTipHashes = tipsService.getRatingTips(NetworkParameters.MAX_RATING_TIP_COUNT);
		
		for (Sha256Hash selectedTipHash : selectedTipHashes) {
			BlockWrap selectedTip = store.getBlockWrap(selectedTipHash);
			if (selectedTips.containsKey(selectedTip)) {
				HashSet<UUID> result = selectedTips.get(selectedTip);
				result.add(UUID.randomUUID());
			} else {
				HashSet<UUID> result = new HashSet<>();
				result.add(UUID.randomUUID());
				selectedTips.put(selectedTip, result);
			}
		}

		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockWrap> blocksByDescendingHeight = blockService.getSolidTipsDescending();
		HashMap<Sha256Hash, HashSet<UUID>> approverHashSets = new HashMap<>();
		for (BlockWrap tip : blocksByDescendingHeight) {
			approverHashSets.put(tip.getBlock().getHash(), new HashSet<>());
		}

		BlockWrap currentBlock = null;
		while ((currentBlock = blocksByDescendingHeight.poll()) != null) {
			// Abort if unmaintained
			if (!currentBlock.getBlockEvaluation().isMaintained() && currentBlock.getBlockEvaluation().isMilestone())
				continue;

			// Add your own hashes as reference if current block is one of the
			// selected tips
			HashSet<UUID> approverHashes = approverHashSets.get(currentBlock.getBlockHash());
			if (selectedTips.containsKey(currentBlock)) {
				approverHashes.addAll(selectedTips.get(currentBlock));
			}

			// Add all current references to both approved blocks (initialize if
			// not yet initialized)
			Block block = blockService.getBlock(currentBlock.getBlockHash());

			if (!approverHashSets.containsKey(block.getPrevBlockHash())) {
				BlockWrap prevBlockEvaluation = store.getBlockWrap(block.getPrevBlockHash());
				if (prevBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBlockEvaluation);
					approverHashSets.put(prevBlockEvaluation.getBlockHash(), new HashSet<>());
				}
			}

			if (!approverHashSets.containsKey(block.getPrevBranchBlockHash())) {
				BlockWrap prevBranchBlockEvaluation = store.getBlockWrap(block.getPrevBranchBlockHash());
				if (prevBranchBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBranchBlockEvaluation);
					approverHashSets.put(prevBranchBlockEvaluation.getBlockHash(), new HashSet<>());
				}
			}

			if (approverHashSets.containsKey(block.getPrevBlockHash()))
				approverHashSets.get(block.getPrevBlockHash()).addAll(approverHashes);

			if (approverHashSets.containsKey(block.getPrevBranchBlockHash()))
				approverHashSets.get(block.getPrevBranchBlockHash()).addAll(approverHashes);

			// Update your rating
			blockService.updateRating(currentBlock.getBlockEvaluation(), approverHashes.size());
			approverHashSets.remove(currentBlock.getBlockHash());
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
		PriorityQueue<BlockEvaluation> blocks = getRatingEntryPointsAscendingAsPriorityQueue();
		HashSet<BlockEvaluation> blocksToTraverse = new HashSet<>(blocks);

		// Now set maintained in order of ascending height
		BlockEvaluation currentBlock = null;
		while ((currentBlock = blocks.poll()) != null) {
			blocksToTraverse.remove(currentBlock);
			traversedBlockHashes.add(currentBlock.getBlockHash());
			List<BlockWrap> solidApproverBlocks = blockService.getSolidApproverBlocks(currentBlock.getBlockHash());
			for (BlockWrap b : solidApproverBlocks) {
				if (blocksToTraverse.contains(b.getBlockEvaluation()))
					continue;

				blocks.add(b.getBlockEvaluation());
				blocksToTraverse.add(b.getBlockEvaluation());
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
	private PriorityQueue<BlockEvaluation> getRatingEntryPointsAscendingAsPriorityQueue() throws BlockStoreException {
		List<BlockEvaluation> candidates = blockService.getRatingEntryPointCandidates();
		if (candidates.isEmpty())
			throw new IllegalStateException("No rating entry point candidates were found!");

		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = new PriorityQueue<BlockEvaluation>(candidates.size(),
				Comparator.comparingLong(BlockEvaluation::getHeight));
		blocksByDescendingHeight.addAll(candidates);
		return blocksByDescendingHeight;
	}
}
