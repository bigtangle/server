/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.Context;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Threading;

/**
 * <p>
 * Provides services for blocks.
 * </p>
 */
@Service
public class UnsolidBlockService {

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    FullPrunedBlockGraph blockgraph;

    @Autowired
    private BlockRequester blockRequester;

    @Autowired
    private BlockService blockService;

    private static final Logger logger = LoggerFactory.getLogger(UnsolidBlockService.class);

    protected final ReentrantLock lock = Threading.lock("UnsolidBlockService");

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            logger.debug(this.getClass().getName() + " UnsolidBlockService running. Returning...");
            return;
        }

        try {
            logger.debug(" Start updateUnsolideServiceSingle: ");
            Context context = new Context(networkParameters);
            Context.propagate(context);
            blockRequester.diff();
            // deleteOldUnsolidBlock();
            reCheckUnsolidBlock();

            logger.debug(" end  updateUnsolideServiceSingle: ");

        } catch (Exception e) {
            logger.warn("updateUnsolideService ", e);
        } finally {
            lock.unlock();
            ;
        }

    }

    /*
     * unsolid blocks can be solid, if previous can be found in network etc.
     * read data from table oder by insert time, use add Block to check again,
     * if missing previous, it may request network for the blocks
     * 
     * BOOT_STRAP_SERVERS de.kafka.bigtangle.net:9092
     * 
     * CONSUMERIDSUFFIX 12324
     */
    public void reCheckUnsolidBlock() throws Exception {
        List<Sha256Hash> storedBlocklist = store.getNonSolidMissingBlocks();

        for (Sha256Hash storedBlock : storedBlocklist) {
            if (storedBlock != null) {
                Block req = blockService.getBlock(storedBlock);

                if (req != null) {
                    store.updateMissingBlock(storedBlock, false);
                    // if the block is there, now scan the rest unsolid blocks
                    if (store.getBlockEvaluation(req.getHash()).getSolid() >= 1) {
                        try {
                            store.beginDatabaseBatchWrite();
                            blockgraph.scanWaitingBlocks(req, true);
                            store.commitDatabaseBatchWrite();
                        } catch (BlockStoreException | VerificationException e) {
                            logger.debug(e.getLocalizedMessage(), e);

                            store.abortDatabaseBatchWrite();
                            throw e;

                        }
                    }
                } else {
                    requestPrevBlock(storedBlock);
                }
            }
        }
    }

    public void requestPrevBlock(Sha256Hash hash) {
        try {
            byte[] re = blockRequester.requestBlock(hash);
            if (re != null) {
                Block req = (Block) networkParameters.getDefaultSerializer().makeBlock(re);

                blockgraph.add(req, true);

            }
        } catch (Exception e) {
            logger.debug("", e);
        }
    }

    public void requestPrev(Block block) {
        try {
            if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
                return;
            }

            Block storedBlock0 = null;
            try {
                storedBlock0 = blockService.getBlock(block.getPrevBlockHash());
            } catch (NoBlockException e) {
                // Ok, no prev
            }

            if (storedBlock0 == null) {
                byte[] re = blockRequester.requestBlock(block.getPrevBlockHash());
                if (re != null) {
                    Block req = (Block) networkParameters.getDefaultSerializer().makeBlock(re);
                    blockgraph.add(req, true);
                }
            }
            Block storedBlock1 = null;

            try {
                storedBlock1 = blockService.getBlock(block.getPrevBranchBlockHash());
            } catch (NoBlockException e) {
                // Ok, no prev
            }

            if (storedBlock1 == null) {
                byte[] re = blockRequester.requestBlock(block.getPrevBranchBlockHash());
                if (re != null) {
                    Block req = (Block) networkParameters.getDefaultSerializer().makeBlock(re);
                    blockgraph.add(req, true);
                }
            }
        } catch (Exception e) {
            logger.debug("", e);
        }
    }

    /*
     * all very old unsolid blocks are deleted
     */
    public void deleteOldUnsolidBlock() throws Exception {

        this.store.deleteOldUnsolid(getTimeSeconds(1));
    }

    public long getTimeSeconds(int days) throws Exception {
        return System.currentTimeMillis() / 1000 - days * 60 * 24 * 60;
    }

}
