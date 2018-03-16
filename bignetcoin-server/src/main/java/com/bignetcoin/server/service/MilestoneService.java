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
import java.util.PriorityQueue;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
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
	 * Update height to be the sum of previous heights
	 * 
	 * @throws Exception
	 */
	public void updateSolidityAndHeight() throws Exception {
		List<BlockEvaluation> tips = blockService.getNonSolidBlocks();
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
			prevBranchBlockSolid = updateSolidityAndHeightRecursive(prevBranchBlockEvaluation);
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
			BlockEvaluation prevBranchBlockEvaluation) throws BlockStoreException {
		blockService.updateHeight(blockEvaluation,
				Math.max(prevBlockEvaluation.getHeight() + 1, prevBranchBlockEvaluation.getHeight() + 1));
		blockService.updateSolid(blockEvaluation, true);
		// TODO update solid tips table by tossing out approved blocks and adding in the
		// new tip
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
			disconnect(block);
		}

		while (true) {
			// Now try to find blocks that can be added to the milestone
			HashSet<BlockEvaluation> blocksToAdd = blockService.getBlocksToAddToMilestone();

			// Optionally, we can already filter unspent UTXO here to lower computational
			// cost

			// Resolve conflicting UTXO spends that have been approved by the network
			// (improbable to occur)
			resolveConflicts(blocksToAdd);

			// Remove blocks from blocksToAdd that have at least one transaction input with
			// its corresponding output not found in the outputs table and remove their
			// approvers recursively too
			filterHasUnspentUTXOs(blocksToAdd);

			// Exit condition: there are no more blocks to add
			if (blocksToAdd.isEmpty())
				break;

			// Finally add the found new milestone blocks to the milestone
			for (BlockEvaluation block : blocksToAdd) {
				connect(block);
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
	private void filterHasUnspentUTXOs(HashSet<BlockEvaluation> blocksToAdd) throws BlockStoreException {
		for (BlockEvaluation e : new HashSet<BlockEvaluation>(blocksToAdd)) {
			Block block = blockService.getBlock(e.getBlockhash());
			for (TransactionInput in : block.getTransactions().stream().flatMap(t -> t.getInputs().stream())
					.collect(Collectors.toList())) {
				if (transactionService.getUTXO(in.getOutpoint()) == null)
					removeBlockAndApproversFrom(blocksToAdd, e);
			}
		}
	}

	private void resolveConflicts(HashSet<BlockEvaluation> blocksToAdd) throws BlockStoreException {
		resolveUnundoableConflicts(blocksToAdd);
		resolveUndoableConflicts(blocksToAdd);
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
		// Create tuples (block, txin) of blocks to add
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
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	private void resolveUndoableConflicts(HashSet<BlockEvaluation> blocksToAdd) throws BlockStoreException {
		// Get the blocks to add as blocks from blockservice
		List<Block> blocks = blockService
				.getBlocks(blocksToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

		// For resolvable conflicts, we first find conflicts between the current
		// milestone and blocksToAdd
		// Create tuples (block, transactioninput) of blocks to add
		Stream<Pair<Block, TransactionOutPoint>> blockInputTuples = blocks.stream().flatMap(b -> b.getTransactions()
				.stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in.getOutpoint())));

		// Now filter to only contain inputs that were already spent in the milestone by
		// unpruned blocks, add in the corresponding milestone blocks with their inputs
		// and distinct group by UTXO ordered by descending rating + cumulative weight
		Stream<Pair<Block, TransactionOutPoint>> candidatesConflictingWithMilestone = blockInputTuples
				.filter(pair -> transactionService.getUTXOSpent(pair.getRight())
						&& transactionService.getUTXOSpender(pair.getRight()) != null); // TODO

		List<Pair<BlockEvaluation, TransactionOutPoint>> milestoneCandidateConflicts = new ArrayList<Pair<BlockEvaluation, TransactionOutPoint>>();
		for (Pair<Block, TransactionOutPoint> pair : candidatesConflictingWithMilestone.collect(Collectors.toList())) {
			BlockEvaluation spenderEvaluation = transactionService.getUTXOSpender(pair.getRight());
			milestoneCandidateConflicts
					.add(Pair.of(blockService.getBlockEvaluation(pair.getLeft().getHash()), pair.getRight()));
			milestoneCandidateConflicts.add(Pair.of(spenderEvaluation, pair.getRight()));
		}

		Collection<HashSet<Pair<BlockEvaluation, TransactionOutPoint>>> conflicts = milestoneCandidateConflicts
				.stream().
				collect(Collectors.groupingBy(Pair::getRight, Collectors.toCollection(HashSet::new))).values();

		// Do the same again but check for conflicts in blocksToAdd itself
//		blockInputTuples = blocks.stream().flatMap(
//				b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in)));
//		Stream<Pair<Block, TransactionInput>> resolvableConflictsAmongCandidates = blockInputTuples
//				.filter(pair -> transactionService.getUTXOSpent(pair.getRight().getOutpoint())
//						&& transactionService.getUTXOSpender(pair.getRight().getOutpoint()) == null);// TODO

		// Resolve conflicts in order by removing all but the first place
		// TODO
//		List<HashSet<Pair<BlockEvaluation, TransactionOutPoint>>> sortedConflicts = new ArrayList<HashSet<Pair<BlockEvaluation, TransactionOutPoint>>>(conflicts);
//		conflictsByMaxRating = conflicts.stream().map(set -> Pair.of(set.stream().map(pair -> pair.getRight()), right))
//		Collections.sort(sortedConflicts, new Comparator<HashSet<Pair<BlockEvaluation, TransactionOutPoint>>>() {
//			@Override
//			public int compare(HashSet<Pair<BlockEvaluation, TransactionOutPoint>> o1,
//					HashSet<Pair<BlockEvaluation, TransactionOutPoint>> o2) {
//				return o1.stream().collect(collector);
//			}
//		});

		// For milestone blocks, call disconnect procedure
		// TODO

		// For candidates, remove them from blocksToAdd
		// for (Pair<Block, TransactionInput> p :
		// irresolvableConflicts.collect(Collectors.toList())) {
		// removeBlockAndApproversFrom(blocksToAdd,
		// blockService.getBlockEvaluation(p.getLeft().getHash()));
		// }
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
	 * Adds the specified block and all approved blocks to the milestone. This will
	 * connect all transactions of the block by marking used UTXOs spent and adding
	 * new UTXOs to the db.
	 * 
	 * @param blockEvaluation
	 * @throws BlockStoreException
	 */
	private void connect(BlockEvaluation blockEvaluation) throws BlockStoreException {
		Block block = blockService.getBlock(blockEvaluation.getBlockhash());

		// If already connected, return
		if (blockEvaluation.isMilestone())
			return;

		// Set milestone true and update latestMilestoneUpdateTime first to stop
		// infinite recursions
		blockService.updateMilestone(blockEvaluation, true);

		// Connect all approved blocks first (not actually needed)
		connect(blockService.getBlockEvaluation(block.getPrevBlockHash()));
		connect(blockService.getBlockEvaluation(block.getPrevBranchBlockHash()));

		// Connect all transactions in block
		// for (Transaction tx : block.getTransactions()) {
		// // Mark all outputs used by tx input as spent
		// for (TransactionInput txin : tx.getInputs()) {
		// TransactionOutput connectedOutput = txin.getConnectedOutput();
		// transactionService.updateTransactionOutputSpent(connectedOutput, true);
		// }
		//
		// // Add all tx outputs as new open outputs
		// for (TransactionOutput txout : tx.getOutputs()) {
		// transactionService.addTransactionOutput(txout);
		// }
		// }

		// TODO call fullprunedblockgraph.connect() and add logic to it
	}

	/**
	 * Removes the specified block and all its output spenders and approvers from
	 * the milestone. This will disconnect all transactions of the block by marking
	 * used UTXOs unspent and removing UTXOs of the block from the db.
	 * 
	 * @param blockEvaluation
	 * @throws BlockStoreException
	 */
	private void disconnect(BlockEvaluation blockEvaluation) throws BlockStoreException {
		Block block = blockService.getBlock(blockEvaluation.getBlockhash());

		// If already disconnected, return
		if (blockEvaluation.isMilestone())
			return;

		// Set milestone false and update latestMilestoneUpdateTime to stop infinite
		// recursions
		blockService.updateMilestone(blockEvaluation, false);

		// Disconnect all approver blocks first
		for (StoredBlock approver : blockService.getApproverBlocks(blockEvaluation.getBlockhash())) {
			disconnect(blockService.getBlockEvaluation(approver.getHeader().getHash()));
		}

		// Disconnect all transactions in block
		// for (Transaction tx : block.getTransactions()) {
		// // Mark all outputs used by tx input as unspent
		// for (TransactionInput txin : tx.getInputs()) {
		// TransactionOutput connectedOutput = txin.getConnectedOutput();
		// transactionService.updateTransactionOutputSpent(connectedOutput, false);
		// }
		//
		// // Remove tx outputs from output db and disconnect spending txs
		// for (TransactionOutput txout : tx.getOutputs()) {
		// if (transactionService.getTransactionOutputSpent(txout)) {
		// disconnect(transactionService.getTransactionOutputSpender(txout));
		// }
		// transactionService.removeTransactionOutput(txout);
		// }
		// }

		// TODO call fullprunedblockgraph.disconnect() and add logic to it
	}

	// Comparator to sort blocks by descending height
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
				solidTips.size() + 1, sortBlocksByDescendingHeight);
		blocksByDescendingHeight.addAll(solidTips);
		return blocksByDescendingHeight;
	}
}
