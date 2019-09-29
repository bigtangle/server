/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.server.config.ServerConfiguration;
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
    protected ServerConfiguration serverConfiguration;
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
            // logger.debug(" Infeasible performRewardVoting: ", e1);
        } catch (Exception e) {
            logger.error("performRewardVoting ", e);
        } finally {
            lock.unlock();
        }

    }

    /**
     * Runs the reward making logic
     * 
     * @return the new block or block voted on
     * @throws Exception
     */
    public Block performRewardVoting() {
        // Make new one
        try {
            return createAndAddMiningRewardBlock();
        } catch (InvalidTransactionDataException e) {
            // This is not a problem
            return null;
        } catch (Exception e) {
            // This is not a problem
            logger.debug("", e);
            return null;
        }
    }

    public Block createAndAddMiningRewardBlock() throws Exception {
        logger.info("createAndAddMiningRewardBlock  started");

        Sha256Hash prevRewardHash = store.getMaxConfirmedReward().getBlockHash();
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
            throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
        return createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, false);
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
        RewardBuilderResult result = null;
        try {
            result = validatorService.makeReward(prevTrunk, prevBranch, prevRewardHash);
        } catch (java.lang.ArithmeticException e) {
            return null;
        }
        Block r1 = blockService.getBlock(prevTrunk);
        Block r2 = blockService.getBlock(prevBranch);

        Block block = new Block(networkParameters, r1, r2);
        block.setBlockType(Block.Type.BLOCKTYPE_REWARD);
        block.setHeight(Math.max(r1.getHeight(), r2.getHeight()) + 1);
        block.setMinerAddress(
                Address.fromBase58(networkParameters, serverConfiguration.getMineraddress()).getHash160());
        // Make the new block
        block.addTransaction(result.getTx());
        block.setDifficultyTarget(result.getDifficulty());
        block.setLastMiningRewardBlock(Math.max(r1.getLastMiningRewardBlock(), r2.getLastMiningRewardBlock()) + 1);

        // Enforce timestamp equal to previous max for reward blocktypes
        block.setTime(Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

        BigInteger chainTarget = Utils.decodeCompactBits(store.getRewardDifficulty(prevRewardHash));
        
        
        
        final Duration timeout = Duration.ofSeconds(30);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {
                 blockService.adjustHeightRequiredBlocks(block);
                block.solve(chainTarget);return "";
            }
        });

        try {
            handler.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            handler.cancel(true);
        }

        executor.shutdownNow();
        
       
        return block;
    }

    public GetTXRewardResponse getMaxConfirmedReward(Map<String, Object> request) throws BlockStoreException {

        return GetTXRewardResponse.create(store.getMaxConfirmedReward());

    }

    public GetTXRewardListResponse getAllConfirmedReward(Map<String, Object> request) throws BlockStoreException {

        return GetTXRewardListResponse.create(store.getAllConfirmedReward());

    }
}
