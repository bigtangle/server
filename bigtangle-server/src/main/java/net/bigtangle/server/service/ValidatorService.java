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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.directory.api.util.exception.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ConflictCandidate;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.script.Script;
import net.bigtangle.script.Script.VerifyFlag;
import net.bigtangle.server.service.SolidityState.State;
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
     * NOTE: The reward block is assumed to having successfully gone through checkRewardSolidity beforehand!
     * For connecting purposes, checks if the given rewardBlock is eligible now.
     * 
     * @param rewardBlock
     * @return eligibility of rewards + new perTxReward)
     * @throws BlockStoreException
     */
    public Pair<RewardEligibility, Long> checkRewardEligibility(Block rewardBlock) throws BlockStoreException {
        try {
            RewardInfo rewardInfo = RewardInfo.parse(rewardBlock.getTransactions().get(0).getData());
            Triple<RewardEligibility, Transaction, Pair<Long, Long>> result = makeReward(
                    rewardBlock.getPrevBlockHash(), rewardBlock.getPrevBranchBlockHash(), Sha256Hash.wrap(rewardInfo.getPrevRewardHash()));

            if (rewardBlock.getDifficultyTarget() != result.getRight().getLeft() || !rewardBlock.getTransactions().get(0).equals(result.getMiddle()))
                return Pair.of(RewardEligibility.INVALID, result.getRight().getRight()); 
            else
                return Pair.of(result.getLeft(), result.getRight().getRight()); 

        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    /**
     * DOES NOT CHECK FOR SOLIDITY.
     * Computes eligibility of rewards + data tx + Pair.of(new difficulty + new perTxReward) here for new reward blocks. 
     * This is a prototype implementation. In the actual Spark implementation, 
     * the computational cost is not a problem, since it is instead backpropagated and calculated for free with delay.
     * For more info, see Jdbctest notes.
     * 
     * @param prevTrunk a predecessor block in the db
     * @param prevBranch a predecessor block in the db
     * @param prevRewardHash the predecessor reward
     * @return eligibility of rewards + data tx + Pair.of(new difficulty + new perTxReward)
     */
    public Triple<RewardEligibility, Transaction, Pair<Long, Long>> makeReward(Sha256Hash prevTrunk, Sha256Hash prevBranch,
            Sha256Hash prevRewardHash) throws BlockStoreException {

        // Count how many blocks from miners in the reward interval are approved
        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        BlockWrap prevTrunkBlock = store.getBlockWrap(prevTrunk);
        BlockWrap prevBranchBlock = store.getBlockWrap(prevBranch);
        blockQueue.add(prevTrunkBlock);
        blockQueue.add(prevBranchBlock);

        // Read previous reward block's data
        BlockWrap prevRewardBlock = store.getBlockWrap(prevRewardHash);
        long prevToHeight = store.getRewardToHeight(prevRewardHash);
        long prevDifficulty = prevRewardBlock.getBlock().getDifficultyTarget();
        long fromHeight = prevToHeight + 1;
        long toHeight = prevToHeight + (long) NetworkParameters.REWARD_HEIGHT_INTERVAL;

        // Initialize
        BlockWrap currentBlock = null, approvedBlock = null;
        long currentHeight = Long.MAX_VALUE;
        long totalRewardCount = 0;

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {
            currentHeight = currentBlock.getBlockEvaluation().getHeight();
            
            // Stop criterion: Block height lower than approved interval height
            if (currentHeight < fromHeight)
                break;

            // If in relevant reward height interval, count it
            if (currentHeight <= toHeight) {
                totalRewardCount++;
            }

            // Continue with both approved blocks
            approvedBlock = store.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                }
            } 
            approvedBlock = store.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                }
            } 
        }

        // New difficulty
        long difficulty = calculateNextDifficulty(prevDifficulty, prevTrunkBlock, prevBranchBlock, prevRewardBlock,
                totalRewardCount);

        // Build transaction for block
        Transaction tx = new Transaction(networkParameters);

        // Build the type-specific tx data
        RewardInfo rewardInfo = new RewardInfo(fromHeight, toHeight, prevRewardHash.toString());
        tx.setData(rewardInfo.toByteArray());

        // New rewards
        long perTxReward = calculateNextTxReward(prevTrunkBlock, prevBranchBlock, prevRewardBlock,
                store.getRewardNextTxReward(prevRewardHash), totalRewardCount);

        // Ensure prevTrunk/prevBranch are same consensus number (but not here)
        if (prevTrunkBlock.getBlock().getLastMiningRewardBlock() != prevRewardBlock.getBlock().getLastMiningRewardBlock()
                || prevBranchBlock.getBlock().getLastMiningRewardBlock() != prevRewardBlock.getBlock().getLastMiningRewardBlock())
            return Triple.of(RewardEligibility.INVALID, tx, Pair.of(difficulty, perTxReward));
        // Ensure enough blocks are approved 
        else if (totalRewardCount >= store.getCountMilestoneBlocksInInterval(fromHeight, toHeight) * 99 / 100 )
            return Triple.of(RewardEligibility.ELIGIBLE, tx, Pair.of(difficulty, perTxReward));
        else 
            return Triple.of(RewardEligibility.INELIGIBLE, tx, Pair.of(difficulty, perTxReward));
    }

    private long calculateNextDifficulty(long prevDifficulty, BlockWrap prevTrunkBlock, BlockWrap prevBranchBlock,
            BlockWrap prevRewardBlock, long totalRewardCount) {
        // The following equals current time by consensus rules
        long currentTime = Math.max(prevTrunkBlock.getBlock().getTimeSeconds(),
                prevBranchBlock.getBlock().getTimeSeconds());
        long timespan = Math.max(1, (currentTime - prevRewardBlock.getBlock().getTimeSeconds()));

        BigInteger prevTarget = Utils.decodeCompactBits(prevDifficulty);
        BigInteger newTarget = prevTarget.multiply(BigInteger.valueOf(NetworkParameters.TARGET_MAX_TPS));
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

        // TODO include result from difficulty adjustment to actually stay on target
        // BigInteger result = BigInteger.valueOf(currPerTxReward);
        long nextPerTxReward = NetworkParameters.TARGET_YEARLY_MINING_PAYOUT * timespan / 31536000L / totalRewardCount;
        nextPerTxReward = Math.max(nextPerTxReward, currPerTxReward / 4);
        nextPerTxReward = Math.min(nextPerTxReward, currPerTxReward * 4);
        nextPerTxReward = Math.max(nextPerTxReward, 1);
        return nextPerTxReward;
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
                
                // Initial issuances are allowed iff no other same token issuances are confirmed, i.e. spent iff any token confirmed
                if (connectedToken.getTokenindex() == 0)
                    return store.getTokenAnyConfirmed(connectedToken.getTokenid(), connectedToken.getTokenindex()); 
                else
                    return store.getTokenSpent(connectedToken.getPrevblockhash());
            case REWARDISSUANCE:
                return store.getRewardSpent(Sha256Hash.wrap(c.getConflictPoint().getConnectedReward().getPrevRewardHash()));
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
            
            // Initial issuances are allowed (although they may be spent already)
            if (connectedToken.getTokenindex() == 0)
                return true; 
            else
                return store.getTokenConfirmed(connectedToken.getPrevblockhash());
        case REWARDISSUANCE:
            return store.getRewardConfirmed(Sha256Hash.wrap(c.getConflictPoint().getConnectedReward().getPrevRewardHash()));
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
    private void removeWhereUsedOutputsUnconfirmed(Set<BlockWrap> blocksToAdd)
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

        // Additionally, for reward blocks: invalid never, ineligible overrule, eligible ok
        // If ineligible, overrule by sufficient age and milestone rating range
        // TODO Also add eligibility lockout period, e.g. time + avg tip height sufficient to avoid prebuilders    
        new HashSet<BlockWrap>(blocksToAdd).stream()
        .filter(b -> b.getBlock().getBlockType() == Type.BLOCKTYPE_REWARD) // prefilter for reward blocks
        .forEach(b -> {
            try {
                switch (store.getRewardEligible(b.getBlock().getHash())) {
                case ELIGIBLE:
                    // OK
                    break;
                case INELIGIBLE:
                    if (!(b.getBlockEvaluation().getRating() > NetworkParameters.MILESTONE_UPPER_THRESHOLD
                            && b.getBlockEvaluation().getInsertTime() < System.currentTimeMillis() / 1000 - NetworkParameters.OVERRULE_TIME)) 
                        blockService.removeBlockAndApproversFrom(blocksToAdd, b);
                    break;
                case INVALID:
                    blockService.removeBlockAndApproversFrom(blocksToAdd, b);
                    break;
                case UNKNOWN:
                    // Cannot happen in non-Spark implementation.
                    blockService.removeBlockAndApproversFrom(blocksToAdd, b);
                    break;
                default:
                    throw new NotImplementedException();
                
                }
            } catch (BlockStoreException e) {
                // Cannot happen.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    // disallow unconfirmed prev UTXOs and spent UTXOs if
    // unmaintained (unundoable) spend
    public void resolveAllConflicts(Set<BlockWrap> blocksToAdd, boolean unconfirmLosingMilestones)
            throws BlockStoreException {
        // Remove blocks and their approvers that have at least one input
        // with its corresponding output not confirmed yet
        removeWhereUsedOutputsUnconfirmed(blocksToAdd);

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
        removeWhereUsedOutputsUnconfirmed(blocksToAdd);
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
    private void resolveUndoableConflicts(Set<BlockWrap> blocksToAdd, boolean unconfirmLosingMilestones)
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
    private HashSet<BlockWrap> resolveConflicts(Set<ConflictCandidate> conflictingOutPoints,
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
    private void findFixableConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints,
            Set<BlockWrap> conflictingMilestoneBlocks) throws BlockStoreException {

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
    private void findCandidateConflicts(Set<BlockWrap> blocksToAdd,
            Set<ConflictCandidate> conflictingOutPoints) throws BlockStoreException {
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
    private void findUndoableMilestoneConflicts(Set<BlockWrap> blocksToAdd,
            Set<ConflictCandidate> conflictingOutPoints, Set<BlockWrap> conflictingMilestoneBlocks)
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
    private BlockWrap getSpendingBlock(ConflictCandidate c) throws BlockStoreException {
        switch (c.getConflictPoint().getType()) {
        case TXOUT:
            final BlockEvaluation utxoSpender = transactionService.getUTXOSpender(c.getConflictPoint().getConnectedOutpoint());
            if (utxoSpender == null)
                return null;
            return store.getBlockWrap(utxoSpender.getBlockHash());
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();
            
            // The spender is always the one block with the same tokenid and index that is confirmed
            return store.getTokenIssuingConfirmedBlock(connectedToken.getTokenid(), connectedToken.getTokenindex()); 
        case REWARDISSUANCE:
            final Sha256Hash txRewardSpender = store.getRewardSpender(Sha256Hash.wrap(c.getConflictPoint().getConnectedReward().getPrevRewardHash()));
            if (txRewardSpender == null)
                return null;
            return store.getBlockWrap(txRewardSpender);
        default:
            throw new NotImplementedException();
        }
    }

    private void filterSpent(Collection<ConflictCandidate> blockConflicts) {
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
     * Checks if the block has all of its dependencies to fully determine its
     * validity. Then checks if the block is valid based on its dependencies.
     * If SolidityState.getSuccessState() is returned, the block is valid.
     * If SolidityState.getFailState() is returned, the block is invalid.
     * Otherwise, appropriate solidity states are returned to imply missing dependencies.
     */
    public SolidityState checkBlockSolidity(Block block, @Nullable StoredBlock storedPrev,
            @Nullable StoredBlock storedPrevBranch) throws BlockStoreException {
        // Check predecessor blocks exist
        if (storedPrev == null) {
            return SolidityState.from(block.getPrevBlockHash());
        }
        if (storedPrevBranch == null) {
            return SolidityState.from(block.getPrevBranchBlockHash());
        }
        
        // Check timestamp: enforce monotone time increase
        if (block.getTimeSeconds() < storedPrev.getHeader().getTimeSeconds()
                || block.getTimeSeconds() < storedPrevBranch.getHeader().getTimeSeconds())
            return SolidityState.getFailState();

        // Check difficulty and latest consensus block is passed through correctly
        if (block.getBlockType() != Block.Type.BLOCKTYPE_REWARD
                && block.getBlockType() != Block.Type.BLOCKTYPE_INITIAL) {
            if (storedPrev.getHeader().getLastMiningRewardBlock() >= storedPrevBranch.getHeader()
                    .getLastMiningRewardBlock()) {
                if (block.getLastMiningRewardBlock() != storedPrev.getHeader().getLastMiningRewardBlock()
                        || block.getDifficultyTarget() != storedPrev.getHeader().getDifficultyTarget())
                    return SolidityState.getFailState();
            } else {
                if (block.getLastMiningRewardBlock() != storedPrevBranch.getHeader().getLastMiningRewardBlock()
                        || block.getDifficultyTarget() != storedPrevBranch.getHeader().getDifficultyTarget())
                    return SolidityState.getFailState();
            }
        }

        long height = Math.max(storedPrev.getHeight(), storedPrevBranch.getHeight()) + 1;

        // Check transactions are solid
        SolidityState transactionalSolidityState = checkTransactionalSolidity(block, height);
        if (!(transactionalSolidityState.getState() == State.Success)) {
            return transactionalSolidityState;
        }

        // Check type-specific solidity
        SolidityState typeSpecificSolidityState = checkTypeSpecificSolidity(block, storedPrev, storedPrevBranch);
        if (!(transactionalSolidityState.getState() == State.Success)) {
            return typeSpecificSolidityState;
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkTransactionalSolidity(Block block, long height) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        // Coinbase allowance
        for (Transaction tx : transactions) {
            if (tx.isCoinBase() && !block.allowCoinbaseTransaction()) {
                return SolidityState.getFailState();
            }
        }
        
        // All used transaction outputs must exist
        for (final Transaction tx : transactions) {
            if (!tx.isCoinBase()) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getHash(), in.getOutpoint().getIndex());
                    if (prevOut == null) {
                        // Missing previous transaction output
                        return SolidityState.from(in.getOutpoint());
                    }
                }
            }
        }
        
        // Transaction validation
        try {
            LinkedList<UTXO> txOutsSpent = new LinkedList<UTXO>();
            LinkedList<UTXO> txOutsCreated = new LinkedList<UTXO>();
            long sigOps = 0;
            
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
            return SolidityState.getFailState();
        } catch (BlockStoreException e) {
            scriptVerificationExecutor.shutdownNow();
            logger.error("", e);
            return SolidityState.getFailState();
        }
        
        return SolidityState.getSuccessState();
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

    private SolidityState checkTypeSpecificSolidity(Block block, StoredBlock storedPrev, StoredBlock storedPrevBranch) throws BlockStoreException {
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
            // Check token issuances are solid
            SolidityState rewardSolidityState = checkRewardSolidity(block, storedPrev, storedPrevBranch);
            if (!(rewardSolidityState.getState() == State.Success)) {
                return rewardSolidityState;
            }
            
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // Check token issuances are solid
            SolidityState tokenSolidityState = checkTokenSolidity(block);
            if (!(tokenSolidityState.getState() == State.Success)) {
                return tokenSolidityState;
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
    
    private SolidityState checkRewardSolidity(Block block, StoredBlock storedPrev, StoredBlock storedPrevBranch) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();
        
        if (transactions.size() != 1)
            return SolidityState.getFailState(); // Incorrect tx count

        if (!transactions.get(0).isCoinBase())
            return SolidityState.getFailState(); // TX is not coinbase

        // Check that the tx has correct data
        RewardInfo rewardInfo;
        try {
            rewardInfo = RewardInfo.parse(transactions.get(0).getData());
        } catch (IOException e) {
            // Incorrect data format
            return SolidityState.getFailState();
        }
        
        // NotNull checks
        if (rewardInfo.getPrevRewardHash() == null)
            return SolidityState.getFailState();     
            
        // Ensure dependency (prev reward hash) exists
        Sha256Hash prevRewardHash = Sha256Hash.wrap(rewardInfo.getPrevRewardHash());
        StoredBlock dependency = store.get(prevRewardHash);
        if (dependency == null)       
            return SolidityState.from(prevRewardHash); 
        
        // Ensure dependency (prev reward hash) is valid predecessor
        if (dependency.getHeader().getBlockType() != Type.BLOCKTYPE_INITIAL && dependency.getHeader().getBlockType() != Type.BLOCKTYPE_REWARD)
            return SolidityState.getFailState();         
        
        // Ensure fromHeight is starting from prevToHeight+1
        if (rewardInfo.getFromHeight() != store.getRewardToHeight(prevRewardHash) + 1)
            return SolidityState.getFailState(); // Invalid fromHeight
        
        // Ensure toHeights follow the rules
        if (rewardInfo.getToHeight() - rewardInfo.getFromHeight() != NetworkParameters.REWARD_HEIGHT_INTERVAL - 1)
            return SolidityState.getFailState(); // Invalid toHeight
        
        return SolidityState.getSuccessState();
    }

    private SolidityState checkTokenSolidity(Block block) {
        if (block.getTransactions().size() != 1) {
            logger.debug("Incorrect tx count! ");
            return SolidityState.getFailState();
        }

        if (!block.getTransactions().get(0).isCoinBase()) {
            logger.debug("TX is not coinbase! ");
            return SolidityState.getFailState(); 
        }
        
        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() == null) {
            logger.debug("No transaction data! ");
            return SolidityState.getFailState(); 
        }
        
        TokenInfo currentToken = null;
        try {
            currentToken = new TokenInfo().parse(tx.getData());
        } catch (IOException e) {
            logger.debug("Token data malformed! ", e);
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens() == null) {
            logger.debug("getTokens is null");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getMultiSignAddresses() == null) {
            logger.debug("getMultiSignAddresses is null");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getTokenid() == null) {
            logger.debug("getTokenid is null");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getPrevblockhash() == null) {
            logger.debug("getTokenid is null");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
            logger.debug("Not allowed");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getTokenindex() > NetworkParameters.TOKEN_MAX_ISSUANCE_NUMBER) {
            logger.debug("Too many token issuances");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getAmount() > Long.MAX_VALUE / NetworkParameters.TOKEN_MAX_ISSUANCE_NUMBER) {
            logger.debug("Too many tokens issued");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getDescription() != null && currentToken.getTokens().getDescription().length() > NetworkParameters.TOKEN_MAX_DESC_LENGTH) {
            logger.debug("Too long description");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getTokenname() != null && currentToken.getTokens().getTokenname().length() > NetworkParameters.TOKEN_MAX_NAME_LENGTH) {
            logger.debug("Too long name");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getUrl() != null && currentToken.getTokens().getUrl().length() > NetworkParameters.TOKEN_MAX_URL_LENGTH) {
            logger.debug("Too long url");
            return SolidityState.getFailState(); 
        }
        if (currentToken.getTokens().getSignnumber() < 0) {
            logger.debug("Invalid sign number");
            return SolidityState.getFailState(); 
        }

        // Check previous issuance hash exists or initial issuance
        if ((currentToken.getTokens().getPrevblockhash().equals("")
                && currentToken.getTokens().getTokenindex() != 0)
                || (!currentToken.getTokens().getPrevblockhash().equals("")
                        && currentToken.getTokens().getTokenindex() == 0)) {
            logger.debug("Must reference a previous block if not index 0");
            return SolidityState.getFailState(); 
        }

        // Must define enough permissioned addresses
        if (currentToken.getTokens().getSignnumber() > currentToken.getMultiSignAddresses().size()) {
            logger.debug("Cannot fulfill required sign number from multisign address list");
            return SolidityState.getFailState(); 
        }

        // Get permissioned addresses
        Token prevToken = null;
        List<MultiSignAddress> permissionedAddresses = null;
        // If not initial issuance, we check according to the previous token
        if (currentToken.getTokens().getTokenindex() != 0) {
            try {
                // Previous issuance must exist to check solidity
                prevToken = store.getToken(currentToken.getTokens().getPrevblockhash());
                if (prevToken == null) {
                    logger.debug("Previous token does not exist");
                    return SolidityState.from(Sha256Hash.wrap(currentToken.getTokens().getPrevblockhash())); 
                }

                // Compare members of previous and current issuance
                if (!currentToken.getTokens().getTokenid().equals(prevToken.getTokenid())) {
                    logger.debug("Wrong token ID");
                    return SolidityState.getFailState(); 
                }
                if (currentToken.getTokens().getTokenindex() != prevToken.getTokenindex() + 1) {
                    logger.debug("Wrong token index");
                    return SolidityState.getFailState(); 
                }
                if (!currentToken.getTokens().getTokenname().equals(prevToken.getTokenname())) {
                    logger.debug("Cannot change token name");
                    return SolidityState.getFailState(); 
                }
                if (currentToken.getTokens().getTokentype() != prevToken.getTokentype()) {
                    logger.debug("Cannot change token type");
                    return SolidityState.getFailState(); 
                }

                // Must allow more issuances
                if (prevToken.isTokenstop()) {
                    logger.debug("Previous token does not allow further issuance");
                    return SolidityState.getFailState(); 
                }

                // Get addresses allowed to reissue
                permissionedAddresses = store.getMultiSignAddressListByTokenidAndBlockHashHex(prevToken.getTokenid(),
                            prevToken.getPrevblockhash());
            } catch (BlockStoreException e) {
                // Cannot happen, previous token must exist
                e.printStackTrace();
            }
        } else {
            // First time issuances must sign for the token id
            permissionedAddresses = new ArrayList<>();
            MultiSignAddress firstTokenAddress = new MultiSignAddress(currentToken.getTokens().getTokenid(), "", currentToken.getTokens().getTokenid());
            permissionedAddresses.add(firstTokenAddress);
        }

        HashMap<String, MultiSignAddress> multiSignAddressRes = new HashMap<String, MultiSignAddress>();
        for (MultiSignAddress multiSignAddress : permissionedAddresses) {
            byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
            multiSignAddress.setAddress(ECKey.fromPublicOnly(pubKey).toAddress(networkParameters).toBase58());
            multiSignAddressRes.put(multiSignAddress.getAddress(), multiSignAddress);
        }

        int signatureCount = 0;
        // Get signatures from transaction
        String jsonStr = new String(tx.getDataSignature());
        MultiSignByRequest multiSignByRequest;
        try {
            multiSignByRequest = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
        } catch (IOException e) {
            logger.debug("Signature data malformed! ", e);
            return SolidityState.getFailState(); 
        }
        for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
            String address = multiSignBy.getAddress();
            if (!multiSignAddressRes.containsKey(address)) {
                logger.debug("multisignby address not in permissioned address list");
                return SolidityState.getFailState(); 
            }
        }

        // Count successful signature verifications
        for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
            byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
            byte[] data = tx.getHash().getBytes();
            byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());
            if (ECKey.verify(data, signature, pubKey))
                signatureCount++;
        }

        // Return whether sufficient signatures exist
        int requiredSignatureCount = prevToken == null ? 1 : prevToken.getSignnumber();
        if (signatureCount >= requiredSignatureCount)
            return SolidityState.getSuccessState();
        else
            return SolidityState.getFailState(); 
    }
}
