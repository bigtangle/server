/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import net.bigtangle.server.service.base.ServiceBaseConnect;
import net.bigtangle.server.service.base.ServiceContract;
import net.bigtangle.store.FullBlockStoreImpl;
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
	protected FullBlockStoreImpl blockGraph;
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
	@Autowired
	protected CacheBlockService cacheBlockService;
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
			Block contractExecution = createContractExecution(contractid, store);
			if (contractExecution != null) {
				log.debug(" contractExecution block is created: " + contractExecution);
				blockService.saveBlock(contractExecution, store);
			}
		}

	}

 

	/**
	 * Runs the ContractExecution making logic
	 * 
	 * @return the new block or block voted on
	 * @throws Exception
	 */

	public Block createContractExecution(String contractid, FullBlockStore store) throws Exception {

		Stopwatch watch = Stopwatch.createStarted();
		Block b = blockService.getBlockPrototype(store);
		log.debug("  getValidatedContractExecutionBlockPair time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		return createContractExecution(b, contractid, store);

	}

	public Block createContractExecution(Block block, String contractid, FullBlockStore store)
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		block.setBlockType(Block.Type.BLOCKTYPE_CONTRACT_EXECUTE);
		// Build transaction for block
		Transaction tx = new Transaction(networkParameters);
		block.addTransaction(tx);
		Sha256Hash prevHash = Sha256Hash.ZERO_HASH;
		// calculate prev
		Sha256Hash prev = store.getLastContractResultBlockHash(contractid);
		if (prev != null) {
			prevHash = prev;
		}
		// collect the order block as reference
		// Read previous reward block's data
		Sha256Hash prevRewardHash = cacheBlockService.getMaxConfirmedReward(store).getBlockHash();
		long prevChainLength = block.getLastMiningRewardBlock();
		Set<Sha256Hash> referencedblocks = new HashSet<Sha256Hash>();
		long cutoffheight = blockService.getRewardCutoffHeight(prevRewardHash, store);

	 
		List<Block.Type> ordertypes = new ArrayList<Block.Type>();
		ordertypes.add(Block.Type.BLOCKTYPE_CONTRACT_EVENT);
	 
		ServiceBaseConnect serviceBase = new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService);
		serviceBase.addRequiredNonContainedBlockHashesTo(referencedblocks,
				blockService.getBlockWrap(block.getPrevBlockHash(), store), cutoffheight, prevChainLength, true,
				ordertypes, store);
		serviceBase.addRequiredNonContainedBlockHashesTo(referencedblocks,
				blockService.getBlockWrap(block.getPrevBranchBlockHash(), store), cutoffheight, prevChainLength, true,
				ordertypes, store);
 

		ContractResult result = new ServiceContract(serverConfiguration, networkParameters, cacheBlockService)
				.executeContract(block, store, contractid, prevHash, referencedblocks);
		// do not create the execution block, if there is no new referencedblocks and no match 
		if (result == null || (result.getOutputTx().getOutputs().isEmpty()
			 && referencedblocks.isEmpty()) )
			return null;

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
				// log.debug(" contractExecution block solve started : " + chainTargetFinal + "
				// \n for block" + block);
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
		// log.debug("contractExecution Solved time {} ms.",
		// watch.elapsed(TimeUnit.MILLISECONDS));
		return block;
	}

}
