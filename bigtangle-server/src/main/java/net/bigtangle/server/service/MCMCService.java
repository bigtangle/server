/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

 
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.DepthAndWeight;
import net.bigtangle.server.data.Rating;
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Threading;

/*
 *  This service offers maintenance functions to update the local state of the Tangle
 */
@Service
public class MCMCService {
    private static final Logger log = LoggerFactory.getLogger(MCMCService.class);

    @Autowired
    protected FullBlockGraph blockGraph;

    @Autowired
    private TipsService tipsService;

    @Autowired
    private BlockService blockService;

  
    @Autowired
    private StoreService storeService;

    /**
     * Scheduled update function that updates the Tangle
     * 
     * @throws BlockStoreException
     */

    protected final ReentrantLock lock = Threading.lock("mcmcService");

    public void startSingleProcess() {
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            log.debug(this.getClass().getName() + "  mcmcService running. Returning...");
            return;
        }

        try {
            // log.info("mcmcService started");
            Stopwatch watch = Stopwatch.createStarted();
             update();
            log.info(  "mcmcService time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            log.error("mcmcService ", e);
        } finally {
            lock.unlock();
        }

    }

    private String updateBoxed() throws InterruptedException, ExecutionException {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {
                update();
                return "finish";
            }
        }); 
        try {
           return handler.get(3000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.debug(" mcmcService  Timeout  ");
            handler.cancel(true);
            return "cancel";
        } finally {
            executor.shutdownNow();
        }
    
    }

    public void update() throws InterruptedException, ExecutionException, BlockStoreException {

        FullBlockStore store = storeService.getStore();

        try { 
            updateWeightAndDepth(store);
            updateRating(store);
            deleteMCMC(store);
        } catch (Exception e) {
            log.debug("update  ", e);
        } finally { 
            store.close();
        }

    }

    
    private void deleteMCMC(FullBlockStore store) throws BlockStoreException { 
        store.deleteMCMC(store.getMaxConfirmedReward().getChainLength() - 500);
    }
    /**
     * Update cumulative weight: the amount of blocks a block is approved by.
     * Update depth: the longest chain of blocks to a tip. Allows unsolid blocks
     * too.
     * 
     * @throws BlockStoreException
     */
    private void updateWeightAndDepth(FullBlockStore store) throws BlockStoreException {
        // Begin from the highest maintained height blocks and go backwards from
        // there
        long cutoffHeight = blockService.getCurrentCutoffHeight(store);
        long maxHeight = blockService.getCurrentMaxHeight(store);
        PriorityQueue<BlockWrap> blockQueue = store.getSolidBlocksInIntervalDescending(cutoffHeight, maxHeight);
        HashMap<Sha256Hash, HashSet<Sha256Hash>> approvers = new HashMap<>();
        HashMap<Sha256Hash, Long> depths = new HashMap<>();

        // Initialize weight and depth of blocks
        for (BlockWrap block : blockQueue) {
            approvers.put(block.getBlockHash(), new HashSet<>());
            depths.put(block.getBlockHash(), 0L);
        }

        BlockWrap currentBlock = null;
        List<DepthAndWeight> depthAndWeight = new ArrayList<DepthAndWeight>();
        while ((currentBlock = blockQueue.poll()) != null) {
            Sha256Hash currentBlockHash = currentBlock.getBlockHash();

            // Abort if unmaintained, since it will be irrelevant for any tip
            // selections
            if (currentBlock.getBlockEvaluation().getHeight() <= cutoffHeight)
                continue;

            // Add your own hash to approver hashes of current approver hashes
            approvers.get(currentBlockHash).add(currentBlockHash);

            // Add all current references to both approved blocks
            Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
            subUpdateWeightAndDepth(blockQueue, approvers, depths, currentBlockHash, prevTrunk, store);

            Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
            subUpdateWeightAndDepth(blockQueue, approvers, depths, currentBlockHash, prevBranch, store);

            // Update and dereference
            depthAndWeight.add(new DepthAndWeight(currentBlock.getBlockHash(), approvers.get(currentBlockHash).size(),
                    depths.get(currentBlockHash)));
            approvers.remove(currentBlockHash);
            depths.remove(currentBlockHash);
        }
        store.updateBlockEvaluationWeightAndDepth(depthAndWeight);
    }

    private void subUpdateWeightAndDepth(PriorityQueue<BlockWrap> blockQueue,
            HashMap<Sha256Hash, HashSet<Sha256Hash>> approvers, HashMap<Sha256Hash, Long> depths,
            Sha256Hash currentBlockHash, Sha256Hash approvedBlockHash, FullBlockStore store)
            throws BlockStoreException {
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
     * Update rating: the percentage of times that tips selected by MCMC approve
     * a block. Allows unsolid blocks too.
     * 
     * @throws BlockStoreException
     */
    private void updateRating(FullBlockStore store) throws BlockStoreException {
        // Select #tipCount solid tips via MCMC
        HashMap<Sha256Hash, HashSet<UUID>> selectedTipApprovers = new HashMap<Sha256Hash, HashSet<UUID>>(
                NetworkParameters.NUMBER_RATING_TIPS);

        long cutoffHeight = blockService.getCurrentCutoffHeight(store);
        long maxHeight = blockService.getCurrentMaxHeight(store);

        Collection<BlockWrap> selectedTips = tipsService.getRatingTips(NetworkParameters.NUMBER_RATING_TIPS, maxHeight,
                store);

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
        PriorityQueue<BlockWrap> blockQueue = store.getSolidBlocksInIntervalDescending(cutoffHeight, maxHeight);
        HashSet<BlockWrap> selectedTipSet = new HashSet<>(selectedTips);
        selectedTipSet.removeAll(blockQueue);
        blockQueue.addAll(selectedTipSet);
        HashMap<Sha256Hash, HashSet<UUID>> approvers = new HashMap<>();
        for (BlockWrap tip : blockQueue) {
            approvers.put(tip.getBlock().getHash(), new HashSet<>());
        }

        BlockWrap currentBlock = null;
        List<Rating> ratings = new ArrayList<Rating>();
        while ((currentBlock = blockQueue.poll()) != null) {
            // Abort if unmaintained
            if (currentBlock.getBlockEvaluation().getHeight() <= cutoffHeight)
                continue;

            // Add your own hashes as reference if current block is one of the
            // selected tips
            if (selectedTipApprovers.containsKey(currentBlock.getBlockHash()))
                approvers.get(currentBlock.getBlockHash())
                        .addAll(selectedTipApprovers.get(currentBlock.getBlockHash()));

            // Add all current references to both approved blocks (initialize if
            // not yet initialized)
            Sha256Hash prevTrunk = currentBlock.getBlock().getPrevBlockHash();
            subUpdateRating(blockQueue, approvers, currentBlock, prevTrunk, store);

            Sha256Hash prevBranch = currentBlock.getBlock().getPrevBranchBlockHash();
            subUpdateRating(blockQueue, approvers, currentBlock, prevBranch, store);

            // Update your rating if solid
            if (currentBlock.getBlockEvaluation().getSolid() == 2)
                ratings.add(new Rating(currentBlock.getBlockHash(), approvers.get(currentBlock.getBlockHash()).size()));
            approvers.remove(currentBlock.getBlockHash());
        }
        store.updateBlockEvaluationRating(ratings);

    }

    private void subUpdateRating(PriorityQueue<BlockWrap> blockQueue, HashMap<Sha256Hash, HashSet<UUID>> approvers,
            BlockWrap currentBlock, Sha256Hash prevTrunk, FullBlockStore store) throws BlockStoreException {
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

}
