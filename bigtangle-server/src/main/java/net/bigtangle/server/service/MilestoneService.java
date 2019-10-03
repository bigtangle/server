/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Context;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.UnsolidBlock;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.service.SolidityState.State;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Threading;

/*
 *  This service offers maintenance functions to update the local state of the Tangle
 */
@Service
public class MilestoneService {
    private static final Logger log = LoggerFactory.getLogger(MilestoneService.class);
    private static final int WARNING_MILESTONE_UPDATE_LOOPS = 20;

    @Autowired
    protected FullPrunedBlockGraph blockGraph;
    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    private TipsService tipsService;
    @Autowired
    private ValidatorService validatorService;
    @Autowired
    private BlockRequester blockRequester;
    @Autowired
    private BlockService blockService;

    @Autowired
    private NetworkParameters params;

    /**
     * Scheduled update function that updates the Tangle
     * 
     * @throws BlockStoreException
     */
    public void update() throws BlockStoreException {
        try {
            update(Integer.MAX_VALUE);
        } catch (InterruptedException | ExecutionException e) {
            // ignore

        }
    }

    public void update(int numberUpdates) throws InterruptedException, ExecutionException {

        Context context = new Context(params);
        Context.propagate(context);
        // cleanupNonSolidMissingBlocks();
        try {
            updateSolidity();

            updateMaintained();
            updateMilestoneDepth();
            updateWeightAndDepth();
            updateRating();

            blockGraph.chainlock.lock();

      //FIXME      store.beginDatabaseBatchWrite();
            updateConfirmed(numberUpdates);
       //     store.commitDatabaseBatchWrite();
        } catch (Exception e) {

        } finally {

            blockGraph.chainlock.unlock();
        }

    }

    /**
     * Scheduled update function that updates the Tangle
     * 
     * @throws BlockStoreException
     */

    protected final ReentrantLock lock = Threading.lock("milestoneService");

    /**
     * the missing blocks are check, blocks behind the last confirmed blocks are
     * removed.
     * 
     * @throws BlockStoreException
     * @throws NoBlockException
     */
    public void cleanupNonSolidMissingBlocks() throws BlockStoreException, NoBlockException {
        TXReward txReward = store.getMaxConfirmedReward();
        store.deleteOldUnsolid(txReward.getToHeight());

    }

