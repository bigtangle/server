/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
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
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.server.config.ServerConfiguration;
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
    protected ServerConfiguration serverConfiguration;
 
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
    public void performRewardVoting() {
        // Make new one
        try {
             Block reward = createAndAddMiningRewardBlock();
             if(reward!=null) {
                 logger.info(" reward block is created: "+ reward);
             }
        } catch (InvalidTransactionDataException e) {
            // This is not a problem 
        } catch (Exception e) {
            // This is not a problem
            logger.debug("", e); 
        }
    }

    public Block createAndAddMiningRewardBlock() throws Exception {
        logger.info("createAndAddMiningRewardBlock  started");

        Sha256Hash prevRewardHash = store.getMaxConfirmedReward().getBlockHash();
        return createAndAddMiningRewardBlock(prevRewardHash);

    }

    public Block createAndAddMiningRewardBlock(Sha256Hash prevRewardHash) throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedRewardBlockPair(prevRewardHash);
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
            result =  makeReward(prevTrunk, prevBranch, prevRewardHash);
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
        BigInteger chainTarget = 
                Utils.decodeCompactBits(store.getRewardDifficulty(prevRewardHash));
        if(Utils.decodeCompactBits(result.getDifficulty()).compareTo(chainTarget) < 0) {
            chainTarget = Utils.decodeCompactBits(result.getDifficulty());
        }
        blockService.adjustHeightRequiredBlocks(block);
       final  BigInteger chainTargetFinal= chainTarget;
    
        final Duration timeout = Duration.ofSeconds(30);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        @SuppressWarnings({ "unchecked", "rawtypes" })
       final   Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception { 
                block.solve(chainTargetFinal); 
                return "";
            }
        });

        try {
            handler.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            handler.cancel(true);
            return null;
        }finally {
            executor.shutdownNow();
        }

     
        
       
        return block;
    }

    public GetTXRewardResponse getMaxConfirmedReward(Map<String, Object> request) throws BlockStoreException {

        return GetTXRewardResponse.create(store.getMaxConfirmedReward());

    }

    public GetTXRewardListResponse getAllConfirmedReward(Map<String, Object> request) throws BlockStoreException {

        return GetTXRewardListResponse.create(store.getAllConfirmedReward());

    }

    /**
     * DOES NOT CHECK FOR SOLIDITY. Computes eligibility of rewards + data tx +
     * Pair.of(new difficulty + new perTxReward) here for new reward blocks.
     * This is a prototype implementation. In the actual Spark implementation,
     * the computational cost is not a problem, since it is instead
     * backpropagated and calculated for free with delay. For more info, see
     * notes.
     * 
     * @param prevTrunk
     *            a predecessor block in the db
     * @param prevBranch
     *            a predecessor block in the db
     * @param prevRewardHash
     *            the predecessor reward
     * @return eligibility of rewards + data tx + Pair.of(new difficulty + new
     *         perTxReward)
     */
    public RewardBuilderResult makeReward(Sha256Hash prevTrunk, Sha256Hash prevBranch, Sha256Hash prevRewardHash)
            throws BlockStoreException {

        // Count how many blocks from miners in the reward interval are approved
        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        BlockWrap prevTrunkBlock = store.getBlockWrap(prevTrunk);
        BlockWrap prevBranchBlock = store.getBlockWrap(prevBranch);
        blockQueue.add(prevTrunkBlock);
        blockQueue.add(prevBranchBlock);

        // Read previous reward block's data
        BlockWrap prevRewardBlock = store.getBlockWrap(prevRewardHash);
        long currChainLength = store.getRewardChainLength(prevRewardHash) + 1;
        long prevToHeight = store.getRewardToHeight(prevRewardHash);
        long prevDifficulty = prevRewardBlock.getBlock().getDifficultyTarget();
        long fromHeight = prevToHeight + 1;
        long toHeight = Math.max(prevTrunkBlock.getBlockEvaluation().getHeight(),
                prevBranchBlock.getBlockEvaluation().getHeight()) - NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE;

        // Initialize
        BlockWrap currentBlock = null, approvedBlock = null;
        long currentHeight = Long.MAX_VALUE;
        long totalRewardCount = 0;

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {
            currentHeight = currentBlock.getBlockEvaluation().getHeight();

            // Stop criterion: Block height lower than approved interval height
            if (currentHeight < fromHeight)
                break;

            // If in relevant reward height interval, count it
            if (currentHeight <= toHeight) {
                totalRewardCount++;
            }

            // Continue with both approved blocks
            approvedBlock = store.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                }
            }
            approvedBlock = store.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                }
            }
        }

        // New difficulty
        long difficulty = calculateNextBlockDifficulty(prevDifficulty, prevTrunkBlock, prevBranchBlock, prevRewardBlock,
                totalRewardCount);

        // Build transaction for block
        Transaction tx = new Transaction(networkParameters);
        
        // Get all blocks approved by previous reward blocks 
        Set<Sha256Hash> blocks = new HashSet<Sha256Hash>();        
        Set<Sha256Hash> prevMilestoneBlocks = blockService.getPastMilestoneBlocks(prevRewardHash);
        long cutoffheight = blockService.getCutoffHeight(prevRewardHash);
        
        blockService.addRequiredNonContainedBlockHashesTo(blocks, prevBranchBlock, prevMilestoneBlocks, cutoffheight);
        blockService.addRequiredNonContainedBlockHashesTo(blocks, prevTrunkBlock, prevMilestoneBlocks, cutoffheight);

        // Build the type-specific tx data
        RewardInfo rewardInfo = new RewardInfo(fromHeight, toHeight, prevRewardHash, blocks, currChainLength);
        tx.setData(rewardInfo.toByteArray());
        tx.setMemo(new MemoInfo("RewardInfo:" + rewardInfo));
        return new RewardBuilderResult(tx, difficulty);
    }

    private long calculateNextBlockDifficulty(long prevDifficulty, BlockWrap prevTrunkBlock, BlockWrap prevBranchBlock,
            BlockWrap prevRewardBlock, long totalRewardCount) {
        // The following equals current time by consensus rules
        long currentTime = Math.max(prevTrunkBlock.getBlock().getTimeSeconds(),
                prevBranchBlock.getBlock().getTimeSeconds());
        long timespan = Math.max(1, (currentTime - prevRewardBlock.getBlock().getTimeSeconds()));

        BigInteger prevTarget = Utils.decodeCompactBits(prevDifficulty);
        BigInteger newTarget = prevTarget.multiply(BigInteger.valueOf(NetworkParameters.TARGET_MAX_TPS));
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));
        newTarget = newTarget.divide(BigInteger.valueOf(totalRewardCount));

        BigInteger maxNewTarget = prevTarget.multiply(BigInteger.valueOf(4));
        BigInteger minNewTarget = prevTarget.divide(BigInteger.valueOf(4));

        if (newTarget.compareTo(maxNewTarget) > 0) {
            newTarget = maxNewTarget;
        }

        if (newTarget.compareTo(minNewTarget) < 0) {
            newTarget = minNewTarget;
        }

        if (newTarget.compareTo(networkParameters.getMaxTarget()) > 0) {
            // TODO logger.info("Difficulty hit proof of work limit: {}",
            // newTarget.toString(16));
            newTarget = networkParameters.getMaxTarget();
        }

        return Utils.encodeCompactBits(newTarget);
    }

}
