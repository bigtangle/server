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
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.data.OrderExecutionResult;
import net.bigtangle.server.service.base.ServiceBaseConnect;
import net.bigtangle.server.service.base.ServiceOrderExecution;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.store.FullBlockStoreImpl;

/**
 * <p>
 * A OrderExecutionService provides service for create and validate the contract
 * common execution.
 * </p>
 */
@Service
public class OrderExecutionService {

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

	// createOrderExecution is time boxed and can run parallel.
	public void startSingleProcess() throws BlockStoreException {

		FullBlockStore store = storeService.getStore();

		try {
			// log.info("create OrderExecution started");
			LockObject lock = store.selectLockobject(LOCKID);
			boolean canrun = false;
			if (lock == null) {
				store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
				canrun = true;
			} else if (lock.getLocktime() < System.currentTimeMillis() - 5 * scheduleConfiguration.getMiningrate()) {
				log.info(" OrderExecution locked is fored delete   " + lock.getLocktime() + " < "
						+ (System.currentTimeMillis() - 5 * scheduleConfiguration.getMiningrate()));
				store.deleteLockobject(LOCKID);
				store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
				canrun = true;
			} else {
				// log.info("OrderExecution running return: " +
				// Utils.dateTimeFormat(lock.getLocktime()));
			}
			if (canrun) {
				createOrderExecution(store);
				store.deleteLockobject(LOCKID);
			}

		} catch (Exception e) {
			log.error("create OrderExecution end  ", e);
			store.deleteLockobject(LOCKID);
		} finally {
			store.close();
		}

	}

	public Block createOrderExecution(FullBlockStore store) throws Exception {
		Block contractExecution = createOrderExecutionDo(store);
		if (contractExecution != null) {
			// log.debug(" createOrder block is created: " + contractExecution);
			blockSaveService.saveBlock(contractExecution, store);
			return contractExecution;
		}
		return null;
	}

	/**
	 * Runs the OrderExecution making logic
	 * 
	 * @return the new block or block voted on
	 * @throws Exception
	 */

	public Block createOrderExecutionDo(FullBlockStore store) throws Exception {

		// Stopwatch watch = Stopwatch.createStarted();
		Block b = cacheBlockPrototypeService.getBlockPrototype(store);
		// log.debug(" getValidatedOrderExecutionBlockPair time {} ms.",
		// watch.elapsed(TimeUnit.MILLISECONDS));
		if (new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService).enableOrderMatchExecutionChain(b)) {

			return createOrderExecution(b, store);
		} else {
			return null;
		}

	}

	public Block createOrderExecution(Block block, FullBlockStore store)
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		block.setBlockType(Block.Type.BLOCKTYPE_ORDER_EXECUTE);
		// Build transaction for block
		Transaction tx = new Transaction(networkParameters);
		block.addTransaction(tx);

		// Read previous reward block's data
		Sha256Hash prevRewardHash = cacheBlockService.getMaxConfirmedReward(store).getBlockHash();
		long prevChainLength = block.getLastMiningRewardBlock();
		Set<BlockWrap> referencedblocks = new HashSet<>();
		long cutoffheight = blockService.getRewardCutoffHeight(prevRewardHash, store);
		List<Orderresult> prevUnspents = collectPrevsChain(store.getOrderResultUnspent());

		List<Block.Type> ordertypes = new ArrayList<Block.Type>();
		ordertypes.add(Block.Type.BLOCKTYPE_ORDER_CANCEL);
		ordertypes.add(Block.Type.BLOCKTYPE_ORDER_OPEN);
		ServiceBaseConnect serviceBase = new ServiceBaseConnect(serverConfiguration, networkParameters,
				cacheBlockService);
		// add all blocks of dependencies
		for (Orderresult b : prevUnspents) {
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

		Set<BlockWrap> collectOrdersNoSpent = collectOrdersNoSpent(referencedblocks, prevUnspents, store);
		Orderresult prevblockHash = prevUnspents.isEmpty() ? Orderresult.zeroOrderresult() : prevUnspents.get(0);
		OrderExecutionResult result = new ServiceOrderExecution(serverConfiguration, networkParameters,
				cacheBlockService)
				.orderMatching(block, prevblockHash, serviceBase.getHashSet(collectOrdersNoSpent), store);
		// do not create the execution block, if there is no new referencedblocks and no
		// match
		if (result == null || (result.getOutputTx().getOutputs().isEmpty() && collectOrdersNoSpent.isEmpty()))
			return null;

		tx.setData(result.toByteArray());

		blockService.adjustHeightRequiredBlocks(block, store);

		return blockSolve(block, Utils.decodeCompactBits(block.getDifficultyTarget()));
	}

	protected List<Orderresult> collectPrevsChain(List<Orderresult> prevs) {
		// get all unspents forms a chain, remove others from prevs
		List<Orderresult> re = new ArrayList<>();
		if (prevs.isEmpty())
			return re;
		Orderresult startingBlock = prevs.get(0);
		re.add(startingBlock);

		while (startingBlock != null) {
			startingBlock = findPrev(prevs, startingBlock);
			if (startingBlock != null)
				re.add(startingBlock);
		}
		return re;
	}

	protected Orderresult findPrev(List<Orderresult> prevs, Orderresult orderresult) {

		for (Orderresult b : prevs) {
			if (orderresult.getPrevblockhash().equals(b.getBlockHash())) {
				return b;
			}
		}
		return null;

	}

	protected Set<BlockWrap> collectOrdersNoSpent(Set<BlockWrap> collectedBlocks, List<Orderresult> prevUnspents,
			FullBlockStore blockStore) throws BlockStoreException {
		Set<BlockWrap> collectOrdersNoSpents = new HashSet<>();
		Set<Sha256Hash> alreadyCollected = collectOrdersNoSpentFrom(prevUnspents);
		for (BlockWrap b : collectedBlocks) {
			if (!alreadyCollected.contains(b.getBlockHash())) {
				collectOrdersNoSpents.add(b);
			}

		}
		return collectOrdersNoSpents;
	}

	protected Set<Sha256Hash> collectOrdersNoSpentFrom(List<Orderresult> prevUnspents) throws BlockStoreException {
		Set<Sha256Hash> collectOrdersNoSpents = new HashSet<>();
		for (Orderresult b : prevUnspents) {
			collectOrdersNoSpents
					.addAll(new OrderExecutionResult().parseChecked(b.getOrderresult()).getReferencedBlocks());
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
