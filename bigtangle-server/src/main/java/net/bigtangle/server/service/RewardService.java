/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.core.ConflictCandidate;
import net.bigtangle.server.service.SolidityState.State;
import net.bigtangle.server.service.ValidatorService.RewardBuilderResult;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Threading;

/**
 * <p>
 * A RewardService provides service for create and validate the reward chain.
 * </p>
 */
@Service
public class RewardService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected FullPrunedBlockGraph blockGraph;
    @Autowired
    private BlockService blockService;
    @Autowired
    protected TipsService tipService;
    @Autowired
    protected ServerConfiguration serverConfiguration;
    @Autowired
    private ValidatorService validatorService;
    @Autowired
    protected NetworkParameters networkParameters;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    protected final ReentrantLock lock = Threading.lock("RewardService");

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            log.debug(this.getClass().getName() + "  RewardService running. Returning...");
            return;
        }

        try {
            log.info("performRewardVoting  started");
            Stopwatch watch = Stopwatch.createStarted();
            performRewardVoting();
            log.info("performRewardVoting time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        } catch (VerificationException e1) {
            // logger.debug(" Infeasible performRewardVoting: ", e1);
        } catch (Exception e) {
            log.error("performRewardVoting ", e);
        } finally {
            lock.unlock();
        }

    }

    /**
     * Runs the reward making logic
     * 
     * @return the new block or block voted on
     * @throws Exception
     */
    public void performRewardVoting() {
        // Make new one
        try {
            Block reward = createAndAddMiningRewardBlock();
            if (reward != null) {
                log.info(" reward block is created: " + reward);
            }
        } catch (InvalidTransactionDataException e) {
            // This is not a problem
        } catch (Exception e) {
            // This is not a problem
            log.debug("", e);
        }
    }

    public Block createAndAddMiningRewardBlock() throws Exception {
        log.info("createAndAddMiningRewardBlock  started");

        Sha256Hash prevRewardHash = store.getMaxConfirmedReward().getBlockHash();
        return createAndAddMiningRewardBlock(prevRewardHash);

    }

    public Block createAndAddMiningRewardBlock(Sha256Hash prevRewardHash) throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedRewardBlockPair(prevRewardHash);
        return createAndAddMiningRewardBlock(prevRewardHash, tipsToApprove.getLeft(), tipsToApprove.getRight());
    }

    public Block createAndAddMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws Exception {
        return createAndAddMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, false);
    }

    public Block createAndAddMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws Exception {

        Block block = createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, override);
        if (block != null)
            blockService.saveBlock(block);
        return block;
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
        return createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, false);
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
        RewardBuilderResult result = null;
        try {
            result = makeReward(prevTrunk, prevBranch, prevRewardHash);
        } catch (java.lang.ArithmeticException e) {
            return null;
        }
        Block r1 = blockService.getBlock(prevTrunk);
        Block r2 = blockService.getBlock(prevBranch);

        Block block = Block.createBlock(networkParameters, r1, r2);

        block.setBlockType(Block.Type.BLOCKTYPE_REWARD);
        block.setHeight(Math.max(r1.getHeight(), r2.getHeight()) + 1);
        block.setMinerAddress(
                Address.fromBase58(networkParameters, serverConfiguration.getMineraddress()).getHash160());
        // Make the new block
        block.addTransaction(result.getTx());
        block.setDifficultyTarget(result.getDifficulty());

        // Enforce timestamp equal to previous max for reward blocktypes
        block.setTime(Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
        BigInteger chainTarget = Utils.decodeCompactBits(store.getRewardDifficulty(prevRewardHash));
        if (Utils.decodeCompactBits(result.getDifficulty()).compareTo(chainTarget) < 0) {
            chainTarget = Utils.decodeCompactBits(result.getDifficulty());
        }
        blockService.adjustHeightRequiredBlocks(block);
        final BigInteger chainTargetFinal = chainTarget;

        final Duration timeout = Duration.ofSeconds(30);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {
                block.solve(chainTargetFinal);
                return "";
            }
        });

        try {
            handler.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            handler.cancel(true);
            return null;
        } finally {
            executor.shutdownNow();
        }

        return block;
    }

    public GetTXRewardResponse getMaxConfirmedReward(Map<String, Object> request) throws BlockStoreException {

        return GetTXRewardResponse.create(store.getMaxConfirmedReward());

    }

    public GetTXRewardListResponse getAllConfirmedReward(Map<String, Object> request) throws BlockStoreException {

        return GetTXRewardListResponse.create(store.getAllConfirmedReward());

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
    public RewardBuilderResult makeReward(Sha256Hash prevTrunk, Sha256Hash prevBranch, Sha256Hash prevRewardHash)
            throws BlockStoreException {

        // Read previous reward block's data
        BlockWrap prevRewardBlock = store.getBlockWrap(prevRewardHash);
        long currChainLength = store.getRewardChainLength(prevRewardHash) + 1;
        long prevDifficulty = prevRewardBlock.getBlock().getDifficultyTarget();

        // Build transaction for block
        Transaction tx = new Transaction(networkParameters);

        Set<Sha256Hash> blocks = new HashSet<Sha256Hash>();
        long cutoffheight = blockService.getCutoffHeight(prevRewardHash);

        // Count how many blocks from miners in the reward interval are approved
        BlockWrap prevTrunkBlock = store.getBlockWrap(prevTrunk);
        BlockWrap prevBranchBlock = store.getBlockWrap(prevBranch);
        blockService.addRequiredNonContainedBlockHashesTo(blocks, prevBranchBlock, cutoffheight);
        blockService.addRequiredNonContainedBlockHashesTo(blocks, prevTrunkBlock, cutoffheight);
        long totalRewardCount = blocks.size() + 1;

        // New difficulty
        long difficulty = calculateNextBlockDifficulty(prevDifficulty, prevTrunkBlock, prevBranchBlock,
                prevRewardBlock.getBlock(), totalRewardCount);

        // Build the type-specific tx data
        RewardInfo rewardInfo = new RewardInfo(prevRewardHash, difficulty, blocks, currChainLength);
        tx.setData(rewardInfo.toByteArray());
        tx.setMemo(new MemoInfo("RewardInfo:" + rewardInfo));
        return new RewardBuilderResult(tx, difficulty);
    }

    private long calculateNextBlockDifficulty(long prevDifficulty, BlockWrap prevTrunkBlock, BlockWrap prevBranchBlock,
            Block prevRewardBlock, long totalRewardCount) {
        // The following equals current time by consensus rules
        long currentTime = Math.max(prevTrunkBlock.getBlock().getTimeSeconds(),
                prevBranchBlock.getBlock().getTimeSeconds());
        long timespan = Math.max(1, (currentTime - prevRewardBlock.getTimeSeconds()));

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

        if (newTarget.compareTo(networkParameters.getMaxTarget()) > 0) {
            // TODO logger.info("Difficulty hit proof of work limit: {}",
            // newTarget.toString(16));
            newTarget = networkParameters.getMaxTarget();
        }

        return Utils.encodeCompactBits(newTarget);
    }

    public boolean runConsensusLogic(Block newestBlock) throws BlockStoreException {

        RewardInfo rewardInfo = RewardInfo.parseChecked(newestBlock.getTransactions().get(0).getData());
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        long currChainLength = store.getRewardChainLength(prevRewardHash) + 1;

        // chain head
        TXReward chainHead = store.getMaxConfirmedReward();
        long maxChainLength = chainHead.getChainLength();

        if (maxChainLength < currChainLength) {

            // Find block to which to rollback (if at all) and all new chain
            // blocks
            List<BlockWrap> newMilestoneBlocks = new ArrayList<>();
            newMilestoneBlocks.add(store.getBlockWrap(newestBlock.getHash()));
            BlockWrap splitPoint = null;
            Sha256Hash prevHash = prevRewardHash;
            for (int i = 0; i <= currChainLength; i++) {
                splitPoint = store.getBlockWrap(prevHash);
                prevHash = store.getRewardPrevBlockHash(prevHash);

                if (store.getRewardConfirmed(splitPoint.getBlockHash()))
                    break;

                newMilestoneBlocks.add(splitPoint);
            }
            Collections.reverse(newMilestoneBlocks);

            // Unconfirm anything not confirmed by milestone
            List<Sha256Hash> wipeBlocks = store.getWhereConfirmedNotMilestone();
            HashSet<Sha256Hash> traversedBlockHashes = new HashSet<>();
            for (Sha256Hash wipeBlock : wipeBlocks)
                blockGraph.unconfirm(wipeBlock, traversedBlockHashes);

            // Rollback to split point
            Sha256Hash maxConfirmedRewardBlockHash;
            while (!(maxConfirmedRewardBlockHash = store.getMaxConfirmedReward().getBlockHash())
                    .equals(splitPoint.getBlockHash())) {

                // Sanity check:
                if (!maxConfirmedRewardBlockHash.equals(networkParameters.getGenesisBlock().getHash())) {

                    // Unset the milestone of this one (where milestone =
                    // maxConfRewardblock.chainLength)
                    long milestoneNumber = store.getRewardChainLength(maxConfirmedRewardBlockHash);
                    List<BlockWrap> blocksInMilestoneInterval = store.getBlocksInMilestoneInterval(milestoneNumber,
                            milestoneNumber);

                    // Unconfirm anything not confirmed by milestone
                    traversedBlockHashes = new HashSet<>();
                    for (BlockWrap wipeBlock : blocksInMilestoneInterval)
                        blockGraph.unconfirm(wipeBlock.getBlockHash(), traversedBlockHashes);
                }
            }

            return buildChainForwards(chainHead, newMilestoneBlocks);
        }

        return true;
    }

    private boolean buildChainForwards(TXReward chainHead, List<BlockWrap> newMilestoneBlocks)
            throws BlockStoreException {
        // Build milestone forwards.
        for (BlockWrap newMilestoneBlock : newMilestoneBlocks) {

            buildRewardChain(newMilestoneBlock.getBlock());
        }
        return true;
    }

    public void buildRewardChain(Block newMilestoneBlock) throws BlockStoreException {

        RewardInfo currRewardInfo = RewardInfo.parseChecked(newMilestoneBlock.getTransactions().get(0).getData());
        Set<Sha256Hash> milestoneSet = currRewardInfo.getBlocks();
        long cutoffHeight = blockService.getCutoffHeight(currRewardInfo.getPrevRewardHash());
        // Check all referenced blocks have their requirements
        checkReferencedBlockRequirements(newMilestoneBlock, cutoffHeight);

        // Ensure the new difficulty and tx is set correctly
        checkGeneratedReward(newMilestoneBlock);

        // Sanity check: At this point, predecessors cannot be missing
        SolidityState solidityState = validatorService.checkSolidity(newMilestoneBlock, false);

        // Unconfirm anything not confirmed by milestone
        List<Sha256Hash> wipeBlocks = store.getWhereConfirmedNotMilestone();
        HashSet<Sha256Hash> traversedBlockHashes = new HashSet<>();
        for (Sha256Hash wipeBlock : wipeBlocks)
            blockGraph.unconfirm(wipeBlock, traversedBlockHashes);
        
        // Find conflicts in the dependency set
        HashSet<BlockWrap> allApprovedNewBlocks = new HashSet<>();
        for (Sha256Hash hash : milestoneSet)
            allApprovedNewBlocks.add(store.getBlockWrap(hash));
         allApprovedNewBlocks.add(store.getBlockWrap(newMilestoneBlock.getHash()));

        // If anything is already spent, no-go
        boolean anySpentInputs = hasSpentInputs(allApprovedNewBlocks);
          Optional<ConflictCandidate> spentInput =
         findFirstSpentInput(allApprovedNewBlocks);

          if (anySpentInputs ) {
              solidityState = SolidityState.getFailState();
              throw new VerificationException("there are hasSpentInputs in allApprovedNewBlocks ");
          }
        // If any conflicts exist between the current set of
        // blocks, no-go
        boolean anyCandidateConflicts = allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream()
                .anyMatch(l -> l.size() > 1);

        // Did we fail? Then we stop now and rerun consensus
        // logic on the new longest chain.
        if (  anyCandidateConflicts) {
            solidityState = SolidityState.getFailState();
            throw new VerificationException("conflicts exist between the current set of ");
        }

        // Otherwise, all predecessors exist and were at least
        // solid > 0, so we should be able to confirm everything
        blockGraph.solidifyBlock(newMilestoneBlock, solidityState, true);
        HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
        for (BlockWrap approvedBlock : allApprovedNewBlocks)
            blockGraph.confirm(approvedBlock.getBlockEvaluation().getBlockHash(), traversedConfirms, cutoffHeight);

        // Set the milestone on all confirmed non-milestone
        // blocks
        long milestoneNumber = store.getRewardChainLength(newMilestoneBlock.getHash());
        store.updateAllConfirmedToMilestone(milestoneNumber);

    }

    /**
     * Called as part of connecting a block when the new block results in a
     * different chain having higher total work.
     *  
     */
    public void handleNewBestChain(Block newChainHead)
            throws BlockStoreException, VerificationException {
      //  checkState(lock.isHeldByCurrentThread());
        // This chain has overtaken the one we currently believe is best.
        // Reorganize is required.
        //
        // Firstly, calculate the block at which the chain diverged. We only
        // need to examine the
        // chain from beyond this block to find differences.
        Block head = getChainHead();
        final Block splitPoint = findSplit(newChainHead, head);
        if(splitPoint==null) {
            log.info(" splitPoint is null, the chain ist not complete: ", newChainHead);
            return;
        }
        
        log.info("Re-organize after split at height {}", splitPoint.getHeight());
        log.info("Old chain head: {}", head);
        log.info("New chain head: {}", newChainHead);
        log.info("Split at block: {}", splitPoint);
        // Then build a list of all blocks in the old part of the chain and the
        // new part.
        final LinkedList<Block> oldBlocks = getPartialChain(head, splitPoint);
        final LinkedList<Block> newBlocks = getPartialChain(newChainHead, splitPoint);
        // Disconnect each transaction in the previous best chain that is no
        // longer in the new best chain
        for (Block oldBlock : oldBlocks) {
            // Sanity check:
            if (!oldBlock.getHash().equals(networkParameters.getGenesisBlock().getHash())) {
                // Unset the milestone of this one
                long milestoneNumber = oldBlock.getRewardInfo().getChainlength();
                List<BlockWrap> blocksInMilestoneInterval = store.getBlocksInMilestoneInterval(milestoneNumber,
                        milestoneNumber);
                // Unconfirm anything not confirmed by milestone
                for (BlockWrap wipeBlock : blocksInMilestoneInterval)
                    blockGraph.unconfirm(wipeBlock.getBlockHash(), new HashSet<>());
            }
        }
        Block cursor;
        // Walk in ascending chronological order.
        for (Iterator<Block> it = newBlocks.descendingIterator(); it.hasNext();) {
            cursor = it.next();
            buildRewardChain(cursor);
            // if we build a chain longer than head, do a commit, even it may be
            // failed after this.
            if (cursor.getRewardInfo().getChainlength()> head.getRewardInfo().getChainlength()) {
                store.commitDatabaseBatchWrite();
                 store.beginDatabaseBatchWrite();
            }
        }

        // Update the pointer to the best known block.
        // setChainHead(storedNewHead);
    }

    private Block getChainHead() throws BlockStoreException {
        return store.get(store.getMaxConfirmedReward().getBlockHash());
    }

    /**
     * Locates the point in the chain at which newBlock and chainHead diverge.
     * Returns null if no split point was found (ie they are not part of the
     * same chain). Returns newChainHead or chainHead if they don't actually
     * diverge but are part of the same chain.
     * return null, if the newChainHead is  not complete locally.
     */
    public Block findSplit(Block newChainHead, Block oldChainHead) throws BlockStoreException {
        Block currentChainCursor = oldChainHead;
        Block newChainCursor = newChainHead;
        // Loop until we find the reward block both chains have in common.
        // Example:
        //
        // A -> B -> C -> D
        // *****\--> E -> F -> G
        //
        // findSplit will return block B. oldChainHead = D and newChainHead = G.
        while (!currentChainCursor.equals(newChainCursor)) {
            if (currentChainCursor.getRewardInfo().getChainlength() > newChainCursor.getRewardInfo().getChainlength()) {
                currentChainCursor = store.get(currentChainCursor.getRewardInfo().getPrevRewardHash());
             //  checkNotNull(currentChainCursor, "Attempt to follow an orphan chain");
            if(currentChainCursor==null) break;
               
            } else {
                newChainCursor = store.get(currentChainCursor.getRewardInfo().getPrevRewardHash());
              //  checkNotNull(newChainCursor, "Attempt to follow an orphan chain");
                if(newChainCursor==null) break;
            }
        }
        return currentChainCursor;
    }

    /**
     * Returns the set of contiguous blocks between 'higher' and 'lower'. Higher
     * is included, lower is not.
     */
    private LinkedList<Block> getPartialChain(Block higher, Block lower) throws BlockStoreException {
        checkArgument(higher.getHeight() > lower.getHeight(), "higher and lower are reversed");
        LinkedList<Block> results = new LinkedList<>();
        Block cursor = higher;
        while (true) {
            results.add(cursor);
            cursor = checkNotNull(store.get(cursor.getRewardInfo().getPrevRewardHash()),
                    "Ran off the end of the chain");
            if (cursor.equals(lower))
                break;
        }
        return results;
    }

    @SuppressWarnings("unused")
    private Optional<ConflictCandidate> findFirstSpentInput(HashSet<BlockWrap> allApprovedNewBlocks) {
        return allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates()).flatMap(i -> i.stream()).filter(c -> {
            try {
                return validatorService.hasSpentDependencies(c);
            } catch (BlockStoreException e) {
                e.printStackTrace();
                return true;
            }
        }).findFirst();
    }

    private boolean hasSpentInputs(HashSet<BlockWrap> allApprovedNewBlocks) {
        return allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates()).flatMap(i -> i.stream()).anyMatch(c -> {
            try {
                return validatorService.hasSpentDependencies(c);
            } catch (BlockStoreException e) {
                e.printStackTrace();
                return true;
            }
        });
    }

    private SolidityState checkReferencedBlockRequirements(Block newMilestoneBlock, long cutoffHeight)
            throws BlockStoreException {

        RewardInfo currRewardInfo = RewardInfo.parseChecked(newMilestoneBlock.getTransactions().get(0).getData());

        for (Sha256Hash hash : currRewardInfo.getBlocks()) {
            BlockWrap block = store.getBlockWrap(hash);
            if (block == null)
                return SolidityState.from(hash, true);
            if (block.getBlock().getHeight() <= cutoffHeight)
                throw new VerificationException("Referenced blocks are below cutoff height.");

            Set<Sha256Hash> requiredBlocks = blockService.getAllRequiredBlockHashes(block.getBlock());
            for (Sha256Hash reqHash : requiredBlocks) {
                BlockWrap req = store.getBlockWrap(reqHash);
                if (req == null)
                    return SolidityState.from(reqHash, true);

                if (req != null && req.getBlockEvaluation().getMilestone() < 0
                        && !currRewardInfo.getBlocks().contains(reqHash)) {
                    throw new VerificationException("Predecessors are not in milestone.");
                }
            }
        }

        return SolidityState.getSuccessState();
    }

    private void checkGeneratedReward(Block newMilestoneBlock) throws BlockStoreException {

        RewardInfo currRewardInfo = RewardInfo.parseChecked(newMilestoneBlock.getTransactions().get(0).getData());

        RewardBuilderResult result = makeReward(newMilestoneBlock.getPrevBlockHash(),
                newMilestoneBlock.getPrevBranchBlockHash(), currRewardInfo.getPrevRewardHash());
        if (newMilestoneBlock.getDifficultyTarget() != result.getDifficulty()) {
            throw new VerificationException("Incorrect difficulty target");
        }
        if (!newMilestoneBlock.getTransactions().get(0).getHash().equals(result.getTx().getHash())) {
            throw new VerificationException("Predecessors are not in milestone.");

        }

    }

    public boolean solidifyWaiting(Block block) throws BlockStoreException {

        SolidityState solidityState = validatorService.checkSolidity(block, false);
        blockGraph.solidifyBlock(block, solidityState, false);
        // TODO this is recursive and may blow the stack
        if (solidityState.getState() != State.MissingPredecessor)
            scanWaitingBlocks(block);

        return true;

    }

    public void scanWaitingBlocks(Block block, Set<Sha256Hash> updateSet) throws BlockStoreException {
        // Finally, look in the solidity waiting queue for blocks that are still
        // waiting
        for (Block b : store.getUnsolidBlocks(block.getHash().getBytes())) {
            if (updateSet != null && !updateSet.contains(b.getHash()))
                continue;

            try {
                // Clear from waiting list
                store.deleteUnsolid(b.getHash());

                // If going through or waiting for more dependencies, all is
                // good
                solidifyWaiting(b);

            } catch (VerificationException e) {
                // If the block is deemed invalid, we do not propagate the error
                // upwards
                log.debug(e.getMessage());
            }
        }
    }

    public void scanWaitingBlocks(Block block) throws BlockStoreException {
        scanWaitingBlocks(block, null);
    }

    public SolidityState checkRewardDifficulty(Block rewardBlock) throws BlockStoreException {
        RewardInfo rewardInfo = RewardInfo.parseChecked(rewardBlock.getTransactions().get(0).getData());

        // Check previous reward blocks exist and get their approved sets
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        if (prevRewardHash == null)
            throw new VerificationException("Missing previous reward block: " + prevRewardHash);

        Block prevRewardBlock = store.get(prevRewardHash);
        if (prevRewardBlock == null)
            return SolidityState.from(prevRewardHash, true);
        if (prevRewardBlock.getBlockType() != Type.BLOCKTYPE_REWARD
                && prevRewardBlock.getBlockType() != Type.BLOCKTYPE_INITIAL)
            throw new VerificationException("Previous reward block is not reward block.");

        checkRewardDifficulty(rewardBlock, rewardInfo, prevRewardBlock);
        
        return SolidityState.getSuccessState();
    }

    public SolidityState checkRewardReferencedBlocks(Block rewardBlock) throws BlockStoreException {
        try {
            RewardInfo rewardInfo = RewardInfo.parseChecked(rewardBlock.getTransactions().get(0).getData());

            // Check previous reward blocks exist and get their approved sets
            Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
            if (prevRewardHash == null)
                throw new VerificationException("Missing previous block reference." + prevRewardHash);

            Block prevRewardBlock = store.get(prevRewardHash);
            if (prevRewardBlock == null)
                return SolidityState.from(prevRewardHash, true);
            if (prevRewardBlock.getBlockType() != Type.BLOCKTYPE_REWARD
                    && prevRewardBlock.getBlockType() != Type.BLOCKTYPE_INITIAL)
                throw new VerificationException("Previous reward block is not reward block.");

            // Get all blocks approved by previous reward blocks
            long cutoffHeight = blockService.getCutoffHeight(prevRewardHash);

            for (Sha256Hash hash : rewardInfo.getBlocks()) {
                BlockWrap block = store.getBlockWrap(hash);
                if (block == null)
                    return SolidityState.from(hash, true);
                if (block.getBlock().getHeight() <= cutoffHeight)
                    throw new VerificationException("Referenced blocks are below cutoff height.");
                
                SolidityState requirementResult = checkRequiredBlocks(rewardInfo, block);
                if (!requirementResult.isSuccessState()) {
                    return requirementResult;
                }
            }

        } catch (Exception e) {
            throw new VerificationException("checkRewardReferencedBlocks not completed:", e);
        }
        
        return SolidityState.getSuccessState();
    }

    private SolidityState checkRequiredBlocks(RewardInfo rewardInfo, BlockWrap block) throws BlockStoreException {
        Set<Sha256Hash> requiredBlocks = blockService.getAllRequiredBlockHashes(block.getBlock());
        for (Sha256Hash reqHash : requiredBlocks) {
            BlockWrap req = store.getBlockWrap(reqHash);
            // the required block must be in this referenced blocks or in
            // milestone
            if (req == null) {
                return SolidityState.from(reqHash, true);
            }
            if (req != null && !rewardInfo.getBlocks().contains(req.getBlockHash())
                    && req.getBlockEvaluation().getMilestone() < 0
                    && !req.getBlock().getHash().equals(rewardInfo.getPrevRewardHash())) {
                throw new VerificationException("required is not in milestone   " + req.getBlockEvaluation());

            }
        }

        return SolidityState.getSuccessState();
    }

    private void checkRewardDifficulty(Block rewardBlock, RewardInfo rewardInfo, Block prevRewardBlock)
            throws BlockStoreException {
        // check the difficulty
        // Count how many blocks from miners in the reward interval are approved
        BlockWrap prevTrunkBlock = store.getBlockWrap(rewardBlock.getPrevBlockHash());
        BlockWrap prevBranchBlock = store.getBlockWrap(rewardBlock.getPrevBranchBlockHash());
        if (prevTrunkBlock == null)
            throw new VerificationException("Previous  block does not exists.");
        if (prevBranchBlock == null)
            throw new VerificationException("prevBranchBlock  block does not exists.");

     
        // check difficulty
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        long difficulty = calculateNextChainDifficulty(prevRewardHash, rewardInfo.getChainlength(), rewardBlock.getTimeSeconds());

   
        
        if (difficulty != rewardInfo.getDifficultyTargetReward()) {
            throw new VerificationException("getDifficultyTargetReward does not match.");
        }
 
   
        
     
    }
    
    public long calculateNextChainDifficulty(Sha256Hash prevRewardHash, long currChainLength, long currentTime)
            throws BlockStoreException {

        if (currChainLength % NetworkParameters.INTERVAL != 0) {
            return store.getRewardDifficulty(prevRewardHash);
        }

        // Get the block INTERVAL ago
        Block oldBlock = null;
        for (int i = 0; i < NetworkParameters.INTERVAL; i++) {
            oldBlock = store.getBlockWrap(prevRewardHash).getBlock();
           // prevRewardHash = blockStore.getRewardPrevBlockHash(oldBlock);
        }

        int timespan = (int) Math.max(1, (currentTime - oldBlock.getTimeSeconds()));
        long prevDifficulty = store.getRewardDifficulty(prevRewardHash);

        // Limit the adjustment step.
        int targetTimespan = NetworkParameters.TARGET_TIMESPAN;
        if (timespan < targetTimespan / 4)
            timespan = targetTimespan / 4;
        if (timespan > targetTimespan * 4)
            timespan = targetTimespan * 4;

        BigInteger newTarget = Utils.decodeCompactBits(prevDifficulty);
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));
        newTarget = newTarget.divide(BigInteger.valueOf(targetTimespan));

        if (newTarget.compareTo(networkParameters.getMaxTargetReward()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", newTarget.toString(16));
            newTarget = networkParameters.getMaxTarget();
        }

        return Utils.encodeCompactBits(newTarget);
    }
}
