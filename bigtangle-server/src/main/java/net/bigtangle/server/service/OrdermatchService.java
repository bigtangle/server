/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Comparator;
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
import net.bigtangle.core.OrderMatchingInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.InfeasiblePrototypeException;
import net.bigtangle.server.core.BlockWrap;
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
        // Find eligible order matchings building on top of the newest order
        // matching
        Sha256Hash prevHash = store.getMaxConfirmedOrderMatchingBlockHash();
        List<Sha256Hash> candidateHashes = store.getOrderMatchingBlocksWithPrevHash(prevHash);
        candidateHashes.removeIf(c -> {
            try {
                return store.getOrderMatchingEligible(c) != Eligibility.ELIGIBLE;
            } catch (BlockStoreException e) {
                // Cannot happen
                throw new RuntimeException();
            }
        });

        // Sort by rating
        List<BlockWrap> candidates = blockService.getBlockWraps(candidateHashes);
        candidates.sort(Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getRating()));
        
        // If exists, push that one, else make new one
        while (!candidates.isEmpty()) {
            BlockWrap votingTarget = candidates.get(candidates.size()-1);
            candidates.remove(candidates.size()-1);
            try {
                Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPairStartingFrom(votingTarget);
                Block r1 = blockService.getBlock(tipsToApprove.getLeft());
                Block r2 = blockService.getBlock(tipsToApprove.getRight());
                blockService.saveBlock(r1.createNextBlock(r2));
                return votingTarget.getBlock();
            } catch (InfeasiblePrototypeException e) {
                continue;
            }
        }
    
        return createAndAddOrderMatchingBlock();
    }

    public Block createAndAddOrderMatchingBlock() throws Exception {

        Sha256Hash prevHash = store.getMaxConfirmedOrderMatchingBlockHash();
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
            throws BlockStoreException {
        return createOrderMatchingBlock(prevHash, prevTrunk, prevBranch, false);
    }

    public Block createOrderMatchingBlock(Sha256Hash prevHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws BlockStoreException {

        BlockWrap r1 = blockService.getBlockWrap(prevTrunk);
        BlockWrap r2 = blockService.getBlockWrap(prevBranch);

        Block block = new Block(networkParameters, r1.getBlock(), r2.getBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_ORDER_MATCHING);
        block.setHeigth( Math.max(r1.getBlock().getHeigth(), r2.getBlock().getHeigth()) + 1);

        OrderMatchingInfo info = new OrderMatchingInfo(
                store.getOrderMatchingToHeight(prevHash) - NetworkParameters.ORDER_MATCHING_OVERLAP_SIZE,
                Math.max(r1.getBlockEvaluation().getHeight(), r2.getBlockEvaluation().getHeight()) + 1, prevHash);

        // Add the data
        Transaction tx = new Transaction(networkParameters);
        tx.setData(info.toByteArray());
        block.addTransaction(tx);
        
        // If infeasible currently, return no new block
        if (Math.subtractExact(info.getToHeight(), store
                .getOrderMatchingToHeight(prevHash)) < NetworkParameters.ORDER_MATCHING_MIN_HEIGHT_INTERVAL - 1) {
            logger.warn("Generated ordermatching block is deemed ineligible! Try again somewhere else?");
            return null;
        }

        // Else return the new block
        block.solve();
        return block;
    }

}
