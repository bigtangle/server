/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.CollectionUtils;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultiset;
import com.lambdaworks.crypto.SCrypt;

/*
 *  check the valuation of block and trigger an update of openoutputs
 */
@Service
public class MilestoneService {

	private static final Logger log = LoggerFactory.getLogger(MilestoneService.class);

	@Autowired
	protected FullPrunedBlockStore store;

	@Autowired
	private BlockService blockService;

	@Autowired
	private TipsService tipsService;

	@Autowired
	private TransactionService transactionService;

	/**
	 * Scheduled update function that updates the Tangle
	 * 
	 * @throws Exception
	 */
	public void update() throws Exception {
		log.info("Milestone Update started");
		Stopwatch watch = Stopwatch.createStarted();
		updateSolidityAndHeight();
		log.info("Milestone solidity and height time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		watch.stop();
		watch = Stopwatch.createStarted();
		updateDepth();
		log.info("Milestone depth time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		watch.stop();
		watch = Stopwatch.createStarted();
		updateCumulativeWeight();
		log.info("Milestone weight time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		watch.stop();
		watch = Stopwatch.createStarted();
		updateRating();
		log.info("Milestone rating time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		watch.stop();
		watch = Stopwatch.createStarted();
		updateMilestone();
		log.info("Milestone update took {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
		// Optional: Trigger batched tip pair selection here

		watch.stop();
	}

	/**
	 * Update solid, true if all directly or indirectly approved blocks exist. If
	 * solid, update height to be the max of previous heights + 1
	 * 
	 * @throws Exception
	 */
	public void updateSolidityAndHeight() throws Exception {
		List<Sha256Hash> nonSolidBlocks = blockService.getNonSolidBlocks();
		for (Sha256Hash nonSolidBlock : nonSolidBlocks)
			updateSolidityAndHeightRecursive(nonSolidBlock);
	}

	private boolean updateSolidityAndHeightRecursive(Sha256Hash hash) throws BlockStoreException {
		BlockEvaluation blockEvaluation = blockService.getBlockEvaluation(hash);

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
			prevBlockSolid = updateSolidityAndHeightRecursive(block.getPrevBlockHash());
		}

		// Check previous branch block exists and is solid
		BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
		if (prevBranchBlockEvaluation == null) {
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBranchBlockSolid = updateSolidityAndHeightRecursive(block.getPrevBranchBlockHash());
		}

		// If both previous blocks are solid, our block should be solidified
		if (prevBlockSolid && prevBranchBlockSolid) {
			solidifyBlock(blockEvaluation, block);
			return true;
		} else {
			blockService.updateSolid(blockEvaluation, false);
			return false;
		}
	}

	private void solidifyBlock(BlockEvaluation blockEvaluation, Block block) throws BlockStoreException {
		// reget evaluations...
		BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
		BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
		blockService.updateHeight(blockEvaluation, Math.max(prevBlockEvaluation.getHeight() + 1, prevBranchBlockEvaluation.getHeight() + 1));
		blockService.updateSolid(blockEvaluation, true);
		tipsService.addTip(blockEvaluation.getBlockhash());
	}

	/**
	 * Update depth, the length of the longest reverse-oriented path to some tip.
	 * 
	 * @throws BlockStoreException
	 */
	public void updateDepth() throws BlockStoreException {
		// TODO also do milestonedepth and select from milestone depth interval for tip selection
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
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = getSolidTipsDescending();
		HashMap<Sha256Hash, HashSet<Sha256Hash>> approverHashSets = new HashMap<>();
		for (BlockEvaluation tip : blockService.getSolidTips()) {
			approverHashSets.put(tip.getBlockhash(), new HashSet<>());
		}

		BlockEvaluation currentBlock = null;
		while ((currentBlock = blocksByDescendingHeight.poll()) != null) {
			// Add your own hash to approver hashes of current approver hashes
			HashSet<Sha256Hash> approverHashes = approverHashSets.get(currentBlock.getBlockhash());
			approverHashes.add(currentBlock.getBlockhash());
			
			// Add all current references to both approved blocks (initialize if not yet initialized)
			Block block = blockService.getBlock(currentBlock.getBlockhash());
			
			if (!approverHashSets.containsKey(block.getPrevBlockHash())) {
				BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
				if (prevBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBlockEvaluation);
					approverHashSets.put(prevBlockEvaluation.getBlockhash(), new HashSet<>());
				}
			}
			
			if (!approverHashSets.containsKey(block.getPrevBranchBlockHash())) {
				BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
				if (prevBranchBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBranchBlockEvaluation);
					approverHashSets.put(prevBranchBlockEvaluation.getBlockhash(), new HashSet<>());
				}
			}
			
			if (approverHashSets.containsKey(block.getPrevBlockHash()))
				approverHashSets.get(block.getPrevBlockHash()).addAll(approverHashes);
			
			if (approverHashSets.containsKey(block.getPrevBranchBlockHash()))
				approverHashSets.get(block.getPrevBranchBlockHash()).addAll(approverHashes);

			// Update your cumulative weight and dereference hashes
			blockService.updateCumulativeWeight(currentBlock, approverHashes.size());
			approverHashSets.remove(currentBlock.getBlockhash());
		}
	}

	/**
	 * Update the percentage of times that tips selected by MCMC approve a block
	 * 
	 * @throws Exception
	 */
	public void updateRating() throws Exception {
		// Select #tipCount solid tips via MCMC
		final int tipCount = 100;
		HashMap<BlockEvaluation, HashSet<EvaluationWrapper>> selectedTips = new HashMap<BlockEvaluation, HashSet<EvaluationWrapper>>(tipCount);
		Random random = new SecureRandom();
		for (int i = 0; i < tipCount; i++) {
			BlockEvaluation selectedTip = blockService.getBlockEvaluation(tipsService.blockToApprove(1, random));
			HashSet<EvaluationWrapper> result;
			if (selectedTips.containsKey(selectedTip))  
				result = selectedTips.get(selectedTip);
			else 
				result = new HashSet<>();
			result.add(new EvaluationWrapper(selectedTip));
			selectedTips.put(selectedTip, result);
		}
		
		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = getSolidTipsDescending();
		HashMap<Sha256Hash, HashSet<EvaluationWrapper>> approverHashSets = new HashMap<>();
		for (BlockEvaluation tip : blockService.getSolidTips()) {
			approverHashSets.put(tip.getBlockhash(), new HashSet<>());
		}

		BlockEvaluation currentBlock = null;
		while ((currentBlock = blocksByDescendingHeight.poll()) != null) {
			// Add your own hashes as reference if current block is one of the selected tips
			HashSet<EvaluationWrapper> approverHashes = approverHashSets.get(currentBlock.getBlockhash());
			if (selectedTips.containsKey(currentBlock)) {
				approverHashes.addAll(selectedTips.get(currentBlock));
			}
			
			// Add all current references to both approved blocks (initialize if not yet initialized)
			Block block = blockService.getBlock(currentBlock.getBlockhash());
			
			if (!approverHashSets.containsKey(block.getPrevBlockHash())) {
				BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
				if (prevBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBlockEvaluation);
					approverHashSets.put(prevBlockEvaluation.getBlockhash(), new HashSet<>());
				}
			}
			
			if (!approverHashSets.containsKey(block.getPrevBranchBlockHash())) {
				BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
				if (prevBranchBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBranchBlockEvaluation);
					approverHashSets.put(prevBranchBlockEvaluation.getBlockhash(), new HashSet<>());
				}
			}
			
			if (approverHashSets.containsKey(block.getPrevBlockHash()))
				approverHashSets.get(block.getPrevBlockHash()).addAll(approverHashes);
			
			if (approverHashSets.containsKey(block.getPrevBranchBlockHash()))
				approverHashSets.get(block.getPrevBranchBlockHash()).addAll(approverHashes);

			// Update your cumulative weight
			blockService.updateRating(currentBlock, approverHashes.size());
			approverHashSets.remove(currentBlock.getBlockhash());
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

			// Optional steps from later to lower computational cost
			if (blocksToAdd.isEmpty())
				break;
			removeWhereUTXONotFoundOrUnconfirmed(blocksToAdd);

			// Resolve conflicting UTXO spends that have been approved by the network
			// (improbable to occur)
			resolveUnundoableConflicts(blocksToAdd);
			resolveUndoableConflicts(blocksToAdd);

			// Remove blocks from blocksToAdd that have at least one transaction input with
			// its corresponding output not found in the outputs table and remove their
			// approvers recursively too
			removeWhereUTXONotFoundOrUnconfirmed(blocksToAdd);

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
	private void removeWhereUTXONotFoundOrUnconfirmed(HashSet<BlockEvaluation> blocksToAdd) throws BlockStoreException {
		for (BlockEvaluation e : new HashSet<BlockEvaluation>(blocksToAdd)) {
			Block block = blockService.getBlock(e.getBlockhash());
			for (TransactionInput in : block.getTransactions().stream().flatMap(t -> t.getInputs().stream()).collect(Collectors.toList())) {
				if (in.isCoinBase())
					continue;
				UTXO utxo = transactionService.getUTXO(in.getOutpoint());
				if (utxo == null || !utxo.isConfirmed())
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
		List<Block> blocks = blockService.getBlocks(blocksToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

		// To check for unundoable conflicts, we do the following:
		// Create tuples (block, txinput) of all blocksToAdd
		Stream<Pair<Block, TransactionInput>> blockInputTuples = blocks.stream()
				.flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in)));

		// Now filter to only contain inputs that were already spent in the milestone
		// when the corresponding block has already been pruned
		Stream<Pair<Block, TransactionInput>> irresolvableConflicts = blockInputTuples
				.filter(pair -> transactionService.getUTXOSpent(pair.getRight()) && transactionService.getUTXOSpender(pair.getRight().getOutpoint()) == null);

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
		List<Block> blocksToAdd = blockService.getBlocks(blockEvaluationsToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

		//TODO validate dynamic validity too and if not, try to reverse until no conflicts
		
		// Find all conflicts between milestone and candidates 
		findMilestoneCandidateConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);
		findCandidateCandidateConflicts(blocksToAdd, conflictingOutPoints);

		// Resolve all conflicts by grouping by UTXO ordered by descending rating
		HashSet<BlockEvaluation> winningBlocks = resolveConflictsByDescendingRating(conflictingOutPoints);

		// TODO fix this, think through all cases again, this is definitely broken...
		// For milestone blocks that have been eliminated call disconnect procedure
		for (BlockEvaluation b : conflictingMilestoneBlocks.stream().filter(b -> !winningBlocks.contains(b)).collect(Collectors.toList())) {
			blockService.disconnect(b);
		}

		// For candidates that have been eliminated (conflictingOutPoints in blocksToAdd
		// \ winningBlocks) remove them from blocksToAdd
		for (Pair<BlockEvaluation, TransactionOutPoint> b : conflictingOutPoints.stream()
				.filter(b -> blockEvaluationsToAdd.contains(b.getLeft()) && !winningBlocks.contains(b.getLeft())).collect(Collectors.toList())) {
			removeBlockAndApproversFrom(blockEvaluationsToAdd, b.getLeft());
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
	private HashSet<BlockEvaluation> resolveConflictsByDescendingRating(HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints)
			throws BlockStoreException {
		// Initialize blocks that will survive the conflict resolution
		HashSet<BlockEvaluation> winningBlocks = conflictingOutPoints.stream().map(p -> p.getLeft()).collect(Collectors.toCollection(HashSet::new));

		// Sort conflicts internally by descending rating, then cumulative weight
		Comparator<Pair<BlockEvaluation, TransactionOutPoint>> byDescendingRating = Comparator
				.comparingLong((Pair<BlockEvaluation, TransactionOutPoint> e) -> e.getLeft().getRating())
				.thenComparingLong((Pair<BlockEvaluation, TransactionOutPoint> e) -> e.getLeft().getCumulativeWeight())
				.thenComparing((Pair<BlockEvaluation, TransactionOutPoint> e) -> e.getLeft().getBlockhash()).reversed();

		Supplier<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> conflictTreeSetSupplier = () -> new TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>(
				byDescendingRating);

		Map<Object, TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> conflicts = conflictingOutPoints.stream()
				.collect(Collectors.groupingBy(Pair::getRight, Collectors.toCollection(conflictTreeSetSupplier)));

		// Sort conflicts among each other by descending max(rating)
		Comparator<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> byDescendingSetRating = Comparator
				.comparingLong((TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getRating())
				.thenComparingLong((TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getCumulativeWeight())
				.thenComparing((TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getBlockhash()).reversed();

		Supplier<TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>>(
				byDescendingSetRating);

		TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> sortedConflicts = conflicts.values().stream()
				.collect(Collectors.toCollection(conflictsTreeSetSupplier));

		// Now handle conflicts by descending max(rating)
		for (TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> conflict : sortedConflicts) {
			// Take the block with the maximum rating in this conflict that is still in
			// winning Blocks
			Pair<BlockEvaluation, TransactionOutPoint> maxRatingPair = conflict.stream().findFirst().orElse(null);

			// If such a block exists, this conflict is resolved by eliminating all other
			// blocks in this conflict from winning Blocks
			if (maxRatingPair != null) {
				for (Pair<BlockEvaluation, TransactionOutPoint> pair : conflict) {
					if (!pair.getLeft().equals(maxRatingPair.getLeft())) {
						winningBlocks.remove(pair.getLeft()); //TODO we must also remove all approving conflict blocks...
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
	private void findCandidateCandidateConflicts(List<Block> blocksToAdd, HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints)
			throws BlockStoreException {
		// Create pairs of blocks and used non-coinbase utxos from blocksToAdd
		Stream<Pair<Block, TransactionOutPoint>> outPoints = blocksToAdd.stream()
				.flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, in.getOutpoint())));

		// Filter to only contain utxos that are spent more than once in the new
		// milestone candidates
		List<Pair<Block, TransactionOutPoint>> candidateCandidateConflicts = outPoints.collect(Collectors.groupingBy(Pair::getRight)).values().stream()
				.filter(l -> l.size() > 1).flatMap(l -> l.stream()).collect(Collectors.toList());

		// Add the conflicting candidates
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
	private void findMilestoneCandidateConflicts(List<Block> blocksToAdd, HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints,
			HashSet<BlockEvaluation> conflictingMilestoneBlocks) throws BlockStoreException {
		// Create pairs of blocks and used non-coinbase utxos from blocksToAdd
		Stream<Pair<Block, TransactionInput>> outPoints = blocksToAdd.stream()
				.flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, in)));

		// Filter to only contain utxos that were already spent by the milestone
		List<Pair<Block, TransactionInput>> candidatesConflictingWithMilestone = outPoints
				.filter(pair -> transactionService.getUTXOSpent(pair.getRight()) && transactionService.getUTXOSpender(pair.getRight().getOutpoint()) != null)
				.collect(Collectors.toList());

		// Add the conflicting candidates and milestone blocks
		for (Pair<Block, TransactionInput> pair : candidatesConflictingWithMilestone) {
			BlockEvaluation milestoneEvaluation = transactionService.getUTXOSpender(pair.getRight().getOutpoint());
			BlockEvaluation toAddEvaluation = blockService.getBlockEvaluation(pair.getLeft().getHash());
			conflictingOutPoints.add(Pair.of(toAddEvaluation, pair.getRight().getOutpoint()));
			conflictingOutPoints.add(Pair.of(milestoneEvaluation, pair.getRight().getOutpoint()));
			conflictingMilestoneBlocks.add(milestoneEvaluation);
			//addMilestoneApprovers(conflictingMilestoneBlocks, milestoneEvaluation);
		}
	}

//	/**
//	 * Recursively adds the specified block and its approvers to the collection if
//	 * the blocks are in the current milestone.
//	 * 
//	 * @param evaluations
//	 * @param milestoneEvaluation
//	 * @throws BlockStoreException
//	 */
//	private void addMilestoneApprovers(HashSet<BlockEvaluation> evaluations, BlockEvaluation milestoneEvaluation) throws BlockStoreException {
//		if (!milestoneEvaluation.isMilestone())
//			return;
//
//		// Add this block and add all of its milestone approvers
//		evaluations.add(milestoneEvaluation);
//		for (Sha256Hash approverHash : blockService.getSolidApproverBlockHashes(milestoneEvaluation.getBlockhash())) {
//			evaluations.add(blockService.getBlockEvaluation(approverHash));
//		}
//	}

	/**
	 * Recursively removes the specified block and its approvers from the collection
	 * if this block is contained in the collection.
	 * 
	 * @param evaluations
	 * @param blockEvaluation
	 * @throws BlockStoreException
	 */
	private void removeBlockAndApproversFrom(Collection<BlockEvaluation> evaluations, BlockEvaluation blockEvaluation) throws BlockStoreException {
		if (!evaluations.contains(blockEvaluation))
			return;

		// Remove this block and remove its approvers
		evaluations.remove(blockEvaluation);
		for (Sha256Hash approver : blockService.getSolidApproverBlockHashes(blockEvaluation.getBlockhash())) {
			removeBlockAndApproversFrom(evaluations, blockService.getBlockEvaluation(approver));
		}
	}

	/**
	 * Returns all solid tips ordered by descending height
	 * 
	 * @return solid tips by ordered by descending height
	 * @throws BlockStoreException
	 */
	private PriorityQueue<BlockEvaluation> getSolidTipsDescending() throws BlockStoreException {
		List<BlockEvaluation> solidTips = blockService.getSolidTips();
		CollectionUtils.filter(solidTips, e -> ((BlockEvaluation) e).isSolid());
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = new PriorityQueue<BlockEvaluation>(solidTips.size() + 1,
				Comparator.comparingLong(BlockEvaluation::getHeight).reversed());
		blocksByDescendingHeight.addAll(solidTips);
		return blocksByDescendingHeight;
	}

	private class EvaluationWrapper {
		public BlockEvaluation blockEvaluation;

		public EvaluationWrapper(BlockEvaluation blockEvaluation) {
			this.blockEvaluation = blockEvaluation;
		}
	}
}
