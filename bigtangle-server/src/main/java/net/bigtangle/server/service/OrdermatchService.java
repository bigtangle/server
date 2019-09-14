/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

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
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException;
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
public class OrdermatchService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected FullPrunedBlockGraph blockgraph;
    @Autowired
    private BlockService blockService;
    @Autowired
    protected TipsService tipService;
    @Autowired
    private RewardService rewardService;

    @Autowired
    protected NetworkParameters networkParameters;

    private   final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final ReentrantLock lock = Threading.lock("OrdermatchService");
 

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            logger.debug(this.getClass().getName() + "  OrdermatchService running. Returning...");
            return;
        }

        try { 
            
                logger.debug(" Start updateOrderMatching: ");

                Stopwatch watch = Stopwatch.createStarted();
                performOrderMatchingVoting();
                logger.info("performOrderMatchingVoting time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

            } catch (VerificationException e1) {
                logger.debug(" Infeasible updateOrderMatching: ", e1);
            } catch (Exception e) {
                logger.error("updateOrderMatching ", e);
            } finally {
                lock.unlock();;
            }

    }

    /**
     * Runs the order matching voting logic: push existing best eligible order
     * matching if exists or make a new eligible order matching now
     * 
     * @return the new block or block voted on
     * @throws Exception
     */
    public Block performOrderMatchingVoting() throws Exception {
        // Merged with reward service
        return null;
    }

    public Block createAndAddOrderMatchingBlock() throws Exception {

        Sha256Hash prevHash = store.getMaxConfirmedReward().getHash();
        return createAndAddOrderMatchingBlock(prevHash);

    }

    public Block createAndAddOrderMatchingBlock(Sha256Hash prevHash) throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        return createAndAddOrderMatchingBlock(prevHash, tipsToApprove.getLeft(), tipsToApprove.getRight());
    }

    public Block createAndAddOrderMatchingBlock(Sha256Hash prevHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws Exception {
        return createAndAddOrderMatchingBlock(prevHash, prevTrunk, prevBranch, false);
    }

    public Block createAndAddOrderMatchingBlock(Sha256Hash prevHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws Exception {

        Block block = createOrderMatchingBlock(prevHash, prevTrunk, prevBranch, override);
        if (block != null)
            blockService.saveBlock(block);
        return block;
    }

    public Block createOrderMatchingBlock(Sha256Hash prevHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws BlockStoreException, NoBlockException {
        return createOrderMatchingBlock(prevHash, prevTrunk, prevBranch, false);
    }

    public Block createOrderMatchingBlock(Sha256Hash prevHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws BlockStoreException, NoBlockException {
        
        return rewardService.createMiningRewardBlock(prevHash, prevTrunk, prevBranch, override);
    }

}
