/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.StoredBlock;
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

    private static final Logger logger = LoggerFactory.getLogger(UnsolidBlockService.class);

    protected final ReentrantLock lock = Threading.lock("UnsolidBlockService");

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            logger.debug(this.getClass().getName() + " UnsolidBlockService running. Returning...");
            return;
        }

        try {

            logger.debug(" Start updateUnsolideServiceSingle: ");
            deleteOldUnsolidBlock();
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
        List<Block> blocklist = store.getNonSolidBlocks();
        for (Block block : blocklist) {
            StoredBlock added = blockgraph.add(block, true);
            if (added != null) {
                this.store.deleteUnsolid(block.getHash());
                logger.debug("addConnected from reCheckUnsolidBlock " + block);
                continue;
            }
            requestPrev(block);
        }
    }

    public void requestPrev(Block block) {
        try {
            if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
                return;
            }
            StoredBlock storedBlock0 = this.store.get(block.getPrevBlockHash());

            if (storedBlock0 == null) {
                byte[] re = blockRequester.requestBlock(block.getPrevBlockHash());
                if (re != null) {
                    Block req = (Block) networkParameters.getDefaultSerializer().makeBlock(re);

                    if (this.store.get(req.getPrevBlockHash()) != null
                            && this.store.get(req.getPrevBranchBlockHash()) != null)
                        blockgraph.add(req, true);
                    else {
                        // pre recursive check
                        logger.debug(" prev not found: " + req.toString());
                        requestPrev(req);
                    }

                }
            }
            StoredBlock storedBlock1 = this.store.get(block.getPrevBranchBlockHash());
            if (storedBlock1 == null) {
                byte[] re = blockRequester.requestBlock(block.getPrevBranchBlockHash());
                if (re != null) {
                    Block req = (Block) networkParameters.getDefaultSerializer().makeBlock(re);

                    if (this.store.get(req.getPrevBlockHash()) != null
                            && this.store.get(req.getPrevBranchBlockHash()) != null)
                        blockgraph.add(req, true);
                    else {
                        // pre recursive check
                        requestPrev(req);
                    }

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
