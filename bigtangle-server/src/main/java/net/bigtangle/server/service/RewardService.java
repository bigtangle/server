/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
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
import net.bigtangle.core.exception.VerificationException.InfeasiblePrototypeException;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.service.ValidatorService.RewardBuilderResult;
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
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final ReentrantLock lock = Threading.lock("RewardService");

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            logger.debug(this.getClass().getName() + "  RewardService running. Returning...");
            return;
        }

        try {
            logger.info("performRewardVoting  started");
            Stopwatch watch = Stopwatch.createStarted();
            performRewardVoting();
            logger.info("performRewardVoting time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        } catch (VerificationException e1) {
            logger.debug(" Infeasible performRewardVoting: ", e1);
        } catch (Exception e) {
            logger.error("performRewardVoting ", e);
        } finally {
            lock.unlock();
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
    
        return createAndAddMiningRewardBlock();
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
            blockService.saveBlock(block);
        return block;
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws BlockStoreException, NoBlockException {
        return createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, false);
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws BlockStoreException, NoBlockException {
        RewardBuilderResult result = validatorService.makeReward(prevTrunk, prevBranch, prevRewardHash);

        if (result.getEligibility() != Eligibility.ELIGIBLE) {
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
        block.addTransaction(result.getTx());
        block.setDifficultyTarget(result.getDifficulty());
        block.setLastMiningRewardBlock(Math.max(r1.getLastMiningRewardBlock(), r2.getLastMiningRewardBlock()) + 1);

        // Enforce timestamp equal to previous max for reward blocktypes
        block.setTime(Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

        block.solve();
        return block;
    }

}
