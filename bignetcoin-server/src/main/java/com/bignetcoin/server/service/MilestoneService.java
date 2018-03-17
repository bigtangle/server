/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
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

	@Autowired
	private TransactionService transactionService;
	

	enum Validity {
		VALID, INVALID, INCOMPLETE
	}

	public Snapshot latestSnapshot;

	public Sha256Hash latestMilestone = Sha256Hash.ZERO_HASH;
	public Sha256Hash latestSolidSubtangleMilestone = latestMilestone;

	public static final int MILESTONE_START_INDEX = 338000;

	public int latestMilestoneIndex = MILESTONE_START_INDEX;
	public int latestSolidSubtangleMilestoneIndex = MILESTONE_START_INDEX;

	/*****************************************************
	 * Experimental update methods *
	 ******************************************************/
	public void update() throws Exception {
		updateSolidityAndHeight();
		updateDepth();
		updateCumulativeWeight();
		updateRating();
		updateMilestone();
		// Optional: Trigger batched tip pair selection here
	}

	/**
	 * Update solid, true if all directly or indirectly approved blocks exist.
	 * If solid, update height to be the max of previous heights + 1
	 * 
	 * @throws Exception
	 */
	public void updateSolidityAndHeight() throws Exception {
		List<BlockEvaluation> nonSolidBlocks = blockService.getNonSolidBlocks();
		for (BlockEvaluation nonSolidBlock : nonSolidBlocks)
			updateSolidityAndHeightRecursive(nonSolidBlock);
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
			// TODO if genesis assume solid? or put genesis into database? for now assume solid
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBlockSolid = updateSolidityAndHeightRecursive(prevBlockEvaluation);
		}

		// Check previous branch block exists and is solid
		BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
		if (prevBranchBlockEvaluation == null) {
			// TODO if genesis assume solid? or put genesis into database? for now assume solid
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBranchBlockSolid = updateSolidityAndHeightRecursive(prevBranchBlockEvaluation);
		}

		// If both previous blocks are solid, our block should be solidified
		if (prevBlockSolid && prevBranchBlockSolid) {
			solidifyBlock(blockEvaluation, prevBlockEvaluation, prevBranchBlockEvaluation);
			return true;
		} else {
			blockService.updateSolid(blockEvaluation, false);
			return false;
		}
	}

	private void solidifyBlock(BlockEvaluation blockEvaluation, BlockEvaluation prevBlockEvaluation,
			BlockEvaluation prevBranchBlockEvaluation) throws BlockStoreException {
		blockService.updateHeight(blockEvaluation,
				Math.max(prevBlockEvaluation.getHeight() + 1, prevBranchBlockEvaluation.getHeight() + 1));
		blockService.updateSolid(blockEvaluation, true);
		tipsService.addTip(blockEvaluation.getBlockhash());
	}

	/**
	 * Update depth, the length of the longest reverse-oriented path to some tip.
	 * 
	 * @throws BlockStoreException
	 */
	public void updateDepth() throws BlockStoreException {
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

	/**
	 * Update cumulative weight, the amount of blocks a block is approved by
	 * 
	 * @throws BlockStoreException
	 */
	public void updateCumulativeWeight() throws BlockStoreException {
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
				for (Sha256Hash approverHash : blockService.getApproverBlockHashes(blockEvaluation.getBlockhash())) {
					blockReferences.addAll(nextHeightBlocks.get(approverHash));
				}

				// Save it to current height's hash sets
				currentHeightBlocks.put(blockEvaluation.getBlockhash(), blockReferences);

				// Update your cumulative weight
				blockEvaluation.setCumulativeWeight(blockReferences.size());
			}

			// Move up to next height
			nextHeightBlocks = currentHeightBlocks;
			currentHeight--;
		}
	}

	/**
	 * Update the percentage of times that tips selected by MCMC approve a block
	 * 
	 * @throws Exception
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
				for (Sha256Hash approverHash : blockService.getApproverBlockHashes(blockEvaluation.getBlockhash())) {
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

	/**
	 * Updates milestone field in block evaluation and changes output table
	 * correspondingly
	 * 
	 * @throws BlockStoreException
	 */
	public void updateMilestone() throws BlockStoreException {
		// First remove any blocks that should no longer be in the milestone
		HashSet<BlockEvaluation> blocksToRemove = blockService.getBlocksToRemoveFromMilestone();
		for (BlockEvaluation block : blocksToRemove) {
			blockService.disconnect(block);
		}

		while (true) {
			// Now try to find blocks that can be added to the milestone
			HashSet<BlockEvaluation> blocksToAdd = blockService.getBlocksToAddToMilestone();

			// Optional to lower computational cost
			removeWhereUTXONotFound(blocksToAdd);

			// Resolve conflicting UTXO spends that have been approved by the network
			// (improbable to occur)
			resolveUnundoableConflicts(blocksToAdd);
			resolveUndoableConflicts(blocksToAdd);

			// Remove blocks from blocksToAdd that have at least one transaction input with
			// its corresponding output not found in the outputs table and remove their
			// approvers recursively too
			removeWhereUTXONotFound(blocksToAdd);

			// Exit condition: there are no more blocks to add
			if (blocksToAdd.isEmpty())
				break;

			// Finally add the found new milestone blocks to the milestone
			for (BlockEvaluation block : blocksToAdd) {
				blockService.connect(block);
			}
		}
	}

	/**
	 * Remove blocks from blocksToAdd that have at least one transaction input with
	 * its corresponding output not found in the outputs table and remove their
	 * approvers recursively too
	 * 
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	private void removeWhereUTXONotFound(HashSet<BlockEvaluation> blocksToAdd) throws BlockStoreException {
		for (BlockEvaluation e : new HashSet<BlockEvaluation>(blocksToAdd)) {
			Block block = blockService.getBlock(e.getBlockhash());
			for (TransactionInput in : block.getTransactions().stream().flatMap(t -> t.getInputs().stream())
					.collect(Collectors.toList())) {
				if (transactionService.getUTXO(in.getOutpoint()) == null)
					removeBlockAndApproversFrom(blocksToAdd, e);
			}
		}
	}

	/**
	 * Resolves conflicts in new blocks to add that cannot be undone due to pruning.
	 * This method does not do anything if not pruning blocks.
	 * 
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	private void resolveUnundoableConflicts(HashSet<BlockEvaluation> blocksToAdd) throws BlockStoreException {
		// Get the blocks to add as actual blocks from blockService
		List<Block> blocks = blockService
				.getBlocks(blocksToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

		// To check for unundoable conflicts, we do the following:
		// Create tuples (block, txinput) of all blocksToAdd
		Stream<Pair<Block, TransactionInput>> blockInputTuples = blocks.stream().flatMap(
				b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in)));

		// Now filter to only contain inputs that were already spent in the milestone
		// when the corresponding block has already been pruned
		Stream<Pair<Block, TransactionInput>> irresolvableConflicts = blockInputTuples
				.filter(pair -> transactionService.getUTXOSpent(pair.getRight().getOutpoint())
						&& transactionService.getUTXOSpender(pair.getRight().getOutpoint()) == null);

		// These blocks cannot be added and must therefore be removed from blocksToAdd
		for (Pair<Block, TransactionInput> p : irresolvableConflicts.collect(Collectors.toList())) {
			removeBlockAndApproversFrom(blocksToAdd, blockService.getBlockEvaluation(p.getLeft().getHash()));
		}
	}

	/**
	 * Resolves conflicts between milestone blocks and milestone candidates as well
	 * as conflicts among milestone candidates.
	 * 
	 * @param blockEvaluationsToAdd
	 * @throws BlockStoreException
	 */
	private void resolveUndoableConflicts(HashSet<BlockEvaluation> blockEvaluationsToAdd) throws BlockStoreException {
		HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints = new HashSet<Pair<BlockEvaluation, TransactionOutPoint>>();
		HashSet<BlockEvaluation> conflictingMilestoneBlocks = new HashSet<BlockEvaluation>();
		List<Block> blocksToAdd = blockService
				.getBlocks(blockEvaluationsToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

		// Find all conflicts between milestone and candidates themselves
		findMilestoneCandidateConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);
		findCandidateCandidateConflicts(blocksToAdd, conflictingOutPoints);

		// Resolve all conflicts by grouping by UTXO ordered by descending rating
		HashSet<BlockEvaluation> winningBlocks = resolveConflictsByDescendingRating(conflictingOutPoints);

		// For milestone blocks that have been eliminated (conflictingMilestone \
		// winningBlocks) call disconnect procedure
		for (BlockEvaluation b : conflictingMilestoneBlocks.stream().filter(b -> !winningBlocks.contains(b))
				.collect(Collectors.toList())) {
			blockService.disconnect(b);
		}

		// For candidates that have been eliminated (blockEvaluationsToAdd \
		// winningBlocks) remove them from blocksToAdd
		for (BlockEvaluation b : blockEvaluationsToAdd.stream().filter(b -> !winningBlocks.contains(b))
				.collect(Collectors.toList())) {
			blockEvaluationsToAdd.remove(b);
		}
	}

	/**
	 * Resolve all conflicts by grouping by UTXO ordered by descending rating.
	 * 
	 * @param blockEvaluationsToAdd
	 * @param conflictingOutPoints
	 * @param conflictingMilestoneBlocks
	 * @return
	 * @throws BlockStoreException
	 */
	private HashSet<BlockEvaluation> resolveConflictsByDescendingRating(
			HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints) throws BlockStoreException {
		// Initialize blocks that will survive the conflict resolution
		HashSet<BlockEvaluation> winningBlocks = conflictingOutPoints.stream().map(p -> p.getLeft()).collect(Collectors.toCollection(HashSet::new));
		
		// Sort conflicts internally by descending rating
		Comparator<Pair<BlockEvaluation, TransactionOutPoint>> byDescendingRating = Comparator
				.comparingLong((Pair<BlockEvaluation, TransactionOutPoint> e) -> e.getLeft().getRating()).reversed();

		Supplier<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> conflictTreeSetSupplier = () -> new TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>(
				byDescendingRating);

		Map<Object, TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> conflicts = conflictingOutPoints.stream()
				.collect(Collectors.groupingBy(Pair::getRight, Collectors.toCollection(conflictTreeSetSupplier)));

		// Sort conflicts among each other by descending max(rating)
		Comparator<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> byDescendingSetRating = Comparator
				.comparingLong(
						(TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getRating())
				.reversed();

		Supplier<TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>>(
				byDescendingSetRating);

		TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> sortedConflicts = conflicts.values().stream()
				.collect(Collectors.toCollection(conflictsTreeSetSupplier));

		// Now handle conflicts by descending max(rating)
		for (TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> conflict : sortedConflicts) {
			// Take the block with the maximum rating in this conflict that is still in
			// winning Blocks
			Pair<BlockEvaluation, TransactionOutPoint> maxRatingPair = conflict.stream()
					.filter(pair -> winningBlocks.contains(pair.getLeft()))
					.max(new Comparator<Pair<BlockEvaluation, TransactionOutPoint>>() {
						@Override
						public int compare(Pair<BlockEvaluation, TransactionOutPoint> o1,
								Pair<BlockEvaluation, TransactionOutPoint> o2) {
							return Long.compare(o1.getLeft().getRating(), o2.getLeft().getRating());
						}
					}).orElse(null);

			// If such a block exists, this conflict is resolved by eliminating all other
			// blocks from winning Blocks
			if (maxRatingPair != null) {
				for (Pair<BlockEvaluation, TransactionOutPoint> pair : conflict) {
					if (!pair.getLeft().equals(maxRatingPair.getLeft())) {
						removeBlockAndApproversFrom(winningBlocks, pair.getLeft());
					}
				}
			}
		}

		return winningBlocks;
	}

	/**
	 * Finds conflicts among blocks to add
	 * 
	 * @param blocksToAdd
	 * @param conflictingOutPoints
	 * @throws BlockStoreException
	 */
	private void findCandidateCandidateConflicts(List<Block> blocksToAdd,
			HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints) throws BlockStoreException {
		Stream<Pair<Block, TransactionOutPoint>> outPoints = blocksToAdd.stream().flatMap(b -> b.getTransactions()
				.stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in.getOutpoint())));

		List<Pair<Block, TransactionOutPoint>> candidateCandidateConflicts = outPoints
				.collect(Collectors.groupingBy(Pair::getRight)).values().stream().filter(l -> l.size() > 1)
				.flatMap(l -> l.stream()).collect(Collectors.toList());

		for (Pair<Block, TransactionOutPoint> pair : candidateCandidateConflicts) {
			BlockEvaluation toAddEvaluation = blockService.getBlockEvaluation(pair.getLeft().getHash());
			conflictingOutPoints.add(Pair.of(toAddEvaluation, pair.getRight()));
		}
	}

	/**
	 * Finds conflicts between current milestone and blocksToAdd
	 * 
	 * @param blocksToAdd
	 * @param conflictingOutPoints
	 * @throws BlockStoreException
	 */
	private void findMilestoneCandidateConflicts(List<Block> blocksToAdd,
			HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints,
			HashSet<BlockEvaluation> conflictingMilestoneBlocks) throws BlockStoreException {
		// Create pairs of blocks and used utxos from blocksToAdd
		Stream<Pair<Block, TransactionOutPoint>> outPoints = blocksToAdd.stream().flatMap(b -> b.getTransactions()
				.stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in.getOutpoint())));

		// Filter to only contain inputs that were already spent by the milestone
		List<Pair<Block, TransactionOutPoint>> candidatesConflictingWithMilestone = outPoints
				.filter(pair -> transactionService.getUTXOSpent(pair.getRight())
						&& transactionService.getUTXOSpender(pair.getRight()) != null)
				.collect(Collectors.toList());

		// Add the conflicting milestone blocks and their milestone approvers
		for (Pair<Block, TransactionOutPoint> pair : candidatesConflictingWithMilestone) {
			BlockEvaluation milestoneEvaluation = transactionService.getUTXOSpender(pair.getRight());
			BlockEvaluation toAddEvaluation = blockService.getBlockEvaluation(pair.getLeft().getHash());
			conflictingOutPoints.add(Pair.of(toAddEvaluation, pair.getRight()));
			conflictingOutPoints.add(Pair.of(milestoneEvaluation, pair.getRight()));
			addMilestoneSubtangle(conflictingMilestoneBlocks, milestoneEvaluation);
		}
	}

	private void addMilestoneSubtangle(HashSet<BlockEvaluation> conflictingMilestoneBlocks,
			BlockEvaluation milestoneEvaluation) throws BlockStoreException {
		// Only add milestone blocks
		if (!milestoneEvaluation.isMilestone())
			return; 
		conflictingMilestoneBlocks.add(milestoneEvaluation);

		// Add all milestone approvers
		for (Sha256Hash approverHash : blockService.getApproverBlockHashes(milestoneEvaluation.getBlockhash())) {
			conflictingMilestoneBlocks.add(blockService.getBlockEvaluation(approverHash));
		}
	}

	/**
	 * Recursively removes the specified block and its approvers from the collection
	 * if this block is contained in the collection.
	 * 
	 * @param blocksToAdd
	 * @param blockEvaluation
	 * @throws BlockStoreException
	 */
	private void removeBlockAndApproversFrom(Collection<BlockEvaluation> blocksToAdd, BlockEvaluation blockEvaluation)
			throws BlockStoreException {
		// If not contained, stop
		if (!blocksToAdd.contains(blockEvaluation))
			return;

		// Remove this block
		blocksToAdd.remove(blockEvaluation);

		// And remove its approvers
		for (Sha256Hash approver : blockService.getApproverBlockHashes(blockEvaluation.getBlockhash())) {
			removeBlockAndApproversFrom(blocksToAdd, blockService.getBlockEvaluation(approver));
		}
	}

	/**
	 * Gets all solid tips ordered by descending height
	 * 
	 * @return solid tips by ordered by descending height
	 * @throws BlockStoreException
	 */
	private PriorityQueue<BlockEvaluation> getSolidTipsDescending() throws BlockStoreException {
		List<BlockEvaluation> solidTips = blockService.getSolidTips();
		CollectionUtils.filter(solidTips, e -> ((BlockEvaluation) e).isSolid());
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = new PriorityQueue<BlockEvaluation>(
				solidTips.size() + 1, Comparator.comparingLong(BlockEvaluation::getHeight).reversed());
		blocksByDescendingHeight.addAll(solidTips);
		return blocksByDescendingHeight;
	}
}
