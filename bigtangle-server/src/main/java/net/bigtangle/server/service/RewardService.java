/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.util.Map;
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

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException.CutoffException;
import net.bigtangle.core.exception.VerificationException.InfeasiblePrototypeException;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.data.OrderMatchingResult;
import net.bigtangle.server.service.base.ServiceBaseConnect.RewardBuilderResult;
import net.bigtangle.server.service.base.ServiceBaseReward;
import net.bigtangle.store.FullBlockStoreImpl;
import net.bigtangle.store.FullBlockStore;

/**
 * <p>
 * A RewardService provides service for create and validate the reward chain.
 * </p>
 */
@Service
public class RewardService {

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

	// createReward is time boxed and can run parallel.
	public void startSingleProcess() throws BlockStoreException {

		FullBlockStore store = storeService.getStore();

		try {
			// log.info("create Reward started");
			LockObject lock = store.selectLockobject(LOCKID);
			boolean canrun = false;
			if (lock == null) {
				store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
				canrun = true;
			} else if (lock.getLocktime() < System.currentTimeMillis() - 5 * scheduleConfiguration.getMiningrate()) {
				log.info(" reward locked is fored delete   " + lock.getLocktime() + " < "
						+ (System.currentTimeMillis() - 5 * scheduleConfiguration.getMiningrate()));
				store.deleteLockobject(LOCKID);
				store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
				canrun = true;
			} else {
				log.info("reward running return:  " + Utils.dateTimeFormat(lock.getLocktime()));
			}
			if (canrun) {
				createReward(store);
				store.deleteLockobject(LOCKID);
			}

		} catch (Exception e) {
			log.error("create Reward end  ", e);
			store.deleteLockobject(LOCKID);
		} finally {
			store.close();
		}

	}

	/**
	 * Runs the reward making logic
	 * 
	 * @return the new block or block voted on
	 * @throws Exception
	 */

	public Block createReward(FullBlockStore store) throws Exception {

		Sha256Hash prevRewardHash = cacheBlockService.getMaxConfirmedReward(store).getBlockHash();
		Block reward = createReward(prevRewardHash, store);
		if (reward != null) {
			log.debug(" reward block is created: " + reward);
		}
		return reward;
	}

	public Block createReward(Sha256Hash prevRewardHash, FullBlockStore store) throws Exception {
		try {
			Stopwatch watch = Stopwatch.createStarted();
			Pair<BlockWrap, BlockWrap> tipsToApprove = tipService.getValidatedRewardBlockPair(prevRewardHash, store);
			log.debug("  getValidatedRewardBlockPair time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

			return createReward(prevRewardHash, tipsToApprove.getLeft(), tipsToApprove.getRight(), store);
		} catch (CutoffException | InfeasiblePrototypeException | NullPointerException e) {
			// fall back to use prev reward as tip
			log.debug(" fall back to use prev reward as tip: ", e);
			BlockWrap prevreward = blockService.getBlockWrap(prevRewardHash, store);
			return createReward(prevRewardHash, prevreward, prevreward, store);
		}
	}

	public Block createReward(Sha256Hash prevRewardHash, BlockWrap prevTrunk, BlockWrap prevBranch,
			FullBlockStore store) throws Exception {
		return createReward(prevRewardHash, prevTrunk, prevBranch, null, store);
	}

	public Block createReward(Sha256Hash prevRewardHash, BlockWrap prevTrunk, BlockWrap prevBranch, Long timeOverride,
			FullBlockStore store) throws Exception {

		Block block = createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, timeOverride, store);

		if (block != null) {
			// check, if the reward block is too old to avoid conflict.
			TXReward latest = cacheBlockService.getMaxConfirmedReward(store);
			if (latest.getChainLength() >= block.getLastMiningRewardBlock()) {
				log.debug("resolved Reward is out of date.");
			} else {
				blockService.saveBlock(block, store);
			}
		}
		return block;
	}