    /**
     * the missing previous block and reward blocks requested and run the
     * solidity check.
     * 
     * 
     * @throws BlockStoreException
     * @throws NoBlockException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private void updateSolidity()
            throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {

        final Duration timeout = Duration.ofSeconds(5);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {

                /*
                 * Cutoff window around current chain.
                 */
                long cutoffHeight = blockService.getCutoffHeight();
                List<UnsolidBlock> storedBlocklist = store.getNonSolidMissingBlocks(cutoffHeight);
                log.debug("getNonSolidMissingBlocks size = " + storedBlocklist.size());
                for (UnsolidBlock storedBlock : storedBlocklist) {
                    if (storedBlock != null) {
                        Block req = blockService.getBlock(storedBlock.missingdependencyHash());

                        if (req != null) {
                            store.updateMissingBlock(storedBlock.missingdependencyHash(), false);
                            // if the block is there, now scan the rest unsolid
                            // blocks
                            if (store.getBlockEvaluation(req.getHash()).getSolid() >= 1) {
                                scanWaitingBlocks(req);
                            }
                        } else {

                            blockRequester.requestBlock(storedBlock.missingdependencyHash());
                        }
                    }
                }
                return "";
            }
        });

        try {
            handler.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            handler.cancel(true);

        } finally {
            executor.shutdownNow();
        }

    }

    /**
     * Update cumulative weight: the amount of blocks a block is approved by.
     * Update depth: the longest chain of blocks to a tip. Allows unsolid blocks
     * too.
     * 
     * @throws BlockStoreException
     */
    private void updateWeightAndDepth() throws BlockStoreException {
        // Begin from the highest maintained height blocks and go backwards from
        // there
        PriorityQueue<BlockWrap> blockQueue = store.getMaintainedBlocksDescending();
        HashMap<Sha256Hash, HashSet<Sha256Hash>> approvers = new HashMap<>();
        HashMap<Sha256Hash, Long> depths = new HashMap<>();

        // Initialize weight and depth of tips
        for (BlockWrap tip : blockQueue) {
            approvers.put(tip.getBlockHash(), new HashSet<>());
            depths.put(tip.getBlockHash(), 0L);
        }

        BlockWrap currentBlock = null;
        long cutoffHeight = blockService.getCutoffHeight();
        while ((currentBlock = blockQueue.poll()) != null) {
            Sha256Hash currentBlockHash = currentBlock.getBlockHash();

            // Abort if unmaintained, since it will be irrelevant for any tip
            // selections
            if (!currentBlock.getBlockEvaluation().isMaintained()
                    || currentBlock.getBlockEvaluation().getHeight() <= cutoffHeight)
                continue;

            // Add your own hash to approver hashes of current approver hashes
            approvers.get(currentBlockHash).add(currentBlockHash);

            // Add all current references to both approved blocks
            Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
            subUpdateWeightAndDepth(blockQueue, approvers, depths, currentBlockHash, prevTrunk);

            Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
            subUpdateWeightAndDepth(blockQueue, approvers, depths, currentBlockHash, prevBranch);

            // Update and dereference
            store.updateBlockEvaluationWeightAndDepth(currentBlock.getBlockHash(),
                    approvers.get(currentBlockHash).size(), depths.get(currentBlockHash));
            approvers.remove(currentBlockHash);
            depths.remove(currentBlockHash);
        }
    }

    private void subUpdateWeightAndDepth(PriorityQueue<BlockWrap> blockQueue,
            HashMap<Sha256Hash, HashSet<Sha256Hash>> approvers, HashMap<Sha256Hash, Long> depths,
            Sha256Hash currentBlockHash, Sha256Hash approvedBlockHash) throws BlockStoreException {
        Long currentDepth = depths.get(currentBlockHash);
        HashSet<Sha256Hash> currentApprovers = approvers.get(currentBlockHash);
        if (!approvers.containsKey(approvedBlockHash)) {
            BlockWrap prevBlock = store.getBlockWrap(approvedBlockHash);
            if (prevBlock != null) {
                blockQueue.add(prevBlock);
                approvers.put(approvedBlockHash, new HashSet<>(currentApprovers));
                depths.put(approvedBlockHash, currentDepth + 1);
            }
        } else {
            approvers.get(approvedBlockHash).addAll(currentApprovers);
            if (currentDepth + 1 > depths.get(approvedBlockHash))
                depths.put(approvedBlockHash, currentDepth + 1);
        }
    }

    /**
     * Update MilestoneDepth: the longest forward path to a milestone block
     * 
     * @throws BlockStoreException
     */
    private void updateMilestoneDepth() throws BlockStoreException {
        // Begin from the highest solid height tips and go backwards from there
        PriorityQueue<BlockWrap> blockQueue = store.getSolidTipsDescending();
        HashMap<Sha256Hash, Long> milestoneDepths = new HashMap<>();

        // Initialize milestone depths as -1
        for (BlockWrap tip : blockQueue) {
            milestoneDepths.put(tip.getBlockHash(), -1L);
        }

        BlockWrap currentBlock = null;
        long cutoffHeight = blockService.getCutoffHeight();
        while ((currentBlock = blockQueue.poll()) != null) {
            // Abort if unmaintained, since it will be irrelevant
            if (!currentBlock.getBlockEvaluation().isMaintained()
                    || currentBlock.getBlockEvaluation().getHeight() <= cutoffHeight)
                continue;

            // If depth is set to -1 and we are milestone, set to 0
            if (milestoneDepths.get(currentBlock.getBlockHash()) == -1L
                    && currentBlock.getBlockEvaluation().getMilestone() != -1)
                milestoneDepths.put(currentBlock.getBlockHash(), 0L);

            // Add all current references to both approved blocks
            Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
            subUpdateMilestoneDepth(blockQueue, milestoneDepths, currentBlock, prevTrunk);

            Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
            subUpdateMilestoneDepth(blockQueue, milestoneDepths, currentBlock, prevBranch);

            // Update and dereference
            store.updateBlockEvaluationMilestoneDepth(currentBlock.getBlockHash(),
                    milestoneDepths.get(currentBlock.getBlockHash()));
            milestoneDepths.remove(currentBlock.getBlockHash());
        }
    }

    private void subUpdateMilestoneDepth(PriorityQueue<BlockWrap> blockQueue, HashMap<Sha256Hash, Long> milestoneDepths,
            BlockWrap currentBlock, Sha256Hash approvedBlock) throws BlockStoreException {
        boolean isMilestone = currentBlock.getBlockEvaluation().getMilestone() != -1;
        long milestoneDepth = milestoneDepths.get(currentBlock.getBlockHash());
        long newMilestoneDepth = Math.min(milestoneDepth + 1,
                NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF + 1);
        if (!milestoneDepths.containsKey(approvedBlock)) {
            BlockWrap prevBlock = store.getBlockWrap(approvedBlock);
            if (prevBlock != null) {
                blockQueue.add(prevBlock);
                milestoneDepths.put(prevBlock.getBlockHash(), isMilestone ? newMilestoneDepth : -1L);
            }
        } else {
            if (isMilestone)
                if (newMilestoneDepth > milestoneDepths.get(approvedBlock))
                    milestoneDepths.put(approvedBlock, newMilestoneDepth);
        }
    }

    /**
     * Update rating: the percentage of times that tips selected by MCMC approve
     * a block. Allows unsolid blocks too.
     * 
     * @throws BlockStoreException
     */
    private void updateRating() throws BlockStoreException {
        // Select #tipCount solid tips via MCMC
        HashMap<Sha256Hash, HashSet<UUID>> selectedTipApprovers = new HashMap<Sha256Hash, HashSet<UUID>>(
                NetworkParameters.NUMBER_RATING_TIPS);
        Collection<BlockWrap> selectedTips = tipsService.getRatingTips(NetworkParameters.NUMBER_RATING_TIPS);

        // Initialize all approvers as UUID
        for (BlockWrap selectedTip : selectedTips) {
            UUID randomUUID = UUID.randomUUID();
            if (selectedTipApprovers.containsKey(selectedTip.getBlockHash())) {
                HashSet<UUID> result = selectedTipApprovers.get(selectedTip.getBlockHash());
                result.add(randomUUID);
            } else {
                HashSet<UUID> result = new HashSet<>();
                result.add(randomUUID);
                selectedTipApprovers.put(selectedTip.getBlockHash(), result);
            }
        }

        // Begin from the highest solid height tips plus selected tips and go
        // backwards from there
        PriorityQueue<BlockWrap> blockQueue = store.getSolidTipsDescending();
        HashSet<BlockWrap> selectedTipSet = new HashSet<>(selectedTips);
        selectedTipSet.removeAll(blockQueue);
        blockQueue.addAll(selectedTipSet);
        HashMap<Sha256Hash, HashSet<UUID>> approvers = new HashMap<>();
        for (BlockWrap tip : blockQueue) {
            approvers.put(tip.getBlock().getHash(), new HashSet<>());
        }

        BlockWrap currentBlock = null;
        long cutoffHeight = blockService.getCutoffHeight();
        while ((currentBlock = blockQueue.poll()) != null) {
            // Abort if unmaintained
            if (!currentBlock.getBlockEvaluation().isMaintained() 
                    || currentBlock.getBlockEvaluation().getHeight() <= cutoffHeight)
                continue;

            // Add your own hashes as reference if current block is one of the
            // selected tips
            if (selectedTipApprovers.containsKey(currentBlock.getBlockHash()))
                approvers.get(currentBlock.getBlockHash())
                        .addAll(selectedTipApprovers.get(currentBlock.getBlockHash()));

            // Add all current references to both approved blocks (initialize if
            // not yet initialized)
            Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
            subUpdateRating(blockQueue, approvers, currentBlock, prevTrunk);

            Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
            subUpdateRating(blockQueue, approvers, currentBlock, prevBranch);

            // Update your rating if solid
            if (currentBlock.getBlockEvaluation().getSolid() == 2)
                store.updateBlockEvaluationRating(currentBlock.getBlockHash(),
                        approvers.get(currentBlock.getBlockHash()).size());
            approvers.remove(currentBlock.getBlockHash());
        }
    }

    private void subUpdateRating(PriorityQueue<BlockWrap> blockQueue, HashMap<Sha256Hash, HashSet<UUID>> approvers,
            BlockWrap currentBlock, Sha256Hash prevTrunk) throws BlockStoreException {
        if (!approvers.containsKey(prevTrunk)) {
            BlockWrap prevBlock = store.getBlockWrap(prevTrunk);
            if (prevBlock != null) {
                blockQueue.add(prevBlock);
                approvers.put(prevBlock.getBlockHash(), new HashSet<>(approvers.get(currentBlock.getBlockHash())));
            }
        } else {
            approvers.get(prevTrunk).addAll(approvers.get(currentBlock.getBlockHash()));
        }
    }

    /**
     * Updates confirmed field in block evaluation and changes output table
     * correspondingly
     * 
     * @throws BlockStoreException
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonParseException
     */
    private void updateConfirmed(int numberUpdates)
            throws BlockStoreException, JsonParseException, JsonMappingException, IOException {
        // First remove any blocks that should no longer be in the milestone
        HashSet<BlockEvaluation> blocksToRemove = store.getBlocksToUnconfirm();
        HashSet<Sha256Hash> traversedUnconfirms = new HashSet<>();
        for (BlockEvaluation block : blocksToRemove)
            blockGraph.unconfirm(block.getBlockHash(), traversedUnconfirms);

        long cutoffHeight = blockService.getCutoffHeight();
        for (int i = 0; i < numberUpdates; i++) {
            // Now try to find blocks that can be added to the milestone.
            // DISALLOWS UNSOLID
            HashSet<BlockWrap> blocksToAdd = store.getBlocksToConfirm(cutoffHeight);

            // VALIDITY CHECKS
            validatorService.resolveAllConflicts(blocksToAdd, cutoffHeight);

            // Finally add the resolved new milestone blocks to the milestone
            HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
            for (BlockWrap block : blocksToAdd)
                blockGraph.confirm(block.getBlockEvaluation().getBlockHash(), traversedConfirms, cutoffHeight);

            // Exit condition: there are no more blocks to add
            if (blocksToAdd.isEmpty())
                break;

            if (i == WARNING_MILESTONE_UPDATE_LOOPS)
                log.warn("High amount of milestone updates per scheduled update. Can't keep up or reorganizing!");
        }
    }

    /**
     * Updates maintained field in block evaluation. Sets maintained to true if
     * reachable by a rating entry point, else false.
     * 
     * @throws BlockStoreException
     */
    private void updateMaintained() throws BlockStoreException {
        HashSet<Sha256Hash> maintainedBlockHashes = store.getMaintainedBlockHashes();
        HashSet<Sha256Hash> traversedBlockHashes = new HashSet<>();
        PriorityQueue<BlockWrap> blocks = store.getRatingEntryPointsAscending();
        HashSet<BlockWrap> blocksToTraverse = new HashSet<>(blocks);

        // Now set maintained in order of ascending height
        BlockWrap currentBlock = null;
        while ((currentBlock = blocks.poll()) != null) {
            blocksToTraverse.remove(currentBlock);
            traversedBlockHashes.add(currentBlock.getBlockHash());
            List<BlockWrap> solidApproverBlocks = store.getApproverBlocks(currentBlock.getBlockHash());
            for (BlockWrap b : solidApproverBlocks) {
                if (blocksToTraverse.contains(b))
                    continue;
                if (b.getBlockEvaluation().getSolid() < 0)
                    continue;
                if (b.getBlock().getHeight() <= currentBlock.getBlock().getHeight())
                    continue;

                blocks.add(b);
                blocksToTraverse.add(b);
            }
        }

        // Unset no longer maintained blocks
        for (Sha256Hash hash : maintainedBlockHashes.stream().filter(h -> !traversedBlockHashes.contains(h))
                .collect(Collectors.toList()))
            store.updateBlockEvaluationMaintained(hash, false);

        // Set now maintained blocks
        for (Sha256Hash hash : traversedBlockHashes.stream().filter(h -> !maintainedBlockHashes.contains(h))
                .collect(Collectors.toList()))
            store.updateBlockEvaluationMaintained(hash, true);
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

    public boolean checkRewardReferencedBlocks(Block rewardBlock) throws BlockStoreException {
        RewardInfo rewardInfo;
        try {
            rewardInfo = RewardInfo.parse(rewardBlock.getTransactions().get(0).getData());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Check previous reward blocks exist and get their approved sets
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        if (prevRewardHash == null)
            return false;
        Block prevRewardBlock = store.get(prevRewardHash);
        if (prevRewardBlock == null)
            return false;

        // Get all blocks approved by previous reward blocks
        Set<Sha256Hash> allMilestoneBlocks = blockService.getPastMilestoneBlocks(prevRewardHash);
        allMilestoneBlocks.addAll(rewardInfo.getBlocks());

        for (Sha256Hash hash : rewardInfo.getBlocks()) {
            BlockWrap block = store.getBlockWrap(hash);
            if (block == null)
                return false;

            Set<Sha256Hash> requiredBlocks = blockService.getAllRequiredBlockHashes(block.getBlock());
            for (Sha256Hash reqHash : requiredBlocks) {
                BlockWrap req = store.getBlockWrap(reqHash);
                if (req == null && !allMilestoneBlocks.contains(reqHash))
                    return false;

                if (req != null && req.getBlockEvaluation().getSolid() >= 1
                        && block.getBlockEvaluation().getSolid() == 0) {
                    scanWaitingBlocks(req.getBlock(), rewardInfo.getBlocks());
                }
            }
        }

        return true;
    }

    public boolean runConsensusLogic(Block newestBlock) throws BlockStoreException {

        try {
            RewardInfo rewardInfo = RewardInfo.parse(newestBlock.getTransactions().get(0).getData());
            Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
            long currChainLength = store.getRewardChainLength(prevRewardHash) + 1;

            // Consensus logic>
            Sha256Hash oldLongestChainEnd = store.getMaxConfirmedReward().getBlockHash();
            long maxChainLength = store.getRewardChainLength(oldLongestChainEnd);
            if (maxChainLength < currChainLength) {

                // Find block to which to rollback (if at all) and all new chain
                // blocks
                List<BlockWrap> newMilestoneBlocks = new ArrayList<>();
                newMilestoneBlocks.add(store.getBlockWrap(newestBlock.getHash()));
                BlockWrap splitPoint = null;
                Sha256Hash prevHash = prevRewardHash;
                for (int i = 0; i < currChainLength; i++) {
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
                    if (maxConfirmedRewardBlockHash.equals(params.getGenesisBlock().getHash()))
                        throw new RuntimeException("Unset genesis. Shouldn't happen");

                    // Unset the milestone of this one (where milestone =
                    // maxConfRewardblock.chainLength)
                    long milestoneNumber = store.getRewardChainLength(maxConfirmedRewardBlockHash);
                    store.updateUnsetMilestone(milestoneNumber);

                    // Unconfirm anything not confirmed by milestone
                    wipeBlocks = store.getWhereConfirmedNotMilestone();
                    traversedBlockHashes = new HashSet<>();
                    for (Sha256Hash wipeBlock : wipeBlocks)
                        blockGraph.unconfirm(wipeBlock, traversedBlockHashes);
                }

                // Build milestone forwards.
                for (BlockWrap newMilestoneBlock : newMilestoneBlocks) {

                    // Sanity check: if my predecessors are still not fully
                    // solid or invalid, there must be something wrong.
                    Set<Sha256Hash> allRequiredBlockHashes = blockService
                            .getAllRequiredBlockHashes(newMilestoneBlock.getBlock());
                    for (Sha256Hash requiredBlockHash : allRequiredBlockHashes) {
                        if (store.getBlockEvaluation(requiredBlockHash).getSolid() != 2) {
                            log.error("Predecessors are not solidified. This should not happen.");
                            throw new RuntimeException("Predecessors are not solidified. This should not happen.");
                        }
                    }

                    // Sanity check: At this point, predecessors cannot be
                    // missing
                    SolidityState solidityState = validatorService.checkSolidity(newMilestoneBlock.getBlock(), false);
                    if (!solidityState.isSuccessState() && !solidityState.isFailState()) {
                        log.error("The block is not failing or successful. This should not happen.");
                        throw new RuntimeException("The block is not failing or successful. This should not happen.");
                    }

                    // Check: If all is ok, try confirming this milestone.
                    if (solidityState.isSuccessState()) {

                        long cutoffHeight = blockService.getCutoffHeight();
                        
                        // Find conflicts in the dependency set
                        HashSet<BlockWrap> allApprovedNewBlocks = new HashSet<>();
                        blockService.addRequiredUnconfirmedBlocksTo(allApprovedNewBlocks, newMilestoneBlock, cutoffHeight);

                        // If anything is already spent, no-go
                        boolean anySpentInputs = allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates())
                                .flatMap(i -> i.stream()).anyMatch(c -> {
                                    try {
                                        return validatorService.hasSpentDependencies(c);
                                    } catch (BlockStoreException e) {
                                        e.printStackTrace();
                                        return true;
                                    }
                                });

                        // If any conflicts exist between the current set of
                        // blocks, no-go
                        boolean anyCandidateConflicts = allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates())
                                .flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint()))
                                .values().stream().anyMatch(l -> l.size() > 1);

                        // Did we fail? Then we stop now and rerun consensus
                        // logic on the new longest chain.
                        if (anySpentInputs || anyCandidateConflicts) {
                            solidityState = SolidityState.getFailState();

                            // Solidification forward with failState

                            blockGraph.solidifyBlock(newMilestoneBlock.getBlock(), solidityState, false);

                            runConsensusLogic(store.get(oldLongestChainEnd));
                            return false;
                        }

                        // Otherwise, all predecessors exist and were at least
                        // solid > 0,
                        // so we should be able to confirm all of the
                        // predecessors.
                        while (!allApprovedNewBlocks.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            HashSet<BlockWrap> nowApprovedBlocks = (HashSet<BlockWrap>) allApprovedNewBlocks.clone();
                            validatorService.removeWhereIneligible(nowApprovedBlocks);
                            validatorService.removeWhereUsedOutputsUnconfirmed(nowApprovedBlocks);
                            // Only here the current milestone block is
                            // eligible, so readd it:
                            nowApprovedBlocks.add(newMilestoneBlock);

                            // Confirm the addable blocks and remove them from
                            // the list
                            HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
                            for (BlockWrap approvedBlock : nowApprovedBlocks)
                                blockGraph.confirm(approvedBlock.getBlockEvaluation().getBlockHash(),
                                        traversedConfirms, cutoffHeight);

                            allApprovedNewBlocks.removeAll(nowApprovedBlocks);
                        }

                        // Set the milestone on all confirmed non-milestone
                        // blocks
                        long milestoneNumber = store.getRewardChainLength(newMilestoneBlock.getBlockHash());
                        store.updateAllConfirmedToMilestone(milestoneNumber);
                    }

                    // Solidification forward
                    try {
                        blockGraph.solidifyBlock(newMilestoneBlock.getBlock(), solidityState, true);
                        scanWaitingBlocks(newMilestoneBlock.getBlock());
                    } catch (BlockStoreException e) {
                        throw e;
                    }
                }
            }

        } catch (IOException e) {
            // Cannot happen when connecting
            throw new RuntimeException(e);
        }

        return true;
    }
}
