/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderMatchingInfo;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.wallet.CoinSelector;
import net.bigtangle.wallet.DefaultCoinSelector;

/**
 * <p>
 * A TransactionService provides service for transactions that send and receive
 * value from user keys. Using these, it is able to create new transactions that
 * spend the recorded transactions, and this is the fundamental operation of the
 * protocol.
 * </p>
 */
@Service
public class TransactionService {

	@Autowired
	protected FullPrunedBlockStore store;
	@Autowired
	protected FullPrunedBlockGraph blockgraph;
	@Autowired
	private BlockService blockService;
	@Autowired
	protected TipsService tipService;
	@Autowired
	protected MilestoneService milestoneService;
	@Autowired
	private ValidatorService validatorService;
	@Autowired
	protected NetworkParameters networkParameters;
	@Autowired
	protected KafkaConfiguration kafkaConfiguration;

	private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

	protected CoinSelector coinSelector = new DefaultCoinSelector();

	private final Semaphore lock = new Semaphore(1);

	public ByteBuffer askTransaction() throws Exception {
		Block rollingBlock = askTransactionBlock();

		byte[] data = rollingBlock.bitcoinSerialize();

		ByteBuffer byteBuffer = ByteBuffer.wrap(data);
		return byteBuffer;
	}

	public Block askTransactionBlock() throws Exception {
		Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
		Block r1 = blockService.getBlock(tipsToApprove.getLeft());
		Block r2 = blockService.getBlock(tipsToApprove.getRight());

		return r1.createNextBlock(r2);
	}
	
	/**
	 * Generates an order reclaim block for the given order and order matching hash
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

        block.solve();
        blockgraph.add(block, false);
        return block;
	}

    
    /**
     * Runs the order reclaim logic: look for lost orders and issue reclaims for the orders
     * 
     * @return the new blocks
     * @throws Exception
     */
	public List<Block> performOrderReclaimMaintenance() throws Exception {
	    // Find height from which on all orders are finished
        Sha256Hash prevRewardHash = store.getMaxConfirmedOrderMatchingBlockHash();
        long finishedHeight = store.getOrderMatchingToHeight(prevRewardHash);
        
        // Find orders that are unspent confirmed with height lower than the passed matching
        List<Sha256Hash> lostOrders = store.getLostOrders(finishedHeight);

        // Perform reclaim for each of the lost orders
        List<Block> result = new ArrayList<>();
        for (Sha256Hash lostOrder : lostOrders) {
            result.add(createAndAddOrderReclaim(lostOrder, prevRewardHash));
        }
        return result;
	}

    
    /**
     * Runs the reward voting logic: push existing best eligible reward if exists or make a new eligible reward now
     * 
     * @return the new block or block voted on
     * @throws Exception
     */
	public Block performOrderMatchingVoting() throws Exception {
        // Find eligible rewards building on top of the newest reward
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
        
        // Find the one most likely to win
        List<BlockWrap> candidates = blockService.getBlockWraps(candidateHashes);
        BlockWrap votingTarget = candidates.stream().max(Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getRating())).orElse(null);
        
