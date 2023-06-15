/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.data.OrderMatchingResult;
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;

/**
 * <p>
 * A ContractExecutionService provides service for create and validate the
 * contract common execution.
 * </p>
 */
@Service
public class ContractExecutionService {

	@Autowired
	protected FullBlockGraph blockGraph;
	@Autowired
	private BlockService blockService;
	@Autowired
	protected TipsService tipService;
	@Autowired
	protected ServerConfiguration serverConfiguration;

	@Autowired
	protected NetworkParameters networkParameters;
	@Autowired
	private StoreService storeService;
	@Autowired
	private ScheduleConfiguration scheduleConfiguration;

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	private final String LOCKID = this.getClass().getName();

	/**
	 * Scheduled update function that updates the Tangle
	 * 
	 * @throws BlockStoreException
	 */

	// createContractExecution is time boxed and can run parallel.
	public void startSingleProcess() throws BlockStoreException {

		FullBlockStore store = storeService.getStore();

		try {
			// log.info("create ContractExecution started");
			LockObject lock = store.selectLockobject(LOCKID);
			boolean canrun = false;
			if (lock == null) {
				store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
				canrun = true;
			} else if (lock.getLocktime() < System.currentTimeMillis() - 5 * scheduleConfiguration.getMiningrate()) {
				log.info(" ContractExecution locked is fored delete   " + lock.getLocktime() + " < "
						+ (System.currentTimeMillis() - 5 * scheduleConfiguration.getMiningrate()));
				store.deleteLockobject(LOCKID);
				store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
				canrun = true;
			} else {
				log.info("ContractExecution running return:  " + Utils.dateTimeFormat(lock.getLocktime()));
			}
			if (canrun) {
				createContractExecution(store);
				store.deleteLockobject(LOCKID);
			}

		} catch (Exception e) {
			log.error("create ContractExecution end  ", e);
			store.deleteLockobject(LOCKID);
		} finally {
			store.close();
		}

	}

	/**
	 * Runs the ContractExecution making logic
	 * 
	 * @return the new block or block voted on
	 * @throws Exception
	 */

	public Block createContractExecution(FullBlockStore store) throws Exception {

		Sha256Hash prevContractExecutionHash = store.getMaxConfirmedReward().getBlockHash();
		Block contractExecution = createContractExecution(prevContractExecutionHash, store);
		if (contractExecution != null) {
			log.debug(" contractExecution block is created: " + contractExecution);
		}
		return contractExecution;
	}

	public Block createContractExecution(Sha256Hash prevContractExecutionHash, FullBlockStore store) throws Exception {

		Stopwatch watch = Stopwatch.createStarted();
		Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair( 
				store);
		log.debug("  getValidatedContractExecutionBlockPair time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return createContractExecution(prevContractExecutionHash, tipsToApprove.getLeft(), tipsToApprove.getRight(),
				store);

	}

	public Block createContractExecution(Sha256Hash prevContractExecutionHash, Sha256Hash prevTrunk,
			Sha256Hash prevBranch, FullBlockStore store) throws Exception {
		return createContractExecution(prevContractExecutionHash, prevTrunk, prevBranch, null, store);
	}

	public Block createContractExecution(Sha256Hash prevContractExecutionHash, Sha256Hash prevTrunk,
			Sha256Hash prevBranch, Long timeOverride, FullBlockStore store) throws Exception {

		Block block = createMiningContractExecutionBlock(prevContractExecutionHash, prevTrunk, prevBranch, timeOverride,
				store);

		if (block != null) {
			// check, if the contractExecution block is too old to avoid conflict.
			TXReward latest = store.getMaxConfirmedReward();
			if (latest.getChainLength() >= block.getLastMiningRewardBlock()) {
				log.debug("resolved contractExecution is out of date.");
			} else {
				blockService.saveBlock(block, store);
			}
		}
		return block;
	}

	public Block createMiningContractExecutionBlock(Sha256Hash prevContractExecutionHash, Sha256Hash prevTrunk,
			Sha256Hash prevBranch, FullBlockStore store)
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		return createMiningContractExecutionBlock(prevContractExecutionHash, prevTrunk, prevBranch, null, store);
	}

	public Block createMiningContractExecutionBlock(Sha256Hash prevContractExecutionHash, Sha256Hash prevTrunk,
			Sha256Hash prevBranch, Long timeOverride, FullBlockStore store)
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		Stopwatch watch = Stopwatch.createStarted();

		Block r1 = blockService.getBlock(prevTrunk, store);
		Block r2 = blockService.getBlock(prevBranch, store);

		long currentTime = Math.max(System.currentTimeMillis() / 1000,
				Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
		if (timeOverride != null)
			currentTime = timeOverride;
		// Build transaction for block
		Transaction tx = new Transaction(networkParameters);

		Block block = Block.createBlock(networkParameters, r1, r2);

		block.setBlockType(Block.Type.BLOCKTYPE_CONTRACT_EXECUTE);
		block.setHeight(Math.max(r1.getHeight(), r2.getHeight()) + 1);

		RewardInfo currRewardInfo = new RewardInfo().parseChecked(tx.getData());
		block.setLastMiningRewardBlock(currRewardInfo.getChainlength());
		block.setDifficultyTarget(currRewardInfo.getDifficultyTargetReward());

		// Enforce timestamp equal to previous max for contractExecution blocktypes
		block.setTime(currentTime);
		BigInteger chainTarget = Utils.decodeCompactBits(store.getRewardDifficulty(prevContractExecutionHash));

		block.addTransaction(tx);
		OrderMatchingResult ordermatchresult = new ServiceBase(serverConfiguration, networkParameters)
				.generateOrderMatching(block, currRewardInfo, store);
		currRewardInfo.setOrdermatchingResult(ordermatchresult.getOrderMatchingResultHash());
		tx.setData(currRewardInfo.toByteArray());
		tx.setData(currRewardInfo.toByteArray());

		blockService.adjustHeightRequiredBlocks(block, store);
		final BigInteger chainTargetFinal = chainTarget;
		log.debug("prepare contractExecution time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
		return blockSolve(block, chainTargetFinal);
	}

	private Block blockSolve(Block block, final BigInteger chainTargetFinal)
			throws InterruptedException, ExecutionException {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final Future<String> handler = executor.submit(new Callable() {
			@Override
			public String call() throws Exception {
				log.debug(" contractExecution block solve started  : " + chainTargetFinal + " \n for block" + block);
				block.solve(chainTargetFinal);
				return "";
			}
		});
		Stopwatch watch = Stopwatch.createStarted();
		try {
			handler.get(scheduleConfiguration.getMiningrate(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			log.debug(" contractExecution solve Timeout  {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
			handler.cancel(true);
			return null;
		} finally {
			executor.shutdownNow();
		}
		log.debug("contractExecution Solved time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
		return block;
	}

}
