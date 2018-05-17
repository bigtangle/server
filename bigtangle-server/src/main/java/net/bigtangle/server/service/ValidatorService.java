/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Collection;
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
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;

//TODO bugs: 
//disallow 0x000... as tx and block hashes
//disallow token issuances without txs

@Service
public class ValidatorService {

    @Autowired
    private BlockService blockService;
    @Autowired
    private TransactionService transactionService;

    public boolean assessMiningRewardBlock(Block header) {
        // TODO begin task for checking local validity assessment of mining
        return true;
    }

    /**
     * Remove blocks from blocksToAdd that have at least one transaction input
     * with its corresponding output not found in the outputs table and remove
     * their approvers recursively too
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    public void removeWhereUTXONotFoundOrUnconfirmed(Collection<BlockEvaluation> blocksToAdd)
            throws BlockStoreException {
        for (BlockEvaluation e : new HashSet<BlockEvaluation>(blocksToAdd)) {
            Block block = blockService.getBlock(e.getBlockhash());
            for (TransactionInput in : block.getTransactions().stream().flatMap(t -> t.getInputs().stream())
                    .collect(Collectors.toList())) {
                if (in.isCoinBase())
                    continue;
                UTXO utxo = transactionService.getUTXO(in.getOutpoint());
                if (utxo == null || !utxo.isConfirmed())
                    blockService.removeBlockAndApproversFrom(blocksToAdd, e);
            }
        }
    }

    /**
     * Resolves conflicts in new blocks to add that cannot be undone due to
     * pruning. This method does not do anything if not pruning blocks.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    public void resolvePrunedConflicts(Collection<BlockEvaluation> blocksToAdd) throws BlockStoreException {
        // Get the blocks to add as actual blocks from blockService
        List<Block> blocks = blockService
                .getBlocks(blocksToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

        // To check for unundoable conflicts, we do the following:
        // Create tuples (block, txinput) of all blocksToAdd
        Stream<Pair<Block, TransactionInput>> blockInputTuples = blocks.stream().flatMap(
                b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in)));

        // Now filter to only contain inputs that were already spent in the
        // milestone where the corresponding block has already been pruned or is
        // unmaintained...
        Stream<Pair<Block, TransactionInput>> irresolvableConflicts = blockInputTuples
                .filter(pair -> transactionService.getUTXOSpent(pair.getRight())
                        && (transactionService.getUTXOSpender(pair.getRight().getOutpoint()) == null
                                || !transactionService.getUTXOSpender(pair.getRight().getOutpoint()).isMaintained()));

        // These blocks cannot be added and must therefore be removed from
        // blocksToAdd
        for (Pair<Block, TransactionInput> p : irresolvableConflicts.collect(Collectors.toList())) {
            blockService.removeBlockAndApproversFrom(blocksToAdd,
                    blockService.getBlockEvaluation(p.getLeft().getHash()));
        }
    }

    /**
     * Resolves conflicts between milestone blocks and milestone candidates as
     * well as conflicts among milestone candidates.
     * 
     * @param blockEvaluationsToAdd
     * @throws BlockStoreException
     */
    public void resolveUndoableConflicts(Collection<BlockEvaluation> blockEvaluationsToAdd) throws BlockStoreException {
        // TODO replace all transactionOutPoints in this class with new class
        // conflictpoints (equals of transactionoutpoints, token issuance ids,
        // mining reward height intervals)
        HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints = new HashSet<Pair<BlockEvaluation, TransactionOutPoint>>();
        HashSet<BlockEvaluation> conflictingMilestoneBlocks = new HashSet<BlockEvaluation>();
        List<Block> blocksToAdd = blockService
                .getBlocks(blockEvaluationsToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

        // Find all conflicts between milestone and candidates
        findMilestoneConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);
        findCandidateConflicts(blocksToAdd, conflictingOutPoints);

        // Resolve all conflicts by grouping by UTXO ordered by descending
        // rating
        Pair<HashSet<BlockEvaluation>, HashSet<BlockEvaluation>> conflictResolution = resolveConflictsByDescendingRating(
                conflictingOutPoints);
        HashSet<BlockEvaluation> losingBlocks = conflictResolution.getRight();

        // For milestone blocks that have been eliminated call disconnect
        // procedure
        for (BlockEvaluation b : conflictingMilestoneBlocks.stream().filter(b -> losingBlocks.contains(b))
                .collect(Collectors.toList())) {
            blockService.unconfirm(b);
        }

        // For candidates that have been eliminated (conflictingOutPoints in
        // blocksToAdd
        // \ winningBlocks) remove them from blocksToAdd
        for (Pair<BlockEvaluation, TransactionOutPoint> b : conflictingOutPoints.stream()
                .filter(b -> blockEvaluationsToAdd.contains(b.getLeft()) && losingBlocks.contains(b.getLeft()))
                .collect(Collectors.toList())) {
            blockService.removeBlockAndApproversFrom(blockEvaluationsToAdd, b.getLeft());
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
    public Pair<HashSet<BlockEvaluation>, HashSet<BlockEvaluation>> resolveConflictsByDescendingRating(
            Collection<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints) throws BlockStoreException {
        // Initialize blocks that will survive the conflict resolution
        HashSet<BlockEvaluation> winningBlocksSingle = conflictingOutPoints.stream().map(p -> p.getLeft())
                .collect(Collectors.toCollection(HashSet::new));
        HashSet<BlockEvaluation> winningBlocks = new HashSet<>();
        for (BlockEvaluation winningBlock : winningBlocksSingle) {
            blockService.addApprovedNonMilestoneBlocksTo(winningBlocks, winningBlock);
            blockService.addMilestoneApproversTo(winningBlocks, winningBlock);
        }
        HashSet<BlockEvaluation> losingBlocks = new HashSet<>(winningBlocks);

        // Sort conflicts internally by descending rating, then cumulative
        // weight
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
                .comparingLong(
                        (TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getRating())
                .thenComparingLong((TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft()
                        .getCumulativeWeight())
                .thenComparingLong(
                        (TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> -s.first().getLeft().getInsertTime())
                .thenComparing(
                        (TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> s) -> s.first().getLeft().getBlockhash())
                .reversed();

        Supplier<TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>>(
                byDescendingSetRating);

        TreeSet<TreeSet<Pair<BlockEvaluation, TransactionOutPoint>>> sortedConflicts = conflicts.values().stream()
                .collect(Collectors.toCollection(conflictsTreeSetSupplier));

        // Now handle conflicts by descending max(rating)
        for (TreeSet<Pair<BlockEvaluation, TransactionOutPoint>> conflict : sortedConflicts) {
            // Take the block with the maximum rating in this conflict that is
            // still in winning Blocks
            Pair<BlockEvaluation, TransactionOutPoint> maxRatingPair = conflict.stream()
                    .filter(p -> winningBlocks.contains(p.getLeft())).findFirst().orElse(null);

            // If such a block exists, this conflict is resolved by eliminating
            // all other
            // blocks in this conflict from winning Blocks
            if (maxRatingPair != null) {
                for (Pair<BlockEvaluation, TransactionOutPoint> pair : conflict) {
                    if (pair.getLeft() != maxRatingPair.getLeft()) {
                        blockService.removeBlockAndApproversFrom(winningBlocks, pair.getLeft());
                    }
                }
            }
        }

        losingBlocks.removeAll(winningBlocks);

        return Pair.of(winningBlocks, losingBlocks);
    }

    /**
     * Finds conflicts among blocks to add
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    public void findCandidateConflicts(Collection<Block> blocksToAdd,
            Collection<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints) throws BlockStoreException {
        // Create pairs of blocks and used non-coinbase utxos from blocksToAdd
        Stream<Pair<Block, TransactionOutPoint>> outPoints = blocksToAdd.stream()
                .flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream())
                        .filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, in.getOutpoint())));

        // Filter to only contain utxos that are spent more than once in the new
        // milestone candidates
        List<Pair<Block, TransactionOutPoint>> candidateCandidateConflicts = outPoints
                .collect(Collectors.groupingBy(Pair::getRight)).values().stream().filter(l -> l.size() > 1)
                .flatMap(l -> l.stream()).collect(Collectors.toList());

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
    public void findMilestoneConflicts(Collection<Block> blocksToAdd,
            Collection<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints,
            Collection<BlockEvaluation> conflictingMilestoneBlocks) throws BlockStoreException {
        // Create pairs of blocks and used non-coinbase utxos from blocksToAdd
        Stream<Pair<Block, TransactionInput>> outPoints = blocksToAdd.stream().flatMap(b -> b.getTransactions().stream()
                .flatMap(t -> t.getInputs().stream()).filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, in)));

        // Filter to only contain utxos that were already spent by maintained
        // milestone blocks.
        List<Pair<Block, TransactionInput>> candidatesConflictingWithMilestone = outPoints
                .filter(pair -> transactionService.getUTXOSpent(pair.getRight())
                        && transactionService.getUTXOSpender(pair.getRight().getOutpoint()) != null 
                        && transactionService.getUTXOSpender(pair.getRight().getOutpoint()).isMaintained())
                .collect(Collectors.toList());

        // Add the conflicting candidates and milestone blocks
        for (Pair<Block, TransactionInput> pair : candidatesConflictingWithMilestone) {
            BlockEvaluation milestoneEvaluation = transactionService.getUTXOSpender(pair.getRight().getOutpoint());
            BlockEvaluation toAddEvaluation = blockService.getBlockEvaluation(pair.getLeft().getHash());
            conflictingOutPoints.add(Pair.of(toAddEvaluation, pair.getRight().getOutpoint()));
            conflictingOutPoints.add(Pair.of(milestoneEvaluation, pair.getRight().getOutpoint()));
            conflictingMilestoneBlocks.add(milestoneEvaluation);
            // addMilestoneApprovers(conflictingMilestoneBlocks,
            // milestoneEvaluation);
        }
    }
}
