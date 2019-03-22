/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.nio.ByteBuffer;
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
import net.bigtangle.core.NetworkParameters;
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

	public void createAndAddMiningRewardBlock() throws Exception {

		if (!lock.tryAcquire()) {
			logger.debug("createAndAddMiningRewardBlock  already running. Returning...");
			return;
		}
		try {
		    logger.info("createAndAddMiningRewardBlock  started");
            Stopwatch watch = Stopwatch.createStarted();
			Sha256Hash prevRewardHash = store.getMaxConfirmedRewardBlockHash();
			createAndAddMiningRewardBlock(prevRewardHash);
			logger.info("createAndAddMiningRewardBlock   time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

	            watch.stop();
		} finally {
			lock.release();
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
		Triple<RewardEligibility, Transaction, Pair<Long, Long>> result = validatorService.makeReward(prevTrunk,
				prevBranch, prevRewardHash);

		if (!(result.getLeft() == RewardEligibility.ELIGIBLE)) {
			if (!override)
				throw new RuntimeException("Generated reward block is deemed ineligible! Try again somewhere else?");
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
