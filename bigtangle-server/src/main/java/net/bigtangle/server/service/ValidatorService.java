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
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.directory.api.util.exception.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.script.Script;
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

    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

    // Tries to update validity according to the mining consensus rules
    // Assumes reward block to not necessarily be valid
    // Returns true if block rewards are valid
    public boolean assessMiningRewardBlock(Block header, long height) throws BlockStoreException {
        return assessMiningRewardBlock(header, height, false);
    }

    // If rewardvalid = false but it is still about to be added to the
    // milestone,
    // we must calculate rewards anyways.
    // Assumes reward block to be valid, which is the case when trying to add to
    // milestone.
    public boolean calculateMiningReward(Block header, long height) throws BlockStoreException {
        return assessMiningRewardBlock(header, height, true);
    }

    // In ascending order of miner addresses, we create reward tx
    // deterministically
    public Transaction generateMiningRewardTX(Block header, long fromHeight) throws BlockStoreException {
        long toHeight = fromHeight + networkParameters.getRewardHeightInterval() - 1;

        // Count how many blocks from miners in the reward interval are approved
        // and build rewards
        long approveCount = 0;
        Block prevRewardBlock = null;
        PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
        HashSet<BlockWrap> queuedBlocks = new HashSet<>();
        HashMap<Address, Long> rewardCount = new HashMap<>();
        blockQueue.add(store.getBlockWrap(header.getPrevBlockHash()));
        blockQueue.add(store.getBlockWrap(header.getPrevBranchBlockHash()));
        queuedBlocks.add(store.getBlockWrap(header.getPrevBlockHash()));
        queuedBlocks.add(store.getBlockWrap(header.getPrevBranchBlockHash()));

        // Initial reward block must be defined
        if (fromHeight == 0)
            prevRewardBlock = networkParameters.getGenesisBlock();

        // Go backwards by height
        BlockWrap currentBlock = null;
        while ((currentBlock = blockQueue.poll()) != null) {
            Block block = currentBlock.getBlockEvaluation().getBlockHash().equals(header.getHash()) ? header : blockService.getBlock(currentBlock.getBlock().getHash());

            // Stop criterion: Block height lower than approved interval height
            if (currentBlock.getBlockEvaluation().getHeight() < fromHeight)
                continue;

            if (currentBlock.getBlockEvaluation().getHeight() <= toHeight) {
                // Count rewards, try to find prevRewardBlock
                approveCount++;
                Address miner = new Address(networkParameters, block.getMinerAddress());
                if (!rewardCount.containsKey(miner))
                    rewardCount.put(miner, 1L);
                else
                    rewardCount.put(miner, rewardCount.get(miner) + 1);

                if (block.getBlockType() == NetworkParameters.BLOCKTYPE_REWARD) {
                    ByteBuffer prevBb = ByteBuffer.wrap(block.getTransactions().get(0).getData());
                    long prevFromHeight = prevBb.getLong();

                    if (prevFromHeight == fromHeight - networkParameters.getRewardHeightInterval()) {
                        // Failure if there are multiple prevRewardBlocks
                        if (prevRewardBlock != null)
                            logger.error(
                                    "Failed in creating reward block here, try resolving conflicts with rewarding in mind");

                        prevRewardBlock = block;
                    }
                }
            }

            // Continue with approved blocks
            BlockWrap prevBlock = store.getBlockWrap(block.getPrevBlockHash());
            if (!queuedBlocks.contains(prevBlock) && prevBlock != null) {
                queuedBlocks.add(prevBlock);
                blockQueue.add(prevBlock);
            }

            BlockWrap prevBranchBlock = store.getBlockWrap(block.getPrevBranchBlockHash());
            if (!queuedBlocks.contains(prevBranchBlock) && prevBranchBlock != null) {
                queuedBlocks.add(prevBranchBlock);
                blockQueue.add(prevBranchBlock);
            }
        }

        // If the previous one has not been assessed yet, we need to assess
        // the previous one first
        if (!blockService.getBlockEvaluation(prevRewardBlock.getHash()).isRewardValid()) {
            logger.error("Not ready for new mining reward block: Make sure the previous one is confirmed first!");
        }

        // TX reward adjustments for next rewards
        long perTxReward = store.getTxRewardValue(prevRewardBlock.getHash());
        long nextPerTxReward = Math.max(1, 20000000 * 365 * 24 * 60 * 60 / approveCount
                / (header.getTimeSeconds() - prevRewardBlock.getTimeSeconds()));
        nextPerTxReward = Math.max(nextPerTxReward, perTxReward / 4);
        nextPerTxReward = Math.min(nextPerTxReward, perTxReward * 4);
        // store.insertTxReward(header.getHash(), nextPerTxReward);

        // Calculate rewards
        PriorityQueue<Triple<Sha256Hash, Address, Long>> sortedMiningRewardCalculations = new PriorityQueue<>(
                Comparator.comparingLong(t -> t.getRight()));
        for (Entry<Address, Long> entry : rewardCount.entrySet())
            sortedMiningRewardCalculations
                    .add(Triple.of(header.getHash(), entry.getKey(), entry.getValue() * perTxReward));

        Transaction tx = new Transaction(networkParameters);
        Triple<Sha256Hash, Address, Long> utxoData;
        while ((utxoData = sortedMiningRewardCalculations.poll()) != null) {
            tx.addOutput(Coin.SATOSHI.times(utxoData.getRight()), utxoData.getMiddle());
        }

        // The input does not really need to be a valid signature, as long
        // as it has the right general form.
        TransactionInput input = new TransactionInput(networkParameters, tx,
                Script.createInputScript(Block.EMPTY_BYTES, Block.EMPTY_BYTES));
        tx.addInput(input);
        
        // Add the type-specific data (fromHeight)
        byte[] data = new byte[8];
        Utils.uint64ToByteArrayLE(fromHeight, data, 0);
        tx.setData(data);

        return tx;
    }

    // TODO cleanup and crosscheck with above code, rewrite this to batched computation of relevant reward block only,
    // e. g. go forward until confirmed reward block is found or up to the end
    private boolean assessMiningRewardBlock(Block header, long height, boolean assumeMilestone) throws BlockStoreException {
        BlockWrap blockEvaluation = store.getBlockWrap(header.getHash());

        // Once set valid, always valid
//        if (blockEvaluation.isRewardValid())
//            return true;

        // Only mining reward blocks
        if (header.getBlockType() != NetworkParameters.BLOCKTYPE_REWARD)
            return false;

        // Get interval height from tx data
        ByteBuffer bb = ByteBuffer.wrap(header.getTransactions().get(0).getData());
        long fromHeight = bb.getLong();
        long toHeight = fromHeight + networkParameters.getRewardHeightInterval() - 1;
        if (height <= toHeight)
            return false;

        // Count how many blocks from the reward interval are approved
        // Also build rewards while we're at it
        long approveCount = 0;
        Block prevRewardBlock = null;
        PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
        HashSet<BlockWrap> queuedBlocks = new HashSet<>();
        HashMap<Address, Long> rewardCount = new HashMap<>();
        blockQueue.add(store.getBlockWrap(header.getPrevBlockHash()));
        blockQueue.add(store.getBlockWrap(header.getPrevBranchBlockHash()));
        queuedBlocks.add(store.getBlockWrap(header.getPrevBlockHash()));
        queuedBlocks.add(store.getBlockWrap(header.getPrevBranchBlockHash()));

        // Initial reward block must be defined
        if (fromHeight == 0)
            prevRewardBlock = networkParameters.getGenesisBlock();

        // Go backwards by height
        BlockWrap currentBlock = null;
        while ((currentBlock = blockQueue.poll()) != null) {
            Block block = currentBlock.getBlock();

            // Stop criterion: Block height lower than approved interval height
            if (currentBlock.getBlockEvaluation().getHeight() < fromHeight)
                continue;

            if (currentBlock.getBlockEvaluation().getHeight() <= toHeight) {
                // Failure criterion: approving non-milestone blocks
                if (!assumeMilestone && currentBlock.getBlockEvaluation().isMilestone() == false)
                    return false;

                // Count rewards, try to find prevRewardBlock
                approveCount++;
                Address miner = new Address(networkParameters, block.getMinerAddress());
                if (!rewardCount.containsKey(miner))
                    rewardCount.put(miner, 1L);
                else
                    rewardCount.put(miner, rewardCount.get(miner) + 1);

                if (block.getBlockType() == NetworkParameters.BLOCKTYPE_REWARD) {
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
            BlockWrap prevBlock = store.getBlockWrap(block.getPrevBlockHash());
            if (!queuedBlocks.contains(prevBlock) && prevBlock != null) {
                queuedBlocks.add(prevBlock);
                blockQueue.add(prevBlock);
            }

            BlockWrap prevBranchBlock = store.getBlockWrap(block.getPrevBranchBlockHash());
            if (!queuedBlocks.contains(prevBranchBlock) && prevBranchBlock != null) {
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
                return false;
            }

            // TX reward adjustments for next rewards
            // TODO get prevTxReward from prevRewardBlock
            long perTxReward = store.getTxRewardValue(prevRewardBlock.getHash());
            long nextPerTxReward = Math.max(1, 20000000 * 365 * 24 * 60 * 60 / approveCount
                    / (header.getTimeSeconds() - prevRewardBlock.getTimeSeconds()));
            nextPerTxReward = Math.max(nextPerTxReward, perTxReward / 4);
            nextPerTxReward = Math.min(nextPerTxReward, perTxReward * 4);
            store.insertTxReward(header.getHash(), nextPerTxReward, fromHeight);

            // Set valid if generated TX is equal to the block's TX
            // This is still unnecessarily traversing twice.
            if (generateMiningRewardTX(header, fromHeight).getHash().equals(header.getTransactions().get(0).getHash())) {
                store.updateBlockEvaluationRewardValid(header.getHash(), true);
                return true;
            } else {
                // Optimization: set invalid forever to not assess this again
                return false;
            }
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
    public void removeWhereInputNotFoundOrUnconfirmed(Collection<BlockWrap> blocksToAdd)
            throws BlockStoreException {
        for (BlockWrap e : new HashSet<BlockWrap>(blocksToAdd)) {
            Block block = blockService.getBlock(e.getBlock().getHash());
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

    public void resolveValidityConflicts(Set<BlockWrap> blocksToAdd, boolean unconfirmLosingMilestones) throws BlockStoreException {
        // Remove blocks and their approvers that have at least one input
        // with its corresponding output not confirmed yet / nonexistent
        removeWhereInputNotFoundOrUnconfirmed(blocksToAdd);

        // Resolve conflicting block combinations
        resolvePrunedConflicts(blocksToAdd);
        resolveUndoableConflicts(blocksToAdd, unconfirmLosingMilestones);
        
        // Remove blocks and their approvers that have at least one input
        // with its corresponding output not confirmed yet / nonexistent
        removeWhereInputNotFoundOrUnconfirmed(blocksToAdd);
    }

    /**
     * Resolves conflicts between milestone blocks and milestone candidates as
     * well as conflicts among milestone candidates.
     * 
     * @param blockEvaluationsToAdd
     * @param unconfirmLosingMilestones 
     * @throws BlockStoreException
     */
    public void resolveUndoableConflicts(Collection<BlockWrap> blockEvaluationsToAdd, boolean unconfirmLosingMilestones) throws BlockStoreException {
        HashSet<Pair<BlockWrap, ConflictPoint>> conflictingOutPoints = new HashSet<Pair<BlockWrap, ConflictPoint>>();
        HashSet<BlockWrap> conflictingMilestoneBlocks = new HashSet<BlockWrap>();
        List<Block> blocksToAdd = blockService
                .getBlocks(blockEvaluationsToAdd.stream().map(e -> e.getBlock().getHash()).collect(Collectors.toList()));

        // Find all conflicts in the new blocks
        findConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);

        // Resolve all conflicts by grouping by UTXO ordered by descending
        // rating
        Pair<HashSet<BlockWrap>, HashSet<BlockWrap>> conflictResolution = 
                resolveConflicts(conflictingOutPoints, unconfirmLosingMilestones);
        HashSet<BlockWrap> losingBlocks = conflictResolution.getRight();

        // For milestone blocks that have been eliminated call disconnect
        // procedure
        for (BlockWrap b : conflictingMilestoneBlocks.stream().filter(b -> losingBlocks.contains(b))
                .collect(Collectors.toList())) {
            if (!unconfirmLosingMilestones) {
                logger.error("Cannot unconfirm milestone blocks when not allowing unconfirmation!");
                break;
            }
            
            blockService.unconfirm(b.getBlockEvaluation());
        }

        // For candidates that have been eliminated (conflictingOutPoints in
        // blocksToAdd
        // \ winningBlocks) remove them from blocksToAdd
        for (Pair<BlockWrap, ConflictPoint> b : conflictingOutPoints.stream()
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
     * @param unconfirmLosingMilestones 
     * @param conflictingMilestoneBlocks
     * @return
     * @throws BlockStoreException
     */
    public Pair<HashSet<BlockWrap>, HashSet<BlockWrap>> resolveConflicts(
            Collection<Pair<BlockWrap, ConflictPoint>> conflictingOutPoints, boolean unconfirmLosingMilestones) throws BlockStoreException {
        // Initialize blocks that will survive the conflict resolution
        HashSet<BlockWrap> winningBlocksSingle = conflictingOutPoints.stream().map(p -> p.getLeft())
                .collect(Collectors.toCollection(HashSet::new));
        HashSet<BlockWrap> winningBlocks = new HashSet<>();
        for (BlockWrap winningBlock : winningBlocksSingle) {
            blockService.addApprovedNonMilestoneBlocksTo(winningBlocks, winningBlock);
            blockService.addMilestoneApproversTo(winningBlocks, winningBlock);
        }
        HashSet<BlockWrap> losingBlocks = new HashSet<>(winningBlocks);

        // Sort conflicts internally by descending rating, then cumulative
        // weight. If not unconfirming, prefer milestones first.
        Comparator<Pair<BlockWrap, ConflictPoint>> byDescendingRating = getConflictComparator(unconfirmLosingMilestones)
                .thenComparingLong((Pair<BlockWrap, ConflictPoint> e) -> e.getLeft().getBlockEvaluation().getRating())
                .thenComparingLong((Pair<BlockWrap, ConflictPoint> e) -> e.getLeft().getBlockEvaluation().getCumulativeWeight())
                .thenComparingLong((Pair<BlockWrap, ConflictPoint> e) -> -e.getLeft().getBlockEvaluation().getInsertTime())
                .thenComparing((Pair<BlockWrap, ConflictPoint> e) -> e.getLeft().getBlockEvaluation().getBlockHash()).reversed();

        Supplier<TreeSet<Pair<BlockWrap, ConflictPoint>>> conflictTreeSetSupplier = () -> new TreeSet<Pair<BlockWrap, ConflictPoint>>(
                byDescendingRating);

        Map<Object, TreeSet<Pair<BlockWrap, ConflictPoint>>> conflicts = conflictingOutPoints.stream()
                .collect(Collectors.groupingBy(Pair::getRight, Collectors.toCollection(conflictTreeSetSupplier)));

        // Sort conflicts among each other by descending max(rating). If not unconfirming, prefer milestones first.
        Comparator<TreeSet<Pair<BlockWrap, ConflictPoint>>> byDescendingSetRating = getConflictSetComparator(unconfirmLosingMilestones)
                .thenComparingLong((TreeSet<Pair<BlockWrap, ConflictPoint>> s) -> s.first().getLeft().getBlockEvaluation().getRating())
                .thenComparingLong(
                        (TreeSet<Pair<BlockWrap, ConflictPoint>> s) -> s.first().getLeft().getBlockEvaluation().getCumulativeWeight())
                .thenComparingLong(
                        (TreeSet<Pair<BlockWrap, ConflictPoint>> s) -> -s.first().getLeft().getBlockEvaluation().getInsertTime())
                .thenComparing((TreeSet<Pair<BlockWrap, ConflictPoint>> s) -> s.first().getLeft().getBlockEvaluation().getBlockHash())
                .reversed();

        Supplier<TreeSet<TreeSet<Pair<BlockWrap, ConflictPoint>>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<Pair<BlockWrap, ConflictPoint>>>(
                byDescendingSetRating);

        TreeSet<TreeSet<Pair<BlockWrap, ConflictPoint>>> sortedConflicts = conflicts.values().stream()
                .collect(Collectors.toCollection(conflictsTreeSetSupplier));

        // Now handle conflicts by descending max(rating)
        for (TreeSet<Pair<BlockWrap, ConflictPoint>> conflict : sortedConflicts) {
            // Take the block with the maximum rating in this conflict that is
            // still in winning Blocks
            Pair<BlockWrap, ConflictPoint> maxRatingPair = null;
            for (Pair<BlockWrap, ConflictPoint> pair : conflict) {
                if (winningBlocks.contains(pair.getLeft())) {
                    maxRatingPair = pair;   
                    break;
                }
            }

            // If such a block exists, this conflict is resolved by eliminating
            // all other
            // blocks in this conflict from winning Blocks
            if (maxRatingPair != null) {
                for (Pair<BlockWrap, ConflictPoint> pair : conflict) {
                    if (pair.getLeft() != maxRatingPair.getLeft()) {
                        blockService.removeBlockAndApproversFrom(winningBlocks, pair.getLeft());
                    }
                }
            }
        }

        losingBlocks.removeAll(winningBlocks);

        return Pair.of(winningBlocks, losingBlocks);
    }

    private Comparator<TreeSet<Pair<BlockWrap, ConflictPoint>>> getConflictSetComparator(boolean unconfirmLosingMilestones) {
        if (!unconfirmLosingMilestones)
            return new Comparator<TreeSet<Pair<BlockWrap, ConflictPoint>>>() {
                @Override
                public int compare(TreeSet<Pair<BlockWrap, ConflictPoint>> o1,
                        TreeSet<Pair<BlockWrap, ConflictPoint>> o2) {
                    if (o1.first().getLeft().getBlockEvaluation().isMilestone() && o2.first().getLeft().getBlockEvaluation().isMilestone())
                        return 0;
                    if (o1.first().getLeft().getBlockEvaluation().isMilestone())
                        return 1;
                    if (o2.first().getLeft().getBlockEvaluation().isMilestone())
                        return -1;
                    return 0;
                }
            };
        else
            return new Comparator<TreeSet<Pair<BlockWrap, ConflictPoint>>>() {
                @Override
                public int compare(TreeSet<Pair<BlockWrap, ConflictPoint>> o1,
                        TreeSet<Pair<BlockWrap, ConflictPoint>> o2) {
                    return 0;
                }
            };
    }

    private Comparator<Pair<BlockWrap, ConflictPoint>> getConflictComparator(boolean unconfirmLosingMilestones) {
        if (!unconfirmLosingMilestones)
            return new Comparator<Pair<BlockWrap,ConflictPoint>>() {
                @Override
                public int compare(Pair<BlockWrap, ConflictPoint> o1, Pair<BlockWrap, ConflictPoint> o2) {
                    if (o1.getLeft().getBlockEvaluation().isMilestone() && o2.getLeft().getBlockEvaluation().isMilestone()) {
                        if (o1.getRight().equals(o2.getRight()))
                            return 0;
                        else {
                            logger.warn("Inconsistent Milestone: Conflicting blocks in milestone"
                                    +": \n"+o1.getLeft().getBlock().getHash()
                                    +"\n"+o2.getLeft().getBlock().getHash());        
                            return 0;
                        }                         
                    }
                    if (o1.getLeft().getBlockEvaluation().isMilestone())
                        return 1;
                    if (o2.getLeft().getBlockEvaluation().isMilestone())
                        return -1;
                    return 0;
                }
            };
        else
            return new Comparator<Pair<BlockWrap,ConflictPoint>>() {
                @Override
                public int compare(Pair<BlockWrap, ConflictPoint> o1, Pair<BlockWrap, ConflictPoint> o2) {
                    return 0;
                }
            };
    }

    /**
     * Finds conflicts in blocksToAdd
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    public void findConflicts(Collection<Block> blocksToAdd,
            Collection<Pair<BlockWrap, ConflictPoint>> conflictingOutPoints,
            Collection<BlockWrap> conflictingMilestoneBlocks) throws BlockStoreException {

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
            Collection<Pair<BlockWrap, ConflictPoint>> conflictingOutPoints) throws BlockStoreException {
        // Create pairs of blocks and used non-coinbase utxos from blocksToAdd
        // Dynamic conflicts: conflicting transactions
        Stream<Pair<Block, ConflictPoint>> transferConflictPoints = blocksToAdd.stream()
                .flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream())
                        .filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, new ConflictPoint(in.getOutpoint()))));

        // Dynamic conflicts: mining reward height intervals
        Stream<Pair<Block, ConflictPoint>> rewardConflictPoints = blocksToAdd.stream()
                .filter(b -> b.getBlockType() == NetworkParameters.BLOCKTYPE_REWARD)
                .map(b -> Pair.of(b, Utils.readInt64(b.getTransactions().get(0).getData(), 0)))
                .map(pair -> Pair.of(pair.getLeft(), new ConflictPoint(pair.getRight()))).filter(pair -> false);

        // Dynamic conflicts: token issuance ids
        Stream<Pair<Block, ConflictPoint>> issuanceConflictPoints = blocksToAdd.stream().filter(t -> false) // TEMP
                .filter(b -> b.getBlockType() == NetworkParameters.BLOCKTYPE_TOKEN_CREATION)
                .map(b -> Pair.of(b, new TokenInfo().parse(b.getTransactions().get(0).getData())))
                .map(pair -> Pair.of(pair.getLeft(), new ConflictPoint(pair.getRight().getTokenSerial())))
                .filter(pair -> false);

        // Filter to only contain conflicts that are spent more than once in the
        // new milestone candidates
        List<Pair<Block, ConflictPoint>> candidateCandidateConflicts = Stream
                .of(transferConflictPoints, rewardConflictPoints, issuanceConflictPoints).flatMap(i -> i)
                .collect(Collectors.groupingBy(Pair::getRight)).values().stream().filter(l -> l.size() > 1)
                .flatMap(l -> l.stream()).collect(Collectors.toList());

        // Add the conflicting candidates
        for (Pair<Block, ConflictPoint> pair : candidateCandidateConflicts) {
            BlockWrap toAddEvaluation = store.getBlockWrap(pair.getLeft().getHash());
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
            Collection<Pair<BlockWrap, ConflictPoint>> conflictingOutPoints,
            Collection<BlockWrap> conflictingMilestoneBlocks) throws BlockStoreException {
        // Create pairs of blocks and outpoints from blocksToAdd where already
        // spent by maintained milestone blocks.
        // Dynamic conflicts: conflicting transactions
        Stream<Pair<Block, ConflictPoint>> transferConflictPoints = blocksToAdd.stream()
                .flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream())
                        .filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, new ConflictPoint(in.getOutpoint()))))
                // Filter such that it has already been spent
                .filter(pair -> alreadySpent(pair.getRight().getOutpoint()));

        // Dynamic conflicts: mining reward height intervals
        Stream<Pair<Block, ConflictPoint>> rewardConflictPoints = blocksToAdd.stream()
                .filter(b -> b.getBlockType() == NetworkParameters.BLOCKTYPE_REWARD)
                .map(b -> Pair.of(b, Utils.readInt64(b.getTransactions().get(0).getData(), 0)))
                .map(pair -> Pair.of(pair.getLeft(), new ConflictPoint(pair.getRight())))
                // Filter such that it has already been spent 
                .filter(pair -> {
                    try {
                        return alreadyRewarded(pair.getRight().getHeight());
                    } catch (BlockStoreException e) {
                        return false;
                    }
                });

        // Dynamic conflicts: token issuance ids already issued
        Stream<Pair<Block, ConflictPoint>> issuanceConflictPoints = blocksToAdd.stream().filter(t -> false) // TEMP
                .filter(b -> b.getBlockType() == NetworkParameters.BLOCKTYPE_TOKEN_CREATION)
                .map(b -> Pair.of(b, new TokenInfo().parse(b.getTransactions().get(0).getData())))
                .map(pair -> Pair.of(pair.getLeft(), new ConflictPoint(pair.getRight().getTokenSerial())))
                // Filter such that it has already been issued 
                .filter(pair -> alreadyIssued(pair.getRight().getTokenSerial()));

        // Add the conflicting candidates and milestone blocks
        for (Pair<Block, ConflictPoint> pair : Stream
                .of(transferConflictPoints, rewardConflictPoints, issuanceConflictPoints).flatMap(i -> i)
                .collect(Collectors.toList())) {

            BlockWrap milestoneEvaluation = null;
            switch (pair.getRight().getType()) {
            case TXOUT:
                milestoneEvaluation = getSpendingBlock(pair.getRight().getOutpoint());
                break;
            case TOKENISSUANCE:
                milestoneEvaluation = getTokenIssuingBlock(pair.getRight().getTokenSerial());
                break;
            case REWARDISSUANCE:
                milestoneEvaluation = getIntervalRewardingBlock(pair.getRight().getHeight());
                break;
            default:
                throw new NotImplementedException();            
            }
            conflictingOutPoints.add(Pair.of(milestoneEvaluation, pair.getRight()));
            conflictingMilestoneBlocks.add(milestoneEvaluation);
            
            //Then add corresponding new block
            BlockWrap toAddEvaluation = store.getBlockWrap(pair.getLeft().getHash());
            conflictingOutPoints.add(Pair.of(toAddEvaluation, pair.getRight()));
        }
    }

    private boolean alreadySpent(TransactionOutPoint transactionOutPoint) {
        return transactionService.getUTXOSpent(transactionOutPoint)
                && getSpendingBlock(transactionOutPoint) != null
                && getSpendingBlock(transactionOutPoint).getBlockEvaluation().isMaintained();
    }

    private boolean alreadyRewarded(long height) throws BlockStoreException {
        return height != store.getMaxPrevTxRewardHeight() + NetworkParameters.REWARD_HEIGHT_INTERVAL;
    }

    private boolean alreadyIssued(TokenSerial tokenSerial) {
        // TODO where token has been issued already
        throw new NotImplementedException();
    }

    private BlockWrap getIntervalRewardingBlock(long height) throws BlockStoreException {
        return store.getBlockWrap(store.getConfirmedRewardBlock(height));
    }

    private BlockWrap getSpendingBlock(TransactionOutPoint transactionOutPoint) {
        try {
            return store.getBlockWrap(transactionService.getUTXOSpender(transactionOutPoint).getBlockHash());
        } catch (BlockStoreException e) {
            return null;
        }
    }

    private BlockWrap getTokenIssuingBlock(TokenSerial tokenSerial) {
        // TODO get milestone block that issued the specified token id and sequence number
        throw new NotImplementedException();
    }

    /**
     * Resolves conflicts in new blocks to add that cannot be undone due to
     * pruning. This method does not do anything if not pruning blocks.
     * 
     * @param blocksToAdd
     * @param unconfirmLosingMilestones 
     * @throws BlockStoreException
     */
    public void resolvePrunedConflicts(Collection<BlockWrap> blocksToAdd) throws BlockStoreException {
//        // TODO when pruning is activated, refactor this to use same code as below 
//        // Get the blocks to add as actual blocks from blockService
//        List<Block> blocks = blockService
//                .getBlocks(blocksToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));
//
//        // TODO dynamic conflictpoints: token issuance ids, mining reward height
//        // intervals, flatout reject conflicts where the block to be created is in
//        // conflict (e. g. same reward)
//
//        // To check for unundoable conflicts, we do the following:
//        // Create tuples (block, txinput) of all blocksToAdd
//        Stream<Pair<Block, TransactionInput>> blockInputTuples = blocks.stream().flatMap(
//                b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in)));
//
//        // Now filter to only contain inputs that were already spent in the
//        // milestone where the corresponding block has already been pruned or is
//        // unmaintained...
//        Stream<Pair<Block, TransactionInput>> irresolvableConflicts = blockInputTuples
//                .filter(pair -> transactionService.getUTXOSpent(pair.getRight().getOutpoint())
//                        && (transactionService.getUTXOSpender(pair.getRight().getOutpoint()) == null
//                                || !transactionService.getUTXOSpender(pair.getRight().getOutpoint()).isMaintained()));
//
//        // These blocks cannot be added and must therefore be removed from
//        // blocksToAdd
//        for (Pair<Block, TransactionInput> p : irresolvableConflicts.collect(Collectors.toList())) {
//            blockService.removeBlockAndApproversFrom(blocksToAdd,
//                    blockService.getBlockEvaluation(p.getLeft().getHash()));
//        }
    }
}
