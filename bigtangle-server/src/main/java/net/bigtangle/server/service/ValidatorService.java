/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderMatchingInfo;
import net.bigtangle.core.OrderOpInfo;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.DifficultyConsensusInheritanceException;
import net.bigtangle.core.exception.VerificationException.GenesisBlockDisallowedException;
import net.bigtangle.core.exception.VerificationException.IncorrectTransactionCountException;
import net.bigtangle.core.exception.VerificationException.InsufficientSignaturesException;
import net.bigtangle.core.exception.VerificationException.InvalidDependencyException;
import net.bigtangle.core.exception.VerificationException.InvalidOrderException;
import net.bigtangle.core.exception.VerificationException.InvalidSignatureException;
import net.bigtangle.core.exception.VerificationException.InvalidTokenOutputException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionException;
import net.bigtangle.core.exception.VerificationException.MalformedTransactionDataException;
import net.bigtangle.core.exception.VerificationException.MissingDependencyException;
import net.bigtangle.core.exception.VerificationException.MissingSignatureException;
import net.bigtangle.core.exception.VerificationException.MissingTransactionDataException;
import net.bigtangle.core.exception.VerificationException.NotCoinbaseException;
import net.bigtangle.core.exception.VerificationException.PreviousTokenDisallowsException;
import net.bigtangle.core.exception.VerificationException.SigOpsException;
import net.bigtangle.core.exception.VerificationException.TimeReversionException;
import net.bigtangle.core.exception.VerificationException.TransactionOutputsDisallowedException;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.script.Script;
import net.bigtangle.script.Script.VerifyFlag;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.core.ConflictCandidate;
import net.bigtangle.server.service.SolidityState.State;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.ContextPropagatingThreadFactory;

@Service
public class ValidatorService {

    @Autowired
    protected FullPrunedBlockGraph blockGraph;
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

    public static class RewardBuilderResult {
        Eligibility eligibility;
        Transaction tx;
        long difficulty, perTxReward;

        public RewardBuilderResult(Eligibility eligibility, Transaction tx, long difficulty, long perTxReward) {
            this.eligibility = eligibility;
            this.tx = tx;
            this.difficulty = difficulty;
            this.perTxReward = perTxReward;
        }

        public Eligibility getEligibility() {
            return eligibility;
        }

        public Transaction getTx() {
            return tx;
        }

        public long getDifficulty() {
            return difficulty;
        }

