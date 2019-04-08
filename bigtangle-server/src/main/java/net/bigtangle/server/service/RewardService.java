/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;

/**
 * <p>
 * A TransactionService provides service for transactions that send and receive
 * value from user keys. Using these, it is able to create new transactions that
 * spend the recorded transactions, and this is the fundamental operation of the
 * protocol.
 * </p>
 */
@Service
public class RewardService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected FullPrunedBlockGraph blockgraph;
    @Autowired
    private BlockService blockService;
    @Autowired
    protected TipsService tipService;

    @Autowired
    private ValidatorService validatorService;
    @Autowired
    protected NetworkParameters networkParameters;

    private final Semaphore lock = new Semaphore(1);

    private static final Logger logger = LoggerFactory.getLogger(RewardService.class);

    public void performRewardVotingSingleton() throws Exception {
        if (!lock.tryAcquire()) {
            logger.debug("performRewardVoting Update already running. Returning...");
            return;
        }
        synchronized (this) {
            try {
            logger.info("performRewardVoting  started");
            Stopwatch watch = Stopwatch.createStarted();
            performRewardVoting();
            logger.info("performRewardVoting time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        } catch (Exception e) {
            logger.warn("performRewardVoting ", e);
        } finally {
            lock.release();
        }
        }

    }

    /**
     * Runs the reward voting logic: push existing best eligible reward if
     * exists or make a new eligible reward now
     * 
     * @return the new block or block voted on
     * @throws Exception
     */
    public Block performRewardVoting() throws Exception {
        // Find eligible rewards building on top of the newest reward
        Sha256Hash prevRewardHash = store.getMaxConfirmedRewardBlockHash();
        List<Sha256Hash> candidateHashes = store.getRewardBlocksWithPrevHash(prevRewardHash);
        candidateHashes.removeIf(c -> {
            try {
                return store.getRewardEligible(c) != Eligibility.ELIGIBLE;
            } catch (BlockStoreException e) {
                // Cannot happen
                throw new RuntimeException();
            }
        });

        // Find the one most likely to win
        List<BlockWrap> candidates = blockService.getBlockWraps(candidateHashes);
        BlockWrap votingTarget = candidates.stream()
                .max(Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getRating())).orElse(null);

        // If exists, push that one, else make new one
        if (votingTarget != null) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPairStartingFrom(votingTarget);
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            blockgraph.add(r1.createNextBlock(r2), false);
            return votingTarget.getBlock();
        } else {
            return createAndAddMiningRewardBlock();
        }
    }

    public Block createAndAddMiningRewardBlock() throws Exception {
        logger.info("createAndAddMiningRewardBlock  started");

        Sha256Hash prevRewardHash = store.getMaxConfirmedRewardBlockHash();
        return createAndAddMiningRewardBlock(prevRewardHash);

    }

    public Block createAndAddMiningRewardBlock(Sha256Hash prevRewardHash) throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        return createAndAddMiningRewardBlock(prevRewardHash, tipsToApprove.getLeft(), tipsToApprove.getRight());
    }

    public Block createAndAddMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws Exception {
        return createAndAddMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, false);
    }

    public Block createAndAddMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws Exception {

        Block block = createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, override);
        if (block != null)
            blockgraph.add(block, false);
        return block;
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws BlockStoreException {
        return createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, false);
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws BlockStoreException {
        Triple<Eligibility, Transaction, Pair<Long, Long>> result = validatorService.makeReward(prevTrunk, prevBranch,
                prevRewardHash);

        if (result.getLeft() != Eligibility.ELIGIBLE) {
            if (!override) {
                logger.warn("Generated reward block is deemed ineligible! Try again somewhere else?");
                return null;
            }
            logger.warn("Generated reward block is deemed ineligible! Overriding.. ");
        }

        Block r1 = blockService.getBlock(prevTrunk);
        Block r2 = blockService.getBlock(prevBranch);

        Block block = new Block(networkParameters, r1, r2);
        block.setBlockType(Block.Type.BLOCKTYPE_REWARD);

        // Make the new block
        block.addTransaction(result.getMiddle());
        block.setDifficultyTarget(result.getRight().getLeft());
        block.setLastMiningRewardBlock(Math.max(r1.getLastMiningRewardBlock(), r2.getLastMiningRewardBlock()) + 1);

        // Enforce timestamp equal to previous max for reward blocktypes
        block.setTime(Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

        block.solve();
        return block;
    }

}
