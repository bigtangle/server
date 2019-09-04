/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.core.BlockWrap;
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

    /**
     * Triggers a deep reorg calculation which may be very expensive.
     * 
     * @throws BlockStoreException
     */
    public void triggerDeepReorg(long timeUnits, TimeUnit unit) throws BlockStoreException {
        // Find reorganize entry point
        long height = findDeepReorgHeight(timeUnits, unit);

        // Use reorganize entry point height to reorganize
        triggerDeepReorg(height);
    }

    /**
     * Find height where blocks exist that have most of the recent blocks as
     * approver
     * 
     * @return height where blocks exist that have most of the recent blocks as
     *         approver
     * @throws BlockStoreException
     */
    public long findDeepReorgHeight(long timeUnits, TimeUnit timeUnit) throws BlockStoreException {
        final long currTime = System.currentTimeMillis() / 1000;
        final long fromTime = currTime - timeUnit.toSeconds(timeUnits);
        List<Sha256Hash> blocks = store.getBlocksOfTimeHigherThan(fromTime);

        // Set target approval rate to complement of percentage required for
        // confirmation
        final long requiredApprovals = blocks.size() * 50 / 100;

        // Select #tipCount solid tips via MCMC
        HashMap<Sha256Hash, HashSet<UUID>> approverCountInits = new HashMap<Sha256Hash, HashSet<UUID>>(blocks.size());

        // Initialize all approvers as UUID
        for (Sha256Hash b : blocks) {
            UUID randomUUID = UUID.randomUUID();
            if (approverCountInits.containsKey(b)) {
                HashSet<UUID> result = approverCountInits.get(b);
                result.add(randomUUID);
            } else {
                HashSet<UUID> result = new HashSet<>();
                result.add(randomUUID);
                approverCountInits.put(b, result);
            }
        }

        // Begin from the highest solid height tips and go backwards from there
        PriorityQueue<BlockWrap> blockQueue = store.getSolidTipsDescending();
        HashMap<Sha256Hash, HashSet<UUID>> approvers = new HashMap<>();
        for (BlockWrap tip : blockQueue) {
            approvers.put(tip.getBlock().getHash(), new HashSet<>());
        }

        BlockWrap currentBlock = null;
        while ((currentBlock = blockQueue.poll()) != null) {
            // Add your own hash if block is selected block
            if (approverCountInits.containsKey(currentBlock.getBlockHash()))
                approvers.get(currentBlock.getBlockHash()).addAll(approverCountInits.get(currentBlock.getBlockHash()));

            // Stop if sufficient approval rate and confirmed
            if (approvers.get(currentBlock.getBlockHash()).size() >= requiredApprovals
                    && currentBlock.getBlockEvaluation().isConfirmed())
                break;

            // Add all current references to both approved blocks
            Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
            propagateToPredecessors(blockQueue, approvers, currentBlock, prevTrunk);

            Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
            propagateToPredecessors(blockQueue, approvers, currentBlock, prevBranch);

            // Housekeeping
            approvers.remove(currentBlock.getBlockHash());
        }

        if (currentBlock != null)
            return Math.max(1, currentBlock.getBlockEvaluation().getHeight());
        else
            return 1;
    }

    private void propagateToPredecessors(PriorityQueue<BlockWrap> blockQueue,
            HashMap<Sha256Hash, HashSet<UUID>> approvers, BlockWrap currentBlock, Sha256Hash prevTrunk)
            throws BlockStoreException {
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
     * Triggers a deep reorg calculation from the specified height which may be
     * very expensive.
     * 
     * @param height
     * @throws BlockStoreException
     */
    public void triggerDeepReorg(long height) throws BlockStoreException {
        synchronized (this) {
            if (height <= 0)
                throw new IllegalArgumentException();

            // Unconfirm all blocks above specified height
            // TODO not allowed if pruned data after this height.
            unconfirmFromHeight(height);

            // Reset maintenance
            store.updateAllBlocksMaintained();

            // Recompute milestone depths
            updateMilestoneDepth();

            // Now just do a normal update
            update(Integer.MAX_VALUE);
        }
    }

    private void unconfirmFromHeight(long height) throws BlockStoreException {
        List<Sha256Hash> blocksToRemove = store.getConfirmedBlocksOfHeightHigherThan(height);
        HashSet<Sha256Hash> traversedUnconfirms = new HashSet<>();
        for (Sha256Hash block : blocksToRemove)
            blockGraph.unconfirm(block, traversedUnconfirms);
    }

    /**
     * Scheduled update function that updates the Tangle
     * 
     * @throws BlockStoreException
     */
    public void update() throws BlockStoreException {
        update(Integer.MAX_VALUE);
    }

    /**
     * Scheduled update function that updates the Tangle
     * 
     * @throws BlockStoreException
     */

    protected final ReentrantLock lock = Threading.lock("milestoneService");
  

    public void update(int numberUpdates) {
        if (!lock.tryLock()) {
            log.debug(this.getClass().getName() + "  Update already running. Returning...");
            return;
        }

        try {
            log.trace("Milestone Update started");
            // clearCacheBlockEvaluations();
            Stopwatch watchAll = Stopwatch.createStarted();
            Stopwatch watch = Stopwatch.createStarted();
            updateMaintained();
            log.trace("Maintained update 1 time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

            watch.stop();
            watch = Stopwatch.createStarted();
            updateWeightAndDepth();
            log.trace("Weight and depth update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

            watch.stop();
            watch = Stopwatch.createStarted();
            updateRating();
            log.trace("Rating update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

            watch.stop();
            watch = Stopwatch.createStarted();
            updateConfirmed(numberUpdates);
            log.trace("Milestone update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

            watch.stop();
            watch = Stopwatch.createStarted();
            updateMilestoneDepth();
            log.trace("Milestonedepth update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

            watch.stop();
            watch = Stopwatch.createStarted();
            updateMaintained();
            log.trace("Maintained update 2 time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

            watch.stop();
            
            log.debug("Maintained update 1 time {} ms.", watchAll.elapsed(TimeUnit.MILLISECONDS));

            watchAll.stop();
            
        } catch (Exception e) {
            log.warn("", e);
        } finally {
            lock.unlock();
        }

    }

  
    /**
     * Update cumulative weight: the amount of blocks a block is approved by.
     * Update depth: the longest chain of blocks to a tip
     * 
     * @throws BlockStoreException
     */
    private void updateWeightAndDepth() throws BlockStoreException {
        // Begin from the highest maintained height blocks and go backwards from there
        PriorityQueue<BlockWrap> blockQueue = store.getMaintainedBlocksDescending();
        HashMap<Sha256Hash, HashSet<Sha256Hash>> approvers = new HashMap<>();
        HashMap<Sha256Hash, Long> depths = new HashMap<>();

        // Initialize weight and depth of tips
        for (BlockWrap tip : blockQueue) {
            approvers.put(tip.getBlockHash(), new HashSet<>());
            depths.put(tip.getBlockHash(), 0L);
        }

        BlockWrap currentBlock = null;
        while ((currentBlock = blockQueue.poll()) != null) {
            Sha256Hash currentBlockHash = currentBlock.getBlockHash();

            // Abort if unmaintained, since it will be irrelevant for any tip
            // selections
            if (!currentBlock.getBlockEvaluation().isMaintained())
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
        while ((currentBlock = blockQueue.poll()) != null) {
            // Abort if unmaintained, since it will be irrelevant
            if (!currentBlock.getBlockEvaluation().isMaintained())
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
     * a block
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

        // Begin from the highest solid height tips and go backwards from there
        PriorityQueue<BlockWrap> blockQueue = store.getSolidTipsDescending();
        HashMap<Sha256Hash, HashSet<UUID>> approvers = new HashMap<>();
        for (BlockWrap tip : blockQueue) {
            approvers.put(tip.getBlock().getHash(), new HashSet<>());
        }

        BlockWrap currentBlock = null;
        while ((currentBlock = blockQueue.poll()) != null) {
            // Abort if unmaintained and confirmed only (for now) TODO
            if (!currentBlock.getBlockEvaluation().isMaintained() && currentBlock.getBlockEvaluation().isConfirmed())
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

            // Update your rating
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
     */
    private void updateConfirmed(int numberUpdates) throws BlockStoreException {
        // First remove any blocks that should no longer be in the milestone
        HashSet<BlockEvaluation> blocksToRemove = store.getBlocksToUnconfirm();
        HashSet<Sha256Hash> traversedUnconfirms = new HashSet<>();
        for (BlockEvaluation block : blocksToRemove)
            blockGraph.unconfirm(block.getBlockHash(), traversedUnconfirms);

        for (int i = 0; i < numberUpdates; i++) {
            // Now try to find blocks that can be added to the milestone
            HashSet<BlockWrap> blocksToAdd = store.getBlocksToConfirm();

            // VALIDITY CHECKS
            validatorService.resolveAllConflicts(blocksToAdd);

            // Finally add the resolved new milestone blocks to the milestone
            HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
            for (BlockWrap block : blocksToAdd)
                blockGraph.confirm(block.getBlockEvaluation().getBlockHash(), traversedConfirms);

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
}