        public long getPerTxReward() {
            return perTxReward;
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
     * NOTE: The reward block is assumed to having successfully gone through
     * checkRewardSolidity beforehand! For connecting purposes, checks if the
     * given rewardBlock is eligible NOW. In Spark, this would be calculated
     * delayed and free.
     * 
     * @param rewardBlock
     * @return eligibility of rewards + new perTxReward)
     * @throws BlockStoreException
     */
    public RewardBuilderResult checkRewardEligibility(Block rewardBlock) throws BlockStoreException {
        try {
            RewardInfo rewardInfo = RewardInfo.parse(rewardBlock.getTransactions().get(0).getData());
            return makeReward(rewardBlock.getPrevBlockHash(),
                    rewardBlock.getPrevBranchBlockHash(), rewardInfo.getPrevRewardHash());

        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * DOES NOT CHECK FOR SOLIDITY. Computes eligibility of rewards + data tx +
     * Pair.of(new difficulty + new perTxReward) here for new reward blocks.
     * This is a prototype implementation. In the actual Spark implementation,
     * the computational cost is not a problem, since it is instead
     * backpropagated and calculated for free with delay. For more info, see
     * notes.
     * 
     * @param prevTrunk
     *            a predecessor block in the db
     * @param prevBranch
     *            a predecessor block in the db
     * @param prevRewardHash
     *            the predecessor reward
     * @return eligibility of rewards + data tx + Pair.of(new difficulty + new
     *         perTxReward)
     */
    public RewardBuilderResult makeReward(Sha256Hash prevTrunk, Sha256Hash prevBranch,
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
        long toHeight = Math.max(prevTrunkBlock.getBlockEvaluation().getHeight(),
                prevBranchBlock.getBlockEvaluation().getHeight()) - NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE;

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
        RewardInfo rewardInfo = new RewardInfo(fromHeight, toHeight, prevRewardHash);
        tx.setData(rewardInfo.toByteArray());

        // New rewards
        long perTxReward = calculateNextTxReward(prevTrunkBlock, prevBranchBlock, prevRewardBlock,
                store.getRewardNextTxReward(prevRewardHash), totalRewardCount, difficulty, prevDifficulty);

        // Invalid if not fulfilling consensus rules
        if (Math.subtractExact(toHeight, fromHeight) < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL - 1)
            return new RewardBuilderResult(Eligibility.INVALID, tx, difficulty, perTxReward);

        // Ensure enough blocks are approved to be eligible
        if (totalRewardCount >= store.getCountMilestoneBlocksInInterval(fromHeight, toHeight)
                * NetworkParameters.REWARD_MIN_MILESTONE_PERCENTAGE / 100)
            return new RewardBuilderResult(Eligibility.ELIGIBLE, tx, difficulty, perTxReward);
        else
            return new RewardBuilderResult(Eligibility.INELIGIBLE, tx, difficulty, perTxReward);
    }

    /**
     * NOTE: The block is assumed to having successfully gone through
     * checkSolidity beforehand! For connecting purposes, checks if the given
     * matching block is eligible NOW. In Spark, this would be calculated
     * delayed and more or less free.
     * 
     * @param block
     * @return eligibility
     * @throws BlockStoreException
     */
    public Eligibility checkOrderMatchingEligibility(Block block) throws BlockStoreException {
        long toHeight = 0;
        long fromHeight = 0;
        try {
            OrderMatchingInfo info = OrderMatchingInfo.parse(block.getTransactions().get(0).getData());
            toHeight = info.getToHeight();
            fromHeight = info.getFromHeight();
        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // Count how many blocks from miners in the reward interval are approved
        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        BlockWrap prevTrunkBlock = store.getBlockWrap(block.getPrevBlockHash());
        BlockWrap prevBranchBlock = store.getBlockWrap(block.getPrevBranchBlockHash());
        blockQueue.add(prevTrunkBlock);
        blockQueue.add(prevBranchBlock);

        // Initialize
        BlockWrap currentBlock = null, approvedBlock = null;
        long currentHeight = Long.MAX_VALUE;
        long totalBlockCount = 0;

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {
            currentHeight = currentBlock.getBlockEvaluation().getHeight();

            // Stop criterion: Block height lower than approved interval height
            if (currentHeight < fromHeight)
                break;

            // If in relevant reward height interval, count it
            if (currentHeight <= toHeight) {
                totalBlockCount++;
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

        // Ensure enough blocks are approved to be eligible
        if (totalBlockCount >= store.getCountMilestoneBlocksInInterval(fromHeight, toHeight)
                * NetworkParameters.ORDER_MATCHING_MIN_MILESTONE_PERCENTAGE / 100)
            return Eligibility.ELIGIBLE;
        else
            return Eligibility.INELIGIBLE;
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
            long currPerTxReward, long totalRewardCount, long difficulty, long prevDifficulty) {
        // The previous time max is equal to the block time by consensus rules
        long currentTime = Math.max(prevTrunkBlock.getBlock().getTimeSeconds(),
                prevBranchBlock.getBlock().getTimeSeconds());
        long timespan = Math.max(1, (currentTime - prevRewardBlock.getBlock().getTimeSeconds()));
        long nextPerTxRewardUnnormalized = NetworkParameters.TARGET_YEARLY_MINING_PAYOUT * timespan / 31536000L
                / totalRewardCount;

        // Include result from difficulty adjustment to stay on target better
        BigInteger target = Utils.decodeCompactBits(difficulty);
        BigInteger prevTarget = Utils.decodeCompactBits(prevDifficulty);
        BigInteger nextPerTxRewardBigInteger = prevTarget.divide(target)
                .multiply(BigInteger.valueOf(nextPerTxRewardUnnormalized));
        long nextPerTxReward = nextPerTxRewardBigInteger.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();

        nextPerTxReward = Math.max(nextPerTxReward, currPerTxReward / 4);
        nextPerTxReward = Math.min(nextPerTxReward, currPerTxReward * 4);
        nextPerTxReward = Math.max(nextPerTxReward, 1);
        return nextPerTxReward;
    }

    /**
     * Checks if the given set is eligible to be walked to during local approval
     * tip selection given the current set of non-milestone blocks to include.
     * This is the case if the set is compatible with the current milestone. It
     * must disallow spent prev UTXOs / unconfirmed prev UTXOs
     * 
     * @param currentApprovedNonMilestoneBlocks
     *            The set of all currently approved non-milestone blocks.
     * @return true if the given set is eligible
     * @throws BlockStoreException
     */
    public boolean isEligibleForApprovalSelection(HashSet<BlockWrap> currentApprovedNonMilestoneBlocks)
            throws BlockStoreException {
        // Currently ineligible blocks are not ineligible. If we find one, we
        // must stop
        if (!findWhereCurrentlyIneligible(currentApprovedNonMilestoneBlocks).isEmpty())
            return false;

        // If there exists a new block whose dependency is already spent
        // (conflicting with milestone) or not confirmed yet (missing other
        // milestones), we fail to
        // approve this block since the current milestone takes precedence /
        // doesn't allow for the addition of these blocks
        if (findBlockWithSpentOrUnconfirmedInputs(currentApprovedNonMilestoneBlocks))
            return false;

        // If conflicts among the approved non-milestone blocks exist, cannot
        // approve
        HashSet<ConflictCandidate> conflictingOutPoints = new HashSet<>();
        findCandidateConflicts(currentApprovedNonMilestoneBlocks, conflictingOutPoints);
        if (!conflictingOutPoints.isEmpty())
            return false;

        // Otherwise, the new approved block set is compatible with current
        // milestone
        return true;
    }

    // Difference between isEligibleForApprovalSelection(HashSet<BlockWrap>)
    // here and
    // resolveAllConflicts(Set<BlockWrap>, boolean) below is that
    // the approval check disallows conflicts completely, while
    // the milestone resolveAllConflicts(Set<BlockWrap>, boolean)
    // takes candidate conflicts + conflicts with maintained milestone and
    // resolves them
    // Both disallow where usedoutput unconfirmed or ineligible for other
    // reasons.
    /**
     * Checks if the given block is eligible to be walked to during local
     * approval tip selection given the current set of non-milestone blocks to
     * include. This is the case if the block + the set is compatible with the
     * current milestone. It must disallow spent prev UTXOs / unconfirmed prev
     * UTXOs
     * 
     * @param block
     *            The block to check for eligibility.
     * @param currentApprovedNonMilestoneBlocks
     *            The set of all currently approved non-milestone blocks.
     * @return true if the given block is eligible to be walked to during
     *         approval tip selection.
     * @throws BlockStoreException
     */
    public boolean isEligibleForApprovalSelection(BlockWrap block, HashSet<BlockWrap> currentApprovedNonMilestoneBlocks)
            throws BlockStoreException {
        // Any milestone blocks are always compatible with the current milestone
        if (block.getBlockEvaluation().isMilestone())
            return true;

        // Get sets of all / all new non-milestone blocks when approving the
        // specified block in combination with the currently included blocks
        @SuppressWarnings("unchecked")
        HashSet<BlockWrap> allApprovedNonMilestoneBlocks = (HashSet<BlockWrap>) currentApprovedNonMilestoneBlocks
                .clone();
        blockService.addApprovedNonMilestoneBlocksTo(allApprovedNonMilestoneBlocks, block);

        // If this set of blocks is eligible, all is fine
        return isEligibleForApprovalSelection(allApprovedNonMilestoneBlocks);
    }

    private boolean isSpent(ConflictCandidate c) throws BlockStoreException {
        switch (c.getConflictPoint().getType()) {
        case TXOUT:
            return transactionService.getUTXOSpent(c.getConflictPoint().getConnectedOutpoint());
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();

            // Initial issuances are allowed iff no other same token issuances
            // are confirmed, i.e. spent iff any token confirmed
            if (connectedToken.getTokenindex() == 0)
                return store.getTokenAnyConfirmed(connectedToken.getTokenid(), connectedToken.getTokenindex());
            else
                return store.getTokenSpent(connectedToken.getPrevblockhash());
        case REWARDISSUANCE:
            return store.getRewardSpent(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
        case ORDERRECLAIM:
            OrderReclaimInfo connectedOrder = c.getConflictPoint().getConnectedOrder();
            return store.getOrderSpent(connectedOrder.getOrderBlockHash(), Sha256Hash.ZERO_HASH);
        case ORDERMATCH:
            return store.getOrderMatchingSpent(c.getConflictPoint().getConnectedOrderMatching().getPrevHash());
        default:
               throw new RuntimeException("No Implementation");
        }
    }

    private boolean isConfirmed(ConflictCandidate c) throws BlockStoreException {
        switch (c.getConflictPoint().getType()) {
        case TXOUT:
            return transactionService.getUTXOConfirmed(c.getConflictPoint().getConnectedOutpoint());
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();

            // Initial issuances are allowed (although they may be spent
            // already)
            if (connectedToken.getTokenindex() == 0)
                return true;
            else
                return store.getTokenConfirmed(connectedToken.getPrevblockhash());
        case REWARDISSUANCE:
            return store.getRewardConfirmed(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
        case ORDERRECLAIM:
            OrderReclaimInfo connectedOrder = c.getConflictPoint().getConnectedOrder();
            // To reclaim, not only must the order record be confirmed unspent,
            // but the non-collecting issuing block must be confirmed too
            return store.getOrderConfirmed(connectedOrder.getOrderBlockHash(), Sha256Hash.ZERO_HASH)
                    && store.getBlockEvaluation(connectedOrder.getNonConfirmingMatcherBlockHash()).isMilestone();
        case ORDERMATCH:
            return store.getOrderMatchingConfirmed(c.getConflictPoint().getConnectedOrderMatching().getPrevHash());
        default:
               throw new RuntimeException("No Implementation");
        }
    }

    private boolean findBlockWithSpentOrUnconfirmedInputs(HashSet<BlockWrap> newApprovedNonMilestoneBlocks) {
        // Get all conflict candidates in blocks
        Stream<ConflictCandidate> candidates = newApprovedNonMilestoneBlocks.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream());

        // Find conflict candidates whose used outputs are already spent or
        // still unconfirmed
        return candidates.filter((ConflictCandidate c) -> {
            try {
                return isSpent(c) || !isConfirmed(c);
            } catch (BlockStoreException e) {
                e.printStackTrace();
            }
            return false;
        }).findFirst().isPresent();
    }

    // disallow unconfirmed prev UTXOs and spent UTXOs if
    // unmaintained (unundoable) spend
    /**
     * Resolves all conflicts such that the milestone is compatible with all
     * blocks remaining in the set of blocks. If not allowing unconfirming
     * losing milestones, the milestones are always prioritized over
     * non-milestone candidates.
     * 
     * @param blocksToAdd
     *            the set of blocks to add to the current milestone
     * @param unconfirmLosingMilestones
     *            if false, the milestones are always prioritized over
     *            non-milestone candidates.
     * @throws BlockStoreException
     */
    public void resolveAllConflicts(Set<BlockWrap> blocksToAdd, boolean unconfirmLosingMilestones)
            throws BlockStoreException {
        // Remove currently ineligible blocks
        // i.e. for reward blocks: invalid never, ineligible overrule, eligible
        // ok
        // If ineligible, overrule by sufficient age and milestone rating range
        removeWhereCurrentlyIneligible(blocksToAdd);

        // Remove blocks and their approvers that have at least one input
        // with its corresponding output not confirmed yet
        removeWhereUsedOutputsUnconfirmed(blocksToAdd);

        // Resolve conflicting block combinations:
        // Disallow conflicts with unmaintained/pruned milestone blocks,
        // i.e. remove those whose input is already spent by such blocks
        resolvePrunedConflicts(blocksToAdd);

        // Then resolve conflicts between maintained milestone + new candidates
        // This may trigger reorgs, i.e. unconfirming current milestones!
        resolveUndoableConflicts(blocksToAdd, unconfirmLosingMilestones);

        // Remove blocks and their approvers that have at least one input
        // with its corresponding output not confirmed yet
        // This is needed since reorgs could have been triggered
        removeWhereUsedOutputsUnconfirmed(blocksToAdd);
    }

    /**
     * Remove blocks from blocksToAdd that are currently locally ineligible.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    private void removeWhereCurrentlyIneligible(Set<BlockWrap> blocksToAdd) {
        findWhereCurrentlyIneligible(blocksToAdd).forEach(b -> {
            try {
                blockService.removeBlockAndApproversFrom(blocksToAdd, b);
            } catch (BlockStoreException e) {
                // Cannot happen.
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Find blocks from blocksToAdd that are currently locally ineligible.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    private Set<BlockWrap> findWhereCurrentlyIneligible(Set<BlockWrap> blocksToAdd) {
        Set<BlockWrap> result = blocksToAdd.stream().filter(b -> b.getBlock().getBlockType() == Type.BLOCKTYPE_REWARD)
                .filter(b -> {
                    try {
                        switch (store.getRewardEligible(b.getBlock().getHash())) {
                        case ELIGIBLE:
                            // OK
                            return false;
                        case INELIGIBLE:
                            // Overruled?
                            return (!(b.getBlockEvaluation().getRating() > NetworkParameters.MILESTONE_UPPER_THRESHOLD
                                    && b.getBlockEvaluation().getInsertTime() < System.currentTimeMillis()
                                            - NetworkParameters.REWARD_OVERRULE_TIME_MS));
                        case INVALID:
                            // Cannot happen in non-Spark implementation.
                            return true;
                        case UNKNOWN:
                            // Cannot happen in non-Spark implementation.
                            return true;
                        default:
                               throw new RuntimeException("No Implementation");

                        }
                    } catch (BlockStoreException e) {
                        // Cannot happen.
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet());

        result.addAll(blocksToAdd.stream().filter(b -> b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_MATCHING)
                .filter(b -> {
                    try {
                        switch (store.getOrderMatchingEligible(b.getBlock().getHash())) {
                        case ELIGIBLE:
                            // OK
                            return false;
                        case INELIGIBLE:
                            // Overruled?
                            return (!(b.getBlockEvaluation().getRating() > NetworkParameters.MILESTONE_UPPER_THRESHOLD
                                    && b.getBlockEvaluation().getInsertTime() < System.currentTimeMillis()
                                            - NetworkParameters.REWARD_OVERRULE_TIME_MS));
                        case INVALID:
                            // Cannot happen in non-Spark implementation.
                            return true;
                        case UNKNOWN:
                            // Cannot happen in non-Spark implementation.
                            return true;
                        default:
                               throw new RuntimeException("No Implementation");

                        }
                    } catch (BlockStoreException e) {
                        // Cannot happen.
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet()));

        return result;
    }

    /**
     * Remove blocks from blocksToAdd that have at least one used output not
     * confirmed yet. They may however be spent already, since this leads to
     * conflicts.
     * 
     * @param blocksToAdd
     * @throws BlockStoreException
     */
    private void removeWhereUsedOutputsUnconfirmed(Set<BlockWrap> blocksToAdd) throws BlockStoreException {
        // Milestone blocks are always ok
        new HashSet<BlockWrap>(blocksToAdd).stream().filter(b -> !b.getBlockEvaluation().isMilestone())
                .flatMap(b -> b.toConflictCandidates().stream()).filter(c -> {
                    try {
                        return !isConfirmed(c); // Any candidates where used
                                                // dependencies unconfirmed
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
        HashSet<BlockWrap> losingBlocks = resolveConflicts(conflictingOutPoints, unconfirmLosingMilestones,
                blocksToAdd);

        // For milestone blocks that have been eliminated call disconnect
        // procedure
        HashSet<Sha256Hash> traversedUnconfirms = new HashSet<>();
        for (BlockWrap b : conflictingMilestoneBlocks.stream().filter(b -> losingBlocks.contains(b))
                .collect(Collectors.toList())) {
            if (!unconfirmLosingMilestones) {
                // Cannot happen since milestones are preferred during conflict
                // sorting
                logger.error("Cannot unconfirm milestone blocks when not allowing unconfirmation!");
                throw new RuntimeException("Cannot unconfirm milestone blocks when not allowing unconfirmation!");
            }

            blockGraph.unconfirm(b.getBlockEvaluation().getBlockHash(), traversedUnconfirms);
        }

        // For candidates that have been eliminated (conflictingOutPoints in
        // blocksToAdd \ winningBlocks) remove them from blocksToAdd
        for (BlockWrap b : losingBlocks) {
            blockService.removeBlockAndApproversFrom(blocksToAdd, b);
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
            boolean unconfirmLosingMilestones, Set<BlockWrap> blocksToAdd) throws BlockStoreException {
        // Initialize blocks that will/will not survive the conflict resolution
        HashSet<BlockWrap> initialBlocks = conflictingOutPoints.stream().map(c -> c.getBlock())
                .collect(Collectors.toCollection(HashSet::new));
        HashSet<BlockWrap> winningBlocks = new HashSet<>(blocksToAdd);
        for (BlockWrap winningBlock : initialBlocks) {
            blockService.addApprovedNonMilestoneBlocksTo(winningBlocks, winningBlock);
            blockService.addMilestoneApproversTo(winningBlocks, winningBlock);
        }
        HashSet<BlockWrap> losingBlocks = new HashSet<>(winningBlocks);

        // Sort conflicts internally by descending rating, then cumulative
        // weight. If not unconfirming, prefer milestones first.
        Comparator<ConflictCandidate> byDescendingRating = getConflictComparator(unconfirmLosingMilestones)
                .thenComparingLong((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getRating())
                .thenComparingLong((ConflictCandidate e) -> -e.getBlock().getBlockEvaluation().getInsertTime())
                .thenComparingLong((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getCumulativeWeight())
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
                        .thenComparingLong((TreeSet<ConflictCandidate> s) -> -s.first().getBlock().getBlockEvaluation()
                                .getInsertTime())
                        .thenComparingLong((TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation()
                                .getCumulativeWeight())
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
            // all other blocks in this conflict from winning blocks
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
    private void findCandidateConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints)
            throws BlockStoreException {
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
    private void findUndoableMilestoneConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints,
            Set<BlockWrap> conflictingMilestoneBlocks) throws BlockStoreException {
        // Find all conflict candidates in blocks to add
        List<ConflictCandidate> conflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.toList());

        // Find only those that are spent in milestone
        filterSpent(conflicts);

        // Add the conflicting candidates and milestone blocks to given set
        for (ConflictCandidate c : conflicts) {
            // Find the spending block we are competing with
            BlockWrap milestoneBlock = getSpendingBlock(c);

            // Only go through if the milestone block is maintained, i.e.
            // undoable
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
            final BlockEvaluation utxoSpender = transactionService
                    .getUTXOSpender(c.getConflictPoint().getConnectedOutpoint());
            if (utxoSpender == null)
                return null;
            return store.getBlockWrap(utxoSpender.getBlockHash());
        case TOKENISSUANCE:
            final Token connectedToken = c.getConflictPoint().getConnectedToken();

            // The spender is always the one block with the same tokenid and
            // index that is confirmed
            return store.getTokenIssuingConfirmedBlock(connectedToken.getTokenid(), connectedToken.getTokenindex());
        case REWARDISSUANCE:
            final Sha256Hash txRewardSpender = store
                    .getRewardSpender(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
            if (txRewardSpender == null)
                return null;
            return store.getBlockWrap(txRewardSpender);
        case ORDERRECLAIM:
            OrderReclaimInfo connectedOrder = c.getConflictPoint().getConnectedOrder();
            final Sha256Hash orderSpender = store.getOrderSpender(connectedOrder.getOrderBlockHash(),
                    Sha256Hash.ZERO_HASH);
            if (orderSpender == null)
                return null;
            return store.getBlockWrap(orderSpender);
        case ORDERMATCH:
            final Sha256Hash orderMatchSpender = store
                    .getOrderMatchingSpender(c.getConflictPoint().getConnectedOrderMatching().getPrevHash());
            if (orderMatchSpender == null)
                return null;
            return store.getBlockWrap(orderMatchSpender);
        default:
               throw new RuntimeException("No Implementation");
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
     * validity. Then checks if the block is valid based on its dependencies. If
     * SolidityState.getSuccessState() is returned, the block is valid. If
     * SolidityState.getFailState() is returned, the block is invalid.
     * Otherwise, appropriate solidity states are returned to imply missing
     * dependencies.
     */
    public SolidityState checkBlockSolidity(Block block, @Nullable BlockWrap storedPrev,
            @Nullable BlockWrap storedPrevBranch, boolean throwExceptions) {
        try {
            if (block.getHash() == Sha256Hash.ZERO_HASH) {
                if (throwExceptions)
                    throw new VerificationException("Lucky zeros not allowed");
                return SolidityState.getFailState();
            }

            if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
                if (throwExceptions)
                    throw new GenesisBlockDisallowedException();
                return SolidityState.getFailState();
            }

            // TODO different block type transactions should include their block
            // type in the transaction hash
            // for now, we must disallow someone burning other people's orders
            if (block.getBlockType() != Type.BLOCKTYPE_ORDER_OPEN) {
                for (Transaction tx : block.getTransactions())
                    try {
                        OrderOpenInfo.parse(tx.getData());
                        if (throwExceptions)
                            throw new MalformedTransactionDataException();
                        return SolidityState.getFailState();
                    } catch (Exception e) {
                        // Expected
                    }
            }

            // Check predecessor blocks exist
            if (storedPrev == null) {
                return SolidityState.from(block.getPrevBlockHash());
            }
            if (storedPrevBranch == null) {
                return SolidityState.from(block.getPrevBranchBlockHash());
            }

            // Check timestamp: enforce monotone time increase
            if (block.getTimeSeconds() < storedPrev.getBlock().getTimeSeconds()
                    || block.getTimeSeconds() < storedPrevBranch.getBlock().getTimeSeconds()) {
                if (throwExceptions)
                    throw new TimeReversionException();
                return SolidityState.getFailState();
            }

            // Check difficulty and latest consensus block is passed through
            // correctly
            if (block.getBlockType() != Block.Type.BLOCKTYPE_REWARD) {
                if (storedPrev.getBlock().getLastMiningRewardBlock() >= storedPrevBranch.getBlock()
                        .getLastMiningRewardBlock()) {
                    if (block.getLastMiningRewardBlock() != storedPrev.getBlock().getLastMiningRewardBlock()
                            || block.getDifficultyTarget() != storedPrev.getBlock().getDifficultyTarget()) {
                        if (throwExceptions)
                            throw new DifficultyConsensusInheritanceException();
                        return SolidityState.getFailState();
                    }
                } else {
                    if (block.getLastMiningRewardBlock() != storedPrevBranch.getBlock().getLastMiningRewardBlock()
                            || block.getDifficultyTarget() != storedPrevBranch.getBlock().getDifficultyTarget()) {
                        if (throwExceptions)
                            throw new DifficultyConsensusInheritanceException();
                        return SolidityState.getFailState();
                    }
                }
            } else {
                if (block.getLastMiningRewardBlock() != Math.max(storedPrev.getBlock().getLastMiningRewardBlock(),
                        storedPrevBranch.getBlock().getLastMiningRewardBlock()) + 1) {
                    if (throwExceptions)
                        throw new DifficultyConsensusInheritanceException();
                    return SolidityState.getFailState();
                }
            }

            long height = Math.max(storedPrev.getBlockEvaluation().getHeight(),
                    storedPrevBranch.getBlockEvaluation().getHeight()) + 1;

            // Check transactions are solid
            SolidityState transactionalSolidityState = checkTransactionalSolidity(block, height, throwExceptions);
            if (!(transactionalSolidityState.getState() == State.Success)) {
                return transactionalSolidityState;
            }

            // Check type-specific solidity
            SolidityState typeSpecificSolidityState = checkTypeSpecificSolidity(block, storedPrev, storedPrevBranch,
                    height, throwExceptions);
            if (!(typeSpecificSolidityState.getState() == State.Success)) {
                return typeSpecificSolidityState;
            }

            return SolidityState.getSuccessState();
        } catch (VerificationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unhandled exception in checkSolidity: ", e);
            if (throwExceptions)
                throw new VerificationException(e);
            return SolidityState.getFailState();
        }
    }

    private SolidityState checkTransactionalSolidity(Block block, long height, boolean throwExceptions)
            throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        // Coinbase allowance (already checked in Block.verifyTransactions
        // for (Transaction tx : transactions) {
        // if (tx.isCoinBase() && !block.allowCoinbaseTransaction()) {
        // if (throwExceptions)
        // throw new CoinbaseDisallowedException();
        // return SolidityState.getFailState();
        // }
        // }

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

            for (Transaction tx : block.getTransactions()) {
                sigOps += tx.getSigOpCount();
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
                            if (sigOps > NetworkParameters.MAX_BLOCK_SIGOPS)
                                throw new SigOpsException();
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
                    UTXO newOut = new UTXO(hash, out.getIndex(), out.getValue(), isCoinBase, script,
                            getScriptAddress(script), block.getHash(), out.getFromaddress(), tx.getMemo(),
                            Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, 0);

                    txOutsCreated.add(newOut);

                    // Filter zero UTXOs
                    // if (newOut.getValue().isZero()) {
                    // throw new InvalidTransactionException("Transaction output
                    // value is zero");
                    // }
                }
                if (!checkOutputSigns(valueOut))
                    throw new InvalidTransactionException("Transaction output value negative");
                if (isCoinBase) {
                    // coinbaseValue = valueOut;
                } else {
                    if (!checkInputOutput(valueIn, valueOut))
                        throw new InvalidTransactionException("Transaction input and output values do not match");
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
            if (throwExceptions)
                throw e;
            return SolidityState.getFailState();
        } catch (BlockStoreException e) {
            scriptVerificationExecutor.shutdownNow();
            logger.error("", e);
            if (throwExceptions)
                throw new VerificationException(e);
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

    private SolidityState checkTypeSpecificSolidity(Block block, BlockWrap storedPrev, BlockWrap storedPrevBranch,
            long height, boolean throwExceptions) throws BlockStoreException {
        switch (block.getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            break;
        case BLOCKTYPE_REWARD:
            // Check rewards are solid
            SolidityState rewardSolidityState = checkRewardSolidity(block, storedPrev, storedPrevBranch, height,
                    throwExceptions);
            if (!(rewardSolidityState.getState() == State.Success)) {
                return rewardSolidityState;
            }

            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // Check token issuances are solid
            SolidityState tokenSolidityState = checkTokenSolidity(block, height, throwExceptions);
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
        case BLOCKTYPE_ORDER_OPEN:
            SolidityState openSolidityState = checkOrderOpenSolidity(block, height, throwExceptions);
            if (!(openSolidityState.getState() == State.Success)) {
                return openSolidityState;
            }
            break;
        case BLOCKTYPE_ORDER_OP:
            SolidityState opSolidityState = checkOrderOpSolidity(block, height, throwExceptions);
            if (!(opSolidityState.getState() == State.Success)) {
                return opSolidityState;
            }
            break;
        case BLOCKTYPE_ORDER_RECLAIM:
            SolidityState recSolidityState = checkOrderReclaimSolidity(block, height, throwExceptions);
            if (!(recSolidityState.getState() == State.Success)) {
                return recSolidityState;
            }
            break;
        case BLOCKTYPE_ORDER_MATCHING:
            // Check rewards are solid
            SolidityState matchingSolidityState = checkOrderMatchingSolidity(block, storedPrev, storedPrevBranch,
                    height, throwExceptions);
            if (!(matchingSolidityState.getState() == State.Success)) {
                return matchingSolidityState;
            }
            break;
        default:
            throw new RuntimeException("No Implementation");
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkOrderReclaimSolidity(Block block, long height, boolean throwExceptions)
            throws BlockStoreException {
        if (block.getTransactions().size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        // No output creation
        if (!block.getTransactions().get(0).getOutputs().isEmpty()) {
            if (throwExceptions)
                throw new TransactionOutputsDisallowedException();
            return SolidityState.getFailState();
        }

        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        OrderReclaimInfo info = null;
        try {
            info = OrderReclaimInfo.parse(tx.getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (info.getOrderBlockHash() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target txhash");
            return SolidityState.getFailState();
        }
        if (info.getNonConfirmingMatcherBlockHash() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target matcher hash");
            return SolidityState.getFailState();
        }

        // Ensure the predecessing order block exists
        BlockWrap orderBlock = store.getBlockWrap(info.getOrderBlockHash());
        if (orderBlock == null) {
            return SolidityState.from(info.getOrderBlockHash());
        }

        // Ensure it is an order open block
        if (orderBlock.getBlock().getBlockType() != Type.BLOCKTYPE_ORDER_OPEN) {
            if (throwExceptions)
                throw new InvalidDependencyException("The given block does not open a reclaimable order.");
            return SolidityState.getFailState();
        }

        // Ensure the predecessing order matching block exists
        BlockWrap orderMatchingBlock = store.getBlockWrap(info.getNonConfirmingMatcherBlockHash());
        if (orderMatchingBlock == null) {
            return SolidityState.from(info.getNonConfirmingMatcherBlockHash());
        }

        // Ensure it is an order matching block
        if (orderMatchingBlock.getBlock().getBlockType() != Type.BLOCKTYPE_ORDER_MATCHING) {
            if (throwExceptions)
                throw new InvalidDependencyException("The given matching block is not the right type.");
            return SolidityState.getFailState();
        }

        // Ensure the predecessing order matching block approves sufficient
        // height, i.e. higher than the order opening
        if (store.getOrderMatchingToHeight(info.getNonConfirmingMatcherBlockHash())
                - NetworkParameters.ORDER_MATCHING_OVERLAP_SIZE < orderBlock.getBlockEvaluation().getHeight()) {
            if (throwExceptions)
                throw new InvalidDependencyException(
                        "The order matching block does not approve the given reclaim block height yet.");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkOrderOpenSolidity(Block block, long height, boolean throwExceptions)
            throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        if (transactions.size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        if (transactions.get(0).getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Check that the tx has correct data
        OrderOpenInfo orderInfo;
        try {
            orderInfo = OrderOpenInfo.parse(transactions.get(0).getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (orderInfo.getTargetTokenid() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target tokenid");
            return SolidityState.getFailState();
        }

        // Check bounds for target coin values
        // TODO after changing the values to 256 bit, remove the value
        // limitations!
        if (orderInfo.getTargetValue() < 1 || orderInfo.getTargetValue() > Integer.MAX_VALUE) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target value");
            return SolidityState.getFailState();
        }

        // Check that the tx inputs only burn one type of tokens
        Coin burnedCoins = countBurnedToken(block);

        if (burnedCoins == null || burnedCoins.getValue() == 0) {
            if (throwExceptions)
                throw new InvalidOrderException("No tokens were offered.");
            return SolidityState.getFailState();
        }

        if (burnedCoins.getValue() > Integer.MAX_VALUE) {
            if (throwExceptions)
                throw new InvalidOrderException("The order is too large.");
            return SolidityState.getFailState();
        }

        // Check that either the burnt token or the target token is BIG
        if (burnedCoins.getTokenHex().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                && orderInfo.getTargetTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                || !burnedCoins.getTokenHex().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                        && !orderInfo.getTargetTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
            if (throwExceptions)
                throw new InvalidOrderException("Invalid exchange combination. Ensure BIG is sold or bought.");
            return SolidityState.getFailState();
        }

        // Check that we have a correct price given in full BIGs
        if (burnedCoins.getTokenHex().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
            if (burnedCoins.getValue() % orderInfo.getTargetValue() != 0
                    || burnedCoins.getValue() / orderInfo.getTargetValue() <= 0) {
                if (throwExceptions)
                    throw new InvalidOrderException("The given order's price is not integer.");
                return SolidityState.getFailState();
            }
        } else {
            if (orderInfo.getTargetValue() % burnedCoins.getValue() != 0
                    || orderInfo.getTargetValue() / burnedCoins.getValue() <= 0) {
                if (throwExceptions)
                    throw new InvalidOrderException("The given order's price is not integer.");
                return SolidityState.getFailState();
            }
        }

        if (orderInfo.getValidToTime() > Math.addExact(orderInfo.getValidFromTime(),
                NetworkParameters.ORDER_TIMEOUT_MAX)) {
            if (throwExceptions)
                throw new InvalidOrderException("The given order's timeout is too long.");
            return SolidityState.getFailState();
        }

        if (!ECKey.fromPublicOnly(orderInfo.getBeneficiaryPubKey()).toAddress(params).toBase58()
                .equals(orderInfo.getBeneficiaryAddress())) {
            if (throwExceptions)
                throw new InvalidOrderException("The address does not match with the given pubkey.");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    /**
     * Counts the number tokens that are being burned in this block. If multiple
     * tokens exist in the transaction, throws InvalidOrderException.
     * 
     * @param block
     * @return
     * @throws BlockStoreException
     */
    public Coin countBurnedToken(Block block) throws BlockStoreException {
        Coin burnedCoins = null;
        for (final Transaction tx : block.getTransactions()) {
            for (int index = 0; index < tx.getInputs().size(); index++) {
                TransactionInput in = tx.getInputs().get(index);
                UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getHash(), in.getOutpoint().getIndex());
                if (prevOut == null) {
                    // Cannot happen due to solidity checks before
                    throw new RuntimeException("Block attempts to spend a not yet existent output!");
                }

                if (burnedCoins == null)
                    burnedCoins = Coin.valueOf(0, Utils.HEX.encode(prevOut.getValue().getTokenid()));

                try {
                    burnedCoins = burnedCoins.add(prevOut.getValue());
                } catch (IllegalArgumentException e) {
                    throw new InvalidOrderException(e.getMessage());
                }
            }

            for (int index = 0; index < tx.getOutputs().size(); index++) {
                TransactionOutput out = tx.getOutputs().get(index);

                try {
                    burnedCoins = burnedCoins.subtract(out.getValue());
                } catch (IllegalArgumentException e) {
                    throw new InvalidOrderException(e.getMessage());
                }
            }
        }
        return burnedCoins;
    }

    private SolidityState checkOrderOpSolidity(Block block, long height, boolean throwExceptions)
            throws BlockStoreException {
        if (block.getTransactions().size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        // No output creation
        if (!block.getTransactions().get(0).getOutputs().isEmpty()) {
            if (throwExceptions)
                throw new TransactionOutputsDisallowedException();
            return SolidityState.getFailState();
        }

        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        OrderOpInfo info = null;
        try {
            info = OrderOpInfo.parse(tx.getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (info.getInitialBlockHash() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid target txhash");
            return SolidityState.getFailState();
        }

        // Ensure the predecessing order exists
        OrderRecord order = store.getOrder(info.getInitialBlockHash(), Sha256Hash.ZERO_HASH);
        if (order == null) {
            return SolidityState.from(info.getInitialBlockHash());
        }

        byte[] pubKey = order.getBeneficiaryPubKey();
        byte[] data = tx.getHash().getBytes();
        byte[] signature = block.getTransactions().get(0).getDataSignature();

        // If signature of beneficiary is missing, fail
        if (!ECKey.verify(data, signature, pubKey)) {
            if (throwExceptions)
                throw new InsufficientSignaturesException();
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkOrderMatchingSolidity(Block block, BlockWrap storedPrev, BlockWrap storedPrevBranch,
            long height, boolean throwExceptions) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        if (transactions.size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        // No output creation
        if (!transactions.get(0).getOutputs().isEmpty()) {
            if (throwExceptions)
                throw new TransactionOutputsDisallowedException();
            return SolidityState.getFailState();
        }

        if (transactions.get(0).getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Check that the tx has correct data
        OrderMatchingInfo info;
        try {
            info = OrderMatchingInfo.parse(transactions.get(0).getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (info.getPrevHash() == null) {
            if (throwExceptions)
                throw new MissingDependencyException();
            return SolidityState.getFailState();
        }

        // Ensure dependency (prev hash) exists
        Sha256Hash prevRewardHash = info.getPrevHash();
        BlockWrap dependency = store.getBlockWrap(prevRewardHash);
        if (dependency == null)
            return SolidityState.from(prevRewardHash);

        // Ensure dependency (prev hash) is valid predecessor
        if (dependency.getBlock().getBlockType() != Type.BLOCKTYPE_INITIAL
                && dependency.getBlock().getBlockType() != Type.BLOCKTYPE_ORDER_MATCHING) {
            if (throwExceptions)
                throw new InvalidDependencyException("Predecessor is not reward or genesis");
            return SolidityState.getFailState();
        }

        // Ensure fromHeight is starting from prevToHeight - overlap range
        if (info.getFromHeight() != store.getOrderMatchingToHeight(prevRewardHash)
                - NetworkParameters.ORDER_MATCHING_OVERLAP_SIZE) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid fromHeight");
            return SolidityState.getFailState();
        }

        // Ensure toHeight follows the rules
        if (info.getToHeight() != height) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid toHeight");
            return SolidityState.getFailState();
        }

        // Ensure heights follow the rules
        if (Math.subtractExact(info.getToHeight(),
                store.getOrderMatchingToHeight(prevRewardHash)) < NetworkParameters.ORDER_MATCHING_MIN_HEIGHT_INTERVAL
                        - 1) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid heights");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkRewardSolidity(Block block, BlockWrap storedPrev, BlockWrap storedPrevBranch,
            long height, boolean throwExceptions) throws BlockStoreException {
        List<Transaction> transactions = block.getTransactions();

        if (transactions.size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        // No output creation
        if (!transactions.get(0).getOutputs().isEmpty()) {
            if (throwExceptions)
                throw new TransactionOutputsDisallowedException();
            return SolidityState.getFailState();
        }

        if (transactions.get(0).getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Check that the tx has correct data
        RewardInfo rewardInfo;
        try {
            rewardInfo = RewardInfo.parse(transactions.get(0).getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // NotNull checks
        if (rewardInfo.getPrevRewardHash() == null) {
            if (throwExceptions)
                throw new MissingDependencyException();
            return SolidityState.getFailState();
        }

        // Ensure dependency (prev reward hash) exists
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        BlockWrap dependency = store.getBlockWrap(prevRewardHash);
        if (dependency == null)
            return SolidityState.from(prevRewardHash);

        // Ensure dependency (prev reward hash) is valid predecessor
        if (dependency.getBlock().getBlockType() != Type.BLOCKTYPE_INITIAL
                && dependency.getBlock().getBlockType() != Type.BLOCKTYPE_REWARD) {
            if (throwExceptions)
                throw new InvalidDependencyException("Predecessor is not reward or genesis");
            return SolidityState.getFailState();
        }

        // Ensure fromHeight is starting from prevToHeight+1
        if (rewardInfo.getFromHeight() != store.getRewardToHeight(prevRewardHash) + 1) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid fromHeight");
            return SolidityState.getFailState();
        }

        // Ensure toHeights follow the rules
        if (rewardInfo.getToHeight() != Math.max(storedPrev.getBlockEvaluation().getHeight(),
                storedPrevBranch.getBlockEvaluation().getHeight()) - NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid toHeight");
            return SolidityState.getFailState();
        }

        // Ensure heights follow the rules
        if (Math.subtractExact(rewardInfo.getToHeight(),
                rewardInfo.getFromHeight()) < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL - 1) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid heights");
            return SolidityState.getFailState();
        }

        // Ensure we are sufficiently far away from the rewarded interval
        if (height - rewardInfo.getToHeight() < NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too close to the rewarded interval");
            return SolidityState.getFailState();
        }

        // Ensure the timestamps are correct
        if (block.getTimeSeconds() != Math.max(storedPrev.getBlock().getTimeSeconds(),
                storedPrevBranch.getBlock().getTimeSeconds())) {
            if (throwExceptions)
                throw new TimeReversionException();
            return SolidityState.getFailState();
        }

        // Ensure the new difficulty is set correctly
        RewardBuilderResult result = makeReward(block.getPrevBlockHash(),
                block.getPrevBranchBlockHash(), rewardInfo.getPrevRewardHash());
        if (block.getDifficultyTarget() != result.getDifficulty()) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Incorrect difficulty target");
            return SolidityState.getFailState();
        }

        // Fallback: Ensure everything is generated canonically
        if (!block.getTransactions().get(0).equals(result.getTx())) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Incorrect Transaction");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }

    private SolidityState checkTokenSolidity(Block block, long height, boolean throwExceptions) {
        if (block.getTransactions().size() != 1) {
            if (throwExceptions)
                throw new IncorrectTransactionCountException();
            return SolidityState.getFailState();
        }

        if (!block.getTransactions().get(0).isCoinBase()) {
            if (throwExceptions)
                throw new NotCoinbaseException();
            return SolidityState.getFailState();
        }

        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        TokenInfo currentToken = null;
        try {
            currentToken = TokenInfo.parse(tx.getData());
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        if (checkTokenField(throwExceptions, currentToken) == SolidityState.getFailState())
            return SolidityState.getFailState();
        // Check field correctness: amount
        if (currentToken.getToken().getAmount() != block.getTransactions().get(0).getOutputSum()) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Incorrect amount field");
            return SolidityState.getFailState();
        }

        // Check all token issuance transaction outputs are actually of the
        // given token
        for (Transaction tx1 : block.getTransactions()) {
            for (TransactionOutput out : tx1.getOutputs()) {
                if (!out.getValue().getTokenHex().equals(currentToken.getToken().getTokenid())) {
                    if (throwExceptions)
                        throw new InvalidTokenOutputException();
                    return SolidityState.getFailState();
                }
            }
        }

        // Check previous issuance hash exists or initial issuance
        if ((currentToken.getToken().getPrevblockhash().equals("") && currentToken.getToken().getTokenindex() != 0)
                || (!currentToken.getToken().getPrevblockhash().equals("")
                        && currentToken.getToken().getTokenindex() == 0)) {
            if (throwExceptions)
                throw new MissingDependencyException();
            return SolidityState.getFailState();
        }

        // Must define enough permissioned addresses
        if (currentToken.getToken().getSignnumber() > currentToken.getMultiSignAddresses().size()) {
            if (throwExceptions)
                throw new InvalidTransactionDataException(
                        "Cannot fulfill required sign number from multisign address list");
            return SolidityState.getFailState();
        }

        // Get permissioned addresses
        Token prevToken = null;
        List<MultiSignAddress> permissionedAddresses = null;
        // If not initial issuance, we check according to the previous token
        if (currentToken.getToken().getTokenindex() != 0) {
            try {
                // Previous issuance must exist to check solidity
                prevToken = store.getToken(currentToken.getToken().getPrevblockhash());
                if (prevToken == null) {
                    if (throwExceptions)
                        return SolidityState.from(Sha256Hash.wrap(currentToken.getToken().getPrevblockhash()));
                    return SolidityState.from(Sha256Hash.wrap(currentToken.getToken().getPrevblockhash()));
                }

                // Compare members of previous and current issuance
                if (!currentToken.getToken().getTokenid().equals(prevToken.getTokenid())) {
                    if (throwExceptions)
                        throw new InvalidDependencyException("Wrong token ID");
                    return SolidityState.getFailState();
                }
                if (currentToken.getToken().getTokenindex() != prevToken.getTokenindex() + 1) {
                    if (throwExceptions)
                        throw new InvalidDependencyException("Wrong token index");
                    return SolidityState.getFailState();
                }
                if (!currentToken.getToken().getTokenname().equals(prevToken.getTokenname())) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Cannot change token name");
                    return SolidityState.getFailState();
                }
                if (currentToken.getToken().getTokentype() != prevToken.getTokentype()) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Cannot change token type");
                    return SolidityState.getFailState();
                }

                // Must allow more issuances
                if (prevToken.isTokenstop()) {
                    if (throwExceptions)
                        throw new PreviousTokenDisallowsException("Previous token does not allow further issuance");
                    return SolidityState.getFailState();
                }

                // Get addresses allowed to reissue
                permissionedAddresses = store.getMultiSignAddressListByTokenidAndBlockHashHex(prevToken.getTokenid(),
                        prevToken.getBlockhash());
            } catch (BlockStoreException e) {
                // Cannot happen, previous token must exist
                e.printStackTrace();
            }
        } else {
            permissionedAddresses = currentToken.getMultiSignAddresses();

            // First time issuances must sign for the token id
            MultiSignAddress firstTokenAddress = new MultiSignAddress(currentToken.getToken().getTokenid(), "",
                    currentToken.getToken().getTokenid());
            permissionedAddresses.add(firstTokenAddress);
        }

        // Get permissioned pubkeys wrapped to check for bytearray equality
        Set<ByteBuffer> permissionedPubKeys = new HashSet<ByteBuffer>();
        for (MultiSignAddress multiSignAddress : permissionedAddresses) {
            byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
            permissionedPubKeys.add(ByteBuffer.wrap(pubKey));
        }

        int signatureCount = 0;
        // Ensure signatures exist
        if (tx.getDataSignature() == null) {
            if (throwExceptions)
                throw new MissingTransactionDataException();
            return SolidityState.getFailState();
        }

        // Get signatures from transaction
        String jsonStr = new String(tx.getDataSignature());
        MultiSignByRequest txSignatures;
        try {
            txSignatures = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
        } catch (IOException e) {
            if (throwExceptions)
                throw new MalformedTransactionDataException();
            return SolidityState.getFailState();
        }

        // Ensure all multiSignBys pubkeys are from the permissioned list
        for (MultiSignBy multiSignBy : txSignatures.getMultiSignBies()) {
            ByteBuffer pubKey = ByteBuffer.wrap(Utils.HEX.decode(multiSignBy.getPublickey()));
            if (!permissionedPubKeys.contains(pubKey)) {
                if (throwExceptions)
                    throw new InvalidSignatureException();
                return SolidityState.getFailState();
            }

            // Cannot use same address multiple times
            permissionedPubKeys.remove(pubKey);
        }

        // For first issuance, ensure the tokenid pubkey signature exists to
        // prevent others from generating conflicts
        if (currentToken.getToken().getTokenindex() == 0) {
            if (permissionedPubKeys.contains(ByteBuffer.wrap(Utils.HEX.decode(currentToken.getToken().getTokenid())))) {
                if (throwExceptions)
                    throw new MissingSignatureException();
                return SolidityState.getFailState();
            }
        }

        // Verify signatures
        for (MultiSignBy multiSignBy : txSignatures.getMultiSignBies()) {
            byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
            byte[] data = tx.getHash().getBytes();
            byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());

            if (ECKey.verify(data, signature, pubKey)) {
                signatureCount++;
            } else {
                if (throwExceptions)
                    throw new InvalidSignatureException();
                return SolidityState.getFailState();
            }
        }

        // Return whether sufficient signatures exist
        int requiredSignatureCount = prevToken == null ? 1 : prevToken.getSignnumber();
        if (signatureCount >= requiredSignatureCount)
            return SolidityState.getSuccessState();

        if (throwExceptions)
            throw new InsufficientSignaturesException();
        return SolidityState.getFailState();
    }

    private SolidityState checkTokenField(boolean throwExceptions, TokenInfo currentToken) {
        if (currentToken.getToken() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("getToken is null");
            return SolidityState.getFailState();
        }
        if (currentToken.getMultiSignAddresses() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("getMultiSignAddresses is null");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getTokenid() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("getTokenid is null");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getPrevblockhash() == null) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("getPrevblockhash is null");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Not allowed");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getTokenindex() > NetworkParameters.TOKEN_MAX_ISSUANCE_NUMBER) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too many token issuances");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getAmount() > Long.MAX_VALUE / NetworkParameters.TOKEN_MAX_ISSUANCE_NUMBER) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too many tokens issued");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getDescription() != null
                && currentToken.getToken().getDescription().length() > NetworkParameters.TOKEN_MAX_DESC_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long description");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getTokenname() != null
                && currentToken.getToken().getTokenname().length() > NetworkParameters.TOKEN_MAX_NAME_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long name");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getUrl() != null
                && currentToken.getToken().getUrl().length() > NetworkParameters.TOKEN_MAX_URL_LENGTH) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Too long url");
            return SolidityState.getFailState();
        }
        if (currentToken.getToken().getSignnumber() < 0) {
            if (throwExceptions)
                throw new InvalidTransactionDataException("Invalid sign number");
            return SolidityState.getFailState();
        }

        return SolidityState.getSuccessState();
    }
}