        // If exists, push that one, else make new one
        if (votingTarget != null) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPairStartingFrom(votingTarget);
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            blockgraph.add(r1.createNextBlock(r2), false);
            return votingTarget.getBlock();
        } else {
            return createAndAddOrderMatchingBlock();
        }
	}

    public Block createAndAddOrderMatchingBlock() throws Exception {
        logger.info("createAndAddMiningRewardBlock  started");
        Stopwatch watch = Stopwatch.createStarted();
        
        if (!lock.tryAcquire()) {
            logger.debug("createAndAddMiningRewardBlock already running. Returning...");
            return null;
        }
        
        try {
            Sha256Hash prevRewardHash = store.getMaxConfirmedRewardBlockHash();
            return createAndAddOrderMatchingBlock(prevRewardHash);
        } finally {
            lock.release();
            logger.info("createAndAddMiningRewardBlock time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        }
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
        blockgraph.add(block, false);
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
        
        OrderMatchingInfo info = new OrderMatchingInfo(store.getOrderMatchingToHeight(prevHash)
                - NetworkParameters.ORDER_MATCHING_OVERLAP_SIZE, 
                Math.max(r1.getBlockEvaluation().getHeight(), r2.getBlockEvaluation().getHeight()) + 1, prevHash);

        // Add the data
        Transaction tx = new Transaction(networkParameters);
        tx.setData(info.toByteArray());
        block.addTransaction(tx);

        block.solve();
        return block;
    }
	
	/**
	 * Runs the reward voting logic: push existing best eligible reward if exists or make a new eligible reward now
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
        BlockWrap votingTarget = candidates.stream().max(Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getRating())).orElse(null);
        
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
        Stopwatch watch = Stopwatch.createStarted();
        
        if (!lock.tryAcquire()) {
            logger.debug("createAndAddMiningRewardBlock already running. Returning...");
            return null;
        }
        
        try {
            Sha256Hash prevRewardHash = store.getMaxConfirmedRewardBlockHash();
            return createAndAddMiningRewardBlock(prevRewardHash);
        } finally {
            lock.release();
            logger.info("createAndAddMiningRewardBlock time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        }
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
        blockgraph.add(block, false);
        return block;
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws BlockStoreException {
        return createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, false);
    }

    public Block createMiningRewardBlock(Sha256Hash prevRewardHash, Sha256Hash prevTrunk, Sha256Hash prevBranch,
            boolean override) throws BlockStoreException {
        Triple<Eligibility, Transaction, Pair<Long, Long>> result = validatorService.makeReward(prevTrunk,
                prevBranch, prevRewardHash);

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

	public boolean getUTXOSpent(TransactionOutPoint txout) throws BlockStoreException {
		return store.getTransactionOutput(txout.getHash(), txout.getIndex()).isSpent();
	}

	public boolean getUTXOConfirmed(TransactionOutPoint txout) throws BlockStoreException {
		return store.getTransactionOutput(txout.getHash(), txout.getIndex()).isConfirmed();
	}

	public BlockEvaluation getUTXOSpender(TransactionOutPoint txout) throws BlockStoreException {
		return store.getTransactionOutputSpender(txout.getHash(), txout.getIndex());
	}

	public UTXO getUTXO(TransactionOutPoint out) throws BlockStoreException {
		return store.getTransactionOutput(out.getHash(), out.getIndex());
	}

	public Optional<Block> addConnected(byte[] bytes, boolean emptyBlock, boolean request) {
		try {
			Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
			if (!checkBlockExists(block)) {
				boolean added = blockgraph.add(block, true);
				if (added) {
					logger.debug("addConnected from kafka " + block);
				} else {
					logger.debug(" unsolid block from kafka " + block);
					if (request)
						blockService.requestPrev(block);

				}
				return Optional.of(block);
			} else {
				logger.debug("addConnected   BlockExists " + block);
			}
		} catch (VerificationException e) {
			logger.debug("addConnected from kafka ", e);
			return Optional.empty();
		} catch (Exception e) {
			logger.debug("addConnected from kafka ", e);
			return Optional.empty();
		}
		return Optional.empty();
	}

	/*
	 * check before add Block from kafka , the block can be already exists.
	 */
	public boolean checkBlockExists(Block block) throws BlockStoreException {
		return store.get(block.getHash()) != null;
	}

	public void streamBlocks(Long heightstart) throws BlockStoreException {
		KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
		store.streamBlocks(heightstart, kafkaMessageProducer);
	}

	public void streamBlocks(Long heightstart, String kafka) throws BlockStoreException {
		KafkaMessageProducer kafkaMessageProducer;
		if (kafka == null || "".equals(kafka)) {
			kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
		} else {
			kafkaMessageProducer = new KafkaMessageProducer(kafka, kafkaConfiguration.getTopicOutName(), true);
		}
		store.streamBlocks(heightstart, kafkaMessageProducer);
	}
}
