/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.Threading;

/**
 * <p>
 * A TransactionService provides service for transactions that send and receive
 * value from user keys. Using these, it is able to create new transactions that
 * spend the recorded transactions, and this is the fundamental operation of the
 * protocol.
 * </p>
 */
@Service
public class OrderReclaimService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected FullPrunedBlockGraph blockgraph;
    @Autowired
    private BlockService blockService;
    @Autowired
    protected TipsService tipService;

    @Autowired
    protected NetworkParameters networkParameters;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final ReentrantLock lock = Threading.lock("OrderReclaimService");

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            logger.debug(this.getClass().getName() + "  Update already running. Returning...");
            return;
        }

        try {

            logger.debug(" Start performOrderReclaimMaintenance: ");
            Stopwatch watch = Stopwatch.createStarted();
            performOrderReclaimMaintenance();
            logger.info("performOrderReclaimMaintenance time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

        } catch (Exception e) {
            logger.warn("performOrderReclaimMaintenance ", e);
        } finally {
            lock.unlock();
        }

    }

    /**
     * Generates an order reclaim block for the given order and order matching
     * hash
     * 
     * @param reclaimedOrder
     * @param orderMatchingHash
     * @return generated block
     * @throws Exception
     */
    public Block createAndAddOrderReclaim(Sha256Hash reclaimedOrder, Sha256Hash orderMatchingHash) throws Exception {
        Transaction tx = new Transaction(networkParameters);
        OrderReclaimInfo info = new OrderReclaimInfo(0, reclaimedOrder, orderMatchingHash);
        tx.setData(info.toByteArray());

        // Create block with order reclaim
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());

        Block block = r1.createNextBlock(r2);
        block.addTransaction(tx);
        block.setBlockType(Block.Type.BLOCKTYPE_ORDER_RECLAIM);
        block.setHeigth( Math.max(r1.getHeigth(), r2.getHeigth()) + 1);

        block.solve();
        blockService.saveBlock(block);
        return block;
    }

    /**
     * Runs the order reclaim logic: look for lost orders and issue reclaims for
     * the orders
     * 
     * @return the new blocks
     * @throws Exception
     */
    public List<Block> performOrderReclaimMaintenance() throws Exception {
        // Find height from which on all orders are finished
        Sha256Hash prevHash = store.getMaxConfirmedOrderMatchingBlockHash();
        long finishedHeight = store.getOrderMatchingToHeight(prevHash);

        // Find orders that are unspent confirmed with height lower than the
        // passed matching
        List<Sha256Hash> lostOrders = store.getLostOrders(finishedHeight);

        // Perform reclaim for each of the lost orders
        List<Block> result = new ArrayList<>();
        for (Sha256Hash lostOrder : lostOrders) {
            result.add(createAndAddOrderReclaim(lostOrder, prevHash));
        }
        return result;
    }

}