	public Block createMiningRewardBlock(Sha256Hash prevRewardHash, BlockWrap prevTrunk, BlockWrap prevBranch,
			FullBlockStore store)
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		return createMiningRewardBlock(prevRewardHash, prevTrunk, prevBranch, null, store);
	}

	public Block createMiningRewardBlock(Sha256Hash prevRewardHash, BlockWrap prevTrunk, BlockWrap prevBranch,
			Long timeOverride, FullBlockStore store)
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {
		Stopwatch watch = Stopwatch.createStarted();

		Block r1 = prevTrunk.getBlock();
		Block r2 = prevBranch.getBlock();

		long currentTime = Math.max(System.currentTimeMillis() / 1000,
				Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
		if (timeOverride != null)
			currentTime = timeOverride;
		ServiceBaseReward serviceBase = new ServiceBaseReward(serverConfiguration, networkParameters, cacheBlockService);
		RewardBuilderResult result = serviceBase.makeReward(prevTrunk, prevBranch, prevRewardHash, currentTime, store);

		Block block = Block.createBlock(networkParameters, r1, r2);

		block.setBlockType(Block.Type.BLOCKTYPE_REWARD);
		block.setHeight(Math.max(r1.getHeight(), r2.getHeight()) + 1);
		block.setMinerAddress(
				Address.fromBase58(networkParameters, serverConfiguration.getMineraddress()).getHash160());

		Transaction tx = result.getTx();
		RewardInfo currRewardInfo = new RewardInfo().parseChecked(tx.getData());
		block.setLastMiningRewardBlock(currRewardInfo.getChainlength());
		block.setDifficultyTarget(calculateNextBlockDifficulty(currRewardInfo));

		// Enforce timestamp equal to previous max for reward blocktypes
		block.setTime(currentTime);
		BigInteger chainTarget = Utils.decodeCompactBits(store.getRewardDifficulty(prevRewardHash));
		if (Utils.decodeCompactBits(result.getDifficulty()).compareTo(chainTarget) < 0) {
			chainTarget = Utils.decodeCompactBits(result.getDifficulty());
		}

		block.addTransaction(tx);
		if (!serviceBase.enableOrderContract(block)) {
			OrderMatchingResult ordermatchresult = serviceBase.generateOrderMatching(block, currRewardInfo, store);
			currRewardInfo.setOrdermatchingResult(ordermatchresult.getOrderMatchingResultHash());
			tx.setData(currRewardInfo.toByteArray());
		}
		Transaction miningTx = serviceBase.generateVirtualMiningRewardTX(block, store);
		currRewardInfo.setMiningResult(miningTx.getHash());
		tx.setData(currRewardInfo.toByteArray());

		blockService.adjustHeightRequiredBlocks(block, store);
		final BigInteger chainTargetFinal = chainTarget;
		log.debug("prepare Reward time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
		return rewardSolve(block, chainTargetFinal);
	}

	private Block rewardSolve(Block block, final BigInteger chainTargetFinal)
			throws InterruptedException, ExecutionException {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final Future<String> handler = executor.submit(new Callable() {
			@Override
			public String call() throws Exception {
				log.debug(" reward block solve started  : " + chainTargetFinal + " \n for block" + block);
				block.solve(chainTargetFinal);
				return "";
			}
		});
		Stopwatch watch = Stopwatch.createStarted();
		try {
			handler.get(scheduleConfiguration.getMiningrate(), TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			log.debug(" reward solve Timeout  {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
			handler.cancel(true);
			return null;
		} finally {
			executor.shutdownNow();
		}
		log.debug("Reward Solved time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
		return block;
	}

	public GetTXRewardResponse getMaxConfirmedReward(Map<String, Object> request, FullBlockStore store)
			throws BlockStoreException {

		return GetTXRewardResponse.create(cacheBlockService.getMaxConfirmedReward(store));

	}

	public GetTXRewardListResponse getAllConfirmedReward(Map<String, Object> request, FullBlockStore store)
			throws BlockStoreException {

		return GetTXRewardListResponse.create(store.getAllConfirmedReward());

	}

	public long calculateNextBlockDifficulty(RewardInfo currRewardInfo) {
		BigInteger difficultyTargetReward = Utils.decodeCompactBits(currRewardInfo.getDifficultyTargetReward());
		BigInteger difficultyChain = difficultyTargetReward
				.multiply(BigInteger.valueOf(NetworkParameters.TARGET_MAX_TPS));
		difficultyChain = difficultyChain.multiply(BigInteger.valueOf(NetworkParameters.TARGET_SPACING));

		if (difficultyChain.compareTo(networkParameters.getMaxTarget()) > 0) {
			// log.info("Difficulty hit proof of work limit: {}",
			// difficultyChain.toString(16));
			difficultyChain = networkParameters.getMaxTarget();
		}

		return Utils.encodeCompactBits(difficultyChain);
	}

}
