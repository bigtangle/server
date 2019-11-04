/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Stopwatch;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;

/*
 *  This service offers maintenance functions to update the local state of the Tangle
 */
@Service
public class ConfirmationService {
    private static final Logger log = LoggerFactory.getLogger(ConfirmationService.class);
    private static final int WARNING_MILESTONE_UPDATE_LOOPS = 20;

    @Autowired
    protected FullPrunedBlockGraph blockGraph;
    @Autowired
    protected FullPrunedBlockStore store;
 
    @Autowired
    private ValidatorService validatorService;
    @Autowired
    private BlockService blockService;
 

    public void startSingleProcess() {
        if (blockGraph.chainlock.isHeldByCurrentThread() || !blockGraph.chainlock.tryLock()) {
            log.debug(this.getClass().getName() + "  ConfirmationService running. Returning...");
            return;
        }

        try {
            Stopwatch watch = Stopwatch.createStarted();
            update();
            log.info("ConfirmationService time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            log.error("ConfirmationService ", e);
        } finally {
            blockGraph.chainlock.unlock();
        }

    }

   
    public void update( ) throws BlockStoreException {
 
        try { 
            store.beginDatabaseBatchWrite();
            updateConfirmed(10);
            store.commitDatabaseBatchWrite();
        } catch (Exception e) {
            log.debug("updateConfirmed ", e);
            store.abortDatabaseBatchWrite();
        } finally {
            store.defaultDatabaseBatchWrite();
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
            TreeSet<BlockWrap> blocksToAdd = store.getBlocksToConfirm(cutoffHeight);

            // VALIDITY CHECKS
            validatorService.resolveAllConflicts(blocksToAdd, cutoffHeight);

            // Finally add the resolved new blocks to the confirmed set
            HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
            for (BlockWrap block : blocksToAdd)
                blockGraph.confirm(block.getBlockEvaluation().getBlockHash(), traversedConfirms, cutoffHeight, -1);

            // Exit condition: there are no more blocks to add
            if (blocksToAdd.isEmpty())
                break;

            if (i == WARNING_MILESTONE_UPDATE_LOOPS)
                log.warn("High amount of milestone updates per scheduled update. Can't keep up or reorganizing!");
        }
    }
}
