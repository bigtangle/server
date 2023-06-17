/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;
import com.google.common.math.LongMath;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancel;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.GenericInvalidityException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.exception.VerificationException.UnsolidException;
import net.bigtangle.script.Script;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.ChainBlockQueue;
import net.bigtangle.server.data.ContractEventRecord;
import net.bigtangle.server.data.DepthAndWeight;
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.data.SolidityState;
import net.bigtangle.server.data.SolidityState.State;
import net.bigtangle.server.service.ServiceBase;
import net.bigtangle.server.service.ServiceContract;
import net.bigtangle.server.service.StoreService;
import net.bigtangle.utils.Gzip;

/**
 * <p>
 * A FullBlockGraph works in conjunction with a {@link FullBlockStore} to verify
 * all the rules of the BigTangle system. Chain block as reward block is added
 * first into ChainBlockQueue as other blocks will be added in parallel. The
 * process of ChainBlockQueue by update chain is locked by database. Chain block
 * will add to chain if there is no exception. if the reward block is unsolid as
 * missing previous block, then it will trigger a sync and be deleted.
 * UpdateConfirm can add UTXO using MCMC and can run only as part of update
 * chain and will be boxed timeout.
 * 
 * </p>
 */
@Service
public class FullBlockGraph {

 
	private static final Logger log = LoggerFactory.getLogger(FullBlockGraph.class);

	@Autowired
	protected NetworkParameters networkParameters;
 
	@Autowired
	ServerConfiguration serverConfiguration;

	@Autowired
	private StoreService storeService;

 

	public boolean add(Block block, boolean allowUnsolid, FullBlockStore store) throws BlockStoreException {
		boolean added;
		if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
			added = addChain(block, allowUnsolid, true, store);
		} else {
			added = addNonChain(block, allowUnsolid, store);
		}

		// TODO move this function to wallet,
		// update spend of origin UTXO to avoid create of double spent
		if (added) {
			updateTransactionOutputSpendPending(block);
		}

