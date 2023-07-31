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
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.data.ContractResult;
import net.bigtangle.server.data.LockObject;
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

	public void createContractExecution(FullBlockStore store) throws Exception {

		// select all contractid from the table with unspent event
		for (String contractid : store.getOpenContractid()) {
			Block contractExecution = createContractExecution(store, contractid);
			if (contractExecution != null) {
				log.debug(" contractExecution block is created: " + contractExecution);
				blockService.saveBlock(contractExecution, store);
			}
		}

	}

	public Block createOrder(FullBlockStore store) throws Exception {
		Block contractExecution = createContractExecution(store, ContractResult.ordermatch);
		if (contractExecution != null) {
	//		log.debug(" createOrder block is created: " + contractExecution);
			blockService.saveBlock(contractExecution, store);
			return contractExecution;
		}
		return null;
	}

	/**
	 * Runs the ContractExecution making logic
	 * 
	 * @return the new block or block voted on
	 * @throws Exception
	 */

	public Block createContractExecution(FullBlockStore store, String contractid) throws Exception {

		Block contractExecution = createContractExecution(contractid, store);
		if (contractExecution != null) {
			log.debug(" contractExecution block is created: " + contractExecution);
		}
		return contractExecution;
	}

	public Block createContractExecution(String contractid, FullBlockStore store) throws Exception {

		Stopwatch watch = Stopwatch.createStarted();
		Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair(store);
		log.debug("  getValidatedContractExecutionBlockPair time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return createContractExecution(tipsToApprove.getLeft(), tipsToApprove.getRight(), contractid, store);

	}

	public Block createContractExecution(Sha256Hash prevTrunk, Sha256Hash prevBranch, String contractid,
			FullBlockStore store)

			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		Stopwatch watch = Stopwatch.createStarted();

		Block r1 = blockService.getBlock(prevTrunk, store);
		Block r2 = blockService.getBlock(prevBranch, store);

		long currentTime = Math.max(System.currentTimeMillis() / 1000,
				Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

		Block block = Block.createBlock(networkParameters, r1, r2);

		block.setBlockType(Block.Type.BLOCKTYPE_CONTRACT_EXECUTE);
		block.setHeight(Math.max(r1.getHeight(), r2.getHeight()) + 1);

		// Enforce timestamp equal to previous max for contractExecution blocktypes
		block.setTime(currentTime);
		// Build transaction for block
		Transaction tx = new Transaction(networkParameters);
		block.addTransaction(tx);
		ContractResult result = new ServiceContract(serverConfiguration, networkParameters).executeContract(block,
				store, contractid);
		if (result == null || result.getSpentContractEventRecord().isEmpty())
			return null;
		// calculate prev
		ContractResult prev = store.getLastContractResult(contractid);
		if (prev != null) {
			result.setPrevblockhash(prev.getBlockHash());
		} else {
			result.setPrevblockhash(networkParameters.getGenesisBlock().getHash());
		}
		tx.setData(result.toByteArray());

		blockService.adjustHeightRequiredBlocks(block, store);

		return blockSolve(block, Utils.decodeCompactBits(block.getDifficultyTarget()));
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
