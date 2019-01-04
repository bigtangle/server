/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.directory.api.util.exception.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ConflictCandidate;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.script.Script;
import net.bigtangle.script.Script.VerifyFlag;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.ContextPropagatingThreadFactory;

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
    @Autowired
    private MultiSignService multiSignService;
    @Autowired
    private NetworkParameters params;

    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

    ExecutorService scriptVerificationExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(), new ContextPropagatingThreadFactory("Script verification"));

    /**
     * A job submitted to the executor which verifies signatures.
     */
    private static class Verifier implements Callable<VerificationException> {
        final Transaction tx;
        final List<Script> prevOutScripts;
        final Set<VerifyFlag> verifyFlags;

        public Verifier(final Transaction tx, final List<Script> prevOutScripts, final Set<VerifyFlag> verifyFlags) {
            this.tx = tx;
            this.prevOutScripts = prevOutScripts;
            this.verifyFlags = verifyFlags;
        }

        @Nullable
        @Override
        public VerificationException call() throws Exception {
            try {
                ListIterator<Script> prevOutIt = prevOutScripts.listIterator();
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    tx.getInputs().get(index).getScriptSig().correctlySpends(tx, index, prevOutIt.next(), verifyFlags);
                }
            } catch (VerificationException e) {
                return e;
            }
            return null;
        }
    }

    /**
     * Get the {@link Script} from the script bytes or return Script of empty
     * byte array.
     */
    private Script getScript(byte[] scriptBytes) {
        try {
            return new Script(scriptBytes);
        } catch (Exception e) {
            return new Script(new byte[0]);
        }
    }

    /**
     * Get the address from the {@link Script} if it exists otherwise return
     * empty string "".
     *
     * @param script
     *            The script.
     * @return The address.
     */
    private String getScriptAddress(@Nullable Script script) {
        String address = "";
        try {
            if (script != null) {
                address = script.getToAddress(params, true).toString();
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return address;
    }

    /**
     * Deterministically creates a mining reward transaction based on the
     * previous blocks and previous reward transaction.
     * 
     * @return Pair of mining reward transaction and boolean indicating whether
     *         this mining reward transaction is eligible to be voted on at this
     *         moment of time.
     * @throws BlockStoreException
     */
    public Triple<Transaction, Boolean, Long> generateMiningRewardTX(Block prevTrunk, Block prevBranch,
            Sha256Hash prevRewardHash) throws BlockStoreException {
        // Count how many blocks from miners in the reward interval are approved
        // and build rewards
        boolean eligibility = true;
        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        BlockWrap prevTrunkBlock = store.getBlockWrap(prevTrunk.getHash());
        BlockWrap prevBranchBlock = store.getBlockWrap(prevBranch.getHash());
        blockQueue.add(prevTrunkBlock);
        blockQueue.add(prevBranchBlock);

        // Read previous reward block's data
        BlockWrap prevRewardBlock = store.getBlockWrap(prevRewardHash);
        long prevToHeight = 0, minHeight = 0, perTxReward = 0;
        try {
            RewardInfo rewardInfo = RewardInfo.parse(prevRewardBlock.getBlock().getTransactions().get(0).getData());
            
            prevToHeight = rewardInfo.getToHeight();
            minHeight = prevToHeight + networkParameters.getRewardHeightInterval() - 1;
            perTxReward = rewardInfo.getNextPerTxReward();

            if (prevTrunkBlock.getBlockEvaluation().getHeight() < minHeight - 1
                    && prevBranchBlock.getBlockEvaluation().getHeight() < minHeight - 1)
                return null;

        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        
        long fromHeight = prevToHeight + 1;
        long toHeight = prevToHeight + networkParameters.getRewardHeightInterval();

        // Initialize
        Set<BlockWrap> currentHeightBlocks = new HashSet<>();
        Map<BlockWrap, Set<Sha256Hash>> snapshotWeights = new HashMap<>();
        Map<Address, Long> finalRewardCount = new HashMap<>();
        BlockWrap currentBlock = null, approvedBlock = null;
        long currentHeight = Long.MAX_VALUE;
        long totalRewardCount = 0;

        for (BlockWrap tip : blockQueue) {
            snapshotWeights.put(tip, new HashSet<>());
        }

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {

            // If we have reached a new height level, try trigger payout
            // calculation
            if (currentHeight > currentBlock.getBlockEvaluation().getHeight()) {

                // Calculate rewards if in reward interval height
                if (currentHeight <= toHeight) {
                    totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
                            totalRewardCount);
                }

                // Finished with this height level, go to next level
                currentHeightBlocks.clear();
                long currentHeightTmp = currentHeight;
                snapshotWeights.entrySet()
                        .removeIf(e -> e.getKey().getBlockEvaluation().getHeight() == currentHeightTmp);
                currentHeight = currentBlock.getBlockEvaluation().getHeight();
            }

            // Stop criterion: Block height lower than approved interval height
            if (currentHeight < fromHeight)
                continue;

            // Add your own hash to approver hashes of current approver hashes
            snapshotWeights.get(currentBlock).add(currentBlock.getBlockHash());

            // If in relevant reward height interval, count it
            if (currentHeight <= toHeight) {
                // Failure criterion: rewarding non-milestone blocks
                if (currentBlock.getBlockEvaluation().isMilestone() == false)
                    eligibility = false;

                // Count the blocks of current height
                currentHeightBlocks.add(currentBlock);
            }

            // Continue with both approved blocks
            approvedBlock = store.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                    snapshotWeights.put(approvedBlock, new HashSet<>(snapshotWeights.get(currentBlock)));
                }
            } else {
                snapshotWeights.get(approvedBlock).add(currentBlock.getBlockHash());
            }
            approvedBlock = store.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                    snapshotWeights.put(approvedBlock, new HashSet<>(snapshotWeights.get(currentBlock)));
                }
            } else {
                snapshotWeights.get(approvedBlock).add(currentBlock.getBlockHash());
            }
        }

        // Exception for height 0 (genesis): since prevblock null, finish payout
        // calculation
        if (currentHeight == 0) {
            // For each height, throw away anything below the 99-percentile
            // in terms of reduced weight
            totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
                    totalRewardCount);
        }

        // Build transaction outputs sorted by addresses
        Transaction tx = new Transaction(networkParameters);
        for (Entry<Address, Long> entry : finalRewardCount.entrySet().stream()
                .sorted(Comparator.comparing((Entry<Address, Long> e) -> e.getKey())).collect(Collectors.toList()))
            tx.addOutput(Coin.SATOSHI.times(entry.getValue() * perTxReward), entry.getKey());

        // The input does not really need to be a valid signature, as long
        // as it has the right general form and is slightly different for
        // different tx
        TransactionInput input = new TransactionInput(networkParameters, tx, Script.createInputScript(
                prevTrunkBlock.getBlockHash().getBytes(), prevBranchBlock.getBlockHash().getBytes()));
        tx.addInput(input);

        // TX reward adjustments for next rewards
        long nextPerTxReward = calculateNextTxReward(prevTrunkBlock, prevBranchBlock, prevRewardBlock, perTxReward,
                totalRewardCount);

        // Build the type-specific tx data
        RewardInfo rewardInfo = new RewardInfo(fromHeight, toHeight, nextPerTxReward, prevRewardHash.toString());
        tx.setData(rewardInfo.toByteArray());

        // Check eligibility: sufficient amount of milestone blocks approved?
        if (!checkEligibility(fromHeight, toHeight, totalRewardCount))
            eligibility = false;

        // New difficulty
        long prevDifficulty = prevTrunk.getLastMiningRewardBlock() > prevBranch.getLastMiningRewardBlock()
                ? prevTrunk.getDifficultyTarget()
                : prevBranch.getDifficultyTarget();
        long difficulty = calculateNextDifficulty(prevDifficulty, prevTrunkBlock, prevBranchBlock, prevRewardBlock,
                totalRewardCount);

        return Triple.of(tx, eligibility, difficulty);
    }

    private long calculateNextDifficulty(long prevDifficulty, BlockWrap prevTrunkBlock, BlockWrap prevBranchBlock,
            BlockWrap prevRewardBlock, long totalRewardCount) {
        // The following equals current time by consensus rules
        long currentTime = Math.max(prevTrunkBlock.getBlock().getTimeSeconds(),
                prevBranchBlock.getBlock().getTimeSeconds());
        long timespan = Math.max(1, (currentTime - prevRewardBlock.getBlock().getTimeSeconds()));

        BigInteger prevTarget = Utils.decodeCompactBits(prevDifficulty);
        BigInteger newTarget = prevTarget.multiply(BigInteger.valueOf(networkParameters.getTargetTPS()));
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));
        newTarget = newTarget.divide(BigInteger.valueOf(totalRewardCount));

        BigInteger maxNewTarget = prevTarget.multiply(BigInteger.valueOf(4));
        BigInteger minNewTarget = prevTarget.divide(BigInteger.valueOf(4));

        if (newTarget.compareTo(maxNewTarget) > 0) {
            newTarget = maxNewTarget;
        }

        if (newTarget.compareTo(minNewTarget) < 0) {
            newTarget = minNewTarget;
        }

        if (newTarget.compareTo(NetworkParameters.MAX_TARGET) > 0) {
            logger.info("Difficulty hit proof of work limit: {}", newTarget.toString(16));
            newTarget = NetworkParameters.MAX_TARGET;
        }

        return Utils.encodeCompactBits(newTarget);
    }

    private long calculateNextTxReward(BlockWrap prevTrunkBlock, BlockWrap prevBranchBlock, BlockWrap prevRewardBlock,
            long currPerTxReward, long totalRewardCount) {
        // The following is used as proxy for current time by consensus rules
        long currentTime = Math.max(prevTrunkBlock.getBlock().getTimeSeconds(),
                prevBranchBlock.getBlock().getTimeSeconds());
        long timespan = Math.max(1, (currentTime - prevRewardBlock.getBlock().getTimeSeconds()));

        // TODO virtual txs for reward
        // TODO RewardInfo parse fully, also same VOSInfo
        // TODO include result from difficulty adjustment
        // BigInteger result = BigInteger.valueOf(currPerTxReward);
        long nextPerTxReward = NetworkParameters.TARGET_YEARLY_MINING_PAYOUT * timespan / 31536000L / totalRewardCount;
        nextPerTxReward = Math.max(nextPerTxReward, currPerTxReward / 4);
        nextPerTxReward = Math.min(nextPerTxReward, currPerTxReward * 4);
        nextPerTxReward = Math.max(nextPerTxReward, 1);
        return nextPerTxReward;
    }

    private boolean checkEligibility(long fromHeight, long toHeight, long totalRewardCount) throws BlockStoreException {
        return totalRewardCount >= store.getCountMilestoneBlocksInInterval(fromHeight, toHeight) * 99 / 100;
    }

    private long calculateHeightRewards(Set<BlockWrap> currentHeightBlocks,
            Map<BlockWrap, Set<Sha256Hash>> snapshotWeights, Map<Address, Long> finalRewardCount,
            long totalRewardCount) {
        long heightRewardCount = (long) Math.ceil(0.95d * currentHeightBlocks.size());
        totalRewardCount += heightRewardCount;

        long rewarded = 0;
        for (BlockWrap rewardedBlock : currentHeightBlocks.stream()
                .sorted(Comparator.comparingLong(b -> snapshotWeights.get(b).size()).reversed())
                .collect(Collectors.toList())) {
            if (rewarded >= heightRewardCount)
                break;

            Address miner = new Address(networkParameters, rewardedBlock.getBlock().getMinerAddress());
            if (!finalRewardCount.containsKey(miner))
                finalRewardCount.put(miner, 1L);
            else
                finalRewardCount.put(miner, finalRewardCount.get(miner) + 1);
            rewarded++;
        }
        return totalRewardCount;
    }

    /**
     * Checks if the given block is eligible to be walked to during local
     * approval tip selection given the current set of non-milestone blocks to
     * include. This is the case if the block + the set is compatible with the
     * current milestone. It must disallow spent prev UTXOs / unconfirmed prev UTXOs
     * 
     * @param block
     *            The block to check for eligibility.
     * @param currentApprovedNonMilestoneBlocks
     *            The set of all currently approved non-milestone blocks. Note
     *            that this set is assumed to be compatible with the current
     *            milestone.
     * @return true if the given block is eligible to be walked to during
     *         approval tip selection.
     * @throws BlockStoreException 
     */
    public boolean isEligibleForApprovalSelection(BlockWrap block,
            HashSet<BlockWrap> currentApprovedNonMilestoneBlocks) throws BlockStoreException {
        // Any milestone blocks are always compatible with the current milestone
        if (block.getBlockEvaluation().isMilestone())
            return true;

        // Get sets of all / all new non-milestone blocks when approving the
        // specified block in combination with the currently included blocks
        @SuppressWarnings("unchecked")
        HashSet<BlockWrap> allApprovedNonMilestoneBlocks = (HashSet<BlockWrap>) currentApprovedNonMilestoneBlocks.clone();
        blockService.addApprovedNonMilestoneBlocksTo(allApprovedNonMilestoneBlocks, block);
        @SuppressWarnings("unchecked")
        HashSet<BlockWrap> newApprovedNonMilestoneBlocks = (HashSet<BlockWrap>) allApprovedNonMilestoneBlocks.clone();
        newApprovedNonMilestoneBlocks.removeAll(currentApprovedNonMilestoneBlocks);

        // If there exists a new block whose dependency is already spent
        // (conflicting with milestone) or not confirmed yet (missing other milestones), we fail to
        // approve this block since the current milestone takes precedence / doesn't allow for the addition of these blocks
        if (findBlockWithSpentOrUnconfirmedInputs(newApprovedNonMilestoneBlocks))
            return false;

        // If conflicts among the approved non-milestone blocks exist, cannot approve
        HashSet<ConflictCandidate> conflictingOutPoints = new HashSet<>();
        findCandidateConflicts(allApprovedNonMilestoneBlocks, conflictingOutPoints);
        if (!conflictingOutPoints.isEmpty())
            return false;

        // Otherwise, the new approved block set is compatible with current milestone
        return true;
    }
    
    
    private boolean isSpent(ConflictCandidate c) throws BlockStoreException {
        switch (c.getConflictPoint().getType()) {
            case TXOUT:
                return transactionService.getUTXOSpent(c.getConflictPoint().getConnectedOutpoint());
            case TOKENISSUANCE:
                final Token connectedToken = c.getConflictPoint().getConnectedToken();
                
                // Initial issuances are allowed iff no other same token issuances are confirmed
                if (connectedToken.getTokenindex() == 0)
                    return store.getTokenAnyConfirmed(connectedToken.getTokenid(), connectedToken.getTokenindex()); 
                else
                    return store.getTokenSpent(connectedToken.getPrevblockhash());
            case REWARDISSUANCE:
                return store.getTxRewardSpent(Sha256Hash.wrap(c.getConflictPoint().getConnectedPrevReward()));
            default:
                throw new NotImplementedException();
        }
    }
    
    private boolean isConfirmed(ConflictCandidate c) throws BlockStoreException {
        switch (c.getConflictPoint().getType()) {
        case TXOUT:
            return transactionService.getUTXOConfirmed(c.getConflictPoint().getConnectedOutpoint());
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();
            
            // Initial issuances are allowed iff no other same token issuances are confirmed
            if (connectedToken.getTokenindex() == 0)
                return !store.getTokenAnyConfirmed(connectedToken.getTokenid(), connectedToken.getTokenindex()); 
            else
                return store.getTokenConfirmed(connectedToken.getPrevblockhash());
        case REWARDISSUANCE:
            return store.getTxRewardConfirmed(Sha256Hash.wrap(c.getConflictPoint().getConnectedPrevReward()));
        default:
            throw new NotImplementedException();
    }
    }

    private boolean findBlockWithSpentOrUnconfirmedInputs(HashSet<BlockWrap> newApprovedNonMilestoneBlocks) {
        // Get all conflict candidates in blocks
        Stream<ConflictCandidate> candidates = newApprovedNonMilestoneBlocks.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream());

        // Find conflict candidates whose used outputs are already spent or still unconfirmed
        return candidates
                .filter((ConflictCandidate c) -> {
                    try {
                        return isSpent(c) || !isConfirmed(c);
                    } catch (BlockStoreException e) {
                        e.printStackTrace();
                    }
                    return false;
                })
                .findFirst()
                .isPresent();
    }

    /**
     * Remove blocks from blocksToAdd that have at least one used output not
     * confirmed yet. They may however be spent already -> conflict.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    public void removeWherePreconditionsUnfulfilled(Collection<BlockWrap> blocksToAdd)
            throws BlockStoreException {
        new HashSet<BlockWrap>(blocksToAdd).stream()
        .filter(b -> !b.getBlockEvaluation().isMilestone()) // Milestones are always ok
        .flatMap(b -> b.toConflictCandidates().stream())
        .filter(c -> {
            try {
                return !isConfirmed(c); // Any candidates where used output unconfirmed
            } catch (BlockStoreException e) {
                // Cannot happen.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }).forEach(c -> {
            try {
                blockService.removeBlockAndApproversFrom(blocksToAdd, c.getBlock());                
            } catch (BlockStoreException e) {
                // Cannot happen.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

        // Additionally, for reward blocks they must be eligible
        // If ineligible, preconditions are sufficient age and milestone
        // rating range
        new HashSet<BlockWrap>(blocksToAdd).stream()
        .filter(b -> b.getBlock().getBlockType() == Type.BLOCKTYPE_REWARD) // prefilter for reward blocks
        .forEach(b -> {
            try {
              if (!store.getTxRewardEligible(b.getBlock().getHash())
                      && !(b.getBlockEvaluation().getRating() > NetworkParameters.MILESTONE_UPPER_THRESHOLD
                              && b.getBlockEvaluation().getInsertTime() < System.currentTimeMillis() / 1000 - 30)) 
                  blockService.removeBlockAndApproversFrom(blocksToAdd, b);
            } catch (BlockStoreException e) {
                // Cannot happen.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    // disallow unconfirmed prev UTXOs and spent UTXOs if
    // unmaintained (unundoable) spend
    public void resolveValidityConflicts(Set<BlockWrap> blocksToAdd, boolean unconfirmLosingMilestones)
            throws BlockStoreException {
        // Remove blocks and their approvers that have at least one input
        // with its corresponding output not confirmed yet
        removeWherePreconditionsUnfulfilled(blocksToAdd);

        // Resolve conflicting block combinations:
        // Disallow conflicts with unmaintained/pruned milestone blocks,
        //  i.e. remove those whose input is already spent by such blocks
        resolvePrunedConflicts(blocksToAdd);
        
        // Then resolve conflicts between maintained milestone + new candidates
        // This may trigger reorgs, i.e. unconfirming current milestones!
        resolveUndoableConflicts(blocksToAdd, unconfirmLosingMilestones);

        // Remove blocks and their approvers that have at least one input
        // with its corresponding output not confirmed yet
        // This is needed since reorgs could have been triggered
        removeWherePreconditionsUnfulfilled(blocksToAdd);
    }

    private void resolvePrunedConflicts(Set<BlockWrap> blocksToAdd) throws BlockStoreException {
        // Find all conflict candidates in blocks to add
        List<ConflictCandidate> conflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.toList());

        // Find only those that are spent in milestone
        filterSpent(conflicts);

        // Add the conflicting candidates and milestone blocks to given set
        for (ConflictCandidate c : conflicts) {
            // Find the spending block we are competing with
            BlockWrap milestoneBlock = getSpendingBlock(c);
            
            // If it is pruned or not maintained, we drop the blocks and warn
            if (milestoneBlock == null || !milestoneBlock.getBlockEvaluation().isMaintained()) {
                blockService.removeBlockAndApproversFrom(blocksToAdd, c.getBlock());
                logger.error("Dropping would-be blocks due to pruning. Reorg not possible!");
            }
        }
    }

    /**
     * Resolves conflicts between milestone blocks and milestone candidates as
     * well as conflicts among milestone candidates.
     * 
     * @param blocksToAdd
     * @param unconfirmLosingMilestones
     * @throws BlockStoreException
     */
    private void resolveUndoableConflicts(Collection<BlockWrap> blocksToAdd, boolean unconfirmLosingMilestones)
            throws BlockStoreException {
        HashSet<ConflictCandidate> conflictingOutPoints = new HashSet<ConflictCandidate>();
        HashSet<BlockWrap> conflictingMilestoneBlocks = new HashSet<BlockWrap>();

        // Find all conflicts in the new blocks + maintained milestone blocks
        findFixableConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);

        // Resolve all conflicts by grouping by UTXO ordered by descending
        // rating
        HashSet<BlockWrap> losingBlocks = resolveConflicts(conflictingOutPoints, unconfirmLosingMilestones);

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
        // blocksToAdd \ winningBlocks) remove them from blocksToAdd
        for (ConflictCandidate b : conflictingOutPoints.stream()
                .filter(b -> blocksToAdd.contains(b.getBlock()) && losingBlocks.contains(b.getBlock()))
                .collect(Collectors.toList())) {
            blockService.removeBlockAndApproversFrom(blocksToAdd, b.getBlock());
        }
    }

    /**
     * Resolve all conflicts by grouping by UTXO ordered by descending rating.
     * 
     * @param blockEvaluationsToAdd
     * @param conflictingOutPoints
     * @param unconfirmLosingMilestones
     * @param conflictingMilestoneBlocks
     * @return losingBlocks: blocks that have been removed due to conflict
     *         resolution
     * @throws BlockStoreException
     */
    public HashSet<BlockWrap> resolveConflicts(Collection<ConflictCandidate> conflictingOutPoints,
            boolean unconfirmLosingMilestones) throws BlockStoreException {
        // Initialize blocks that will survive the conflict resolution
        HashSet<BlockWrap> initialBlocks = conflictingOutPoints.stream().map(c -> c.getBlock())
                .collect(Collectors.toCollection(HashSet::new));
        HashSet<BlockWrap> winningBlocks = new HashSet<>();
        for (BlockWrap winningBlock : initialBlocks) {
            blockService.addApprovedNonMilestoneBlocksTo(winningBlocks, winningBlock);
            blockService.addMilestoneApproversTo(winningBlocks, winningBlock);
        }
        HashSet<BlockWrap> losingBlocks = new HashSet<>(winningBlocks);

        // Sort conflicts internally by descending rating, then cumulative
        // weight. If not unconfirming, prefer milestones first.
        Comparator<ConflictCandidate> byDescendingRating = getConflictComparator(unconfirmLosingMilestones)
                .thenComparingLong((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getRating())
                .thenComparingLong((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getCumulativeWeight())
                .thenComparingLong((ConflictCandidate e) -> -e.getBlock().getBlockEvaluation().getInsertTime())
                .thenComparing((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getBlockHash()).reversed();

        Supplier<TreeSet<ConflictCandidate>> conflictTreeSetSupplier = () -> new TreeSet<ConflictCandidate>(
                byDescendingRating);

        Map<Object, TreeSet<ConflictCandidate>> conflicts = conflictingOutPoints.stream().collect(
                Collectors.groupingBy(i -> i.getConflictPoint(), Collectors.toCollection(conflictTreeSetSupplier)));

        // Sort conflicts among each other by descending max(rating). If not
        // unconfirming, prefer milestones first.
        Comparator<TreeSet<ConflictCandidate>> byDescendingSetRating = getConflictSetComparator(
                unconfirmLosingMilestones)
                        .thenComparingLong(
                                (TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation().getRating())
                        .thenComparingLong((TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation()
                                .getCumulativeWeight())
                        .thenComparingLong((TreeSet<ConflictCandidate> s) -> -s.first().getBlock().getBlockEvaluation()
                                .getInsertTime())
                        .thenComparing((TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation()
                                .getBlockHash())
                        .reversed();

        Supplier<TreeSet<TreeSet<ConflictCandidate>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<ConflictCandidate>>(
                byDescendingSetRating);

        TreeSet<TreeSet<ConflictCandidate>> sortedConflicts = conflicts.values().stream()
                .collect(Collectors.toCollection(conflictsTreeSetSupplier));

        // Now handle conflicts by descending max(rating)
        for (TreeSet<ConflictCandidate> conflict : sortedConflicts) {
            // Take the block with the maximum rating in this conflict that is
            // still in winning Blocks
            ConflictCandidate maxRatingPair = null;
            for (ConflictCandidate c : conflict) {
                if (winningBlocks.contains(c.getBlock())) {
                    maxRatingPair = c;
                    break;
                }
            }

            // If such a block exists, this conflict is resolved by eliminating
            // all other
            // blocks in this conflict from winning Blocks
            if (maxRatingPair != null) {
                for (ConflictCandidate c : conflict) {
                    if (c != maxRatingPair) {
                        blockService.removeBlockAndApproversFrom(winningBlocks, c.getBlock());
                    }
                }
            }
        }

        losingBlocks.removeAll(winningBlocks);

        return losingBlocks;
    }

    private Comparator<TreeSet<ConflictCandidate>> getConflictSetComparator(boolean unconfirmLosingMilestones) {
        if (!unconfirmLosingMilestones)
            return new Comparator<TreeSet<ConflictCandidate>>() {
                @Override
                public int compare(TreeSet<ConflictCandidate> o1, TreeSet<ConflictCandidate> o2) {
                    if (o1.first().getBlock().getBlockEvaluation().isMilestone()
                            && o2.first().getBlock().getBlockEvaluation().isMilestone())
                        return 0;
                    if (o1.first().getBlock().getBlockEvaluation().isMilestone())
                        return 1;
                    if (o2.first().getBlock().getBlockEvaluation().isMilestone())
                        return -1;
                    return 0;
                }
            };
        else
            return new Comparator<TreeSet<ConflictCandidate>>() {
                @Override
                public int compare(TreeSet<ConflictCandidate> o1, TreeSet<ConflictCandidate> o2) {
                    return 0;
                }
            };
    }

    private Comparator<ConflictCandidate> getConflictComparator(boolean unconfirmLosingMilestones) {
        if (!unconfirmLosingMilestones)
            return new Comparator<ConflictCandidate>() {
                @Override
                public int compare(ConflictCandidate o1, ConflictCandidate o2) {
                    if (o1.getBlock().getBlockEvaluation().isMilestone()
                            && o2.getBlock().getBlockEvaluation().isMilestone()) {
                        if (o1.equals(o2))
                            return 0;
                        else {
                            logger.warn("Inconsistent Milestone: Conflicting blocks in milestone" + ": \n"
                                    + o1.getBlock().getBlock().getHash() + "\n" + o2.getBlock().getBlock().getHash());
                            return 0;
                        }
                    }
                    if (o1.getBlock().getBlockEvaluation().isMilestone())
                        return 1;
                    if (o2.getBlock().getBlockEvaluation().isMilestone())
                        return -1;
                    return 0;
                }
            };
        else
            return new Comparator<ConflictCandidate>() {
                @Override
                public int compare(ConflictCandidate o1, ConflictCandidate o2) {
                    return 0;
                }
            };
    }

    /**
     * Finds conflicts in blocksToAdd itself and with the milestone.
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    public void findFixableConflicts(Collection<BlockWrap> blocksToAdd, Collection<ConflictCandidate> conflictingOutPoints,
            Collection<BlockWrap> conflictingMilestoneBlocks) throws BlockStoreException {

        findUndoableMilestoneConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);
        findCandidateConflicts(blocksToAdd, conflictingOutPoints);
    }

    /**
     * Finds conflicts among blocks to add themselves
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    private void findCandidateConflicts(Collection<BlockWrap> blocksToAdd,
            Collection<ConflictCandidate> conflictingOutPoints) throws BlockStoreException {
        // Get conflicts that are spent more than once in the
        // candidates
        List<ConflictCandidate> candidateCandidateConflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream()
                .filter(l -> l.size() > 1).flatMap(l -> l.stream()).collect(Collectors.toList());

        // Add the conflicting candidates
        for (ConflictCandidate c : candidateCandidateConflicts) {
            conflictingOutPoints.add(c);
        }
    }

    /**
     * Finds conflicts between current milestone and blocksToAdd
     * 
     * @param blocksToAdd
     * @param conflictingOutPoints
     * @throws BlockStoreException
     */
    // TODO should be sets not collections
    private void findUndoableMilestoneConflicts(Collection<BlockWrap> blocksToAdd,
            Collection<ConflictCandidate> conflictingOutPoints, Collection<BlockWrap> conflictingMilestoneBlocks)
            throws BlockStoreException {
        // Find all conflict candidates in blocks to add
        List<ConflictCandidate> conflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.toList());

        // Find only those that are spent in milestone
        filterSpent(conflicts);

        // Add the conflicting candidates and milestone blocks to given set
        for (ConflictCandidate c : conflicts) {
            // Find the spending block we are competing with
            BlockWrap milestoneBlock = getSpendingBlock(c);
            
            // Only go through if the milestone block is maintained, i.e. undoable
            if (milestoneBlock == null || !milestoneBlock.getBlockEvaluation().isMaintained())
                continue;

            // Add milestone block
            conflictingOutPoints.add(ConflictCandidate.fromConflictPoint(milestoneBlock, c.getConflictPoint()));
            conflictingMilestoneBlocks.add(milestoneBlock);

            // Then add corresponding new block
            conflictingOutPoints.add(c);
        }
    }

    // Returns null if no spending block found
    // TODO refactor this garbage
    private BlockWrap getSpendingBlock(ConflictCandidate c) throws BlockStoreException {
        BlockWrap milestoneBlock = null;
        switch (c.getConflictPoint().getType()) {
        case TXOUT:
            final BlockEvaluation utxoSpender = transactionService.getUTXOSpender(c.getConflictPoint().getConnectedOutpoint());
            if (utxoSpender == null)
                return null;
            milestoneBlock = store.getBlockWrap(utxoSpender.getBlockHash());
            break;
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();
            
            // Initial issuances are competing with each other
            if (connectedToken.getTokenindex() == 0) {
                return store.getTokenIssuingConfirmedBlock(connectedToken.getTokenid(), connectedToken.getTokenindex()); 
            }
            else {
                final String tokenSpender = store.getTokenSpender(connectedToken.getPrevblockhash());
                if (tokenSpender == null)
                    return null;
                milestoneBlock = store.getBlockWrap(Sha256Hash.wrap(tokenSpender));
            }
            break;
        case REWARDISSUANCE:
            final Sha256Hash txRewardSpender = store.getTxRewardSpender(Sha256Hash.wrap(c.getConflictPoint().getConnectedPrevReward()));
            if (txRewardSpender == null)
                return null;
            milestoneBlock = store.getBlockWrap(txRewardSpender);
            break;
        default:
            throw new NotImplementedException();
        }
        return milestoneBlock;
    }

    public void filterSpent(Collection<ConflictCandidate> blockConflicts) {
        blockConflicts.removeIf(c -> {
            try {
                return !isSpent(c);
            } catch (BlockStoreException e) {
                e.printStackTrace();
                return true;
            }
        });
    }

    /*
     * Check if the block is made correctly. Allow conflicts for transaction
     * data (non-Javadoc)
     */
    public boolean checkBlockValidity(Block block, StoredBlock storedPrev, StoredBlock storedPrevBranch, long height)
            throws VerificationException {
        // Check timestamp: enforce monotone time increase
        if (block.getTimeSeconds() < storedPrev.getHeader().getTimeSeconds()
                || block.getTimeSeconds() < storedPrevBranch.getHeader().getTimeSeconds())
            return false;

        // Check difficulty and latest consensus reward block is passed through
        // correctly
        if (block.getBlockType() != Block.Type.BLOCKTYPE_REWARD
                && block.getBlockType() != Block.Type.BLOCKTYPE_INITIAL) {
            if (storedPrev.getHeader().getLastMiningRewardBlock() >= storedPrevBranch.getHeader()
                    .getLastMiningRewardBlock()) {
                if (block.getLastMiningRewardBlock() != storedPrev.getHeader().getLastMiningRewardBlock()
                        || block.getDifficultyTarget() != storedPrev.getHeader().getDifficultyTarget())
                    return false;
            } else {
                if (block.getLastMiningRewardBlock() != storedPrevBranch.getHeader().getLastMiningRewardBlock()
                        || block.getDifficultyTarget() != storedPrevBranch.getHeader().getDifficultyTarget())
                    return false;
            }
        }

        // Check correctness of TXs and their data
        if (!checkTransactionalValidity(block, height))
            return false;

        // Check type-specific validity
        if (!checkTypeSpecificValidity(block, storedPrev, storedPrevBranch))
            return false;

        return true;
    }

    private boolean checkTypeSpecificValidity(Block block, StoredBlock storedPrev, StoredBlock storedPrevBranch) {
        List<Transaction> transactions = block.getTransactions();
        switch (block.getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
//            // Check genesis block specific validity, can only one genesis block
//            if (!block.getHash().equals(networkParameters.getGenesisBlock().getHash())) {
//                return false;
//            }
            break;
        case BLOCKTYPE_REWARD:
//            if (transactions.size() != 1)
//                return false; // Too many or too few transactions for token
//                              // creation
//
//            if (!transactions.get(0).isCoinBase())
//                return false; // TX is not coinbase when it should be

            // Check that the tx has correct data (long fromHeight)
            // TODO overhaul
            try {
                RewardInfo rewardInfo = RewardInfo.parse(transactions.get(0).getData());                
                Sha256Hash prevRewardHash = Sha256Hash.wrap(rewardInfo.getPrevRewardHash());
                
                // Check if eligible
                try {
                    Triple<Transaction, Boolean, Long> rewardEligibleDifficulty = generateMiningRewardTX(
                            storedPrev.getHeader(), storedPrevBranch.getHeader(), prevRewardHash);

                    // Reward must have been built correctly.
                    if (!rewardEligibleDifficulty.getLeft().getHash().equals(block.getTransactions().get(0).getHash()))
                        return false;

                    // Difficulty must be correct
                    if (rewardEligibleDifficulty.getRight() != block.getDifficultyTarget())
                        return false;
                    if (Math.max(storedPrev.getHeader().getLastMiningRewardBlock(),
                            storedPrevBranch.getHeader().getLastMiningRewardBlock())
                            + 1 != block.getLastMiningRewardBlock())
                        return false;
                } catch (BlockStoreException e) {
                    logger.info("", e);
                    return false;
                }
                
            } catch (IOException e) {
                // Cannot happen
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            // Check reward block specific validity
            // Get reward data from previous reward cycle
//            Sha256Hash prevRewardHash = null;
//            @SuppressWarnings("unused")
//            long fromHeight = 0, nextPerTxReward = 0;
//            try {
//                byte[] hashBytes = new byte[32];
//                ByteBuffer bb = ByteBuffer.wrap(block.getTransactions().get(0).getData());
//                fromHeight = bb.getLong();
//                nextPerTxReward = bb.getLong();
//                bb.get(hashBytes, 0, 32);
//                prevRewardHash = Sha256Hash.wrap(hashBytes);
//            } catch (Exception e) {
//                e.printStackTrace();
//                return false;
//            }
//
//            try {
//                Triple<Transaction, Boolean, Long> rewardEligibleDifficulty = generateMiningRewardTX(
//                        storedPrev.getHeader(), storedPrevBranch.getHeader(), prevRewardHash);
//
//                // Reward must have been built correctly.
//                if (!rewardEligibleDifficulty.getLeft().getHash().equals(block.getTransactions().get(0).getHash()))
//                    return false;
//
//                // Difficulty must be correct
//                if (rewardEligibleDifficulty.getRight() != block.getDifficultyTarget())
//                    return false;
//                if (Math.max(storedPrev.getHeader().getLastMiningRewardBlock(),
//                        storedPrevBranch.getHeader().getLastMiningRewardBlock())
//                        + 1 != block.getLastMiningRewardBlock())
//                    return false;
//            } catch (BlockStoreException e) {
//                logger.info("", e);
//                return false;
//            }

            break;
        case BLOCKTYPE_TOKEN_CREATION:
//            if (transactions.size() != 1)
//                return false; // Too many or too few transactions for token
//                              // creation
//
//            if (!transactions.get(0).isCoinBase())
//                return false; // TX is not coinbase when it should be;

            // Check issuance block specific validity
            try {
                // TODO split in checks of solidity and validity

                // Check according to previous issuance, or if it does not exist
                // the normal signature
                if (!this.multiSignService.checkToken(block, false)) {
                    return false;
                }
            } catch (Exception e) {
                logger.error("", e);
                return false;
            }

            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_VOS:
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            break;
        default:
            throw new NotImplementedException("Blocktype not implemented!");
        }

        return true;
    }

    private boolean checkTransactionalValidity(Block block, long height) {
        List<Transaction> transactions = block.getTransactions();

        // Coinbase allowance
        for (Transaction tx : transactions) {
            if (tx.isCoinBase() && !block.allowCoinbaseTransaction()) {
                return false;
            }
        }

        // Transaction validity
        LinkedList<UTXO> txOutsSpent = new LinkedList<UTXO>();
        LinkedList<UTXO> txOutsCreated = new LinkedList<UTXO>();
        long sigOps = 0;

        try {
            if (scriptVerificationExecutor.isShutdown())
                scriptVerificationExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            List<Future<VerificationException>> listScriptVerificationResults = new ArrayList<Future<VerificationException>>(
                    block.getTransactions().size());

            if (!params.isCheckpoint(height)) {
                for (Transaction tx : block.getTransactions()) {
                    final Set<VerifyFlag> verifyFlags = params.getTransactionVerificationFlags(block, tx);

                    if (verifyFlags.contains(VerifyFlag.P2SH))
                        sigOps += tx.getSigOpCount();
                }
            }

            for (final Transaction tx : block.getTransactions()) {
                boolean isCoinBase = tx.isCoinBase();
                Map<String, Coin> valueIn = new HashMap<String, Coin>();
                Map<String, Coin> valueOut = new HashMap<String, Coin>();

                final List<Script> prevOutScripts = new LinkedList<Script>();
                final Set<VerifyFlag> verifyFlags = params.getTransactionVerificationFlags(block, tx);
                if (!isCoinBase) {
                    for (int index = 0; index < tx.getInputs().size(); index++) {
                        TransactionInput in = tx.getInputs().get(index);
                        UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getHash(),
                                in.getOutpoint().getIndex());
                        if (prevOut == null) {
                            // Cannot happen due to solidity checks before
                            throw new RuntimeException("Block attempts to spend a not yet existent output!");
                        }

                        if (valueIn.containsKey(Utils.HEX.encode(prevOut.getValue().getTokenid()))) {
                            valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()), valueIn
                                    .get(Utils.HEX.encode(prevOut.getValue().getTokenid())).add(prevOut.getValue()));
                        } else {
                            valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()), prevOut.getValue());

                        }
                        if (verifyFlags.contains(VerifyFlag.P2SH)) {
                            if (prevOut.getScript().isPayToScriptHash())
                                sigOps += Script.getP2SHSigOpCount(in.getScriptBytes());
                            if (sigOps > Block.MAX_BLOCK_SIGOPS)
                                throw new VerificationException("Too many P2SH SigOps in block");
                        }
                        prevOutScripts.add(prevOut.getScript());
                        txOutsSpent.add(prevOut);
                    }
                }
                Sha256Hash hash = tx.getHash();
                for (TransactionOutput out : tx.getOutputs()) {
                    if (valueOut.containsKey(Utils.HEX.encode(out.getValue().getTokenid()))) {
                        valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()),
                                valueOut.get(Utils.HEX.encode(out.getValue().getTokenid())).add(out.getValue()));
                    } else {
                        valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()), out.getValue());
                    }
                    // For each output, add it to the set of unspent outputs so
                    // it can be consumed
                    // in future.
                    Script script = getScript(out.getScriptBytes());
                    UTXO newOut = new UTXO(hash, out.getIndex(), out.getValue(), height, isCoinBase, script,
                            getScriptAddress(script), block.getHash(), out.getFromaddress(), tx.getMemo(),
                            Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, 0);

                    txOutsCreated.add(newOut);
                }
                if (!checkOutputSigns(valueOut))
                    throw new VerificationException("Transaction output value out of range");
                if (isCoinBase) {
                    // coinbaseValue = valueOut;
                } else {
                    if (!checkInputOutput(valueIn, valueOut))
                        throw new VerificationException("Transaction input value out of range");
                    // totalFees = totalFees.add(valueIn.subtract(valueOut));
                }

                if (!isCoinBase) {
                    // Because correctlySpends modifies transactions, this must
                    // come after we are done with tx
                    FutureTask<VerificationException> future = new FutureTask<VerificationException>(
                            new Verifier(tx, prevOutScripts, verifyFlags));
                    scriptVerificationExecutor.execute(future);
                    listScriptVerificationResults.add(future);
                }
            }
            for (Future<VerificationException> future : listScriptVerificationResults) {
                VerificationException e;
                try {
                    e = future.get();
                } catch (InterruptedException thrownE) {
                    throw new RuntimeException(thrownE); // Shouldn't happen
                } catch (ExecutionException thrownE) {
                    // log.error("Script.correctlySpends threw a non-normal
                    // exception: " ,thrownE );
                    throw new VerificationException(
                            "Bug in Script.correctlySpends, likely script malformed in some new and interesting way.",
                            thrownE);
                }
                if (e != null)
                    throw e;
            }
        } catch (VerificationException e) {
            scriptVerificationExecutor.shutdownNow();
            logger.info("", e);
            return false;
        } catch (BlockStoreException e) {
            scriptVerificationExecutor.shutdownNow();
            logger.info("", e);
            return false;
        }

        return true;
    }

    private boolean checkOutputSigns(Map<String, Coin> valueOut) {
        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            // System.out.println(entry.getKey() + "/" + entry.getValue());
            if (entry.getValue().signum() < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean checkInputOutput(Map<String, Coin> valueInput, Map<String, Coin> valueOut) {
        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            if (!valueInput.containsKey(entry.getKey())) {
                return false;
            } else {
                if (valueInput.get(entry.getKey()).compareTo(entry.getValue()) < 0)
                    return false;
            }
        }
        return true;
    }

    /*
     * Checks if the block has all of its dependencies to fully determine its
     * validity. If SolidityState.getFailState() is returned, this is equivalent
     * to the block always being invalid.
     */
    public SolidityState checkBlockSolidity(Block block, @Nullable StoredBlock storedPrev,
            @Nullable StoredBlock storedPrevBranch) throws BlockStoreException {
        if (storedPrev == null) {
            return SolidityState.from(block.getPrevBlockHash());
        }

        if (storedPrevBranch == null) {
            return SolidityState.from(block.getPrevBranchBlockHash());
        }

        SolidityState transactionalSolidityState = checkTransactionalSolidity(block);
        if (!transactionalSolidityState.isOK()) {
            return transactionalSolidityState;
        }

        SolidityState typeSpecificSolidityState = checkTypeSpecificSolidity(block);
        if (!transactionalSolidityState.isOK()) {
            return typeSpecificSolidityState;
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkTypeSpecificSolidity(Block block) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();
        switch (block.getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            return SolidityState.getFailState();
        case BLOCKTYPE_REWARD:
            if (transactions.size() != 1)
                return SolidityState.getFailState(); // Incorrect tx count

            if (!transactions.get(0).isCoinBase())
                return SolidityState.getFailState(); // TX is not coinbase

            // Check that the tx has correct data
            try {
                RewardInfo rewardInfo = RewardInfo.parse(transactions.get(0).getData());
                
                // Ensure dependency (prev reward hash) exists
                StoredBlock dependency = store.get(Sha256Hash.wrap(rewardInfo.getPrevRewardHash()));
                if (dependency == null)       
                    return SolidityState.from(Sha256Hash.wrap(rewardInfo.getPrevRewardHash())); 
                
                // TODO move the stuff below to validity OR simply merge validity into solidity since failstate = invalid?
                // Ensure dependency (prev reward hash) is valid
                if (dependency.getHeader().getBlockType() != Type.BLOCKTYPE_INITIAL && dependency.getHeader().getBlockType() != Type.BLOCKTYPE_REWARD)
                    return SolidityState.getFailState();         
                
                // TODO Ensure all fields of RewardInfo are ok
                if (rewardInfo.getToHeight() % NetworkParameters.REWARD_HEIGHT_INTERVAL != NetworkParameters.REWARD_HEIGHT_INTERVAL - 1)
                    return SolidityState.getFailState(); // Invalid fromHeight
                
                // TODO check FromHeight is starting from prevToHeight+1
                
                // TODO remove rewardInfo.getNextPerTxReward() and save in db just in time;
                
//                byte[] data = ;
//                if (data == null || data.length < 8)
//                    return SolidityState.getFailState(); // Missing fromHeight
//                                                         // data
//                long u = Utils.readInt64(data, 0);
//                if (rewardInfo.getFromHeight() % NetworkParameters.REWARD_HEIGHT_INTERVAL != 0)
//                    return SolidityState.getFailState(); // Invalid fromHeight
                
            } catch (IOException e) {
                // Incorrect data format
                return SolidityState.getFailState();
            }
            
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            if (transactions.size() != 1)
                return SolidityState.getFailState(); // Incorrect tx count

            if (!transactions.get(0).isCoinBase())
                return SolidityState.getFailState(); // TX is not coinbase
            
            try {
                // TODO add waiting for older token issuance by
                // TODO split in checks solidity and validity

                // TODO if (tokenInfo.getTokens().getTokenindex() == 0) do not add on solidity waiting list

                // Check according to previous issuance, or if it does not exist
                // the normal signature
                if (!this.multiSignService.checkToken(block, false)) {
                    return SolidityState.getFailState();
                }
            } catch (Exception e) {
                logger.error("", e);
                return SolidityState.getFailState();
            }

            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_VOS:
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            break;
        default:
            throw new NotImplementedException("Blocktype not implemented!");
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkTransactionalSolidity(Block block) throws BlockStoreException {
        // All used transaction outputs must exist
        for (final Transaction tx : block.getTransactions()) {
            if (!tx.isCoinBase()) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getHash(), in.getOutpoint().getIndex());
                    if (prevOut == null) {
                        return SolidityState.from(in.getOutpoint());
                    }
                }
            }
        }

        return SolidityState.getSuccessState();
    }
}
