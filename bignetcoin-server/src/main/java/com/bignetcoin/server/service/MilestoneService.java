/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.security.SecureRandom;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.CollectionUtils;
import org.springframework.stereotype.Service;

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

	private final Logger log = LoggerFactory.getLogger(MilestoneService.class);

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

	// TODO inefficient due to repeated database queries, fix: merge block and
	// blockevaluation tables, batch updates

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

	/*
	 * Update solid true if all directly or indirectly approved blocks exist Update
	 * height to be the sum of previous heights
	 */
	public void updateSolidityAndHeight() throws Exception {
		List<BlockEvaluation> tips = blockService.getTips();
		for (BlockEvaluation tip : tips)
			updateSolidityAndHeightRecursive(tip);
	}

	private boolean updateSolidityAndHeightRecursive(BlockEvaluation blockEvaluation) throws BlockStoreException {
		// Solid blocks stay solid
		if (blockEvaluation.isSolid()) {
			return true;
		}

		// Missing blocks -> not solid, request
		Block block = blockService.getBlock(blockEvaluation.getBlockhash());
		if (block == null) {
			// TODO broken graph, download the missing remote block needed
			blockService.updateSolid(blockEvaluation, false);
			return false;
		}

		boolean prevBlockSolid = false;
		boolean prevBranchBlockSolid = false;

		// Check previous trunk block
		BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
		if (prevBlockEvaluation == null) {
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBlockSolid = updateSolidityAndHeightRecursive(prevBlockEvaluation);
		}

		// Check previous branch block
		BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
		if (prevBranchBlockEvaluation == null) {
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBranchBlockSolid = updateSolidityAndHeightRecursive(prevBlockEvaluation);
		}

		// Check if all referenced blocks are solid (and therefore exist)
		if (prevBlockSolid && prevBranchBlockSolid) {
			// On success, also set height since it can now be calculated
			blockService.updateHeight(blockEvaluation,
					Math.max(prevBlockEvaluation.getHeight() + 1, prevBranchBlockEvaluation.getHeight() + 1));
			blockService.updateSolid(blockEvaluation, true);
			return true;
		} else {
			blockService.updateSolid(blockEvaluation, false);
			return false;
		}
	}

	/*
	 * Update depth, the length of the longest reverse-oriented path to some tip.
	 * Update cumulative weight, the amount of direct or indirect approvers of the
	 * block TODO batch database queries by heights
	 */
	public void updateDepthsAndCumulativeWeights() throws BlockStoreException {
		// Select solid tips to begin from
		List<BlockEvaluation> solidTips = blockService.getTips();
		CollectionUtils.filter(solidTips, e -> ((BlockEvaluation) e).isSolid());
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = new PriorityQueue<BlockEvaluation>(solidTips.size(),
				sortBlocksByDescendingHeight);
		blocksByDescendingHeight.addAll(solidTips);

		// Initialize tips with weight 1 and depth 0
		for (BlockEvaluation blockEvaluation : blocksByDescendingHeight) {
			blockService.updateCumulativeWeight(blockEvaluation, 1);
			blockService.updateDepth(blockEvaluation, 0);
		}

		// Add the current cumulative weight to the approved blocks and update their
		// depth
		BlockEvaluation currentBlockEvaluation;
		while ((currentBlockEvaluation = blocksByDescendingHeight.poll()) != null) {
			Block block = blockService.getBlock(currentBlockEvaluation.getBlockhash());
			BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
			BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());

			// If previous blocks are unpruned, update their cumulative weight and depth and
			// add them to the priority queue
			if (prevBlockEvaluation != null) {
				blockService.updateCumulativeWeight(prevBlockEvaluation,
						prevBlockEvaluation.getCumulativeweight() + currentBlockEvaluation.getCumulativeweight());
				blockService.updateDepth(prevBlockEvaluation,
						Math.max(currentBlockEvaluation.getDepth() + 1, prevBlockEvaluation.getDepth()));
				blocksByDescendingHeight.offer(prevBlockEvaluation);
			}
			if (prevBranchBlockEvaluation != null) {
				blockService.updateCumulativeWeight(prevBranchBlockEvaluation,
						prevBranchBlockEvaluation.getCumulativeweight() + currentBlockEvaluation.getCumulativeweight());
				blockService.updateDepth(prevBranchBlockEvaluation,
						Math.max(currentBlockEvaluation.getDepth() + 1, prevBranchBlockEvaluation.getDepth()));
				blocksByDescendingHeight.offer(prevBranchBlockEvaluation);
			}

	        //log.debug("hash : " + currentBlockEvaluation.getBlockhash() + " -> " + currentBlockEvaluation.getCumulativeweight());
		}
	}

	/*
	 * Update the percentage of times that tips selected by MCMC approve a block
	 */
	public void updateRating() throws Exception {
		// Select 100 solid tips via MCMC
		int tipCount = 100;
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = new PriorityQueue<BlockEvaluation>(tipCount,
				sortBlocksByDescendingHeight);
		Random random = new SecureRandom();
		for (int i = 0; i < tipCount; i++)
			blocksByDescendingHeight
					.offer(blockService.getBlockEvaluation(tipsService.blockToApprove(null, null, 27, 27, random)));

		// Begin from the highest height blocks and go backwards
		long height = blockService.getHeightMax();
		HashMap<Sha256Hash, BlockEvaluation> nextHeightBlocks = null;
		HashMap<Sha256Hash, BlockEvaluation> currentHeightBlocks = null; // TODO get currentHeightBlocks
		while (height >= 0) {
			for (BlockEvaluation blockEvaluation : currentHeightBlocks.values()) {
				blockEvaluation.setRating(0);
				for (Sha256Hash approverHash : blockService.getApproverBlockHash(blockEvaluation.getBlockhash())) {
					// TODO preload blocks too, fixes itself when merging blockevaluation and undoableblocks
					blockService.updateRating(blockEvaluation, blockEvaluation.getRating() + nextHeightBlocks.get(approverHash).getRating());
				}
				//TODO add count of this block in blocksByDescendingHeight
			}
			nextHeightBlocks = currentHeightBlocks;
			// TODO get currentHeightBlocks
			height--;
		}
	}
}
