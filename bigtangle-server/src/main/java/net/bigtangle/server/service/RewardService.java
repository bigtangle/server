/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
import net.bigtangle.core.exception.VerificationException.CutoffException;
import net.bigtangle.core.exception.VerificationException.InfeasiblePrototypeException;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.core.ConflictCandidate;
import net.bigtangle.server.data.OrderMatchingResult;
import net.bigtangle.server.data.SolidityState;
import net.bigtangle.server.service.ValidatorService.RewardBuilderResult;
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Threading;

/**
 * <p>
 * A RewardService provides service for create and validate the reward chain.
 * </p>
 */
@Service
public class RewardService {
 
    @Autowired
    protected FullBlockGraph blockGraph;
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
    @Autowired
    private StoreService  storeService;
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Scheduled update function that updates the Tangle
     * 
     * @throws BlockStoreException
     */

    protected final ReentrantLock lock = Threading.lock("RewardService");

    // createReward is time boxed and can run parallel.
    public void startSingleProcess() throws BlockStoreException {
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            log.debug(this.getClass().getName() + "  RewardService running. Returning...");
            return;
        }
        FullBlockStore store = storeService.getStore();
        
        try {
            // log.info("create Reward started");
           
            createReward(store);
         
        } catch (Exception e) {
            log.error("create Reward end  ", e);
        } finally {
            lock.unlock();
            store.close();
        }

    }

    /**
     * Runs the reward making logic
     * 
     * @return the new block or block voted on
     * @throws Exception
     */

    private Block createReward(FullBlockStore store) throws Exception {

        Sha256Hash prevRewardHash = store.getMaxConfirmedReward().getBlockHash();
        Block reward = createReward(prevRewardHash,store);
        if (reward != null) {
            log.debug(" reward block is created: " + reward);
        }
        return reward;
    }

    public Block createReward(Sha256Hash prevRewardHash,FullBlockStore store) throws Exception {
        try {
            Stopwatch watch = Stopwatch.createStarted();
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedRewardBlockPair(prevRewardHash,store);
            log.debug("  getValidatedRewardBlockPair time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

            return createReward(prevRewardHash, tipsToApprove.getLeft(), tipsToApprove.getRight(),store);
        } catch (CutoffException | InfeasiblePrototypeException  | NullPointerException e ) {
            // fall back to use prev reward as tip
            log.debug(" fall back to use prev reward as tip: ", e);
            Block prevreward = store.get(prevRewardHash);
            return createReward(prevRewardHash, prevreward.getHash(), prevreward.getHash(),store);
        }
    }

    public Block createReward(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch, FullBlockStore store) throws Exception {
        return createReward(prevRewardHash, prevTrunk, prevBranch, null,store);
    }

    public Block createReward(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch, Long timeOverride, FullBlockStore store)
            throws Exception {

        Block block = createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, timeOverride,store);

        if (block != null) {
            // check, if the reward block is too old to avoid conflict.
            TXReward latest = store.getMaxConfirmedReward();
            if (latest.getChainLength() >= block.getLastMiningRewardBlock()    ) {
                log.debug("resolved Reward is out of date.");
            } else {
                blockService.saveBlock(block,store); 
            }
        }
        return block;
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch, FullBlockStore store)
            throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
        return createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, null,store);
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            Long timeOverride, FullBlockStore store) throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
        Stopwatch watch = Stopwatch.createStarted();

        Block r1 = blockService.getBlock(prevTrunk,store);
        Block r2 = blockService.getBlock(prevBranch,store);

        long currentTime = Math.max(System.currentTimeMillis() / 1000,
                Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
        if (timeOverride != null)
            currentTime = timeOverride;
        RewardBuilderResult result = makeReward(prevTrunk, prevBranch, prevRewardHash, currentTime,store);

        Block block = Block.createBlock(networkParameters, r1, r2);

        block.setBlockType(Block.Type.BLOCKTYPE_REWARD);
        block.setHeight(Math.max(r1.getHeight(), r2.getHeight()) + 1);
        block.setMinerAddress(
                Address.fromBase58(networkParameters, serverConfiguration.getMineraddress()).getHash160());

        Transaction tx = result.getTx();
        RewardInfo currRewardInfo = new RewardInfo().parseChecked(tx.getData());
        block.setLastMiningRewardBlock(currRewardInfo.getChainlength());
        block.setDifficultyTarget(calculateNextBlockDifficulty(currRewardInfo));

        // Enforce timestamp equal to previous max for reward blocktypes
        block.setTime(currentTime);
        BigInteger chainTarget = Utils.decodeCompactBits(store.getRewardDifficulty(prevRewardHash));
        if (Utils.decodeCompactBits(result.getDifficulty()).compareTo(chainTarget) < 0) {
            chainTarget = Utils.decodeCompactBits(result.getDifficulty());
        }

        block.addTransaction(tx);
        OrderMatchingResult ordermatchresult = blockGraph.generateOrderMatching(block, currRewardInfo,store);
        currRewardInfo.setOrdermatchingResult(ordermatchresult.getOrderMatchingResultHash());
        tx.setData(currRewardInfo.toByteArray());
        Transaction miningTx = blockGraph.generateVirtualMiningRewardTX(block,store);
        currRewardInfo.setMiningResult(miningTx.getHash());
        tx.setData(currRewardInfo.toByteArray());

        blockService.adjustHeightRequiredBlocks(block,store);
        final BigInteger chainTargetFinal = chainTarget;
        log.debug("prepare Reward time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        return rewardSolve(block, chainTargetFinal);
    }

    private Block rewardSolve(Block block, final BigInteger chainTargetFinal)
            throws InterruptedException, ExecutionException {
        final Duration timeout = Duration.ofMillis(scheduleConfiguration.getMiningrate());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {
                log.debug(" reward block solve started  : " + chainTargetFinal + " \n for block" + block);
                block.solve(chainTargetFinal);
                return "";
            }
        });
        Stopwatch watch = Stopwatch.createStarted();
        try {
            handler.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.debug(" reward solve Timeout  ");
            handler.cancel(true);
            return null;
        } finally {
            executor.shutdownNow();
        }
        log.debug("Reward Solved time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        return block;
    }

    public GetTXRewardResponse getMaxConfirmedReward(Map<String, Object> request,FullBlockStore store) throws BlockStoreException {

        return GetTXRewardResponse.create(store.getMaxConfirmedReward());

    }

    public GetTXRewardListResponse getAllConfirmedReward(Map<String, Object> request,FullBlockStore store) throws BlockStoreException {

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
    public RewardBuilderResult makeReward(Sha256Hash prevTrunk, Sha256Hash prevBranch, Sha256Hash prevRewardHash,
            long currentTime, FullBlockStore store) throws BlockStoreException {

        // Read previous reward block's data
        long prevChainLength = store.getRewardChainLength(prevRewardHash);

        // Build transaction for block
        Transaction tx = new Transaction(networkParameters);

        Set<Sha256Hash> blocks = new HashSet<Sha256Hash>();
        long cutoffheight = blockService.getRewardCutoffHeight(prevRewardHash,store);

        // Count how many blocks from miners in the reward interval are approved
        BlockWrap prevTrunkBlock = store.getBlockWrap(prevTrunk);
        BlockWrap prevBranchBlock = store.getBlockWrap(prevBranch);
        try {
            blockService.addRequiredNonContainedBlockHashesTo(blocks, prevBranchBlock, cutoffheight, prevChainLength,
                    true,store);
            blockService.addRequiredNonContainedBlockHashesTo(blocks, prevTrunkBlock, cutoffheight, prevChainLength,
                    true,store);
        } catch (CutoffException e) {

            // Robustness
            e.printStackTrace();
            blocks = new HashSet<Sha256Hash>();
            blockService.addRequiredNonContainedBlockHashesTo(blocks, prevBranchBlock, cutoffheight, prevChainLength,
                    false,store);
            blockService.addRequiredNonContainedBlockHashesTo(blocks, prevTrunkBlock, cutoffheight, prevChainLength,
                    false,store);
        }

        long difficultyReward = calculateNextChainDifficulty(prevRewardHash, prevChainLength + 1, currentTime,store);

        // Build the type-specific tx data
        RewardInfo rewardInfo = new RewardInfo(prevRewardHash, difficultyReward, blocks, prevChainLength + 1);
        tx.setData(rewardInfo.toByteArray());
        tx.setMemo(new MemoInfo("Reward"));
        return new RewardBuilderResult(tx, difficultyReward);
    }

    public long calculateNextBlockDifficulty(RewardInfo currRewardInfo) {
        BigInteger difficultyTargetReward = Utils.decodeCompactBits(currRewardInfo.getDifficultyTargetReward());
        BigInteger difficultyChain = difficultyTargetReward
                .multiply(BigInteger.valueOf(NetworkParameters.TARGET_MAX_TPS));
        difficultyChain = difficultyChain.multiply(BigInteger.valueOf(NetworkParameters.TARGET_SPACING));

        if (difficultyChain.compareTo(networkParameters.getMaxTarget()) > 0) {
            // log.info("Difficulty hit proof of work limit: {}",
            // difficultyChain.toString(16));
            difficultyChain = networkParameters.getMaxTarget();
        }

        return Utils.encodeCompactBits(difficultyChain);
    }

    public void buildRewardChain(Block newMilestoneBlock, FullBlockStore store) throws BlockStoreException {

        RewardInfo currRewardInfo = new RewardInfo().parseChecked(newMilestoneBlock.getTransactions().get(0).getData());
        Set<Sha256Hash> milestoneSet = currRewardInfo.getBlocks();
        long cutoffHeight = blockService.getRewardCutoffHeight(currRewardInfo.getPrevRewardHash(),store);

        // Check all referenced blocks have their requirements
        SolidityState solidityState = checkReferencedBlockRequirements(newMilestoneBlock, cutoffHeight,store);
        if (!solidityState.isSuccessState())
            throw new VerificationException(" checkReferencedBlockRequirements is failed: " + solidityState.toString());

        // Solidify referenced blocks
        solidifyBlocks(currRewardInfo,store);

        // Ensure the new difficulty and tx is set correctly
        checkGeneratedReward(newMilestoneBlock,store);

        // Sanity check: No reward blocks are approved
        checkContainsNoRewardBlocks(newMilestoneBlock,store);

        // Check: At this point, predecessors must be solid
        solidityState = validatorService.checkSolidity(newMilestoneBlock, false,store);
        if (!solidityState.isSuccessState())
            throw new VerificationException(" validatorService.checkSolidity is failed: " + solidityState.toString());

        // Unconfirm anything not confirmed by milestone
        List<Sha256Hash> wipeBlocks = store.getWhereConfirmedNotMilestone();
        HashSet<Sha256Hash> traversedBlockHashes = new HashSet<>();
        for (Sha256Hash wipeBlock : wipeBlocks)
            blockGraph.unconfirm(wipeBlock, traversedBlockHashes,store);

        // Find conflicts in the dependency set
        HashSet<BlockWrap> allApprovedNewBlocks = new HashSet<>();
        for (Sha256Hash hash : milestoneSet)
            allApprovedNewBlocks.add(store.getBlockWrap(hash));
        allApprovedNewBlocks.add(store.getBlockWrap(newMilestoneBlock.getHash()));

        // If anything is already spent, no-go
        boolean anySpentInputs = hasSpentInputs(allApprovedNewBlocks,store);
        // Optional<ConflictCandidate> spentInput =
        // findFirstSpentInput(allApprovedNewBlocks);

        if (anySpentInputs) {
            solidityState = SolidityState.getFailState();
            throw new VerificationException("there are hasSpentInputs in allApprovedNewBlocks ");
        }
        // If any conflicts exist between the current set of
        // blocks, no-go
        boolean anyCandidateConflicts = allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates())
                .flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream()
                .anyMatch(l -> l.size() > 1);
        // List<List<ConflictCandidate>> candidateConflicts =
        // allApprovedNewBlocks.stream()
        // .map(b -> b.toConflictCandidates()).flatMap(i -> i.stream())
        // .collect(Collectors.groupingBy(i ->
        // i.getConflictPoint())).values().stream().filter(l -> l.size() > 1)
        // .collect(Collectors.toList());

        // Did we fail? Then we stop now and rerun consensus
        // logic on the new longest chain.
        if (anyCandidateConflicts) {
            solidityState = SolidityState.getFailState();
            throw new VerificationException("conflicts exist between the current set of ");
        }

        // Otherwise, all predecessors exist and were at least
        // solid > 0, so we should be able to confirm everything
        blockGraph.solidifyBlock(newMilestoneBlock, solidityState, true,store);
        HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
        long milestoneNumber = store.getRewardChainLength(newMilestoneBlock.getHash());
        for (BlockWrap approvedBlock : allApprovedNewBlocks)
            blockGraph.confirm(approvedBlock.getBlockEvaluation().getBlockHash(), traversedConfirms, milestoneNumber,store);

    }

    public void solidifyBlocks(RewardInfo currRewardInfo, FullBlockStore store) throws BlockStoreException {
        Comparator<Block> comparator = Comparator.comparingLong((Block b) -> b.getHeight())
                .thenComparing((Block b) -> b.getHash());
        TreeSet<Block> referencedBlocks = new TreeSet<Block>(comparator);
        for (Sha256Hash hash : currRewardInfo.getBlocks()) {
            referencedBlocks.add(store.get(hash));
        }
        for (Block block : referencedBlocks) {
            solidifyWaiting(block,store);
        }
    }

    /**
     * Called as part of connecting a block when the new block results in a
     * different chain having higher total work.
     * 
     */
    public void handleNewBestChain(Block newChainHead, FullBlockStore store) throws BlockStoreException, VerificationException {
        // checkState(lock.isHeldByCurrentThread());
        // This chain has overtaken the one we currently believe is best.
        // Reorganize is required.
        //
        // Firstly, calculate the block at which the chain diverged. We only
        // need to examine the
        // chain from beyond this block to find differences.
        Block head = getChainHead(store);
        final Block splitPoint = findSplit(newChainHead, head,store);
        if (splitPoint == null) {
            log.info(" splitPoint is null, the chain ist not complete: ", newChainHead);
            return;
        }

        log.info("Re-organize after split at height {}", splitPoint.getHeight());
        log.info("Old chain head: \n {}", head);
        log.info("New chain head: \n {}", newChainHead);
        log.info("Split at block: \n {}", splitPoint);
        // Then build a list of all blocks in the old part of the chain and the
        // new part.
        LinkedList<Block> oldBlocks = new LinkedList<Block>();
        if (!head.getHash().equals(splitPoint.getHash())) {
            oldBlocks = getPartialChain(head, splitPoint,store);
        }
        final LinkedList<Block> newBlocks = getPartialChain(newChainHead, splitPoint,store);
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
                    blockGraph.unconfirm(wipeBlock.getBlockHash(), new HashSet<>(),store);
            }
        }
        Block cursor;
        // Walk in ascending chronological order.
        for (Iterator<Block> it = newBlocks.descendingIterator(); it.hasNext();) {
            cursor = it.next();
            buildRewardChain(cursor,store);
            // if we build a chain longer than head, do a commit, even it may be
            // failed after this.
            if (cursor.getRewardInfo().getChainlength() > head.getRewardInfo().getChainlength()) {
                store.commitDatabaseBatchWrite();
                store.beginDatabaseBatchWrite();
            }
        }

        // Update the pointer to the best known block.
        // setChainHead(storedNewHead);
    }

    private Block getChainHead( FullBlockStore store) throws BlockStoreException {
        return store.get(store.getMaxConfirmedReward().getBlockHash());
    }

    /**
     * Locates the point in the chain at which newBlock and chainHead diverge.
     * Returns null if no split point was found (ie they are not part of the
     * same chain). Returns newChainHead or chainHead if they don't actually
     * diverge but are part of the same chain. return null, if the newChainHead
     * is not complete locally.
     */
    public Block findSplit(Block newChainHead, Block oldChainHead, FullBlockStore store) throws BlockStoreException {
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
                checkNotNull(currentChainCursor, "Attempt to follow an orphan chain");

            } else {
                newChainCursor = store.get(newChainCursor.getRewardInfo().getPrevRewardHash());
                checkNotNull(newChainCursor, "Attempt to follow an orphan chain");

            }
        }
        return currentChainCursor;
    }

    /**
     * Returns the set of contiguous blocks between 'higher' and 'lower'. Higher
     * is included, lower is not.
     */
    private LinkedList<Block> getPartialChain(Block higher, Block lower, FullBlockStore store) throws BlockStoreException {
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
    private Optional<ConflictCandidate> findFirstSpentInput(HashSet<BlockWrap> allApprovedNewBlocks, FullBlockStore store) {
        return allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates()).flatMap(i -> i.stream()).filter(c -> {
            try {
                return validatorService.hasSpentDependencies(c,store);
            } catch (BlockStoreException e) {
                e.printStackTrace();
                return true;
            }
        }).findFirst();
    }

    private boolean hasSpentInputs(HashSet<BlockWrap> allApprovedNewBlocks, FullBlockStore store) {
        return allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates()).flatMap(i -> i.stream()).anyMatch(c -> {
            try {
                return validatorService.hasSpentDependencies(c,store);
            } catch (BlockStoreException e) {
                e.printStackTrace();
                return true;
            }
        });
    }

    /*
     * check blocks are in not in milestone
     */
    private SolidityState checkReferencedBlockRequirements(Block newMilestoneBlock, long cutoffHeight, FullBlockStore store)
            throws BlockStoreException {

        RewardInfo currRewardInfo = new RewardInfo().parseChecked(newMilestoneBlock.getTransactions().get(0).getData());

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

    private void checkContainsNoRewardBlocks(Block newMilestoneBlock, FullBlockStore store) throws BlockStoreException {

        RewardInfo currRewardInfo = new RewardInfo().parseChecked(newMilestoneBlock.getTransactions().get(0).getData());
        for (Sha256Hash hash : currRewardInfo.getBlocks()) {
            BlockWrap block = store.getBlockWrap(hash);
            if (block.getBlock().getBlockType() == Type.BLOCKTYPE_REWARD)
                throw new VerificationException("Reward block approves other reward blocks");
        }
    }

    private void checkGeneratedReward(Block newMilestoneBlock, FullBlockStore store) throws BlockStoreException {

        RewardInfo currRewardInfo = new RewardInfo().parseChecked(newMilestoneBlock.getTransactions().get(0).getData());

        RewardBuilderResult result = makeReward(newMilestoneBlock.getPrevBlockHash(),
                newMilestoneBlock.getPrevBranchBlockHash(), currRewardInfo.getPrevRewardHash(),
                newMilestoneBlock.getTimeSeconds(),store);
        if (currRewardInfo.getDifficultyTargetReward() != result.getDifficulty()) {
            throw new VerificationException("Incorrect difficulty target");
        }

        OrderMatchingResult ordermatchresult = blockGraph.generateOrderMatching(newMilestoneBlock,store);

        // Only check the Hash of OrderMatchingResult
        if (!currRewardInfo.getOrdermatchingResult().equals(ordermatchresult.getOrderMatchingResultHash())) {
            throw new VerificationException("OrderMatchingResult transactions output is wrong.");
        }

        Transaction miningTx = blockGraph.generateVirtualMiningRewardTX(newMilestoneBlock,store);

        // Only check the Hash of OrderMatchingResult
        if (!currRewardInfo.getMiningResult().equals(miningTx.getHash())) {
            throw new VerificationException("generateVirtualMiningRewardTX transactions output is wrong.");
        }
    }

    public boolean solidifyWaiting(Block block, FullBlockStore store) throws BlockStoreException {

        SolidityState solidityState = validatorService.checkSolidity(block, false,store);
        blockGraph.solidifyBlock(block, solidityState, false,store);
        return true;
    }

    public void scanWaitingBlocks(Block block, Set<Sha256Hash> updateSet, FullBlockStore store) throws BlockStoreException {
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
                solidifyWaiting(b,store);
            } catch (VerificationException e) {
                // If the block is deemed invalid, we do not propagate the error
                // upwards
                log.debug(e.getMessage());
            }
        }
    }

    public void scanWaitingBlocks(Block block, FullBlockStore store) throws BlockStoreException {
        scanWaitingBlocks(block, null,store);
    }

    public SolidityState checkRewardDifficulty(Block rewardBlock, FullBlockStore store) throws BlockStoreException {
        RewardInfo rewardInfo = new RewardInfo().parseChecked(rewardBlock.getTransactions().get(0).getData());

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

        checkRewardDifficulty(rewardBlock, rewardInfo, prevRewardBlock,store);

        return SolidityState.getSuccessState();
    }

    public SolidityState checkRewardReferencedBlocks(Block rewardBlock, FullBlockStore store) throws BlockStoreException {
        try {
            RewardInfo rewardInfo = new RewardInfo().parseChecked(rewardBlock.getTransactions().get(0).getData());

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
            long cutoffHeight = blockService.getRewardCutoffHeight(prevRewardHash,store);

            for (Sha256Hash hash : rewardInfo.getBlocks()) {
                BlockWrap block = store.getBlockWrap(hash);
                if (block == null)
                    return SolidityState.from(hash, true);
                if (block.getBlock().getHeight() <= cutoffHeight)
                    throw new VerificationException("Referenced blocks are below cutoff height.");

                SolidityState requirementResult = checkRequiredBlocks(rewardInfo, block,store);
                if (!requirementResult.isSuccessState()) {
                    return requirementResult;
                }
            }

        } catch (Exception e) {
            throw new VerificationException("checkRewardReferencedBlocks not completed:", e);
        }

        return SolidityState.getSuccessState();
    }

    /*
     * check only if the blocks in database
     */
    private SolidityState checkRequiredBlocks(RewardInfo rewardInfo, BlockWrap block, FullBlockStore store) throws BlockStoreException {
        Set<Sha256Hash> requiredBlocks = blockService.getAllRequiredBlockHashes(block.getBlock());
        for (Sha256Hash reqHash : requiredBlocks) {
            BlockWrap req = store.getBlockWrap(reqHash);
            // the required block must be in this referenced blocks or in
            // milestone
            if (req == null) {
                return SolidityState.from(reqHash, true);
            }
        }

        return SolidityState.getSuccessState();
    }

    private void checkRewardDifficulty(Block rewardBlock, RewardInfo rewardInfo, Block prevRewardBlock, FullBlockStore store)
            throws BlockStoreException {

        // check difficulty
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        long difficulty = calculateNextChainDifficulty(prevRewardHash, rewardInfo.getChainlength(),
                rewardBlock.getTimeSeconds(),store);

        if (difficulty != rewardInfo.getDifficultyTargetReward()) {
            throw new VerificationException("getDifficultyTargetReward does not match.");
        }

    }

    public long calculateNextChainDifficulty(Sha256Hash prevRewardHash, long currChainLength, long currentTime, FullBlockStore store)
            throws BlockStoreException {

        if (currChainLength % NetworkParameters.INTERVAL != 0) {
            return store.getRewardDifficulty(prevRewardHash);
        }

        // Get the block INTERVAL ago
        for (int i = 0; i < NetworkParameters.INTERVAL - 1; i++) {
            prevRewardHash = store.getRewardPrevBlockHash(prevRewardHash);
        }
        Block oldBlock = store.get(prevRewardHash);

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
            // log.info("Difficulty hit proof of work limit: {}",
            // newTarget.toString(16));
            newTarget = networkParameters.getMaxTargetReward();
        }

        if (prevDifficulty != (Utils.encodeCompactBits(newTarget))) {
            log.info("Difficulty  change from {} to: {} and diff={}", prevDifficulty,
                    Utils.encodeCompactBits(newTarget), prevDifficulty - Utils.encodeCompactBits(newTarget));

        }

        return Utils.encodeCompactBits(newTarget);
    }
}
