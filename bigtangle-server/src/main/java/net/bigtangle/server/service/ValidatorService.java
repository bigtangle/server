/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.UTXO;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class ValidatorService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    private BlockService blockService;
    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    private TransactionService transactionService;

    // Tries to update validity according to the mining consensus rules
    // Assumes reward block to not necessarily be valid
    // Returns true if block rewards are valid
    public boolean assessMiningRewardBlock(Block header) throws BlockStoreException {
        return assessMiningRewardBlock(header, false);
    }

    // If rewardvalid = false but it is still about to be added to the
    // milestone,
    // we must calculate rewards anyways.
    // Assumes reward block to be valid, which is the case when trying to add to
    // milestone.
    public boolean calculateMiningReward(Block header) throws BlockStoreException {
        return assessMiningRewardBlock(header, true);
    }

    private boolean assessMiningRewardBlock(Block header, boolean assumeMilestone) throws BlockStoreException {
        BlockEvaluation blockEvaluation = blockService.getBlockEvaluation(header.getHash());

        // Once set valid, always valid
        if (blockEvaluation.isRewardValid())
            return true;

        // Only solid mining reward blocks
        if (!assumeMilestone && header.getBlocktype() != NetworkParameters.BLOCKTYPE_REWARD
                || !blockEvaluation.isSolid())
            return false;

        // Get interval height from tx data
        ByteBuffer bb = ByteBuffer.wrap(header.getTransactions().get(0).getData());
        long fromHeight = bb.getLong();
        long toHeight = fromHeight + networkParameters.getRewardHeightInterval() - 1;
        if (toHeight >= blockEvaluation.getHeight())
            return false;

        // Count how many blocks from the reward interval are approved
        // Also build rewards while we're at it
        long approveCount = 0;
        Block prevRewardBlock = null;
        PriorityQueue<BlockEvaluation> blockQueue = new PriorityQueue<BlockEvaluation>(
                Comparator.comparingLong(BlockEvaluation::getHeight).reversed());
        HashSet<BlockEvaluation> queuedBlocks = new HashSet<>();
        HashMap<Address, Long> rewardCount = new HashMap<>();
        blockQueue.add(blockEvaluation);
        queuedBlocks.add(blockEvaluation);
        
        // Initial reward block must be defined
        if (fromHeight == 0)
            prevRewardBlock = networkParameters.getGenesisBlock();

        // Go backwards by height
        BlockEvaluation currentBlock = null;
        while ((currentBlock = blockQueue.poll()) != null) {
            Block block = blockService.getBlock(currentBlock.getBlockhash());

            // Stop criterion: Block height lower than approved interval height
            if (currentBlock.getHeight() < fromHeight)
                continue;

            if (currentBlock.getHeight() <= toHeight) {
                // Failure criterion: approving non-milestone blocks
                if (!assumeMilestone && currentBlock.isMilestone() == false)
                    return false;

                // Count rewards, try to find prevRewardBlock
                approveCount++;
                Address miner = new Address(networkParameters, block.getMineraddress());
                if (!rewardCount.containsKey(miner))
                    rewardCount.put(miner, 1L);
                else
                    rewardCount.put(miner, rewardCount.get(miner) + 1);

                if (block.getBlocktype() == NetworkParameters.BLOCKTYPE_REWARD) {
                    ByteBuffer prevBb = ByteBuffer.wrap(block.getTransactions().get(0).getData());
                    long prevFromHeight = prevBb.getLong();

                    if (prevFromHeight == fromHeight - networkParameters.getRewardHeightInterval()) {
                        // Failure if there are multiple prevRewardBlocks
                        // approved
                        if (prevRewardBlock != null)
                            return false;

                        prevRewardBlock = block;
                    }

                }
            }

            // Continue with approved blocks
            BlockEvaluation prevBlock = blockService.getBlockEvaluation(block.getPrevBlockHash());
            if (!queuedBlocks.contains(prevBlock)) {
                queuedBlocks.add(prevBlock);
                blockQueue.add(prevBlock);
            }

            BlockEvaluation prevBranchBlock = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
            if (!queuedBlocks.contains(prevBranchBlock)) {
                queuedBlocks.add(prevBranchBlock);
                blockQueue.add(prevBranchBlock);
            }
        }

        // Compare with amount of milestone blocks in interval to assess
        // validity
        if (prevRewardBlock != null && (assumeMilestone
                || approveCount > store.getCountMilestoneBlocksInInterval(fromHeight, toHeight) * 95 / 100)) {
            // If the previous one has not been assessed yet, we need to assess
            // the previous one first
            if (!blockService.getBlockEvaluation(prevRewardBlock.getHash()).isRewardValid()) {
                if (!assessMiningRewardBlock(prevRewardBlock, assumeMilestone)) {
                    return false;
                }
            }

            // Calculate rewards and store them for later
            long perTxReward = store.getTxReward(prevRewardBlock.getHash());
            for (Entry<Address, Long> entry : rewardCount.entrySet()) {
                store.insertMiningRewardCalculation(header.getHash(), entry.getKey(), entry.getValue() * perTxReward);
            }

            // TX reward adjustments
            long nextPerTxReward = Math.max(1, 20000000 * 365 * 24 * 60 * 60 / approveCount / (header.getTimeSeconds() - prevRewardBlock.getTimeSeconds()));
            nextPerTxReward = Math.max(nextPerTxReward, perTxReward / 4);
            nextPerTxReward = Math.min(nextPerTxReward, perTxReward * 4);
            store.insertTxReward(header.getHash(), nextPerTxReward);

            // Set valid
            store.updateBlockEvaluationRewardValid(header.getHash(), true);
            return true;
        } else {
            return false;
        }
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

        // TODO dynamic conflictpoints: token issuance ids, mining reward height
        // intervals

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
        HashSet<Pair<BlockEvaluation, ConflictPoint>> conflictingOutPoints = new HashSet<Pair<BlockEvaluation, ConflictPoint>>();
        HashSet<BlockEvaluation> conflictingMilestoneBlocks = new HashSet<BlockEvaluation>();
        List<Block> blocksToAdd = blockService
                .getBlocks(blockEvaluationsToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

        // Find all conflicts in the new blocks
        findConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);

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
        for (Pair<BlockEvaluation, ConflictPoint> b : conflictingOutPoints.stream()
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
            Collection<Pair<BlockEvaluation, ConflictPoint>> conflictingOutPoints) throws BlockStoreException {
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
        Comparator<Pair<BlockEvaluation, ConflictPoint>> byDescendingRating = Comparator
                .comparingLong((Pair<BlockEvaluation, ConflictPoint> e) -> e.getLeft().getRating())
                .thenComparingLong((Pair<BlockEvaluation, ConflictPoint> e) -> e.getLeft().getCumulativeWeight())
                .thenComparingLong((Pair<BlockEvaluation, ConflictPoint> e) -> -e.getLeft().getInsertTime())
                .thenComparing((Pair<BlockEvaluation, ConflictPoint> e) -> e.getLeft().getBlockhash()).reversed();

        Supplier<TreeSet<Pair<BlockEvaluation, ConflictPoint>>> conflictTreeSetSupplier = () -> new TreeSet<Pair<BlockEvaluation, ConflictPoint>>(
                byDescendingRating);

        Map<Object, TreeSet<Pair<BlockEvaluation, ConflictPoint>>> conflicts = conflictingOutPoints.stream()
                .collect(Collectors.groupingBy(Pair::getRight, Collectors.toCollection(conflictTreeSetSupplier)));

        // Sort conflicts among each other by descending max(rating)
        Comparator<TreeSet<Pair<BlockEvaluation, ConflictPoint>>> byDescendingSetRating = Comparator
                .comparingLong((TreeSet<Pair<BlockEvaluation, ConflictPoint>> s) -> s.first().getLeft().getRating())
                .thenComparingLong(
                        (TreeSet<Pair<BlockEvaluation, ConflictPoint>> s) -> s.first().getLeft().getCumulativeWeight())
                .thenComparingLong(
                        (TreeSet<Pair<BlockEvaluation, ConflictPoint>> s) -> -s.first().getLeft().getInsertTime())
                .thenComparing((TreeSet<Pair<BlockEvaluation, ConflictPoint>> s) -> s.first().getLeft().getBlockhash())
                .reversed();

        Supplier<TreeSet<TreeSet<Pair<BlockEvaluation, ConflictPoint>>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<Pair<BlockEvaluation, ConflictPoint>>>(
                byDescendingSetRating);

        TreeSet<TreeSet<Pair<BlockEvaluation, ConflictPoint>>> sortedConflicts = conflicts.values().stream()
                .collect(Collectors.toCollection(conflictsTreeSetSupplier));

        // Now handle conflicts by descending max(rating)
        for (TreeSet<Pair<BlockEvaluation, ConflictPoint>> conflict : sortedConflicts) {
            // Take the block with the maximum rating in this conflict that is
            // still in winning Blocks
            Pair<BlockEvaluation, ConflictPoint> maxRatingPair = conflict.stream()
                    .filter(p -> winningBlocks.contains(p.getLeft())).findFirst().orElse(null);

            // If such a block exists, this conflict is resolved by eliminating
            // all other
            // blocks in this conflict from winning Blocks
            if (maxRatingPair != null) {
                for (Pair<BlockEvaluation, ConflictPoint> pair : conflict) {
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
     * Finds conflicts in blocksToAdd
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    public void findConflicts(Collection<Block> blocksToAdd,
            Collection<Pair<BlockEvaluation, ConflictPoint>> conflictingOutPoints,
            Collection<BlockEvaluation> conflictingMilestoneBlocks) throws BlockStoreException {

        findMilestoneConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);
        findCandidateConflicts(blocksToAdd, conflictingOutPoints);
    }

    /**
     * Finds conflicts among blocks to add themselves
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    private void findCandidateConflicts(Collection<Block> blocksToAdd,
            Collection<Pair<BlockEvaluation, ConflictPoint>> conflictingOutPoints) throws BlockStoreException {
        // Create pairs of blocks and used non-coinbase utxos from blocksToAdd
        Stream<Pair<Block, ConflictPoint>> outPoints = blocksToAdd.stream()
                .flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream())
                        .filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, new ConflictPoint(in.getOutpoint()))));

        // TODO dynamic conflictpoints: token issuance ids, mining reward height
        // intervals

        // Filter to only contain conflicts that are spent more than once in the
        // new
        // milestone candidates
        List<Pair<Block, ConflictPoint>> candidateCandidateConflicts = outPoints
                .collect(Collectors.groupingBy(Pair::getRight)).values().stream().filter(l -> l.size() > 1)
                .flatMap(l -> l.stream()).collect(Collectors.toList());

        // Add the conflicting candidates
        for (Pair<Block, ConflictPoint> pair : candidateCandidateConflicts) {
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
    private void findMilestoneConflicts(Collection<Block> blocksToAdd,
            Collection<Pair<BlockEvaluation, ConflictPoint>> conflictingOutPoints,
            Collection<BlockEvaluation> conflictingMilestoneBlocks) throws BlockStoreException {
        // Create pairs of blocks and used non-coinbase utxos from blocksToAdd
        Stream<Pair<Block, TransactionInput>> outPoints = blocksToAdd.stream().flatMap(b -> b.getTransactions().stream()
                .flatMap(t -> t.getInputs().stream()).filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, in)));

        // TODO dynamic conflictpoints: token issuance ids, mining reward height
        // intervals

        // Filter to only contain conflicts that were already spent by
        // maintained
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
            conflictingOutPoints.add(Pair.of(toAddEvaluation, new ConflictPoint(pair.getRight().getOutpoint())));
            conflictingOutPoints.add(Pair.of(milestoneEvaluation, new ConflictPoint(pair.getRight().getOutpoint())));
            conflictingMilestoneBlocks.add(milestoneEvaluation);
            // addMilestoneApprovers(conflictingMilestoneBlocks,
            // milestoneEvaluation);
        }
    }
}
