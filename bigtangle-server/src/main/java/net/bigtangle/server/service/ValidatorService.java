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
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
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
        PriorityQueue<BlockEvaluation> blockQueue = new PriorityQueue<BlockEvaluation>(
                Comparator.comparingLong(BlockEvaluation::getHeight).reversed());
        HashSet<BlockEvaluation> queuedBlocks = new HashSet<>();
        HashMap<Address, Long> rewardCount = new HashMap<>();
        blockQueue.add(blockService.getBlockEvaluation(header.getPrevBlockHash()));
        blockQueue.add(blockService.getBlockEvaluation(header.getPrevBranchBlockHash()));
        queuedBlocks.add(blockService.getBlockEvaluation(header.getPrevBlockHash()));
        queuedBlocks.add(blockService.getBlockEvaluation(header.getPrevBranchBlockHash()));

        // Initial reward block must be defined
        if (fromHeight == 0)
            prevRewardBlock = networkParameters.getGenesisBlock();

        // Go backwards by height
        BlockEvaluation currentBlock = null;
        while ((currentBlock = blockQueue.poll()) != null) {
            Block block = currentBlock.getBlockhash().equals(header.getHash()) ? header : blockService.getBlock(currentBlock.getBlockhash());

            // Stop criterion: Block height lower than approved interval height
            if (currentBlock.getHeight() < fromHeight)
                continue;

            if (currentBlock.getHeight() <= toHeight) {
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
                        if (prevRewardBlock != null)
                            logger.error(
                                    "Failed in creating reward block here, try resolving conflicts with rewarding in mind");

                        prevRewardBlock = block;
                    }
                }
            }

            // Continue with approved blocks
            BlockEvaluation prevBlock = blockService.getBlockEvaluation(block.getPrevBlockHash());
            if (!queuedBlocks.contains(prevBlock) && prevBlock != null) {
                queuedBlocks.add(prevBlock);
                blockQueue.add(prevBlock);
            }

            BlockEvaluation prevBranchBlock = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
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

    // TODO rewrite this to batched computation of relevant reward block only,
    // e. g. go forward until confirmed reward block is found or up to the end
    // Use above code not this one
    private boolean assessMiningRewardBlock(Block header, long height, boolean assumeMilestone) throws BlockStoreException {
        BlockEvaluation blockEvaluation = blockService.getBlockEvaluation(header.getHash());

        // Once set valid, always valid
        if (blockEvaluation.isRewardValid())
            return true;

        // Only mining reward blocks
        if (header.getBlocktype() != NetworkParameters.BLOCKTYPE_REWARD)
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
        PriorityQueue<BlockEvaluation> blockQueue = new PriorityQueue<BlockEvaluation>(
                Comparator.comparingLong(BlockEvaluation::getHeight).reversed());
        HashSet<BlockEvaluation> queuedBlocks = new HashSet<>();
        HashMap<Address, Long> rewardCount = new HashMap<>();
        blockQueue.add(blockService.getBlockEvaluation(header.getPrevBlockHash()));
        blockQueue.add(blockService.getBlockEvaluation(header.getPrevBranchBlockHash()));
        queuedBlocks.add(blockService.getBlockEvaluation(header.getPrevBlockHash()));
        queuedBlocks.add(blockService.getBlockEvaluation(header.getPrevBranchBlockHash()));

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
            if (!queuedBlocks.contains(prevBlock) && prevBlock != null) {
                queuedBlocks.add(prevBlock);
                blockQueue.add(prevBlock);
            }

            BlockEvaluation prevBranchBlock = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
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
            long perTxReward = store.getTxRewardValue(prevRewardBlock.getHash());
            long nextPerTxReward = Math.max(1, 20000000 * 365 * 24 * 60 * 60 / approveCount
                    / (header.getTimeSeconds() - prevRewardBlock.getTimeSeconds()));
            nextPerTxReward = Math.max(nextPerTxReward, perTxReward / 4);
            nextPerTxReward = Math.min(nextPerTxReward, perTxReward * 4);
            store.insertTxReward(header.getHash(), nextPerTxReward, fromHeight);

            // Set valid if generated TX is equal to the block's TX
            // TODO this is still unnecessarily traversing twice
            if (generateMiningRewardTX(header, fromHeight).getHash().equals(header.getTransactions().get(0).getHash())) {
                store.updateBlockEvaluationRewardValid(header.getHash(), true);
                return true;
            } else {
                // TODO else set invalid forever to not assess this again and
                // again
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
    public void removeWhereInputNotFoundOrUnconfirmed(Collection<BlockEvaluation> blocksToAdd)
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
        // TODO refactor this to use same code as below
        // Get the blocks to add as actual blocks from blockService
        List<Block> blocks = blockService
                .getBlocks(blocksToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

        // TODO dynamic conflictpoints: token issuance ids, mining reward height
        // intervals

        // TODO flatout reject conflicts where the block to be created is in
        // conflict (e. g. same reward)

        // To check for unundoable conflicts, we do the following:
        // Create tuples (block, txinput) of all blocksToAdd
        Stream<Pair<Block, TransactionInput>> blockInputTuples = blocks.stream().flatMap(
                b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in)));

        // Now filter to only contain inputs that were already spent in the
        // milestone where the corresponding block has already been pruned or is
        // unmaintained...
        Stream<Pair<Block, TransactionInput>> irresolvableConflicts = blockInputTuples
                .filter(pair -> transactionService.getUTXOSpent(pair.getRight().getOutpoint())
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
        // Dynamic conflicts: conflicting transactions
        Stream<Pair<Block, ConflictPoint>> transferConflictPoints = blocksToAdd.stream()
                .flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream())
                        .filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, new ConflictPoint(in.getOutpoint()))));

        // Dynamic conflicts: mining reward height intervals
        Stream<Pair<Block, ConflictPoint>> rewardConflictPoints = blocksToAdd.stream()
                .filter(b -> b.getBlocktype() == NetworkParameters.BLOCKTYPE_REWARD)
                .map(b -> Pair.of(b, Utils.readInt64(b.getTransactions().get(0).getData(), 0)))
                .map(pair -> Pair.of(pair.getLeft(), new ConflictPoint(pair.getRight()))).filter(pair -> false);

        // Dynamic conflicts: token issuance ids
        Stream<Pair<Block, ConflictPoint>> issuanceConflictPoints = blocksToAdd.stream().filter(t -> false) // TEMP
                .filter(b -> b.getBlocktype() == NetworkParameters.BLOCKTYPE_TOKEN_CREATION)
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
        // Create pairs of blocks and outpoints from blocksToAdd where already
        // spent by maintained milestone blocks.
        // Dynamic conflicts: conflicting transactions
        Stream<Pair<Block, ConflictPoint>> transferConflictPoints = blocksToAdd.stream()
                .flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream())
                        .filter(in -> !in.isCoinBase()).map(in -> Pair.of(b, new ConflictPoint(in.getOutpoint()))))
                // Filter such that it has already been spent
                .filter(pair -> transactionService.getUTXOSpent(pair.getRight().getOutpoint())
                        && transactionService.getUTXOSpender(pair.getRight().getOutpoint()) != null
                        && transactionService.getUTXOSpender(pair.getRight().getOutpoint()).isMaintained());

        // Dynamic conflicts: mining reward height intervals
        Stream<Pair<Block, ConflictPoint>> rewardConflictPoints = blocksToAdd.stream()
                .filter(b -> b.getBlocktype() == NetworkParameters.BLOCKTYPE_REWARD)
                .map(b -> Pair.of(b, Utils.readInt64(b.getTransactions().get(0).getData(), 0)))
                .map(pair -> Pair.of(pair.getLeft(), new ConflictPoint(pair.getRight())))
                // Filter such that it has already been spent 
                .filter(pair -> {
                    try {
                        return pair.getRight().getHeight() != store.getMaxPrevTxRewardHeight() + NetworkParameters.REWARD_HEIGHT_INTERVAL;
                    } catch (BlockStoreException e) {
                        return false;
                    }
                });

        // Dynamic conflicts: token issuance ids
        Stream<Pair<Block, ConflictPoint>> issuanceConflictPoints = blocksToAdd.stream().filter(t -> false) // TEMP
                .filter(b -> b.getBlocktype() == NetworkParameters.BLOCKTYPE_TOKEN_CREATION)
                .map(b -> Pair.of(b, new TokenInfo().parse(b.getTransactions().get(0).getData())))
                .map(pair -> Pair.of(pair.getLeft(), new ConflictPoint(pair.getRight().getTokenSerial())))
                .filter(pair -> false);
        // TODO where token has been issued already (issuing block selectable
        // see above)

        // Add the conflicting candidates and milestone blocks
        for (Pair<Block, ConflictPoint> pair : Stream
                .of(transferConflictPoints, rewardConflictPoints, issuanceConflictPoints).flatMap(i -> i)
                .collect(Collectors.toList())) {
            BlockEvaluation toAddEvaluation = blockService.getBlockEvaluation(pair.getLeft().getHash());
            conflictingOutPoints.add(Pair.of(toAddEvaluation, pair.getRight()));

            // TODO Select the milestone block that is conflicting with a switch
            BlockEvaluation milestoneEvaluation = transactionService.getUTXOSpender(pair.getRight().getOutpoint());
            conflictingOutPoints.add(Pair.of(milestoneEvaluation, pair.getRight()));
            conflictingMilestoneBlocks.add(milestoneEvaluation);
        }
    }
}