		return added;
	}

	/*
	 * speedup of sync without updateTransactionOutputSpendPending.
	 */
	public boolean addNoSpendPending(Block block, boolean allowUnsolid, FullBlockStore store)
			throws BlockStoreException {
		boolean a;
		if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
			a = addChain(block, allowUnsolid, true, store);
		} else {
			a = addNonChain(block, allowUnsolid, store);
		}
		return a;
	}

	public boolean add(Block block, boolean allowUnsolid, boolean updatechain, FullBlockStore store)
			throws BlockStoreException {
		boolean a = add(block, allowUnsolid, store);
		if (updatechain) {
			updateChain();
		}
		return a;
	}

	public boolean addChain(Block block, boolean allowUnsolid, boolean tryConnecting, FullBlockStore store)
			throws BlockStoreException {

		// Check the block is partially formally valid and fulfills PoW
		block.verifyHeader();
		block.verifyTransactions();
		// no more check add data
		saveChainBlockQueue(block, store, false);

		return true;
	}

	public void updateChain() throws BlockStoreException {
		updateChainConnected();
		updateConfirmedTimeBoxed();
	}

	public void updateChainConnected() throws BlockStoreException {
		String LOCKID = "chain";
		int LockTime = 1000000;
		FullBlockStore store = storeService.getStore();
		try {
			// log.info("create Reward started");
			LockObject lock = store.selectLockobject(LOCKID);
			boolean canrun = false;
			if (lock == null) {
				try {
					store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
					canrun = true;
				} catch (Exception e) {
					// ignore
				}
			} else {
				if (lock.getLocktime() < System.currentTimeMillis() - LockTime) {
					store.deleteLockobject(LOCKID);
					store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
					canrun = true;
				} else {
					if (lock.getLocktime() < System.currentTimeMillis() - 10000)
						log.info("updateChain running start = " + Utils.dateTimeFormat(lock.getLocktime()));
				}
			}
			if (canrun) {
				Stopwatch watch = Stopwatch.createStarted();
				saveChainConnected(store, false);
				store.deleteLockobject(LOCKID);
				if (watch.elapsed(TimeUnit.MILLISECONDS) > 1000) {
					log.info("updateChain time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
				}
				watch.stop();
			}
		} catch (Exception e) {
			store.deleteLockobject(LOCKID);
			throw e;
		} finally {
			if (store != null)
				store.close();
		}

	}

	private void saveChainBlockQueue(Block block, FullBlockStore store, boolean orphan) throws BlockStoreException {
		// save the block
		try {
			store.beginDatabaseBatchWrite();
			ChainBlockQueue chainBlockQueue = new ChainBlockQueue(block.getHash().getBytes(),
					Gzip.compress(block.unsafeBitcoinSerialize()), block.getLastMiningRewardBlock(), orphan,
					block.getTimeSeconds());
			store.insertChainBlockQueue(chainBlockQueue);
			store.commitDatabaseBatchWrite();
		} catch (Exception e) {
			store.abortDatabaseBatchWrite();
			throw e;
		} finally {
			store.defaultDatabaseBatchWrite();

		}
	}

	/*
	 *  
	 */
	public void saveChainConnected(FullBlockStore store, boolean updatelowchain)
			throws VerificationException, BlockStoreException {
		List<ChainBlockQueue> cbs = store.selectChainblockqueue(false, serverConfiguration.getSyncblocks());
		if (cbs != null && !cbs.isEmpty()) {
			Stopwatch watch = Stopwatch.createStarted();
			log.info("selectChainblockqueue with size  " + cbs.size());
			// check only do add if there is longer chain as saved in database
			TXReward maxConfirmedReward = store.getMaxConfirmedReward();
			ChainBlockQueue maxFromQuery = cbs.get(cbs.size() - 1);
			if (!updatelowchain && maxConfirmedReward.getChainLength() > maxFromQuery.getChainlength()) {
				log.info("not longest chain in  selectChainblockqueue {}  < {}", maxFromQuery.toString(),
						maxConfirmedReward.toString());
				return;
			}
			for (ChainBlockQueue chainBlockQueue : cbs) {
				saveChainConnected(chainBlockQueue, store);
			}
			log.info("saveChainConnected time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
		}
	}

	private void saveChainConnected(ChainBlockQueue chainBlockQueue, FullBlockStore store)
			throws VerificationException, BlockStoreException {

		try {
			store.beginDatabaseBatchWrite();

			// It can be down lock for update of this on database
			Block block = networkParameters.getDefaultSerializer().makeBlock(chainBlockQueue.getBlock());

			// Check the block is partially formally valid and fulfills PoW
			block.verifyHeader();
			// block.verifyTransactions();

			ServiceBase serviceBase = new ServiceBase(serverConfiguration, networkParameters);
			SolidityState solidityState = serviceBase.checkChainSolidity(block, true, store);

			if (solidityState.isDirectlyMissing()) {
				log.debug("Block isDirectlyMissing. saveChainConnected stop to save." + block.toString());
				// sync the lastest chain from remote start from the -2 rewards
				// syncBlockService.startSingleProcess(block.getLastMiningRewardBlock()
				// - 2, false);
				return;
			}

			if (solidityState.isFailState()) {
				log.debug("Block isFailState. remove it from ChainBlockQueue." + block.toString());
				return;
			}
			// Inherit solidity from predecessors if they are not solid
			solidityState = serviceBase.getMinPredecessorSolidity(block, false, store, false);

			// Sanity check
			if (solidityState.isFailState() || solidityState.getState() == State.MissingPredecessor) {
				log.debug("Block isFailState. remove it from ChainBlockQueue." + block.toString());
				return;
			}
			connectRewardBlock(block, solidityState, store);
			store.commitDatabaseBatchWrite();
		} catch (Exception e) {
			store.abortDatabaseBatchWrite();
			throw e;
		} finally {
			deleteChainQueue(chainBlockQueue, store);
			store.defaultDatabaseBatchWrite();
		}
	}

	private void deleteChainQueue(ChainBlockQueue chainBlockQueue, FullBlockStore store) throws BlockStoreException {
		List<ChainBlockQueue> l = new ArrayList<ChainBlockQueue>();
		l.add(chainBlockQueue);
		store.deleteChainBlockQueue(l);
	}

	public boolean addNonChain(Block block, boolean allowUnsolid, FullBlockStore blockStore)
			throws BlockStoreException {

		// Check the block is partially formally valid and fulfills PoW

		block.verifyHeader();
		block.verifyTransactions();

		// allow non chain block predecessors not solid
		SolidityState solidityState = new ServiceBase(serverConfiguration, networkParameters).checkSolidity(block,
				!allowUnsolid, blockStore, false);
		if (solidityState.isFailState()) {
			log.debug(solidityState.toString());
		}
		// If explicitly wanted (e.g. new block from local clients), this
		// block must strictly be solid now.
		if (!allowUnsolid) {
			switch (solidityState.getState()) {
			case MissingPredecessor:
				throw new UnsolidException();
			case MissingCalculation:
			case Success:
				break;
			case Invalid:
				throw new GenericInvalidityException();
			}
		}

		// Accept the block
		try {
			blockStore.beginDatabaseBatchWrite();
			connect(block, solidityState, blockStore);
			blockStore.commitDatabaseBatchWrite();
		} catch (Exception e) {
			blockStore.abortDatabaseBatchWrite();
			throw e;
		} finally {
			blockStore.defaultDatabaseBatchWrite();
		}

		return true;
	}

	private void connectRewardBlock(final Block block, SolidityState solidityState, FullBlockStore store)
			throws BlockStoreException, VerificationException {

		if (solidityState.isFailState()) {
			connect(block, solidityState, store);
			return;
		}
		Block head = store.get(store.getMaxConfirmedReward().getBlockHash());
		if (block.getRewardInfo().getPrevRewardHash().equals(head.getHash())) {
			connect(block, solidityState, store);
			new ServiceBase(serverConfiguration, networkParameters).buildRewardChain(block, store);
		} else {
			// This block connects to somewhere other than the top of the best
			// known chain. We treat these differently.

			boolean haveNewBestChain = block.getRewardInfo().getChainlength() > head.getRewardInfo().getChainlength();
			// TODO check this
			// block.getRewardInfo().moreWorkThan(head.getRewardInfo());
			if (haveNewBestChain) {
				log.info("Block is causing a re-organize");
				connect(block, solidityState, store);
				new ServiceBase(serverConfiguration, networkParameters).handleNewBestChain(block, store);
			} else {
				// parallel chain, save as unconfirmed
				connect(block, solidityState, store);
			}

		}
	}

	/**
	 * Inserts the specified block into the DB
	 * 
	 * @param block         the block
	 * @param solidityState
	 * @param height        the block's height
	 * @throws BlockStoreException
	 * @throws VerificationException
	 */
	private void connect(final Block block, SolidityState solidityState, FullBlockStore store)
			throws BlockStoreException, VerificationException {

		store.put(block);
		solidifyBlock(block, solidityState, false, store);
	}

	/**
	 * Get the {@link Script} from the script bytes or return Script of empty byte
	 * array.
	 */
	private Script getScript(byte[] scriptBytes) {
		try {
			return new Script(scriptBytes);
		} catch (Exception e) {
			return new Script(new byte[0]);
		}
	}

	/**
	 * Get the address from the {@link Script} if it exists otherwise return empty
	 * string "".
	 *
	 * @param script The script.
	 * @return The address.
	 */
	private String getScriptAddress(@Nullable Script script) {
		String address = "";
		try {
			if (script != null) {
				address = script.getToAddress(networkParameters, true).toString();
			}
		} catch (Exception e) {
			// e.printStackTrace();
		}
		return address;
	}
 
 
	public void solidifyBlock(Block block, SolidityState solidityState, boolean setMilestoneSuccess,
			FullBlockStore blockStore) throws BlockStoreException {

		switch (solidityState.getState()) {
		case MissingCalculation:
			blockStore.updateBlockEvaluationSolid(block.getHash(), 1);

			// Reward blocks follow different logic: If this is new, run
			// consensus logic
			if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
				solidifyReward(block, blockStore);
				return;
			}
			// Insert other blocks into waiting list
			// insertUnsolidBlock(block, solidityState, blockStore);
			break;
		case MissingPredecessor:
			if (block.getBlockType() == Type.BLOCKTYPE_INITIAL
					&& blockStore.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() > 0) {
				throw new RuntimeException("Should not happen");
			}

			blockStore.updateBlockEvaluationSolid(block.getHash(), 0);

			// Insert into waiting list
			// insertUnsolidBlock(block, solidityState, blockStore);
			break;
		case Success:
			// If already set, nothing to do here...
			if (blockStore.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 2)
				return;

			// TODO don't calculate again, it may already have been calculated
			// before
			connectUTXOs(block, blockStore);
			connectTypeSpecificUTXOs(block, blockStore);
			new ServiceBase(serverConfiguration, networkParameters).calculateBlock(block, blockStore);

			if (block.getBlockType() == Type.BLOCKTYPE_REWARD && !setMilestoneSuccess) {
				// If we don't want to set the milestone success, initialize as
				// missing calc
				blockStore.updateBlockEvaluationSolid(block.getHash(), 1);
			} else {
				// Else normal update
				blockStore.updateBlockEvaluationSolid(block.getHash(), 2);
			}
			if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
				solidifyReward(block, blockStore);
				return;
			}

			break;
		case Invalid:

			blockStore.updateBlockEvaluationSolid(block.getHash(), -1);
			break;
		}
	}

	protected void connectUTXOs(Block block, FullBlockStore blockStore)
			throws BlockStoreException, VerificationException {
		List<Transaction> transactions = block.getTransactions();
		connectUTXOs(block, transactions, blockStore);
	}

	// TODO update other output data can be deadlock, as non chain block
	// run in parallel
	private void updateTransactionOutputSpendPending(Block block) {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final Future<String> handler = executor.submit(new Callable() {
			@Override
			public String call() throws Exception {

				FullBlockStore blockStore = storeService.getStore();
				try {
					updateTransactionOutputSpendPending(block, blockStore);

					// Initialize MCMC
					if (blockStore.getMCMC(block.getHash()) == null) {
						ArrayList<DepthAndWeight> depthAndWeight = new ArrayList<DepthAndWeight>();
						depthAndWeight.add(new DepthAndWeight(block.getHash(), 1, 0));
						blockStore.updateBlockEvaluationWeightAndDepth(depthAndWeight);
					}
				} finally {
					if (blockStore != null)
						blockStore.close();
				}
				return "";
			}
		});
		try {
			handler.get(2000l, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			log.info("TimeoutException cancel updateTransactionOutputSpendPending ");
			handler.cancel(true);
		} catch (Exception e) {
			// ignore
			log.info("updateTransactionOutputSpendPending", e);
		} finally {
			executor.shutdownNow();
		}

	}

	private void updateTransactionOutputSpendPending(Block block, FullBlockStore blockStore)
			throws BlockStoreException {
		for (final Transaction tx : block.getTransactions()) {
			boolean isCoinBase = tx.isCoinBase();
			List<UTXO> spendPending = new ArrayList<UTXO>();
			if (!isCoinBase) {
				for (int index = 0; index < tx.getInputs().size(); index++) {
					TransactionInput in = tx.getInputs().get(index);
					UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getBlockHash(),
							in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
					if (prevOut != null) {
						spendPending.add(prevOut);
					}
				}
			}

			blockStore.updateTransactionOutputSpendPending(spendPending);

		}
	}

	private void connectUTXOs(Block block, List<Transaction> transactions, FullBlockStore blockStore)
			throws BlockStoreException {
		for (final Transaction tx : transactions) {
			boolean isCoinBase = tx.isCoinBase();
			List<UTXO> utxos = new ArrayList<UTXO>();
			for (TransactionOutput out : tx.getOutputs()) {
				Script script = getScript(out.getScriptBytes());
				String fromAddress = fromAddress(tx, isCoinBase);
				int minsignnumber = 1;
				if (script.isSentToMultiSig()) {
					minsignnumber = script.getNumberOfSignaturesRequiredToSpend();
				}
				UTXO newOut = new UTXO(tx.getHash(), out.getIndex(), out.getValue(), isCoinBase, script,
						getScriptAddress(script), block.getHash(), fromAddress, tx.getMemo(),
						Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, minsignnumber, 0,
						block.getTimeSeconds(), null);

				if (!newOut.isZero()) {
					utxos.add(newOut);
					if (script.isSentToMultiSig()) {

						for (ECKey ecKey : script.getPubKeys()) {
							String toaddress = ecKey.toAddress(networkParameters).toBase58();
							OutputsMulti outputsMulti = new OutputsMulti(newOut.getTxHash(), toaddress,
									newOut.getIndex());
							blockStore.insertOutputsMulti(outputsMulti);
						}
					}
				}
				// reset cache
				// outputService.evictTransactionOutputs(fromAddress);
				// outputService.evictTransactionOutputs( getScriptAddress(script));
			}
			blockStore.addUnspentTransactionOutput(utxos);
		}
	}

	private String fromAddress(final Transaction tx, boolean isCoinBase) {
		String fromAddress = "";
		if (!isCoinBase) {
			for (TransactionInput t : tx.getInputs()) {
				try {
					if (t.getConnectedOutput().getScriptPubKey().isSentToAddress()) {
						fromAddress = t.getFromAddress().toBase58();
					} else {
						fromAddress = new Address(networkParameters,
								Utils.sha256hash160(t.getConnectedOutput().getScriptPubKey().getPubKey())).toBase58();

					}

					if (!fromAddress.equals(""))
						return fromAddress;
				} catch (Exception e) {
					// No address found.
				}
			}
			return fromAddress;
		}
		return fromAddress;
	}

	private void connectTypeSpecificUTXOs(Block block, FullBlockStore blockStore) throws BlockStoreException {
		switch (block.getBlockType()) {
		case BLOCKTYPE_CROSSTANGLE:
			break;
		case BLOCKTYPE_FILE:
			break;
		case BLOCKTYPE_GOVERNANCE:
			break;
		case BLOCKTYPE_INITIAL:
			break;
		case BLOCKTYPE_REWARD:
			break;
		case BLOCKTYPE_TOKEN_CREATION:
			connectToken(block, blockStore);
			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			new ServiceContract(serverConfiguration, networkParameters).connectContractExecute(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_OPEN:
			new ServiceBase(serverConfiguration, networkParameters).connectOrder(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			connectCancelOrder(block, blockStore);
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			connectContractEvent(block, blockStore);
		default:
			break;

		}
	}

	private void connectCancelOrder(Block block, FullBlockStore blockStore) throws BlockStoreException {
		try {
			OrderCancelInfo info = new OrderCancelInfo().parse(block.getTransactions().get(0).getData());
			OrderCancel record = new OrderCancel(info.getBlockHash());
			record.setBlockHash(block.getHash());
			blockStore.insertCancelOrder(record);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * price is in version 1 not in OrderOpenInfo
	 */
	/*
	 * Buy order is defined by given targetValue=buy amount, targetToken=buytoken
	 * 
	 * offervalue = targetValue * price / 10**targetDecimal price= offervalue *
	 * 10**targetDecimal/targetValue offerToken=orderBaseToken
	 * 
	 */
	/*
	 * Sell Order is defined by given offerValue= sell amount, offentoken=sellToken
	 * 
	 * targetvalue = offervalue * price / 10**offerDecimal
	 * targetToken=orderBaseToken
	 */
	public void versionPrice(OrderRecord record, OrderOpenInfo reqInfo) {
		if (reqInfo.getVersion() == 1) {
			boolean buy = record.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING);
			if (buy) {
				record.setPrice(calc(record.getOfferValue(), LongMath.checkedPow(10, record.getTokenDecimals()),
						record.getTargetValue()));
			} else {
				record.setPrice(calc(record.getTargetValue(), LongMath.checkedPow(10, record.getTokenDecimals()),
						record.getOfferValue()));
			}
		}
	}

	public Long calc(long m, long factor, long d) {
		return BigInteger.valueOf(m).multiply(BigInteger.valueOf(factor)).divide(BigInteger.valueOf(d)).longValue();
	}

	private void connectContractEvent(Block block, FullBlockStore blockStore) throws BlockStoreException {
		try {
			ContractEventInfo reqInfo = new ContractEventInfo().parse(block.getTransactions().get(0).getData());

			ContractEventRecord record = new ContractEventRecord(block.getHash(),
					reqInfo.getContractTokenid(), false, false, null, reqInfo.getOfferValue(),
					reqInfo.getOfferTokenid(), reqInfo.getValidToTime(), reqInfo.getValidFromTime(),
					reqInfo.getBeneficiaryAddress());
			List<ContractEventRecord> contracts = new ArrayList<ContractEventRecord>();
			contracts.add(record);
			blockStore.insertContractEvent(contracts);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void connectToken(Block block, FullBlockStore blockStore) throws BlockStoreException {
		Transaction tx = block.getTransactions().get(0);
		if (tx.getData() != null) {
			try {
				byte[] buf = tx.getData();
				TokenInfo tokenInfo = new TokenInfo().parse(buf);

				// Correctly insert tokens
				tokenInfo.getToken().setConfirmed(false);
				tokenInfo.getToken().setBlockHash(block.getHash());

				blockStore.insertToken(block.getHash(), tokenInfo.getToken());

				// Correctly insert additional data
				for (MultiSignAddress permissionedAddress : tokenInfo.getMultiSignAddresses()) {
					if (permissionedAddress == null)
						continue;
					// The primary key must be the correct block
					permissionedAddress.setBlockhash(block.getHash());
					permissionedAddress.setTokenid(tokenInfo.getToken().getTokenid());
					if (permissionedAddress.getAddress() != null)
						blockStore.insertMultiSignAddress(permissionedAddress);
				}
			} catch (IOException e) {

				throw new RuntimeException(e);
			}

		}
	}
 
	 
  
 
	/*
	 * It must use BigInteger to calculation to avoid overflow. Order can handle
	 * only Long
	 */
	public Long totalAmount(long price, long amount, int tokenDecimal) {

		BigInteger[] rearray = BigInteger.valueOf(price).multiply(BigInteger.valueOf(amount))
				.divideAndRemainder(BigInteger.valueOf(LongMath.checkedPow(10, tokenDecimal)));
		BigInteger re = rearray[0];
		BigInteger remainder = rearray[1];
		if (remainder.compareTo(BigInteger.ZERO) > 0) {
			// This remainder will cut
			// log.debug("Price and quantity value with remainder " + remainder
			// + "/"
			// + BigInteger.valueOf(LongMath.checkedPow(10, tokenDecimal)));
		}

		if (re.compareTo(BigInteger.ZERO) < 0 || re.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
			throw new InvalidTransactionDataException("Invalid target total value: " + re);
		}
		return re.longValue();
	}
 
 
	/**
	 * Deterministically creates a mining reward transaction based on the previous
	 * blocks and previous reward transaction. DOES NOT CHECK FOR SOLIDITY. You have
	 * to ensure that the approved blocks result in an eligible reward block.
	 * 
	 * @return mining reward transaction
	 * @throws BlockStoreException
	 */
	public Transaction generateVirtualMiningRewardTX(Block block, FullBlockStore blockStore)
			throws BlockStoreException {

		RewardInfo rewardInfo = new RewardInfo().parseChecked(block.getTransactions().get(0).getData());
		Set<Sha256Hash> candidateBlocks = rewardInfo.getBlocks();

		// Count how many blocks from miners in the reward interval are approved
		// and build rewards
		Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
				Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
		for (Sha256Hash bHash : candidateBlocks) {
			blockQueue.add(blockStore.getBlockWrap(bHash));
		}

		// Initialize
		Set<BlockWrap> currentHeightBlocks = new HashSet<>();
		Map<BlockWrap, Set<Sha256Hash>> snapshotWeights = new HashMap<>();
		Map<Address, Long> finalRewardCount = new HashMap<>();
		BlockWrap currentBlock = null, approvedBlock = null;
		Address consensusBlockMiner = new Address(networkParameters, block.getMinerAddress());
		long currentHeight = Long.MAX_VALUE;
		long totalRewardCount = 0;

		for (BlockWrap tip : blockQueue) {
			snapshotWeights.put(tip, new HashSet<>());
		}

		// Go backwards by height
		while ((currentBlock = blockQueue.poll()) != null) {

			// If we have reached a new height level, trigger payout
			// calculation
			if (currentHeight > currentBlock.getBlockEvaluation().getHeight()) {

				// Calculate rewards
				totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
						totalRewardCount);

				// Finished with this height level, go to next level
				currentHeightBlocks.clear();
				long currentHeight_ = currentHeight;
				snapshotWeights.entrySet().removeIf(e -> e.getKey().getBlockEvaluation().getHeight() == currentHeight_);
				currentHeight = currentBlock.getBlockEvaluation().getHeight();
			}

			// Stop criterion: Block not in candidate list
			if (!candidateBlocks.contains(currentBlock.getBlockHash()))
				continue;

			// Add your own hash to approver hashes of current approver hashes
			snapshotWeights.get(currentBlock).add(currentBlock.getBlockHash());

			// Count the blocks of current height
			currentHeightBlocks.add(currentBlock);

			// Continue with both approved blocks
			approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
			if (!blockQueue.contains(approvedBlock)) {
				if (approvedBlock != null) {
					blockQueue.add(approvedBlock);
					snapshotWeights.put(approvedBlock, new HashSet<>(snapshotWeights.get(currentBlock)));
				}
			} else {
				snapshotWeights.get(approvedBlock).add(currentBlock.getBlockHash());
			}
			approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
			if (!blockQueue.contains(approvedBlock)) {
				if (approvedBlock != null) {
					blockQueue.add(approvedBlock);
					snapshotWeights.put(approvedBlock, new HashSet<>(snapshotWeights.get(currentBlock)));
				}
			} else {
				snapshotWeights.get(approvedBlock).add(currentBlock.getBlockHash());
			}
		}

		// Exception for height 0 (genesis): since prevblock does not exist,
		// finish payout
		// calculation
		if (currentHeight == 0) {
			totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
					totalRewardCount);
		}

		// Build transaction outputs sorted by addresses
		Transaction tx = new Transaction(networkParameters);

		// Reward the consensus block with the static reward
		tx.addOutput(Coin.SATOSHI.times(NetworkParameters.REWARD_AMOUNT_BLOCK_REWARD), consensusBlockMiner);

		// Reward twice: once the consensus block, once the normal block maker
		// of good quantiles
		for (Entry<Address, Long> entry : finalRewardCount.entrySet().stream()
				.sorted(Comparator.comparing((Entry<Address, Long> e) -> e.getKey())).collect(Collectors.toList())) {
			tx.addOutput(Coin.SATOSHI.times(entry.getValue() * NetworkParameters.PER_BLOCK_REWARD),
					consensusBlockMiner);
			tx.addOutput(Coin.SATOSHI.times(entry.getValue() * NetworkParameters.PER_BLOCK_REWARD), entry.getKey());
		}

		// The input does not really need to be a valid signature, as long
		// as it has the right general form and is slightly different for
		// different tx
		TransactionInput input = new TransactionInput(networkParameters, tx, Script
				.createInputScript(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
		tx.addInput(input);
		tx.setMemo(new MemoInfo("MiningRewardTX"));
		return tx;
	}

	// For each height, throw away anything below the 99-percentile
	// in terms of reduced weight
	private long calculateHeightRewards(Set<BlockWrap> currentHeightBlocks,
			Map<BlockWrap, Set<Sha256Hash>> snapshotWeights, Map<Address, Long> finalRewardCount,
			long totalRewardCount) {
		long heightRewardCount = (long) Math.ceil(0.95d * currentHeightBlocks.size());
		totalRewardCount += heightRewardCount;

		long rewarded = 0;
		for (BlockWrap rewardedBlock : currentHeightBlocks.stream()
				.sorted(Comparator.comparingLong(b -> snapshotWeights.get(b).size()).reversed())
				.collect(Collectors.toList())) {
			if (rewarded >= heightRewardCount)
				break;

			Address miner = new Address(networkParameters, rewardedBlock.getBlock().getMinerAddress());
			if (!finalRewardCount.containsKey(miner))
				finalRewardCount.put(miner, 1L);
			else
				finalRewardCount.put(miner, finalRewardCount.get(miner) + 1);
			rewarded++;
		}
		return totalRewardCount;
	}

	public void updateConfirmedDo(TXReward maxConfirmedReward, FullBlockStore blockStore) throws BlockStoreException {

		// First remove any blocks that should no longer be in the milestone
		HashSet<BlockEvaluation> blocksToRemove = blockStore.getBlocksToUnconfirm();
		HashSet<Sha256Hash> traversedUnconfirms = new HashSet<>();
		ServiceBase serviceBase = new ServiceBase(serverConfiguration, networkParameters);
		for (BlockEvaluation block : blocksToRemove) {

			try {
				blockStore.beginDatabaseBatchWrite();
				serviceBase.unconfirm(block.getBlockHash(), traversedUnconfirms, blockStore);
				blockStore.commitDatabaseBatchWrite();
			} catch (Exception e) {
				blockStore.abortDatabaseBatchWrite();
				throw e;
			} finally {
				blockStore.defaultDatabaseBatchWrite();
			}
		}
		// TXReward maxConfirmedReward = blockStore.getMaxConfirmedReward();

		long cutoffHeight = serviceBase.getCurrentCutoffHeight(maxConfirmedReward, blockStore);
		long maxHeight = serviceBase.getCurrentMaxHeight(maxConfirmedReward, blockStore);

		// Now try to find blocks that can be added to the milestone.
		// DISALLOWS UNSOLID
		TreeSet<BlockWrap> blocksToAdd = blockStore.getBlocksToConfirm(cutoffHeight, maxHeight);

		// VALIDITY CHECKS
		serviceBase.resolveAllConflicts(blocksToAdd, cutoffHeight, blockStore);

		// Finally add the resolved new blocks to the confirmed set
		HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
		for (BlockWrap block : blocksToAdd) {
			try {
				blockStore.beginDatabaseBatchWrite();
				new ServiceBase(serverConfiguration, networkParameters)
						.confirm(block.getBlockEvaluation().getBlockHash(), traversedConfirms, (long) -1, blockStore);
				blockStore.commitDatabaseBatchWrite();
			} catch (Exception e) {
				blockStore.abortDatabaseBatchWrite();
				throw e;
			} finally {
				blockStore.defaultDatabaseBatchWrite();
			}
		}

	}

	private void solidifyReward(Block block, FullBlockStore blockStore) throws BlockStoreException {

		RewardInfo rewardInfo = new RewardInfo().parseChecked(block.getTransactions().get(0).getData());
		Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
		long currChainLength = blockStore.getRewardChainLength(prevRewardHash) + 1;
		long difficulty = rewardInfo.getDifficultyTargetReward();

		blockStore.insertReward(block.getHash(), prevRewardHash, difficulty, currChainLength);
	}

	public void updateConfirmed() throws BlockStoreException {
		String LOCKID = "chain";
		int LockTime = 1000000;
		FullBlockStore store = storeService.getStore();
		try {
			// log.info("create Reward started");
			LockObject lock = store.selectLockobject(LOCKID);
			boolean canrun = false;
			if (lock == null) {
				try {
					store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
					canrun = true;
				} catch (Exception e) {
					// ignore
				}
			} else {
				if (lock.getLocktime() < System.currentTimeMillis() - LockTime) {
					store.deleteLockobject(LOCKID);
					store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
					canrun = true;
				} else {
					if (lock.getLocktime() < System.currentTimeMillis() - 2000)
						log.info("updateConfirmed running start = " + Utils.dateTimeFormat(lock.getLocktime()));
				}
			}
			if (canrun) {
				Stopwatch watch = Stopwatch.createStarted();
				TXReward maxConfirmedReward = store.getMaxConfirmedReward();
				updateConfirmedDo(maxConfirmedReward, store);
				if (watch.elapsed(TimeUnit.MILLISECONDS) > 1000) {
					log.info("updateConfirmedDo time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
				}

				cleanUp(maxConfirmedReward, store);
				if (watch.elapsed(TimeUnit.MILLISECONDS) > 1000) {
					log.info("cleanUp time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
				}

				store.deleteLockobject(LOCKID);
				watch.stop();
			}
		} catch (Exception e) {
			store.deleteLockobject(LOCKID);
			log.info(" ", e);
			throw e;
		} finally {
			if (store != null)
				store.close();
		}
	}

	public void cleanUp(TXReward maxConfirmedReward, FullBlockStore store) throws BlockStoreException {
		cleanUpDo(maxConfirmedReward, store);
	}

	public void cleanUpDo(TXReward maxConfirmedReward, FullBlockStore store) throws BlockStoreException {

		Block rewardblock = store.get(maxConfirmedReward.getBlockHash());
		// log.info(" cleanUpDo until block " + "" + rewardblock);
		store.prunedClosedOrders(rewardblock.getTimeSeconds());
		// max keep 500 blockchain as spendblock number

		long maxRewardblock = rewardblock.getLastMiningRewardBlock() - 500;
		// log.info(" prunedHistoryUTXO until reward block " + "" + rewardblock);
		store.prunedHistoryUTXO(maxRewardblock);
		// store.prunedPriceTicker(rewardblock.getTimeSeconds() - 30 *
		// DaySeconds);

	}

	/*
	 * run timeboxed updateConfirmed, there is no transaction here. Timeout will
	 * cancel the rest of update confirm and can be update from next run
	 */
	public void updateConfirmedTimeBoxed() throws BlockStoreException {

		ExecutorService executor = Executors.newSingleThreadExecutor();
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final Future<String> handler = executor.submit(new Callable() {
			@Override
			public String call() throws Exception {
				updateConfirmed();
				return "";
			}
		});
		try {
			handler.get(3000l, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			log.info("Timeout cancel updateConfirmed ");
			handler.cancel(true);
		} catch (Exception e) {
			// ignore
			log.info("updateConfirmed", e);
		} finally {
			executor.shutdownNow();
		}

	}

}
