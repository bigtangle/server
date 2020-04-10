/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.core.BatchBlock;
import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.BlockService;
 
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Threading;

@Component
@EnableAsync
public class BlockPrototypeService {

    private static final Logger logger = LoggerFactory.getLogger(BlockPrototypeService.class);

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    private NetworkParameters networkParameters;
 
    @Autowired
    private BlockService blockService;

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    ServerConfiguration serverConfiguration;

    @Scheduled(fixedRate = 10000) 
    public void batch() {
        if (scheduleConfiguration.isMilestone_active() && serverConfiguration.checkService()) {
            startSingleProcess();
        }

    }

    protected final ReentrantLock lock = Threading.lock("BlockPrototypeService");

    public void startSingleProcess() {
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            logger.debug(this.getClass().getName() + "  Update already running. Returning...");
            return;
        }

        logger.info("BlockPrototypeService start");
        try {
            blockprototype();
        } catch (Exception e) {
            logger.info("BlockPrototypeService error", e);
        } finally {
            lock.unlock();
        }

    }

    private void blockprototype() throws BlockStoreException, Exception {
        List<BatchBlock> batchBlocks = this.store.getBatchBlockList();
        if (batchBlocks.isEmpty()) {
            return;
        }
        Block block = blockService.getBlockPrototype();
        for (BatchBlock batchBlock : batchBlocks) {
            byte[] payloadBytes = batchBlock.getBlock();
            Block putBlock = this.networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
            for (Transaction transaction : putBlock.getTransactions()) {
                block.addTransaction(transaction);
            }
        }
        if (block.getTransactions().size() == 0) {
            return;
        }
        block.solve();
        blockService.saveBlock(block);
        for (BatchBlock batchBlock : batchBlocks) {
            this.store.deleteBatchBlock(batchBlock.getHash());
        }
    }
}
