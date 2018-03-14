/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.CollectionUtils;
import org.springframework.stereotype.Service;

import com.google.common.collect.HashMultiset;

/*
 *  check the valuation of block and trigger an update of openoutputs
 */
@Service
public class MilestoneService {

	@Autowired
	protected FullPrunedBlockStore store;

	@Autowired
	private BlockService blockService;

	@Autowired
	private TipsService tipsService;

	enum Validity {
		VALID, INVALID, INCOMPLETE
	}

	public Snapshot latestSnapshot;

	public Sha256Hash latestMilestone = Sha256Hash.ZERO_HASH;
	public Sha256Hash latestSolidSubtangleMilestone = latestMilestone;

	public static final int MILESTONE_START_INDEX = 338000;
	private static final int NUMBER_OF_KEYS_IN_A_MILESTONE = 20;

	public int latestMilestoneIndex = MILESTONE_START_INDEX;
	public int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

	private final Set<Sha256Hash> analyzedMilestoneCandidates = new HashSet<>();

	private Validity validateMilestone(Block block) throws Exception {
		return Validity.VALID;
	}

	void updateLatestSolidSubtangleMilestone() throws Exception {

	}

	/*****************************************************
	 * Experimental update methods *
	 ******************************************************/
	// Sorts blocks by descending height
	private Comparator<BlockEvaluation> sortBlocksByDescendingHeight = new Comparator<BlockEvaluation>() {
		@Override
		public int compare(BlockEvaluation arg0, BlockEvaluation arg1) {
			long res = (arg1.getHeight() - arg0.getHeight());
			if (res > 0)
				return 1;
			if (res < 0)
				return -1;
			return 0;
		}
	};

	private PriorityQueue<BlockEvaluation> getSolidTipsDescending() {
		List<BlockEvaluation> solidTips = blockService.getLastSolidTips();
		CollectionUtils.filter(solidTips, e -> ((BlockEvaluation) e).isSolid());
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = new PriorityQueue<BlockEvaluation>(solidTips.size(),
				sortBlocksByDescendingHeight);
		blocksByDescendingHeight.addAll(solidTips);
		return blocksByDescendingHeight;
	}

	/*
	 * Update solid true if all directly or indirectly approved blocks exist Update
	 * height to be the sum of previous heights
	 */
	public void updateSolidityAndHeight() throws Exception {
		List<BlockEvaluation> tips = blockService.getAllTips();
		for (BlockEvaluation tip : tips)
			updateSolidityAndHeightRecursive(tip);
	}

	private boolean updateSolidityAndHeightRecursive(BlockEvaluation blockEvaluation) throws BlockStoreException {
		// Solid blocks stay solid
		if (blockEvaluation.isSolid()) {
			return true;
		}

		// Missing blocks -> not solid, request from network
		Block block = blockService.getBlock(blockEvaluation.getBlockhash());
		if (block == null) {
			// TODO broken graph, download the missing remote block needed
			blockService.updateSolid(blockEvaluation, false);
			return false;
		}

		boolean prevBlockSolid = false;
		boolean prevBranchBlockSolid = false;

		// Check previous trunk block exists and is solid
		BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
		if (prevBlockEvaluation == null) {
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBlockSolid = updateSolidityAndHeightRecursive(prevBlockEvaluation);
		}

		// Check previous branch block exists and is solid
		BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
		if (prevBranchBlockEvaluation == null) {
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBranchBlockSolid = updateSolidityAndHeightRecursive(prevBlockEvaluation);
		}

		// If both previous blocks are solid, our block is solid and should be
		// solidified
		if (prevBlockSolid && prevBranchBlockSolid) {
			solidifyBlock(blockEvaluation, prevBlockEvaluation, prevBranchBlockEvaluation);
			return true;
		} else {
			blockService.updateSolid(blockEvaluation, false);
			return false;
		}
	}

	private void solidifyBlock(BlockEvaluation blockEvaluation, BlockEvaluation prevBlockEvaluation,
			BlockEvaluation prevBranchBlockEvaluation) {
		blockService.updateHeight(blockEvaluation,
				Math.max(prevBlockEvaluation.getHeight() + 1, prevBranchBlockEvaluation.getHeight() + 1));
		blockService.updateSolid(blockEvaluation, true);
	}

