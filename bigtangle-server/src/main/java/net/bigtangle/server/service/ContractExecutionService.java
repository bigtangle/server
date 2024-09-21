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
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.Contractresult;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Orderresult;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.ContractExecutionResult;
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.service.base.ServiceBaseConnect;
import net.bigtangle.server.service.base.ServiceContract;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.store.FullBlockStoreImpl;

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
	@Autowired
	protected CacheBlockPrototypeService cacheBlockPrototypeService;
	@Autowired
	private BlockSaveService blockSaveService;
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
				// log.info("ContractExecution running return: " +
				// Utils.dateTimeFormat(lock.getLocktime()));
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
				blockSaveService.saveBlock(contractExecution, store);
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

		// Stopwatch watch = Stopwatch.createStarted();
		Block b = cacheBlockPrototypeService.getBlockPrototype(store);
		// log.debug(" getValidatedContractExecutionBlockPair time {} ms.",
		// watch.elapsed(TimeUnit.MILLISECONDS));
	 		return createContractExecution(b, contractid, store);
		 
 

	}

	public Block createContractExecution(Block block, String contractid, FullBlockStore store)
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		// calculate prev
		List<Contractresult> prevs = store.getContractresultUnspent(contractid);

		return createContractExecution(block, contractid, prevs, store);
	}

	public Block createContractExecution(Block block, String contractid, List<Contractresult> prevs,
			FullBlockStore store)
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		block.setBlockType(Block.Type.BLOCKTYPE_CONTRACT_EXECUTE);
		// Build transaction for block
		Transaction tx = new Transaction(networkParameters);
		block.addTransaction(tx);
		// collect the order block as reference
		// Read previous reward block's data
		Sha256Hash prevRewardHash = cacheBlockService.getMaxConfirmedReward(store).getBlockHash();
		long prevChainLength = block.getLastMiningRewardBlock();
		Set<BlockWrap> referencedblocks = new HashSet<>();
		long cutoffheight = blockService.getRewardCutoffHeight(prevRewardHash, store);

		List<Block.Type> ordertypes = new ArrayList<Block.Type>();
		ordertypes.add(Block.Type.BLOCKTYPE_CONTRACT_EVENT);
		ordertypes.add(Block.Type.BLOCKTYPE_CONTRACTEVENT_CANCEL);
		// the related previous execution
		ordertypes.add(Block.Type.BLOCKTYPE_CONTRACT_EXECUTE);
		ServiceBaseConnect serviceBase = new ServiceBaseConnect(serverConfiguration, networkParameters,
				cacheBlockService);
		// add all blocks of dependencies
		for (Contractresult b : prevs) {
			serviceBase.addRequiredNonContainedBlockHashesTo(referencedblocks,
					blockService.getBlockWrap(b.getBlockHash(), store), cutoffheight, prevChainLength, true, ordertypes,
					true, store);
		}

		serviceBase.addRequiredNonContainedBlockHashesTo(referencedblocks,
				blockService.getBlockWrap(block.getPrevBlockHash(), store), cutoffheight, prevChainLength, true,
				ordertypes, true, store);
		serviceBase.addRequiredNonContainedBlockHashesTo(referencedblocks,
				blockService.getBlockWrap(block.getPrevBranchBlockHash(), store), cutoffheight, prevChainLength, true,
				ordertypes, true, store);
		Set<BlockWrap> collectNotSpents = collectNotSpents(referencedblocks, prevs, store);

		Contractresult prevblockHash = prevs.isEmpty() ? Contractresult.zeroContractresult() : prevs.get(0);
		ContractExecutionResult result = new ServiceContract(serverConfiguration, networkParameters, cacheBlockService)
				.executeContract(block, store, contractid, prevblockHash, serviceBase.getHashSet(collectNotSpents));
		// do not create the execution block, if there is no new referencedblocks and no
		// match
		if (result == null || (result.getOutputTx().getOutputs().isEmpty() && collectNotSpents.isEmpty()))
			return null;

		tx.setData(result.toByteArray());

		blockService.adjustHeightRequiredBlocks(block, store);

		return blockSolve(block, Utils.decodeCompactBits(block.getDifficultyTarget()));
	}

	protected List<Contractresult> collectPrevsChain(List<Contractresult> prevs) {
		// get all unspents forms a chain, remove others from prevs
		List<Contractresult> re = new ArrayList<>();
		if (prevs.isEmpty())
			return re;
		Contractresult startingBlock = prevs.get(0);
		re.add(startingBlock);

		while (startingBlock != null) {
			startingBlock = findPrev(prevs, startingBlock);
			if (startingBlock != null)
				re.add(startingBlock);
		}
		return re;
	}

	protected Contractresult findPrev(List<Contractresult> prevs, Contractresult orderresult) {

		for (Contractresult b : prevs) {
			if (orderresult.getPrevblockhash().equals(b.getBlockHash())) {
				return b;
			}
		}
		return null;

	}

	protected Set<BlockWrap> collectNotSpents(Set<BlockWrap> collectedBlocks, List<Contractresult> prevUnspents,
			FullBlockStore blockStore) throws BlockStoreException {
		Set<BlockWrap> collectOrdersNoSpents = new HashSet<>();
		Set<Sha256Hash> alreadyCollected = collectOrdersNotSpentFrom(prevUnspents);
		for (BlockWrap b : collectedBlocks) {
			if (!alreadyCollected.contains(b.getBlockHash())
					|| Block.Type.BLOCKTYPE_CONTRACT_EXECUTE.equals(b.getBlock().getBlockType())) {
				collectOrdersNoSpents.add(b);
			}
		}
		return collectOrdersNoSpents;
	}

	protected Set<Sha256Hash> collectOrdersNotSpentFrom(List<Contractresult> prevUnspents) throws BlockStoreException {
		Set<Sha256Hash> collectOrdersNoSpents = new HashSet<>();
		for (Contractresult b : prevUnspents) {
			collectOrdersNoSpents
					.addAll(new ContractExecutionResult().parseChecked(b.getContractresult()).getReferencedBlocks());
		}
		return collectOrdersNoSpents;
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
