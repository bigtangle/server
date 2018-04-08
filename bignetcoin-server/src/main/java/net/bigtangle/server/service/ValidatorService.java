/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;

@Service
public class ValidatorService {

	@Autowired
	private BlockService blockService;
	@Autowired
	private TransactionService transactionService;
	
	public boolean assessMiningRewardBlock(Block header) {
        // TODO begin checking local validity assessment since it begins as false
		return true;
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

	//TODO validate other dynamic validities too (TransactionOutPoint -> ConflictPoint)
	public HashSet<BlockEvaluation> resolveConflictsByDescendingRating(HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints)
			throws BlockStoreException {
		// Initialize blocks that will survive the conflict resolution
		HashSet<BlockEvaluation> winningBlocksSingle = conflictingOutPoints.stream().map(p -> p.getLeft()).collect(Collectors.toCollection(HashSet::new));
		HashSet<BlockEvaluation> winningBlocks = new HashSet<>();
		for (BlockEvaluation winningBlock : winningBlocksSingle) {
			addApprovedNonMilestoneBlocks(winningBlocks, winningBlock);
			addMilestoneApprovers(winningBlocks, winningBlock);
		}

		// Sort conflicts internally by descending rating, then cumulative weight
		Comparator<Pair<BlockEvaluation, TransactionOutPoint>> byDescendingRating = Comparator
				.comparingLong((Pair<BlockEvaluation, TransactionOutPoint> e) -> e.getLeft().getRating())
				.thenComparingLong((Pair<BlockEvaluation, TransactionOutPoint> e) -> e.getLeft().getCumulativeWeight())
				.thenComparingLong((Pair<BlockEvaluation, TransactionOutPoint> e) -> -e.getLeft().getInsertTime())
				.thenComparing((Pair<BlockEvaluation, TransactionOutPoint> e) -> e.getLeft().getBlockhash()).reversed();

		Supplier<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> conflictTreeSetSupplier = () -> new TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>(
				byDescendingRating);

		Map<Object, TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> conflicts = conflictingOutPoints.stream()
				.collect(Collectors.groupingBy(Pair::getRight, Collectors.toCollection(conflictTreeSetSupplier)));

		// Sort conflicts among each other by descending max(rating)
		Comparator<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> byDescendingSetRating = Comparator
				.comparingLong((TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getRating())
				.thenComparingLong((TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getCumulativeWeight())
				.thenComparingLong((TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> -s.first().getLeft().getInsertTime())
				.thenComparing((TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getBlockhash()).reversed();

		Supplier<TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>>(
				byDescendingSetRating);

		TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> sortedConflicts = conflicts.values().stream()
				.collect(Collectors.toCollection(conflictsTreeSetSupplier));

		// Now handle conflicts by descending max(rating)
		for (TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> conflict : sortedConflicts) {
			// Take the block with the maximum rating in this conflict that is still in winning Blocks
			Pair<BlockEvaluation, TransactionOutPoint> maxRatingPair = conflict.stream().filter(p -> winningBlocks.contains(p.getLeft())).findFirst().orElse(null);

			// If such a block exists, this conflict is resolved by eliminating all other
			// blocks in this conflict from winning Blocks
			if (maxRatingPair != null) {
				for (Pair<BlockEvaluation, TransactionOutPoint> pair : conflict) {
					if (pair.getLeft() != maxRatingPair.getLeft()) {
						blockService.removeBlockAndApproversFrom(winningBlocks, pair.getLeft());
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
	public void findCandidateCandidateConflicts(List<Block> blocksToAdd, HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints)
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
	public void findMilestoneCandidateConflicts(List<Block> blocksToAdd, HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints,
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

	/**
	 * Recursively adds the specified block and its approvers to the collection if
	 * the blocks are in the current milestone.
	 * 
	 * @param evaluations
	 * @param milestoneEvaluation
	 * @throws BlockStoreException
	 */
	private void addMilestoneApprovers(HashSet<BlockEvaluation> evaluations, BlockEvaluation milestoneEvaluation) throws BlockStoreException {
		if (!milestoneEvaluation.isMilestone())
			return;

		// Add this block and add all of its milestone approvers
		evaluations.add(milestoneEvaluation);
		for (Sha256Hash approverHash : blockService.getSolidApproverBlockHashes(milestoneEvaluation.getBlockhash())) {
			addMilestoneApprovers(evaluations, blockService.getBlockEvaluation(approverHash));
		}
	}

	/**
	 * Recursively adds the specified block and its approved blocks to the collection if
	 * the blocks are not in the current milestone.
	 * 
	 * @param evaluations
	 * @param milestoneEvaluation
	 * @throws BlockStoreException
	 */
	private void addApprovedNonMilestoneBlocks(HashSet<BlockEvaluation> evaluations, BlockEvaluation evaluation) throws BlockStoreException {
		if (evaluation.isMilestone())
			return;

		// Add this block and add all of its approved non-milestone blocks
		evaluations.add(evaluation);
		
		Block block = blockService.getBlock(evaluation.getBlockhash());
		BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
		BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
		
		if (prevBlockEvaluation != null)
			addApprovedNonMilestoneBlocks(evaluations, prevBlockEvaluation);
		if (prevBranchBlockEvaluation != null)
			addApprovedNonMilestoneBlocks(evaluations, prevBranchBlockEvaluation);
	}
}