	/*
	 * Update depth, the length of the longest reverse-oriented path to some tip.
	 */
	public void updateDepths() throws BlockStoreException {
		// Select solid tips to begin from
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = getSolidTipsDescending();

		// Initialize tips with depth 0
		for (BlockEvaluation blockEvaluation : blocksByDescendingHeight) {
			blockService.updateDepth(blockEvaluation, 0);
		}

		// Update the depth going backwards
		BlockEvaluation currentBlockEvaluation;
		while ((currentBlockEvaluation = blocksByDescendingHeight.poll()) != null) {
			Block block = blockService.getBlock(currentBlockEvaluation.getBlockhash());
			BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
			BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());

			// If previous blocks are unpruned / maintained, update their depth if the
			// newfound depth is greater than previously and add them to the queue
			if (prevBlockEvaluation != null) {
				if (prevBlockEvaluation.getDepth() < currentBlockEvaluation.getDepth() + 1) {
					blockService.updateDepth(prevBlockEvaluation, currentBlockEvaluation.getDepth() + 1);
					blocksByDescendingHeight.offer(prevBlockEvaluation);
				}
			}
			
			if (prevBranchBlockEvaluation != null) {
				if (prevBranchBlockEvaluation.getDepth() < currentBlockEvaluation.getDepth() + 1) {
					blockService.updateDepth(prevBranchBlockEvaluation, currentBlockEvaluation.getDepth() + 1);
					blocksByDescendingHeight.offer(prevBranchBlockEvaluation);
				}
			}
		}
	}

	/*
	 * Update cumulative weight, the amount of blocks a block is approved by
	 */
	public void updateCumulativeWeights() throws BlockStoreException {
		// Begin from the highest solid height tips and go backwards from there
		long currentHeight = blockService.getMaxSolidHeight();
		HashMap<Sha256Hash, HashSet<Sha256Hash>> currentHeightBlocks = null, nextHeightBlocks = null;

		while (currentHeight >= 0) {
			// Initialize results of current height
			currentHeightBlocks = new HashMap<>();

			for (BlockEvaluation blockEvaluation : blockService.getSolidBlocksOfHeight(currentHeight)) {
				// Add your own hash as reference
				HashSet<Sha256Hash> blockReferences = new HashSet<Sha256Hash>();
				blockReferences.add(blockEvaluation.getBlockhash());

				// Add all references of all approvers
				for (Sha256Hash approverHash : blockService.getApproverBlockHash(blockEvaluation.getBlockhash())) {
					blockReferences.addAll(nextHeightBlocks.get(approverHash));
				}

				// Save it to current height's hash sets
				currentHeightBlocks.put(blockEvaluation.getBlockhash(), blockReferences);

				// Update your cumulative weight
				blockEvaluation.setCumulativeweight(blockReferences.size());
			}

			// Move up to next height
			nextHeightBlocks = currentHeightBlocks;
			currentHeight--;
		}
	}

	/*
	 * Update the percentage of times that tips selected by MCMC approve a block
	 */
	public void updateRating() throws Exception {
		// Select #tipCount solid tips via MCMC
		int tipCount = 100;
		List<BlockEvaluation> selectedTips = new ArrayList<BlockEvaluation>(tipCount);
		Random random = new SecureRandom();
		for (int i = 0; i < tipCount; i++)
			selectedTips.add(blockService.getBlockEvaluation(tipsService.blockToApprove(null, null, 27, 27, random)));

		// Begin from the highest solid height tips and go backwards from there
		long currentHeight = blockService.getMaxSolidHeight();
		HashMap<Sha256Hash, HashMultiset<Sha256Hash>> currentHeightBlocks = null, nextHeightBlocks = null;

		while (currentHeight >= 0) {
			// Initialize results of current height
			currentHeightBlocks = new HashMap<>();

			for (BlockEvaluation blockEvaluation : blockService.getSolidBlocksOfHeight(currentHeight)) {
				// Add your own hashes as reference if you are one of the selected tips
				HashMultiset<Sha256Hash> selectedTipReferences = HashMultiset.create(tipCount);
				for (BlockEvaluation tip : selectedTips) {
					if (tip.getBlockhash() == blockEvaluation.getBlockhash()) {
						selectedTipReferences.add(tip.getBlockhash());
					}
				}

				// Add all selected tip references of all approvers
				for (Sha256Hash approverHash : blockService.getApproverBlockHash(blockEvaluation.getBlockhash())) {
					selectedTipReferences.addAll(nextHeightBlocks.get(approverHash));
				}

				// Save it to current height's results
				currentHeightBlocks.put(blockEvaluation.getBlockhash(), selectedTipReferences);

				// Update your rating
				blockEvaluation.setRating(selectedTipReferences.size());
			}

			// Move up to next height
			nextHeightBlocks = currentHeightBlocks;
			currentHeight--;
		}
	}

	public void updateMilestone() {

	}

}
