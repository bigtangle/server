/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.math.LongMath;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.BlockMCMC;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancel;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PermissionDomainname;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.ConflictPossibleException;
import net.bigtangle.core.exception.VerificationException.CutoffException;
import net.bigtangle.core.exception.VerificationException.DifficultyConsensusInheritanceException;
import net.bigtangle.core.exception.VerificationException.GenesisBlockDisallowedException;
import net.bigtangle.core.exception.VerificationException.IncorrectTransactionCountException;
import net.bigtangle.core.exception.VerificationException.InsufficientSignaturesException;
import net.bigtangle.core.exception.VerificationException.InvalidDependencyException;
import net.bigtangle.core.exception.VerificationException.InvalidOrderException;
import net.bigtangle.core.exception.VerificationException.InvalidSignatureException;
import net.bigtangle.core.exception.VerificationException.InvalidTokenOutputException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionException;
import net.bigtangle.core.exception.VerificationException.MalformedTransactionDataException;
import net.bigtangle.core.exception.VerificationException.MissingDependencyException;
import net.bigtangle.core.exception.VerificationException.MissingSignatureException;
import net.bigtangle.core.exception.VerificationException.MissingTransactionDataException;
import net.bigtangle.core.exception.VerificationException.NotCoinbaseException;
import net.bigtangle.core.exception.VerificationException.PreviousTokenDisallowsException;
import net.bigtangle.core.exception.VerificationException.SigOpsException;
import net.bigtangle.core.exception.VerificationException.TimeReversionException;
import net.bigtangle.core.exception.VerificationException.TransactionOutputsDisallowedException;
import net.bigtangle.core.exception.VerificationException.UnsolidException;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.core.ordermatch.OrderBookEvents;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;
import net.bigtangle.core.ordermatch.OrderBookEvents.Match;
import net.bigtangle.core.ordermatch.TradePair;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.core.response.MultiSignByRequest;
import net.bigtangle.core.response.PermissionedAddressesResponse;
import net.bigtangle.script.Script;
import net.bigtangle.script.Script.VerifyFlag;
import net.bigtangle.server.config.BurnedAddress;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.core.ConflictCandidate;
import net.bigtangle.server.data.ContractEventRecord;
import net.bigtangle.server.data.ContractResult;
import net.bigtangle.server.data.OrderMatchingResult;
import net.bigtangle.server.data.SolidityState;
import net.bigtangle.server.data.SolidityState.State;
import net.bigtangle.server.utils.OrderBook;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.ContextPropagatingThreadFactory;
import net.bigtangle.utils.DomainValidator;
import net.bigtangle.utils.Json;

public class ServiceBase {

	protected ServerConfiguration serverConfiguration;
	protected NetworkParameters networkParameters;

	private static final Logger logger = LoggerFactory.getLogger(ServiceBase.class);

	public ServiceBase(ServerConfiguration serverConfiguration, NetworkParameters networkParameters) {
		super();
		this.serverConfiguration = serverConfiguration;
		this.networkParameters = networkParameters;
	}

	// cache only binary block only
	// @Cacheable(value = "blocksCache", key = "#blockhash")
	public Block getBlock(Sha256Hash blockhash, FullBlockStore store) throws BlockStoreException {
		// logger.debug("read from databse and no cache for:"+ blockhash);
		return store.get(blockhash);
	}

	public BlockWrap getBlockWrap(Sha256Hash blockhash, FullBlockStore store) throws BlockStoreException {
		return store.getBlockWrap(blockhash);
	}

	public List<Block> getBlocks(List<Sha256Hash> hashes, FullBlockStore store) throws BlockStoreException {
		List<Block> blocks = new ArrayList<>();
		for (Sha256Hash hash : hashes) {
			blocks.add(getBlock(hash, store));
		}
		return blocks;
	}

	public List<BlockWrap> getBlockWraps(List<Sha256Hash> hashes, FullBlockStore store) throws BlockStoreException {
		List<BlockWrap> blocks = new ArrayList<>();
		for (Sha256Hash hash : hashes) {
			blocks.add(getBlockWrap(hash, store));
		}
		return blocks;
	}

	public BlockEvaluation getBlockEvaluation(Sha256Hash hash, FullBlockStore store) throws BlockStoreException {
		return store.getBlockWrap(hash).getBlockEvaluation();
	}

	public BlockMCMC getBlockMCMC(Sha256Hash hash, FullBlockStore store) throws BlockStoreException {
		return store.getBlockWrap(hash).getMcmc();
	}

	public long getTimeSeconds(int days) {
		return System.currentTimeMillis() / 1000 - (long) days * 60 * 24 * 60;
	}

	/**
	 * Recursively removes the specified block and its approvers from the collection
	 * if this block is contained in the collection.
	 */
	public void removeBlockAndApproversFrom(Collection<BlockWrap> blocks, BlockWrap startingBlock, FullBlockStore store)
			throws BlockStoreException {

		PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
				Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()));
		Set<Sha256Hash> blockQueueSet = new HashSet<>();
		blockQueue.add(startingBlock);
		blockQueueSet.add(startingBlock.getBlockHash());

		while (!blockQueue.isEmpty()) {
			BlockWrap block = blockQueue.poll();
			blockQueueSet.remove(block.getBlockHash());

			// Nothing to remove further if not in set
			if (!blocks.contains(block))
				continue;

			// Remove this block.
			blocks.remove(block);

			// Queue all of its approver blocks if not already queued.
			for (Sha256Hash req : store.getSolidApproverBlockHashes(block.getBlockHash())) {
				if (!blockQueueSet.contains(req)) {
					BlockWrap pred = store.getBlockWrap(req);
					blockQueueSet.add(req);
					blockQueue.add(pred);
				}
			}
		}
	}

	/**
	 * Recursively adds the specified block and its approvers to the collection if
	 * the blocks are in the current milestone and not in the collection.
	 */
	public void addConfirmedApproversTo(Collection<BlockWrap> blocks, BlockWrap startingBlock, FullBlockStore store)
			throws BlockStoreException {

		PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
				Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()));
		Set<Sha256Hash> blockQueueSet = new HashSet<>();
		blockQueue.add(startingBlock);
		blockQueueSet.add(startingBlock.getBlockHash());

		while (!blockQueue.isEmpty()) {
			BlockWrap block = blockQueue.poll();
			blockQueueSet.remove(block.getBlockHash());

			// Nothing added if already in set or not confirmed
			if (!block.getBlockEvaluation().isConfirmed() || blocks.contains(block))
				continue;

			// Add this block.
			blocks.add(block);

			// Queue all of its confirmed approver blocks if not already queued.
			for (Sha256Hash req : store.getSolidApproverBlockHashes(block.getBlockHash())) {
				if (!blockQueueSet.contains(req)) {
					BlockWrap pred = store.getBlockWrap(req);
					blockQueueSet.add(req);
					blockQueue.add(pred);
				}
			}
		}
	}

	/**
	 * Recursively adds the specified block and its approved blocks to the
	 * collection if the blocks are not in the collection. if a block is missing
	 * somewhere, returns false. throwException will be true, if it required the
	 * validation for consensus. Otherwise, it does ignore the cutoff blocks.
	 *
	 */
	public boolean addRequiredNonContainedBlockHashesTo(Collection<Sha256Hash> blocks, BlockWrap startingBlock,
			long cutoffHeight, long prevMilestoneNumber, boolean throwException, FullBlockStore store)
			throws BlockStoreException {

		PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
				Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
		Set<Sha256Hash> blockQueueSet = new HashSet<>();
		blockQueue.add(startingBlock);
		blockQueueSet.add(startingBlock.getBlockHash());
		boolean notMissingAnything = true;

		while (!blockQueue.isEmpty()) {
			BlockWrap block = blockQueue.poll();
			blockQueueSet.remove(block.getBlockHash());

			// Nothing added if already in set
			if (blocks.contains(block.getBlockHash()))
				continue;

			// Nothing added if already in milestone
			if (block.getBlockEvaluation().getMilestone() >= 0
					&& block.getBlockEvaluation().getMilestone() <= prevMilestoneNumber)
				continue;

			// Check if the block is in cutoff and not in chain
			if (block.getBlock().getHeight() <= cutoffHeight && block.getBlockEvaluation().getMilestone() < 0) {
				if (throwException) {
					// TODO throw new CutoffException(
					logger.debug("Block is cut off at " + cutoffHeight + " for block: " + block.getBlock().toString());
				} else {
					notMissingAnything = false;
					continue;
				}
			}

			// Add this block.
			blocks.add(block.getBlockHash());

			// Queue all of its required blocks if not already queued.
			for (Sha256Hash req : getAllRequiredBlockHashes(block.getBlock(), false)) {
				if (!blockQueueSet.contains(req)) {
					BlockWrap pred = store.getBlockWrap(req);
					if (pred == null) {
						notMissingAnything = false;
						continue;
					} else {
						blockQueueSet.add(req);
						blockQueue.add(pred);
					}
				}
			}
		}

		return notMissingAnything;
	}

	/**
	 * Recursively adds the specified block and its approved blocks to the
	 * collection if the blocks are not in the current milestone and not in the
	 * collection. if a block is missing somewhere, returns false.
	 *
	 */
	public boolean addRequiredUnconfirmedBlocksTo(Collection<BlockWrap> blocks, BlockWrap startingBlock,
			long cutoffHeight, FullBlockStore store) throws BlockStoreException {

		PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
				Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
		Set<Sha256Hash> blockQueueSet = new HashSet<>();
		blockQueue.add(startingBlock);
		blockQueueSet.add(startingBlock.getBlockHash());
		boolean notMissingAnything = true;

		while (!blockQueue.isEmpty()) {
			BlockWrap block = blockQueue.poll();
			blockQueueSet.remove(block.getBlockHash());

			// Nothing added if already in set or confirmed
			if (block.getBlockEvaluation().getMilestone() >= 0 || block.getBlockEvaluation().isConfirmed()
					|| blocks.contains(block))
				continue;

			// Check if the block is in cutoff and not in chain
			if (block.getBlock().getHeight() <= cutoffHeight && block.getBlockEvaluation().getMilestone() < 0) {
				throw new CutoffException(
						"Block is cut off at " + cutoffHeight + " for block: " + block.getBlock().toString());
			}

			// Add this block.
			blocks.add(block);

			// Queue all of its required blocks if not already queued.
			for (Sha256Hash req : getAllRequiredBlockHashes(block.getBlock(), false)) {
				if (!blockQueueSet.contains(req)) {
					BlockWrap pred = store.getBlockWrap(req);
					if (pred == null) {
						notMissingAnything = false;
						continue;
					} else {
						blockQueueSet.add(req);
						blockQueue.add(pred);
					}
				}
			}
		}

		return notMissingAnything;
	}

	public AbstractResponse searchBlock(Map<String, Object> request, FullBlockStore store) throws BlockStoreException {
		@SuppressWarnings("unchecked")
		List<String> address = (List<String>) request.get("address");
		String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
		long height = request.get("height") == null ? 0L : Long.parseLong(request.get("height").toString());
		List<BlockEvaluationDisplay> evaluations = store.getSearchBlockEvaluations(address, lastestAmount, height,
				serverConfiguration.getMaxsearchblocks());
		return GetBlockEvaluationsResponse.create(evaluations);
	}

	public AbstractResponse searchBlockByBlockHashs(Map<String, Object> request, FullBlockStore store)
			throws BlockStoreException {
		@SuppressWarnings("unchecked")
		List<String> blockhashs = (List<String>) request.get("blockhashs");
		List<BlockEvaluationDisplay> evaluations = store.getSearchBlockEvaluationsByhashs(blockhashs);

		return GetBlockEvaluationsResponse.create(evaluations);
	}

	public List<BlockWrap> getEntryPointCandidates(long currChainLength, FullBlockStore store)
			throws BlockStoreException {
		return store.getEntryPoints(currChainLength);
	}

	public void batchBlock(Block block, FullBlockStore store) throws BlockStoreException {

		store.insertBatchBlock(block);
	}

	public void insertMyserverblocks(Sha256Hash prevhash, Sha256Hash hash, Long inserttime, FullBlockStore store)
			throws BlockStoreException {

		store.insertMyserverblocks(prevhash, hash, inserttime);
	}

	public boolean existMyserverblocks(Sha256Hash prevhash, FullBlockStore store) throws BlockStoreException {

		return store.existMyserverblocks(prevhash);
	}

	public void deleteMyserverblocks(Sha256Hash prevhash, FullBlockStore store) throws BlockStoreException {

		store.deleteMyserverblocks(prevhash);
	}

	public GetBlockListResponse blocksFromChainLength(Long start, Long end, FullBlockStore store)
			throws BlockStoreException {

		return GetBlockListResponse.create(store.blocksFromChainLength(start, end));
	}

	public GetBlockListResponse blocksFromNonChainHeigth(long cutoffHeight, FullBlockStore store)
			throws BlockStoreException {

		TXReward maxConfirmedReward = store.getMaxConfirmedReward();
		long my = getCurrentCutoffHeight(maxConfirmedReward, store);
		return GetBlockListResponse.create(store.blocksFromNonChainHeigth(Math.max(cutoffHeight, my)));
	}

	public boolean getUTXOSpent(TransactionOutPoint txout, FullBlockStore store) throws BlockStoreException {
		UTXO a = store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
		if(a==null) {
			solidifyWaiting( store .get(txout.getBlockHash()), store);
			a = store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
 		}
		 return a.isSpent();
 
	}

	public boolean getUTXOConfirmed(TransactionOutPoint txout, FullBlockStore store) throws BlockStoreException {
		return store.getOutputConfirmation(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
	}

	public BlockEvaluation getUTXOSpender(TransactionOutPoint txout, FullBlockStore store) throws BlockStoreException {
		return store.getTransactionOutputSpender(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
	}

	public UTXO getUTXO(TransactionOutPoint out, FullBlockStore store) throws BlockStoreException {
		return store.getTransactionOutput(out.getBlockHash(), out.getTxHash(), out.getIndex());
	}

	public long calcHeightRequiredBlocks(Block block, List<BlockWrap> allPredecessors, FullBlockStore store)
			throws BlockStoreException {
		// List<BlockWrap> requires = getAllRequirements(block, store);
		long height = 0;
		for (BlockWrap b : allPredecessors) {
			height = Math.max(height, b.getBlock().getHeight());
		}
		return height + 1;
	}

	public List<BlockWrap> getAllRequirements(Block block, FullBlockStore store) throws BlockStoreException {
		return getAllRequirements(block, getAllRequiredBlockHashes(block, false), store);
	}

	public List<BlockWrap> getAllRequirements(Block block, Set<Sha256Hash> allPredecessorBlockHashes,
			FullBlockStore store) throws BlockStoreException {
		List<BlockWrap> result = new ArrayList<>();
		for (Sha256Hash pred : allPredecessorBlockHashes)
			result.add(store.getBlockWrap(pred));
		return result;
	}

	public long getCurrentMaxHeight(TXReward maxConfirmedReward, FullBlockStore store) throws BlockStoreException {
		// TXReward maxConfirmedReward = store.getMaxConfirmedReward();
		if (maxConfirmedReward == null)
			return NetworkParameters.FORWARD_BLOCK_HORIZON;
		return store.get(maxConfirmedReward.getBlockHash()).getHeight() + NetworkParameters.FORWARD_BLOCK_HORIZON;
	}

	public long getCurrentCutoffHeight(TXReward maxConfirmedReward, FullBlockStore store) throws BlockStoreException {
		// TXReward maxConfirmedReward = store.getMaxConfirmedReward();
		if (maxConfirmedReward == null)
			return 0;
		long chainlength = Math.max(0, maxConfirmedReward.getChainLength() - NetworkParameters.MILESTONE_CUTOFF);
		TXReward confirmedAtHeightReward = store.getRewardConfirmedAtHeight(chainlength);
		return store.get(confirmedAtHeightReward.getBlockHash()).getHeight();
	}

	public long getRewardMaxHeight(Sha256Hash prevRewardHash) {
		return Long.MAX_VALUE;
		// Block rewardBlock = store.get(prevRewardHash);
		// return rewardBlock.getHeight() +
		// NetworkParameters.FORWARD_BLOCK_HORIZON;
	}

	public long getRewardCutoffHeight(Sha256Hash prevRewardHash, FullBlockStore store) throws BlockStoreException {

		Sha256Hash currPrevRewardHash = prevRewardHash;
		for (int i = 0; i < NetworkParameters.MILESTONE_CUTOFF; i++) {
			Block currRewardBlock;

			currRewardBlock = getBlock(currPrevRewardHash, store);
			RewardInfo currRewardInfo = new RewardInfo()
					.parseChecked(currRewardBlock.getTransactions().get(0).getData());
			if (currPrevRewardHash.equals(networkParameters.getGenesisBlock().getHash()))
				return 0;

			currPrevRewardHash = currRewardInfo.getPrevRewardHash();

		}
		return store.get(currPrevRewardHash).getHeight();
	}

	/**
	 * Returns all blocks that must be confirmed if this block is confirmed.
	 *
	 */

	public Set<Sha256Hash> getAllRequiredBlockHashes(Block block, boolean includeTransaction) {
		Set<Sha256Hash> predecessors = new HashSet<>();
		predecessors.add(block.getPrevBlockHash());
		predecessors.add(block.getPrevBranchBlockHash());

		// All used transaction outputs

		final List<Transaction> transactions = block.getTransactions();

		for (final Transaction tx : transactions) {
			if (!tx.isCoinBase()) {
				for (int index = 0; index < tx.getInputs().size(); index++) {
					TransactionInput in = tx.getInputs().get(index);
					// due to virtual txs from order/reward
					predecessors.add(in.getOutpoint().getBlockHash());
				}
			}

		}
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
			RewardInfo rewardInfo = new RewardInfo().parseChecked(transactions.get(0).getData());
			predecessors.add(rewardInfo.getPrevRewardHash());
			break;
		case BLOCKTYPE_TOKEN_CREATION:
			TokenInfo currentToken = new TokenInfo().parseChecked(transactions.get(0).getData());
			predecessors.add(Sha256Hash.wrap(currentToken.getToken().getDomainNameBlockHash()));
			if (currentToken.getToken().getPrevblockhash() != null)
				predecessors.add(currentToken.getToken().getPrevblockhash());
			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_OPEN:
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			// OrderCancelInfo opInfo = new
			// OrderCancelInfo().parseChecked(transactions.get(0).getData());
			// predecessors.add(opInfo.getBlockHash());
			break;
		default:
			throw new RuntimeException("No Implementation");
		}

		return predecessors;
	}

	/*
	 * failed blocks without conflict for retry
	 */
	public AbstractResponse findRetryBlocks(Map<String, Object> request, FullBlockStore store)
			throws BlockStoreException {
		@SuppressWarnings("unchecked")
		List<String> address = (List<String>) request.get("address");
		String lastestAmount = request.get("lastestAmount") == null ? "0" : request.get("lastestAmount").toString();
		long height = request.get("height") == null ? 0L : Long.parseLong(request.get("height").toString());
		List<BlockEvaluationDisplay> evaluations = store.getSearchBlockEvaluations(address, lastestAmount, height,
				serverConfiguration.getMaxsearchblocks());
		return GetBlockEvaluationsResponse.create(evaluations);
	}

	public void checkBlockBeforeSave(Block block, FullBlockStore store) throws BlockStoreException {

		block.verifyHeader();
		if (!checkPossibleConflict(block, store))
			throw new ConflictPossibleException("Conflict Possible");
		checkDomainname(block);
	}

	public void checkDomainname(Block block) {
		switch (block.getBlockType()) {
		case BLOCKTYPE_TOKEN_CREATION:
			TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
			if (TokenType.domainname.ordinal() == currentToken.getToken().getTokentype()) {
				if (!DomainValidator.getInstance().isValid(currentToken.getToken().getTokenname()))
					throw new VerificationException("Domain name is not valid.");
			}
			break;
		default:
			break;
		}
	}

	/*
	 * Transactions in a block may has spent output, It is not final that the reject
	 * of the block Return false, if there is possible conflict
	 */
	public boolean checkPossibleConflict(Block block, FullBlockStore store) throws BlockStoreException {
		// All used transaction outputs
		final List<Transaction> transactions = block.getTransactions();
		for (final Transaction tx : transactions) {
			if (!tx.isCoinBase()) {
				for (int index = 0; index < tx.getInputs().size(); index++) {
					TransactionInput in = tx.getInputs().get(index);

					UTXO b = store.getTransactionOutput(in.getOutpoint().getBlockHash(), in.getOutpoint().getTxHash(),
							in.getOutpoint().getIndex());
					if (b != null && b.isConfirmed() && b.isSpent()) {
						// there is a confirmed output, conflict is very
						// possible
						return false;
					}
					if (b != null && !b.isConfirmed() && !checkSpendpending(b)) {
						// there is a not confirmed output, conflict may be
						// possible
						// check the time, if the output is stale
						return false;
					}
				}
			}
		}
		return true;
	}

	/*
	 * spendpending has timeout for 5 minute return false, if there is spendpending
	 * and timeout not
	 */
	public boolean checkSpendpending(UTXO output) {
		int SPENTPENDINGTIMEOUT = 300000;
		if (output.isSpendPending()) {
			return (System.currentTimeMillis() - output.getSpendPendingTime()) > SPENTPENDINGTIMEOUT;
		}
		return true;

	}

	ExecutorService scriptVerificationExecutor = Executors.newFixedThreadPool(
			Runtime.getRuntime().availableProcessors(), new ContextPropagatingThreadFactory("Script verification"));

	/**
	 * A job submitted to the executor which verifies signatures.
	 */
	private static class Verifier implements Callable<VerificationException> {
		final Transaction tx;
		final List<Script> prevOutScripts;
		final Set<VerifyFlag> verifyFlags;

		public Verifier(final Transaction tx, final List<Script> prevOutScripts, final Set<VerifyFlag> verifyFlags) {
			this.tx = tx;
			this.prevOutScripts = prevOutScripts;
			this.verifyFlags = verifyFlags;
		}

		@Nullable
		@Override
		public VerificationException call() throws Exception {
			try {
				ListIterator<Script> prevOutIt = prevOutScripts.listIterator();
				for (int index = 0; index < tx.getInputs().size(); index++) {
					tx.getInputs().get(index).getScriptSig().correctlySpends(tx, index, prevOutIt.next(), verifyFlags);
				}
			} catch (VerificationException e) {
				return e;
			}
			return null;
		}
	}

	public static class RewardBuilderResult {
		Transaction tx;
		long difficulty;

		public RewardBuilderResult(Transaction tx, long difficulty) {
			this.tx = tx;
			this.difficulty = difficulty;
		}

		public Transaction getTx() {
			return tx;
		}

		public long getDifficulty() {
			return difficulty;
		}
	}

	/**
	 * Checks if the given set is eligible to be walked to during local approval tip
	 * selection given the currentcheckBur set of non-confirmed blocks to include.
	 * This is the case if the set is compatible with the current milestone. It must
	 * disallow spent prev UTXOs / unconfirmed prev UTXOs
	 * 
	 * @param currentApprovedUnconfirmedBlocks The set of all currently approved
	 *                                         unconfirmed blocks.
	 * @return true if the given set is eligible
	 * @throws BlockStoreException
	 */
	public boolean isEligibleForApprovalSelection(HashSet<BlockWrap> currentApprovedUnconfirmedBlocks,
			FullBlockStore store) throws BlockStoreException {
		// Currently ineligible blocks are not ineligible. If we find one, we
		// must stop
		if (!findWhereCurrentlyIneligible(currentApprovedUnconfirmedBlocks).isEmpty())
			return false;

		// If there exists a new block whose dependency is already spent
		// or not confirmed yet, we fail to approve this block since the
		// current set of confirmed blocks takes precedence
		if (findBlockWithSpentOrUnconfirmedInputs(currentApprovedUnconfirmedBlocks, store))
			return false;

		// If conflicts among the approved blocks exist, cannot approve
		HashSet<ConflictCandidate> conflictingOutPoints = new HashSet<>();
		findCandidateConflicts(currentApprovedUnconfirmedBlocks, conflictingOutPoints);
		if (!conflictingOutPoints.isEmpty())
			return false;

		// Otherwise, the new approved block set is compatible with current
		// confirmation set
		return true;
	}

	/**
	 * Checks if the given block is eligible to be walked to during local approval
	 * tip selection given the current set of unconfirmed blocks to include. This is
	 * the case if the block + the set is compatible with the current confirmeds. It
	 * must disallow spent prev UTXOs / unconfirmed prev UTXOs or unsolid blocks.
	 * 
	 * @param block                            The block to check for eligibility.
	 * @param currentApprovedUnconfirmedBlocks The set of all currently approved
	 *                                         unconfirmed blocks.
	 * @return true if the given block is eligible to be walked to during approval
	 *         tip selection.
	 * @throws BlockStoreException
	 */
	public boolean isEligibleForApprovalSelection(BlockWrap block, HashSet<BlockWrap> currentApprovedUnconfirmedBlocks,
			long cutoffHeight, long maxHeight, FullBlockStore store) throws BlockStoreException {
		// Any confirmed blocks are always compatible with the current
		// confirmeds
		if (block.getBlockEvaluation().isConfirmed())
			return true;

		// Unchecked blocks are not allowed
		if (block.getBlockEvaluation().getSolid() < 2)
			return false;

		// Above maxHeight is not allowed
		if (block.getBlockEvaluation().getHeight() > maxHeight)
			return false;

		// Get sets of all / all new unconfirmed blocks when approving the
		// specified block in combination with the currently included blocks
		@SuppressWarnings("unchecked")
		HashSet<BlockWrap> allApprovedUnconfirmedBlocks = (HashSet<BlockWrap>) currentApprovedUnconfirmedBlocks.clone();
		try {
			if (!addRequiredUnconfirmedBlocksTo(allApprovedUnconfirmedBlocks, block, cutoffHeight, store))
				throw new RuntimeException("Shouldn't happen: Block is solid but missing predecessors. ");
		} catch (VerificationException e) {
			return false;
		}

		// If this set of blocks is eligible, all is fine
		return isEligibleForApprovalSelection(allApprovedUnconfirmedBlocks, store);
	}

	public boolean hasSpentDependencies(ConflictCandidate c, FullBlockStore store) throws BlockStoreException {
		switch (c.getConflictPoint().getType()) {
		case TXOUT:
			return getUTXOSpent(c.getConflictPoint().getConnectedOutpoint(), store);
		case TOKENISSUANCE:
			final Token connectedToken = c.getConflictPoint().getConnectedToken();

			// Initial issuances are allowed iff no other same token issuances
			// are confirmed, i.e. spent iff any token confirmed
			if (connectedToken.getTokenindex() == 0)
				return store.getTokenAnyConfirmed(connectedToken.getTokenid(), connectedToken.getTokenindex());
			else
				return store.getTokenSpent(connectedToken.getPrevblockhash());
		case REWARDISSUANCE:
			return store.getRewardSpent(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
		case DOMAINISSUANCE:
			// exception for the block
			final Token connectedDomainToken = c.getConflictPoint().getConnectedDomainToken();
			return store.getDomainIssuingConfirmedBlock(connectedDomainToken.getTokenname(),
					connectedDomainToken.getDomainNameBlockHash(), connectedDomainToken.getTokenindex()) != null;
		case CONTRACTEXECUTE:
			final ContractResult connectedContracExecute = c.getConflictPoint().getConnectedContracExecute();
			if (connectedContracExecute.getPrevblockhash().equals(networkParameters.getGenesisBlock().getHash())) {
				return false;
			} else {
				return store.checkContractResultSpent(connectedContracExecute.getPrevblockhash()) != null;
			}
		default:
			throw new RuntimeException("Not Implemented");
		}
	}

	public boolean hasConfirmedDependencies(ConflictCandidate c, FullBlockStore store) throws BlockStoreException {
		switch (c.getConflictPoint().getType()) {
		case TXOUT:
			return getUTXOConfirmed(c.getConflictPoint().getConnectedOutpoint(), store);
		case TOKENISSUANCE:
			final Token connectedToken = c.getConflictPoint().getConnectedToken();

			// Initial issuances are allowed (although they may be spent
			// already)
			if (connectedToken.getTokenindex() == 0)
				return true;
			else
				return store.getTokenConfirmed(connectedToken.getPrevblockhash());
		case REWARDISSUANCE:
			return store.getRewardConfirmed(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
		case DOMAINISSUANCE:
			final Token connectedDomainToken = c.getConflictPoint().getConnectedDomainToken();
			return store.getTokenConfirmed(Sha256Hash.wrap(connectedDomainToken.getDomainNameBlockHash()));
		case CONTRACTEXECUTE:
			final ContractResult connectedContracExecute = c.getConflictPoint().getConnectedContracExecute();
			if (connectedContracExecute.getPrevblockhash().equals(networkParameters.getGenesisBlock().getHash())) {
				return true;
			} else {
				return store.checkContractResultConfirmed(connectedContracExecute.getPrevblockhash());
			}

		default:
			throw new RuntimeException("not implemented");
		}
	}

	private boolean findBlockWithSpentOrUnconfirmedInputs(HashSet<BlockWrap> blocks, FullBlockStore store) {
		// Get all conflict candidates in blocks
		Stream<ConflictCandidate> candidates = blocks.stream().map(b -> b.toConflictCandidates())
				.flatMap(i -> i.stream());

		// Find conflict candidates whose used outputs are already spent or
		// still unconfirmed
		return candidates.filter((ConflictCandidate c) -> {
			try {
				return hasSpentDependencies(c, store) || !hasConfirmedDependencies(c, store);
			} catch (BlockStoreException e) {
				e.printStackTrace();
			}
			return false;
		}).findFirst().isPresent();
	}

	/**
	 * Resolves all conflicts such that the confirmed set is compatible with all
	 * blocks remaining in the set of blocks.
	 * 
	 * @param blocksToAdd  the set of blocks to add to the current milestone
	 * @param cutoffHeight
	 * @throws BlockStoreException
	 */
	public void resolveAllConflicts(TreeSet<BlockWrap> blocksToAdd, long cutoffHeight, FullBlockStore store)
			throws BlockStoreException {
		// Cutoff: Remove if predecessors neither in milestone nor to be
		// confirmed
		removeWhereUnconfirmedRequirements(blocksToAdd, store);

		// Remove ineligible blocks, i.e. only reward blocks
		// since they follow a different logic
		removeWhereIneligible(blocksToAdd, store);

		// Remove blocks and their approvers that have at least one input
		// with its corresponding output not confirmed yet
		removeWhereUsedOutputsUnconfirmed(blocksToAdd, store);

		// Resolve conflicting block combinations:
		// Disallow conflicts with milestone blocks,
		// i.e. remove those whose input is already spent by such blocks
		resolveMilestoneConflicts(blocksToAdd, store);

		// Then resolve conflicts between non-milestone + new candidates
		resolveTemporaryConflicts(blocksToAdd, cutoffHeight, store);

		// Remove blocks and their approvers that have at least one input
		// with its corresponding output no longer confirmed
		removeWhereUsedOutputsUnconfirmed(blocksToAdd, store);
	}

	/**
	 * Remove blocks from blocksToAdd that miss their required predecessors, i.e.
	 * the predecessors are not confirmed or in blocksToAdd.
	 * 
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	private void removeWhereUnconfirmedRequirements(TreeSet<BlockWrap> blocksToAdd, FullBlockStore store)
			throws BlockStoreException {
		Iterator<BlockWrap> iterator = blocksToAdd.iterator();
		while (iterator.hasNext()) {
			BlockWrap b = iterator.next();
			List<BlockWrap> allRequirements = getAllRequirements(b.getBlock(), store);
			for (BlockWrap req : allRequirements) {
				if (!req.getBlockEvaluation().isConfirmed() && !blocksToAdd.contains(req)) {
					iterator.remove();
					break;
				}
			}
		}
	}

	/**
	 * Remove blocks from blocksToAdd that are currently locally ineligible.
	 * 
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	public void removeWhereIneligible(Set<BlockWrap> blocksToAdd, FullBlockStore store) {
		findWhereCurrentlyIneligible(blocksToAdd).forEach(b -> {
			try {
				removeBlockAndApproversFrom(blocksToAdd, b, store);
			} catch (BlockStoreException e) {
				// Cannot happen.
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		});
	}

	/**
	 * Find blocks from blocksToAdd that are currently locally ineligible.
	 * 
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	private Set<BlockWrap> findWhereCurrentlyIneligible(Set<BlockWrap> blocksToAdd) {
		return blocksToAdd.stream().filter(b -> b.getBlock().getBlockType() == Type.BLOCKTYPE_REWARD)
				.collect(Collectors.toSet());
	}

	/**
	 * Remove blocks from blocksToAdd that have at least one used output not
	 * confirmed yet. They may however be spent already, since this leads to
	 * conflicts.
	 * 
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	public void removeWhereUsedOutputsUnconfirmed(Set<BlockWrap> blocksToAdd, FullBlockStore store)
			throws BlockStoreException {
		// Confirmed blocks are always ok
		new HashSet<BlockWrap>(blocksToAdd).stream().filter(b -> !b.getBlockEvaluation().isConfirmed())
				.flatMap(b -> b.toConflictCandidates().stream()).filter(c -> {
					try {
						return !hasConfirmedDependencies(c, store); // Any
																	// candidates
						// where used
						// dependencies unconfirmed
					} catch (BlockStoreException e) {
						// Cannot happen.
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}).forEach(c -> {
					try {
						removeBlockAndApproversFrom(blocksToAdd, c.getBlock(), store);
					} catch (BlockStoreException e) {
						// Cannot happen.
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				});
	}

	private void resolveMilestoneConflicts(Set<BlockWrap> blocksToAdd, FullBlockStore store)
			throws BlockStoreException {
		// Find all conflict candidates in blocks to add
		List<ConflictCandidate> conflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
				.flatMap(i -> i.stream()).collect(Collectors.toList());

		// Find only those that are spent
		filterSpent(conflicts, store);

		// Drop any spent by milestone
		for (ConflictCandidate c : conflicts) {
			// Find the spending block we are competing with
			BlockWrap milestoneBlock = getSpendingBlock(c, store);

			// If it is pruned or a milestone, we drop the blocks
			if (milestoneBlock == null || milestoneBlock.getBlockEvaluation().getMilestone() != -1) {
				removeBlockAndApproversFrom(blocksToAdd, c.getBlock(), store);
			}
		}
	}

	/**
	 * Resolves conflicts between non-milestone blocks and candidates
	 * 
	 * @param blocksToAdd
	 * @param cutoffHeight
	 * @throws BlockStoreException
	 */
	private void resolveTemporaryConflicts(Set<BlockWrap> blocksToAdd, long cutoffHeight, FullBlockStore store)
			throws BlockStoreException {
		HashSet<ConflictCandidate> conflictingOutPoints = new HashSet<ConflictCandidate>();
		HashSet<BlockWrap> conflictingConfirmedBlocks = new HashSet<BlockWrap>();

		// Find all conflicts in the new blocks + confirmed blocks
		findFixableConflicts(blocksToAdd, conflictingOutPoints, conflictingConfirmedBlocks, store);

		// Resolve all conflicts by grouping by UTXO ordered by descending
		// rating
		HashSet<BlockWrap> losingBlocks = resolveTemporaryConflicts(conflictingOutPoints, blocksToAdd, cutoffHeight,
				store);

		// For confirmed blocks that have been eliminated call disconnect
		// procedure
		HashSet<Sha256Hash> traversedUnconfirms = new HashSet<>();
		for (BlockWrap b : conflictingConfirmedBlocks.stream().filter(b -> losingBlocks.contains(b))
				.collect(Collectors.toList())) {
			unconfirmRecursive(b.getBlockEvaluation().getBlockHash(), traversedUnconfirms, store);
		}

		// For candidates that have been eliminated (conflictingOutPoints in
		// blocksToAdd \ winningBlocks) remove them from blocksToAdd
		for (BlockWrap b : losingBlocks) {
			removeBlockAndApproversFrom(blocksToAdd, b, store);
		}
	}

	/**
	 * Resolve all conflicts by grouping by UTXO ordered by descending rating.
	 * 
	 * @param conflictingOutPoints
	 * @return losingBlocks: blocks that have been removed due to conflict
	 *         resolution
	 * @throws BlockStoreException
	 */
	private HashSet<BlockWrap> resolveTemporaryConflicts(Set<ConflictCandidate> conflictingOutPoints,
			Set<BlockWrap> blocksToAdd, long cutoffHeight, FullBlockStore store) throws BlockStoreException {
		// Initialize blocks that will/will not survive the conflict resolution
		HashSet<BlockWrap> initialBlocks = conflictingOutPoints.stream().map(c -> c.getBlock())
				.collect(Collectors.toCollection(HashSet::new));
		HashSet<BlockWrap> winningBlocks = new HashSet<>(blocksToAdd);
		for (BlockWrap winningBlock : initialBlocks) {
			if (!addRequiredUnconfirmedBlocksTo(winningBlocks, winningBlock, cutoffHeight, store))
				throw new RuntimeException("Shouldn't happen: Block is solid but missing predecessors. ");
			addConfirmedApproversTo(winningBlocks, winningBlock, store);
		}
		HashSet<BlockWrap> losingBlocks = new HashSet<>(winningBlocks);

		// Sort conflicts internally by descending rating, then cumulative
		// weight.
		Comparator<ConflictCandidate> byDescendingRating = getConflictComparator()
				.thenComparingLong((ConflictCandidate e) -> e.getBlock().getMcmc().getRating())
				.thenComparingLong((ConflictCandidate e) -> e.getBlock().getMcmc().getCumulativeWeight())
				.thenComparingLong((ConflictCandidate e) -> -e.getBlock().getBlockEvaluation().getInsertTime())
				.thenComparing((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getBlockHash()).reversed();

		Supplier<TreeSet<ConflictCandidate>> conflictTreeSetSupplier = () -> new TreeSet<ConflictCandidate>(
				byDescendingRating);

		Map<Object, TreeSet<ConflictCandidate>> conflicts = conflictingOutPoints.stream().collect(
				Collectors.groupingBy(i -> i.getConflictPoint(), Collectors.toCollection(conflictTreeSetSupplier)));

		// Sort conflicts among each other by descending max(rating).
		Comparator<TreeSet<ConflictCandidate>> byDescendingSetRating = getConflictSetComparator()
				.thenComparingLong((TreeSet<ConflictCandidate> s) -> s.first().getBlock().getMcmc().getRating())
				.thenComparingLong(
						(TreeSet<ConflictCandidate> s) -> s.first().getBlock().getMcmc().getCumulativeWeight())
				.thenComparingLong(
						(TreeSet<ConflictCandidate> s) -> -s.first().getBlock().getBlockEvaluation().getInsertTime())
				.thenComparing(
						(TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation().getBlockHash())
				.reversed();

		Supplier<TreeSet<TreeSet<ConflictCandidate>>> conflictsTreeSetSupplier = () -> new TreeSet<TreeSet<ConflictCandidate>>(
				byDescendingSetRating);

		TreeSet<TreeSet<ConflictCandidate>> sortedConflicts = conflicts.values().stream()
				.collect(Collectors.toCollection(conflictsTreeSetSupplier));

		// Now handle conflicts by descending max(rating)
		for (TreeSet<ConflictCandidate> conflict : sortedConflicts) {
			// Take the block with the maximum rating in this conflict that is
			// still in winning Blocks
			ConflictCandidate maxRatingPair = null;
			for (ConflictCandidate c : conflict) {
				if (winningBlocks.contains(c.getBlock())) {
					maxRatingPair = c;
					break;
				}
			}

			// If such a block exists, this conflict is resolved by eliminating
			// all other blocks in this conflict from winning blocks
			if (maxRatingPair != null) {
				for (ConflictCandidate c : conflict) {
					if (c != maxRatingPair) {
						removeBlockAndApproversFrom(winningBlocks, c.getBlock(), store);
					}
				}
			}
		}

		losingBlocks.removeAll(winningBlocks);

		return losingBlocks;
	}

	private Comparator<TreeSet<ConflictCandidate>> getConflictSetComparator() {
		return new Comparator<TreeSet<ConflictCandidate>>() {
			@Override
			public int compare(TreeSet<ConflictCandidate> o1, TreeSet<ConflictCandidate> o2) {
				return 0;
			}
		};
	}

	private Comparator<ConflictCandidate> getConflictComparator() {
		return new Comparator<ConflictCandidate>() {
			@Override
			public int compare(ConflictCandidate o1, ConflictCandidate o2) {
				return 0;
			}
		};
	}

	/**
	 * Finds conflicts in blocksToAdd itself and with the confirmed blocks.
	 * 
	 * @param blocksToAdd
	 * @param conflictingOutPoints
	 * @throws BlockStoreException
	 */
	private void findFixableConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints,
			Set<BlockWrap> conflictingConfirmedBlocks, FullBlockStore store) throws BlockStoreException {

		findUndoableConflicts(blocksToAdd, conflictingOutPoints, conflictingConfirmedBlocks, store);
		findCandidateConflicts(blocksToAdd, conflictingOutPoints);
	}

	/**
	 * Finds conflicts among blocks to add themselves
	 * 
	 * @param blocksToAdd
	 * @param conflictingOutPoints
	 * @throws BlockStoreException
	 */
	private void findCandidateConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints)
			throws BlockStoreException {
		// Get conflicts that are spent more than once in the
		// candidates
		List<ConflictCandidate> candidateCandidateConflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
				.flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream()
				.filter(l -> l.size() > 1).flatMap(l -> l.stream()).collect(Collectors.toList());

		// Add the conflicting candidates
		for (ConflictCandidate c : candidateCandidateConflicts) {
			conflictingOutPoints.add(c);
		}
	}

	/**
	 * Finds conflicts between current confirmed and blocksToAdd
	 * 
	 * @param blocksToAdd
	 * @param conflictingOutPoints
	 * @throws BlockStoreException
	 */
	private void findUndoableConflicts(Set<BlockWrap> blocksToAdd, Set<ConflictCandidate> conflictingOutPoints,
			Set<BlockWrap> conflictingConfirmedBlocks, FullBlockStore store) throws BlockStoreException {
		// Find all conflict candidates in blocks to add
		List<ConflictCandidate> conflicts = blocksToAdd.stream().map(b -> b.toConflictCandidates())
				.flatMap(i -> i.stream()).collect(Collectors.toList());

		// Find only those that are spent in confirmed
		filterSpent(conflicts, store);

		// Add the conflicting candidates and confirmed blocks to given set
		for (ConflictCandidate c : conflicts) {
			// Find the spending block we are competing with
			BlockWrap confirmedBlock = getSpendingBlock(c, store);

			// Only go through if the block is undoable, i.e. not milestone
			if (confirmedBlock == null || confirmedBlock.getBlockEvaluation().getMilestone() != -1)
				continue;

			// Add confirmed block
			conflictingOutPoints.add(ConflictCandidate.fromConflictPoint(confirmedBlock, c.getConflictPoint()));
			conflictingConfirmedBlocks.add(confirmedBlock);

			// Then add corresponding new block
			conflictingOutPoints.add(c);
		}
	}

	// Returns null if no spending block found
	private BlockWrap getSpendingBlock(ConflictCandidate c, FullBlockStore store) throws BlockStoreException {
		switch (c.getConflictPoint().getType()) {
		case TXOUT:
			final BlockEvaluation utxoSpender = getUTXOSpender(c.getConflictPoint().getConnectedOutpoint(), store);
			if (utxoSpender == null)
				return null;
			return store.getBlockWrap(utxoSpender.getBlockHash());
		case TOKENISSUANCE:
			final Token connectedToken = c.getConflictPoint().getConnectedToken();

			// The spender is always the one block with the same tokenid and
			// index that is confirmed
			return store.getTokenIssuingConfirmedBlock(connectedToken.getTokenid(), connectedToken.getTokenindex());
		case REWARDISSUANCE:
			final Sha256Hash txRewardSpender = store
					.getRewardSpender(c.getConflictPoint().getConnectedReward().getPrevRewardHash());
			if (txRewardSpender == null)
				return null;
			return store.getBlockWrap(txRewardSpender);
		case DOMAINISSUANCE:
			final Token connectedDomainToken = c.getConflictPoint().getConnectedDomainToken();

			// The spender is always the one block with the same domainname and
			// predecessing domain tokenid that is confirmed
			return store.getDomainIssuingConfirmedBlock(connectedDomainToken.getTokenname(),
					connectedDomainToken.getDomainNameBlockHash(), connectedDomainToken.getTokenindex());
		case CONTRACTEXECUTE:
			final ContractResult connectedContracExecute = c.getConflictPoint().getConnectedContracExecute();
			Sha256Hash t = connectedContracExecute.getSpenderBlockHash();
			if (t == null)
				return null;
			return store.getBlockWrap(t);
		default:
			throw new RuntimeException("No Implementation");
		}
	}

	private void filterSpent(Collection<ConflictCandidate> blockConflicts, FullBlockStore store) {
		blockConflicts.removeIf(c -> {
			try {
				return !hasSpentDependencies(c, store);
			} catch (BlockStoreException e) {
				e.printStackTrace();
				return true;
			}
		});
	}

	private SolidityState checkPredecessorsExistAndOk(Block block, boolean throwExceptions,
			 List<BlockWrap> allRequirements, FullBlockStore store) throws BlockStoreException {
		//
		for (BlockWrap pred : allRequirements) {
		//	final BlockWrap pred = store.getBlockWrap(predecessorReq);
			if (pred == null)
				return SolidityState.from(Sha256Hash.ZERO_HASH, true);
			if (pred.getBlock().getBlockType().requiresCalculation() && pred.getBlockEvaluation().getSolid() != 2)
				return SolidityState.fromMissingCalculation(pred.getBlockHash());
			if (pred.getBlock().getHeight() >= block.getHeight()) {
				if (throwExceptions)
					throw new VerificationException("Height of used blocks must be lower than height of this block.");
				return SolidityState.getFailState();
			}
		}
		return SolidityState.getSuccessState();
	}

	public Set<Sha256Hash> getMissingPredecessors(Block block, FullBlockStore store) throws BlockStoreException {
		Set<Sha256Hash> missingPredecessorBlockHashes = new HashSet<>();
		final Set<Sha256Hash> allPredecessorBlockHashes = getAllRequiredBlockHashes(block, false);
		for (Sha256Hash predecessorReq : allPredecessorBlockHashes) {
			BlockWrap pred = store.getBlockWrap(predecessorReq);
			if (pred == null)
				missingPredecessorBlockHashes.add(predecessorReq);

		}
		return missingPredecessorBlockHashes;
	}

	public SolidityState getMinPredecessorSolidity(Block block, boolean throwExceptions, FullBlockStore store)
			throws BlockStoreException {
		return getMinPredecessorSolidity(block, throwExceptions, getAllRequirements(block, store), store, true);
	}

	public SolidityState getMinPredecessorSolidity(Block block, boolean throwExceptions,
			List<BlockWrap> allPredecessors, FullBlockStore store, boolean predecessorsSolid)
			throws BlockStoreException {
		// final List<BlockWrap> allPredecessors = getAllRequirements(block, store);
		SolidityState missingCalculation = null;
		SolidityState missingDependency = null;
		for (BlockWrap predecessor : allPredecessors) {
			if (predecessor.getBlockEvaluation().getSolid() == 2) {
				continue;
			} else if (predecessor.getBlockEvaluation().getSolid() == 1 && predecessorsSolid) {
				missingCalculation = SolidityState.fromMissingCalculation(predecessor.getBlockHash());
			} else if (predecessor.getBlockEvaluation().getSolid() == 0 && predecessorsSolid) {
				missingDependency = SolidityState.from(predecessor.getBlockHash(), false);
			} else if (predecessor.getBlockEvaluation().getSolid() == -1 && predecessorsSolid) {
				if (throwExceptions)
					throw new VerificationException("The used blocks are invalid. getSolid() == -1");
				return SolidityState.getFailState();
			} else {
				logger.warn("predecessor.getBlockEvaluation().getSolid() =  "
						+ predecessor.getBlockEvaluation().getSolid() + " " + block.toString());
				continue;
				// throw new RuntimeException("not implemented");
			}
		}

		if (missingDependency == null) {
			if (missingCalculation == null) {
				return SolidityState.getSuccessState();
			} else {
				return missingCalculation;
			}
		} else {
			return missingDependency;
		}
	}

	/*
	 * Checks if the block is formally correct without relying on predecessors
	 */
	public SolidityState checkFormalBlockSolidity(Block block, boolean throwExceptions) {
		try {
			if (block.getHash() == Sha256Hash.ZERO_HASH) {
				if (throwExceptions)
					throw new VerificationException("Lucky zeros not allowed");
				return SolidityState.getFailState();
			}

			if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
				if (throwExceptions)
					throw new GenesisBlockDisallowedException();
				return SolidityState.getFailState();
			}

			// Disallow someone burning other people's orders
			if (block.getBlockType() != Type.BLOCKTYPE_ORDER_OPEN) {
				for (Transaction tx : block.getTransactions())
					if (tx.getDataClassName() != null && tx.getDataClassName().equals("OrderOpen")) {
						if (throwExceptions)
							throw new MalformedTransactionDataException();
						return SolidityState.getFailState();
					}
			}

			// Check transaction solidity
			SolidityState transactionalSolidityState = checkFormalTransactionalSolidity(block, throwExceptions);
			if (!(transactionalSolidityState.getState() == State.Success)) {
				return transactionalSolidityState;
			}

			// Check type-specific solidity
			SolidityState typeSpecificSolidityState = checkFormalTypeSpecificSolidity(block, throwExceptions);
			if (!(typeSpecificSolidityState.getState() == State.Success)) {
				return typeSpecificSolidityState;
			}

			return SolidityState.getSuccessState();
		} catch (VerificationException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Unhandled exception in checkSolidity: ", e);
			if (throwExceptions)
				throw new VerificationException(e);
			return SolidityState.getFailState();
		}
	}

	private SolidityState checkFormalTransactionalSolidity(Block block, boolean throwExceptions)
			throws BlockStoreException {
		try {

			long sigOps = 0;

			for (Transaction tx : block.getTransactions()) {
				sigOps += tx.getSigOpCount();
			}

			for (final Transaction tx : block.getTransactions()) {
				Map<String, Coin> valueOut = new HashMap<String, Coin>();
				for (TransactionOutput out : tx.getOutputs()) {
					if (valueOut.containsKey(Utils.HEX.encode(out.getValue().getTokenid()))) {
						valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()),
								valueOut.get(Utils.HEX.encode(out.getValue().getTokenid())).add(out.getValue()));
					} else {
						valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()), out.getValue());
					}
				}
				if (!checkTxOutputSigns(valueOut)) {
					throw new InvalidTransactionException("Transaction output value negative");
				}

				final Set<VerifyFlag> verifyFlags = networkParameters.getTransactionVerificationFlags(block, tx);
				if (verifyFlags.contains(VerifyFlag.P2SH)) {
					if (sigOps > NetworkParameters.MAX_BLOCK_SIGOPS)
						throw new SigOpsException();
				}
			}

		} catch (VerificationException e) {
			scriptVerificationExecutor.shutdownNow();
			logger.info("", e);
			if (throwExceptions)
				throw e;
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	private SolidityState checkFormalTypeSpecificSolidity(Block block, boolean throwExceptions)
			throws BlockStoreException {
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
			// Check rewards are solid
			SolidityState rewardSolidityState = checkFormalRewardSolidity(block, throwExceptions);
			if (!(rewardSolidityState.getState() == State.Success)) {
				return rewardSolidityState;
			}

			break;
		case BLOCKTYPE_TOKEN_CREATION:
			// Check token issuances are solid
			SolidityState tokenSolidityState = checkFormalTokenSolidity(block, throwExceptions);
			if (!(tokenSolidityState.getState() == State.Success)) {
				return tokenSolidityState;
			}

			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_OPEN:
			SolidityState openSolidityState = checkFormalOrderOpenSolidity(block, throwExceptions);
			if (!(openSolidityState.getState() == State.Success)) {
				return openSolidityState;
			}
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			SolidityState opSolidityState = checkFormalOrderOpSolidity(block, throwExceptions);
			if (!(opSolidityState.getState() == State.Success)) {
				return opSolidityState;
			}
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			SolidityState check = checkFormalContractEventSolidity(block, throwExceptions);
			if (!(check.getState() == State.Success)) {
				return check;
			}
			break;
		default:
			throw new RuntimeException("No Implementation");
		}

		return SolidityState.getSuccessState();
	}

	private SolidityState checkFormalOrderOpenSolidity(Block block, boolean throwExceptions)
			throws BlockStoreException {
		List<Transaction> transactions = block.getTransactions();

		if (transactions.get(0).getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		// Check that the tx has correct data
		OrderOpenInfo orderInfo;
		try {
			orderInfo = new OrderOpenInfo().parse(transactions.get(0).getData());
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		if (!transactions.get(0).getDataClassName().equals("OrderOpen")) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		// NotNull checks
		if (orderInfo.getTargetTokenid() == null) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Invalid target tokenid");
			return SolidityState.getFailState();
		}

		// Check bounds for target coin values
		if (orderInfo.getTargetValue() < 1 || orderInfo.getTargetValue() > Long.MAX_VALUE) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Invalid target long value: " + orderInfo.getTargetValue());
			return SolidityState.getFailState();
		}

		if (orderInfo.getValidToTime() > Math.addExact(orderInfo.getValidFromTime(),
				NetworkParameters.ORDER_TIMEOUT_MAX)) {
			if (throwExceptions)
				throw new InvalidOrderException("The given order's timeout is too long.");
			return SolidityState.getFailState();
		}

		if (!ECKey.fromPublicOnly(orderInfo.getBeneficiaryPubKey()).toAddress(networkParameters).toBase58()
				.equals(orderInfo.getBeneficiaryAddress())) {
			if (throwExceptions)
				throw new InvalidOrderException("The address does not match with the given pubkey.");
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	private SolidityState checkFormalContractEventSolidity(Block block, boolean throwExceptions)
			throws BlockStoreException {
		List<Transaction> transactions = block.getTransactions();

		if (transactions.get(0).getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		// Check that the tx has correct data
		ContractEventInfo contractEventInfo;
		try {
			contractEventInfo = new ContractEventInfo().parse(transactions.get(0).getData());
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		if (!transactions.get(0).getDataClassName().equals("ContractEventInfo")) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		// NotNull checks
		if (contractEventInfo.getContractTokenid() == null) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Invalid contract tokenid");
			return SolidityState.getFailState();
		}

		if (contractEventInfo.getValidToTime() > Math.addExact(contractEventInfo.getValidFromTime(),
				NetworkParameters.ORDER_TIMEOUT_MAX)) {
			if (throwExceptions)
				throw new InvalidOrderException("The given order's timeout is too long.");
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	private SolidityState checkFormalOrderOpSolidity(Block block, boolean throwExceptions) throws BlockStoreException {

		// No output creation
		if (!block.getTransactions().get(0).getOutputs().isEmpty()) {
			if (throwExceptions)
				throw new TransactionOutputsDisallowedException();
			return SolidityState.getFailState();
		}

		Transaction tx = block.getTransactions().get(0);
		if (tx.getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		OrderCancelInfo info = null;
		try {
			info = new OrderCancelInfo().parse(tx.getData());
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		// NotNull checks
		if (info.getBlockHash() == null) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Invalid target txhash");
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	private SolidityState checkFormalRewardSolidity(Block block, boolean throwExceptions) throws BlockStoreException {
		List<Transaction> transactions = block.getTransactions();

		if (transactions.size() != 1) {
			if (throwExceptions)
				throw new IncorrectTransactionCountException();
			return SolidityState.getFailState();
		}

		// No output creation
		if (!transactions.get(0).getOutputs().isEmpty()) {
			if (throwExceptions)
				throw new TransactionOutputsDisallowedException();
			return SolidityState.getFailState();
		}

		if (transactions.get(0).getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		// Check that the tx has correct data
		RewardInfo rewardInfo = new RewardInfo().parseChecked(transactions.get(0).getData());
		// NotNull checks
		if (rewardInfo.getPrevRewardHash() == null) {
			if (throwExceptions)
				throw new MissingDependencyException();
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	public SolidityState checkFormalTokenSolidity(Block block, boolean throwExceptions) throws BlockStoreException {

		if (!block.getTransactions().get(0).isCoinBase()) {
			if (throwExceptions)
				throw new NotCoinbaseException();
			return SolidityState.getFailState();
		}

		Transaction tx = block.getTransactions().get(0);
		if (tx.getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		TokenInfo currentToken = null;
		try {
			currentToken = new TokenInfo().parse(tx.getData());
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		if (checkFormalTokenFields(throwExceptions, currentToken) == SolidityState.getFailState())
			return SolidityState.getFailState();

		// Check field correctness: amount
		if (!currentToken.getToken().getAmount().equals(block.getTransactions().get(0).getOutputSum())) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Incorrect amount field");
			return SolidityState.getFailState();
		}

		// Check all token issuance transaction outputs are actually of the
		// given token
		for (Transaction tx1 : block.getTransactions()) {
			for (TransactionOutput out : tx1.getOutputs()) {
				if (!out.getValue().getTokenHex().equals(currentToken.getToken().getTokenid())
						&& !out.getValue().isBIG()) {
					if (throwExceptions)
						throw new InvalidTokenOutputException();
					return SolidityState.getFailState();
				}
			}
		}

		// Must define enough permissioned addresses
		if (currentToken.getToken().getSignnumber() > currentToken.getMultiSignAddresses().size()) {
			if (throwExceptions)
				throw new InvalidTransactionDataException(
						"Cannot fulfill required sign number from multisign address list");
			return SolidityState.getFailState();
		}

		// Ensure signatures exist
		if (tx.getDataSignature() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		// Get signatures from transaction
		String jsonStr = new String(tx.getDataSignature());
		try {
			Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	public void checkTokenUnique(Block block, FullBlockStore store)
			throws BlockStoreException, JsonParseException, JsonMappingException, IOException {
		/*
		 * Token is unique with token name and domain
		 */
		TokenInfo currentToken = new TokenInfo().parse(block.getTransactions().get(0).getData());
		if (store.getTokennameAndDomain(currentToken.getToken().getTokenname(),
				currentToken.getToken().getDomainNameBlockHash()) && currentToken.getToken().getTokenindex() == 0) {
			throw new VerificationException(" Token name and domain exists.");
		}
	}

	/*
	 * Checks if the block is valid based on itself and its dependencies. Rechecks
	 * formal criteria too. If SolidityState.getSuccessState() is returned, the
	 * block is valid. If SolidityState.getFailState() is returned, the block is
	 * invalid. Otherwise, appropriate solidity states are returned to imply missing
	 * dependencies.
	 */
	private SolidityState checkFullBlockSolidity(Block block, boolean throwExceptions, List<BlockWrap> allPredecessors,
			FullBlockStore store) {
		try {
			BlockWrap storedPrev = store.getBlockWrap(block.getPrevBlockHash());
			BlockWrap storedPrevBranch = store.getBlockWrap(block.getPrevBranchBlockHash());

			if (block.getHash() == Sha256Hash.ZERO_HASH) {
				if (throwExceptions)
					throw new VerificationException("Lucky zeros not allowed");
				return SolidityState.getFailState();
			}
			// Check predecessor blocks exist
			if (storedPrev == null) {
				return SolidityState.from(block.getPrevBlockHash(), true);
			}
			if (storedPrevBranch == null) {
				return SolidityState.from(block.getPrevBranchBlockHash(), true);
			}
			if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
				if (throwExceptions)
					throw new GenesisBlockDisallowedException();
				return SolidityState.getFailState();
			}

			// Check height, all required max +1
			if (block.getHeight() != calcHeightRequiredBlocks(block, allPredecessors, store)) {
				if (throwExceptions)
					throw new VerificationException("Wrong height");
				return SolidityState.getFailState();
			}

			// Disallow someone burning other people's orders
			if (block.getBlockType() != Type.BLOCKTYPE_ORDER_OPEN) {
				for (Transaction tx : block.getTransactions())
					if (tx.getDataClassName() != null && tx.getDataClassName().equals("OrderOpen")) {
						if (throwExceptions)
							throw new MalformedTransactionDataException();
						return SolidityState.getFailState();
					}
			}

			// Check timestamp: enforce monotone time increase
			if (block.getTimeSeconds() < storedPrev.getBlock().getTimeSeconds()
					|| block.getTimeSeconds() < storedPrevBranch.getBlock().getTimeSeconds()) {
				if (throwExceptions)
					throw new TimeReversionException();
				return SolidityState.getFailState();
			}

			// Check difficulty and latest consensus block is passed through
			// correctly
			if (block.getBlockType() != Block.Type.BLOCKTYPE_REWARD) {
				if (storedPrev.getBlock().getLastMiningRewardBlock() >= storedPrevBranch.getBlock()
						.getLastMiningRewardBlock()) {
					if (block.getLastMiningRewardBlock() != storedPrev.getBlock().getLastMiningRewardBlock()
							|| block.getDifficultyTarget() != storedPrev.getBlock().getDifficultyTarget()) {
						if (throwExceptions)
							throw new DifficultyConsensusInheritanceException();
						return SolidityState.getFailState();
					}
				} else {
					if (block.getLastMiningRewardBlock() != storedPrevBranch.getBlock().getLastMiningRewardBlock()
							|| block.getDifficultyTarget() != storedPrevBranch.getBlock().getDifficultyTarget()) {
						if (throwExceptions)
							throw new DifficultyConsensusInheritanceException();
						return SolidityState.getFailState();
					}
				}
			}

			// Check transactions are solid
			SolidityState transactionalSolidityState = checkFullTransactionalSolidity(block, block.getHeight(),
					throwExceptions, store);
			if (!(transactionalSolidityState.getState() == State.Success)) {
				return transactionalSolidityState;
			}

			// Check type-specific solidity
			SolidityState typeSpecificSolidityState = checkFullTypeSpecificSolidity(block, storedPrev, storedPrevBranch,
					block.getHeight(), throwExceptions, store);
			if (!(typeSpecificSolidityState.getState() == State.Success)) {
				return typeSpecificSolidityState;
			}

			return SolidityState.getSuccessState();
		} catch (VerificationException e) {
			throw e;
		} catch (Exception e) {
			logger.error("Unhandled exception in checkSolidity: ", e);
			if (throwExceptions)
				throw new VerificationException(e);
			return SolidityState.getFailState();
		}
	}

	private boolean checkUnique(List<TransactionOutPoint> allInputTx, TransactionOutPoint t) {
		for (TransactionOutPoint out : allInputTx) {
			if (t.getTxHash().equals(out.getTxHash()) && t.getBlockHash().equals(out.getBlockHash())
					&& t.getIndex() == out.getIndex()) {
				return true;
			}
		}
		return false;

	}

	private void checCoinbaseTransactionalSolidity(Block block, FullBlockStore store) throws BlockStoreException {
		// only reward block and contract can be set coinbase and check by caculation
		for (final Transaction tx : block.getTransactions()) {
			if (tx.isCoinBase() && (block.getBlockType() == Type.BLOCKTYPE_REWARD
					|| block.getBlockType() == Type.BLOCKTYPE_CONTRACT_EXECUTE)) {
				throw new InvalidTransactionException("coinbase is not allowed ");
			}
		}

	}

	private SolidityState checkFullTransactionalSolidity(Block block, long height, boolean throwExceptions,
			FullBlockStore store) throws BlockStoreException {

		checCoinbaseTransactionalSolidity(block, store);

		List<Transaction> transactions = block.getTransactions();

		// All used transaction outputs as input must exist and unique
		// check CoinBase only for reward block and contract verify
		List<TransactionOutPoint> allInputTx = new ArrayList<>();
		for (final Transaction tx : transactions) {
			if (!tx.isCoinBase()) {
				for (int index = 0; index < tx.getInputs().size(); index++) {
					TransactionInput in = tx.getInputs().get(index);
					UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getBlockHash(),
							in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
					if (prevOut == null) {
						// Missing previous transaction output
						return SolidityState.from(in.getOutpoint(), true);
					}
					if (checkUnique(allInputTx, in.getOutpoint())) {
						throw new InvalidTransactionException(
								"input outputpoint is not unique " + in.getOutpoint().toString());
					}
					allInputTx.add(in.getOutpoint());
				}
				if (checkBurnedFromAddress(tx, block.getLastMiningRewardBlock())) {
					throw new InvalidTransactionException("Burned Address");
				}
			}

		}

		// Transaction validation
		try {
			LinkedList<UTXO> txOutsSpent = new LinkedList<UTXO>();
			long sigOps = 0;

			if (scriptVerificationExecutor.isShutdown())
				scriptVerificationExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

			List<Future<VerificationException>> listScriptVerificationResults = new ArrayList<Future<VerificationException>>(
					block.getTransactions().size());

			for (Transaction tx : block.getTransactions()) {
				sigOps += tx.getSigOpCount();
			}
			// pro block check fee
			Boolean checkFee = false;
			if (block.getBlockType().equals(Block.Type.BLOCKTYPE_REWARD)
					|| block.getBlockType().equals(Block.Type.BLOCKTYPE_CONTRACT_EXECUTE)) {
				checkFee = true;
			}
			for (final Transaction tx : block.getTransactions()) {
				boolean isCoinBase = tx.isCoinBase();
				Map<String, Coin> valueIn = new HashMap<String, Coin>();
				Map<String, Coin> valueOut = new HashMap<String, Coin>();

				final List<Script> prevOutScripts = new LinkedList<Script>();
				final Set<VerifyFlag> verifyFlags = networkParameters.getTransactionVerificationFlags(block, tx);
				if (!isCoinBase) {
					for (int index = 0; index < tx.getInputs().size(); index++) {
						TransactionInput in = tx.getInputs().get(index);
						UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getBlockHash(),
								in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
						if (prevOut == null) {
							// Cannot happen due to solidity checks before
							throw new RuntimeException("Block attempts to spend a not yet existent output!");
						}

						if (valueIn.containsKey(Utils.HEX.encode(prevOut.getValue().getTokenid()))) {
							valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()), valueIn
									.get(Utils.HEX.encode(prevOut.getValue().getTokenid())).add(prevOut.getValue()));
						} else {
							valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()), prevOut.getValue());

						}
						if (verifyFlags.contains(VerifyFlag.P2SH)) {
							if (prevOut.getScript().isPayToScriptHash())
								sigOps += Script.getP2SHSigOpCount(in.getScriptBytes());
							if (sigOps > NetworkParameters.MAX_BLOCK_SIGOPS)
								throw new SigOpsException();
						}
						prevOutScripts.add(prevOut.getScript());
						txOutsSpent.add(prevOut);
					}
				}
				// Sha256Hash hash = tx.getHash();
				for (TransactionOutput out : tx.getOutputs()) {
					if (valueOut.containsKey(Utils.HEX.encode(out.getValue().getTokenid()))) {
						valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()),
								valueOut.get(Utils.HEX.encode(out.getValue().getTokenid())).add(out.getValue()));
					} else {
						valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()), out.getValue());
					}
				}
				if (!checkTxOutputSigns(valueOut))
					throw new InvalidTransactionException("Transaction output value negative");
				if (isCoinBase) {
					// coinbaseValue = valueOut;
				} else {
					if (checkTxInputOutput(valueIn, valueOut, block)) {
						checkFee = true;
					}
				}

				if (!isCoinBase) {
					// Because correctlySpends modifies transactions, this must
					// come after we are done with tx
					FutureTask<VerificationException> future = new FutureTask<VerificationException>(
							new Verifier(tx, prevOutScripts, verifyFlags));
					scriptVerificationExecutor.execute(future);
					listScriptVerificationResults.add(future);
				}
			}
			if (!checkFee && enableFee(block))
				throw new VerificationException.NoFeeException(Coin.FEE_DEFAULT.toString());

			for (Future<VerificationException> future : listScriptVerificationResults) {
				VerificationException e;
				try {
					e = future.get();
				} catch (InterruptedException thrownE) {
					throw new RuntimeException(thrownE); // Shouldn't happen
				} catch (ExecutionException thrownE) {
					// logger.error("Script.correctlySpends threw a non-normal
					// exception: " ,thrownE );
					throw new VerificationException(
							"Bug in Script.correctlySpends, likely script malformed in some new and interesting way.",
							thrownE);
				}
				if (e != null)
					throw e;
			}
		} catch (VerificationException e) {
			logger.info("", e);
			if (throwExceptions)
				throw e;
			return SolidityState.getFailState();
		} catch (BlockStoreException e) {
			logger.error("", e);
			if (throwExceptions)
				throw new VerificationException(e);
			return SolidityState.getFailState();
		} finally {
			scriptVerificationExecutor.shutdownNow();
		}

		return SolidityState.getSuccessState();
	}

	private boolean enableFee(Block block) {
		return (block.getLastMiningRewardBlock() > 1283681
				&& networkParameters.getId().equals(NetworkParameters.ID_MAINNET))
				|| networkParameters.getId().equals(NetworkParameters.ID_UNITTESTNET);
	}

	private Boolean checkBurnedFromAddress(final Transaction tx, Long chain) {
		String fromAddress = fromAddress(tx);
		for (BurnedAddress burned : BurnedAddress.init()) {
			// logger.debug(" checkBurnedFromAddress " + fromAddress + " " +
			// burned.getLockaddress() + " " + chain + " "
			// + burned.getChain());
			if (burned.getLockaddress().equals(fromAddress) && chain >= burned.getChain()) {
				return true;
			}
		}

		return false;

	}

	private String fromAddress(final Transaction tx) {
		String fromAddress = "";
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
				return "";
			}
		}
		return fromAddress;

	}

	private boolean checkTxOutputSigns(Map<String, Coin> valueOut) {
		for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
			// System.out.println(entry.getKey() + "/" + entry.getValue());
			if (entry.getValue().signum() < 0) {
				return false;
			}
		}
		return true;
	}

	private boolean checkTxInputOutput(Map<String, Coin> valueInput, Map<String, Coin> valueOut, Block block) {
		Boolean checkFee = false;

		for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
			if (!valueInput.containsKey(entry.getKey())) {
				throw new InvalidTransactionException("Transaction input and output values do not match");
			} else {
				// add check fee
				if (entry.getValue().isBIG() && !checkFee) {
					if (valueInput.get(entry.getKey()).compareTo(entry.getValue().add(Coin.FEE_DEFAULT)) >= 0) {
						checkFee = true;
					}
				}
				if (valueInput.get(entry.getKey()).compareTo(entry.getValue()) < 0) {
					throw new InvalidTransactionException("Transaction input and output values do not match");

				}
			}
		}
		// add check fee, no big in valueOut, but valueInput contain fee
		if (!checkFee) {
			if (valueOut.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) == null) {
				if (valueInput.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) != null && valueInput
						.get(NetworkParameters.BIGTANGLE_TOKENID_STRING).compareTo(Coin.FEE_DEFAULT) >= 0) {
					checkFee = true;
				}
			}
		}
		return checkFee;
	}

	private SolidityState checkFullTypeSpecificSolidity(Block block, BlockWrap storedPrev, BlockWrap storedPrevBranch,
			long height, boolean throwExceptions, FullBlockStore store) throws BlockStoreException {
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
			// Check rewards are solid
			SolidityState rewardSolidityState = checkFullRewardSolidity(block, storedPrev, storedPrevBranch, height,
					throwExceptions, store);
			if (!(rewardSolidityState.getState() == State.Success)) {
				return rewardSolidityState;
			}

			break;
		case BLOCKTYPE_TOKEN_CREATION:
			// Check token issuances are solid
			SolidityState tokenSolidityState = checkFullTokenSolidity(block, height, throwExceptions, store);
			if (!(tokenSolidityState.getState() == State.Success)) {
				return tokenSolidityState;
			}

			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_OPEN:
			SolidityState openSolidityState = checkFullOrderOpenSolidity(block, height, throwExceptions, store);
			if (!(openSolidityState.getState() == State.Success)) {
				return openSolidityState;
			}
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			SolidityState opSolidityState = checkFullOrderOpSolidity(block, height, throwExceptions, store);
			if (!(opSolidityState.getState() == State.Success)) {
				return opSolidityState;
			}
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			SolidityState check = checkFullContractEventSolidity(block, height, throwExceptions);
			if (!(check.getState() == State.Success)) {
				return check;
			}
			break;
		default:
			throw new RuntimeException("No Implementation");
		}

		return SolidityState.getSuccessState();
	}

	private SolidityState checkFullContractEventSolidity(Block block, long height, boolean throwExceptions)
			throws BlockStoreException {
		return checkFormalContractEventSolidity(block, throwExceptions);
	}

	private SolidityState checkFullOrderOpenSolidity(Block block, long height, boolean throwExceptions,
			FullBlockStore store) throws BlockStoreException {
		List<Transaction> transactions = block.getTransactions();

		if (transactions.get(0).getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		// Check that the tx has correct data
		OrderOpenInfo orderInfo;
		try {
			orderInfo = new OrderOpenInfo().parse(transactions.get(0).getData());
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		if (!transactions.get(0).getDataClassName().equals("OrderOpen")) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		// NotNull checks
		if (orderInfo.getTargetTokenid() == null) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Invalid target tokenid");
			return SolidityState.getFailState();
		}

		// Check bounds for target coin values
		if (orderInfo.getTargetValue() < 1 || orderInfo.getTargetValue() > Long.MAX_VALUE) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Invalid target value");
			return SolidityState.getFailState();
		}

		// Check that the tx inputs only burn one type of tokens, only for offerTokenid
		Coin burnedCoins = null;
		if (orderInfo.getVersion() > 1) {
			burnedCoins = countBurnedToken(block, store, orderInfo.getOfferTokenid());
		} else {
			burnedCoins = countBurnedToken(block, store);
		}

		if (burnedCoins == null || burnedCoins.getValue().longValue() == 0) {
			if (throwExceptions)
				// throw new InvalidOrderException("No tokens were offered.");
				return SolidityState.getFailState();
		}

		if (burnedCoins.getValue().longValue() > Long.MAX_VALUE) {
			if (throwExceptions)
				throw new InvalidOrderException("The order is too large.");
			return SolidityState.getFailState();
		}
		// calculate the offervalue for version == 1
		if (orderInfo.getVersion() == 1) {
			orderInfo.setOfferValue(burnedCoins.getValue().longValue());
			orderInfo.setOfferTokenid(burnedCoins.getTokenHex());
		}

		// Check that the tx inputs only burn must be the offerValue
		if (burnedCoins.isBIG() && enableFee(block)) {
			// fee
			if (!burnedCoins.subtract(Coin.FEE_DEFAULT)
					.equals(new Coin(orderInfo.getOfferValue(), Utils.HEX.decode(orderInfo.getOfferTokenid())))) {
				if (throwExceptions)
					throw new InvalidOrderException("The Transaction data burnedCoins is not same as OfferValue .");
				return SolidityState.getFailState();

			}
		} else {
			if (!burnedCoins
					.equals(new Coin(orderInfo.getOfferValue(), Utils.HEX.decode(orderInfo.getOfferTokenid())))) {
				if (throwExceptions)
					throw new InvalidOrderException("The Transaction data burnedCoins is not same as OfferValue .");
				return SolidityState.getFailState();
			}
		}
		// Check that either the burnt token or the target token base token
		if (checkOrderBaseToken(orderInfo, burnedCoins)) {
			if (throwExceptions)
				throw new InvalidOrderException(
						"Invalid exchange combination. Ensure order base token is sold or bought.");
			return SolidityState.getFailState();
		}

		// Check that we have a correct price given in full Base Token
		if (orderInfo.getPrice() != null && orderInfo.getPrice() <= 0 && orderInfo.getVersion() > 1) {
			if (throwExceptions)
				throw new InvalidOrderException("The given order's price is not integer.");
			return SolidityState.getFailState();
		}

		if (orderInfo.getValidToTime() > Math.addExact(orderInfo.getValidFromTime(),
				NetworkParameters.ORDER_TIMEOUT_MAX)) {
			if (throwExceptions)
				throw new InvalidOrderException("The given order's timeout is too long.");
			return SolidityState.getFailState();
		}

		if (!ECKey.fromPublicOnly(orderInfo.getBeneficiaryPubKey()).toAddress(networkParameters).toBase58()
				.equals(orderInfo.getBeneficiaryAddress())) {
			if (throwExceptions)
				throw new InvalidOrderException("The address does not match with the given pubkey.");
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	private boolean checkOrderBaseToken(OrderOpenInfo orderInfo, Coin burnedCoins) {
		return burnedCoins.getTokenHex().equals(orderInfo.getOrderBaseToken())
				&& orderInfo.getTargetTokenid().equals(orderInfo.getOrderBaseToken())
				|| !burnedCoins.getTokenHex().equals(orderInfo.getOrderBaseToken())
						&& !orderInfo.getTargetTokenid().equals(orderInfo.getOrderBaseToken());
	}

	public Coin countBurnedToken(Block block, FullBlockStore store) throws BlockStoreException {
		Coin burnedCoins = null;
		for (final Transaction tx : block.getTransactions()) {
			for (int index = 0; index < tx.getInputs().size(); index++) {
				TransactionInput in = tx.getInputs().get(index);
				UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getBlockHash(), in.getOutpoint().getTxHash(),
						in.getOutpoint().getIndex());
				if (prevOut == null) {
					// Cannot happen due to solidity checks before
					throw new RuntimeException("Block attempts to spend a not yet existent output!");
				}

				if (burnedCoins == null)
					burnedCoins = Coin.valueOf(0, Utils.HEX.encode(prevOut.getValue().getTokenid()));

				try {
					burnedCoins = burnedCoins.add(prevOut.getValue());
				} catch (IllegalArgumentException e) {
					throw new InvalidOrderException(e.getMessage());
				}
			}

			for (int index = 0; index < tx.getOutputs().size(); index++) {
				TransactionOutput out = tx.getOutputs().get(index);

				try {
					burnedCoins = burnedCoins.subtract(out.getValue());
				} catch (IllegalArgumentException e) {
					throw new InvalidOrderException(e.getMessage());
				}
			}
		}
		return burnedCoins;
	}

	/**
	 * Counts the number tokens that are being burned in this block. If multiple
	 * tokens exist in the transaction, throws InvalidOrderException.
	 * 
	 * @param block
	 * @return
	 * @throws BlockStoreException
	 */
	public Coin countBurnedToken(Block block, FullBlockStore store, String tokenid) throws BlockStoreException {
		Coin burnedCoins = null;
		for (final Transaction tx : block.getTransactions()) {

			for (int index = 0; index < tx.getInputs().size(); index++) {
				TransactionInput in = tx.getInputs().get(index);
				UTXO prevOut = store.getTransactionOutput(in.getOutpoint().getBlockHash(), in.getOutpoint().getTxHash(),
						in.getOutpoint().getIndex());
				if (prevOut == null) {
					// Cannot happen due to solidity checks before
					throw new RuntimeException("Block attempts to spend a not yet existent output!");
				}
				if (Utils.HEX.encode(prevOut.getValue().getTokenid()).equals(tokenid)) {
					if (burnedCoins == null)
						burnedCoins = Coin.valueOf(0, Utils.HEX.encode(prevOut.getValue().getTokenid()));

					try {
						burnedCoins = burnedCoins.add(prevOut.getValue());
					} catch (IllegalArgumentException e) {
						throw new InvalidOrderException(e.getMessage());
					}
				}
			}

			for (int index = 0; index < tx.getOutputs().size(); index++) {
				TransactionOutput out = tx.getOutputs().get(index);

				try {
					if (Utils.HEX.encode(out.getValue().getTokenid()).equals(tokenid)) {
						burnedCoins = burnedCoins.subtract(out.getValue());
					}
				} catch (IllegalArgumentException e) {
					throw new InvalidOrderException(e.getMessage());
				}
			}
		}
		return burnedCoins;
	}

	private SolidityState checkFullOrderOpSolidity(Block block, long height, boolean throwExceptions,
			FullBlockStore store) throws BlockStoreException {

		// No output creation
		if (!block.getTransactions().get(0).getOutputs().isEmpty()) {
			if (throwExceptions)
				throw new TransactionOutputsDisallowedException();
			return SolidityState.getFailState();
		}

		Transaction tx = block.getTransactions().get(0);
		if (tx.getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		OrderCancelInfo info = null;
		try {
			info = new OrderCancelInfo().parse(tx.getData());
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		// NotNull checks
		if (info.getBlockHash() == null) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Invalid target txhash");
			return SolidityState.getFailState();
		}

		// Ensure the predecessing order exists
		OrderRecord order = store.getOrder(info.getBlockHash(), Sha256Hash.ZERO_HASH);
		if (order == null) {
			return SolidityState.from(info.getBlockHash(), true);
		}

		byte[] pubKey = order.getBeneficiaryPubKey();
		byte[] data = tx.getHash().getBytes();
		byte[] signature = block.getTransactions().get(0).getDataSignature();

		// If signature of beneficiary is missing, fail
		if (!ECKey.verify(data, signature, pubKey)) {
			if (throwExceptions)
				throw new InsufficientSignaturesException();
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	private SolidityState checkFullRewardSolidity(Block block, BlockWrap storedPrev, BlockWrap storedPrevBranch,
			long height, boolean throwExceptions, FullBlockStore store) throws BlockStoreException {
		List<Transaction> transactions = block.getTransactions();

		if (transactions.size() != 1) {
			if (throwExceptions)
				throw new IncorrectTransactionCountException();
			return SolidityState.getFailState();
		}

		// No output creation
		if (!transactions.get(0).getOutputs().isEmpty()) {
			if (throwExceptions)
				throw new TransactionOutputsDisallowedException();
			return SolidityState.getFailState();
		}

		if (transactions.get(0).getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		// Check that the tx has correct data
		RewardInfo rewardInfo = new RewardInfo().parseChecked(transactions.get(0).getData());

		// NotNull checks
		if (rewardInfo.getPrevRewardHash() == null) {
			if (throwExceptions)
				throw new MissingDependencyException();
			return SolidityState.getFailState();
		}

		// Ensure dependency (prev reward hash) exists
		Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
		BlockWrap dependency = store.getBlockWrap(prevRewardHash);
		if (dependency == null)
			return SolidityState.from(prevRewardHash, true);

		// Ensure dependency (prev reward hash) is valid predecessor
		if (dependency.getBlock().getBlockType() != Type.BLOCKTYPE_INITIAL
				&& dependency.getBlock().getBlockType() != Type.BLOCKTYPE_REWARD) {
			if (throwExceptions)
				throw new InvalidDependencyException("Predecessor is not reward or genesis");
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	public SolidityState checkFullTokenSolidity(Block block, long height, boolean throwExceptions, FullBlockStore store)
			throws BlockStoreException {

		// TODO (check fee get(1))
		if (!block.getTransactions().get(0).isCoinBase()) {
			if (throwExceptions)
				throw new NotCoinbaseException();
			return SolidityState.getFailState();
		}

		Transaction tx = block.getTransactions().get(0);
		if (tx.getData() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		TokenInfo currentToken = null;
		try {
			currentToken = new TokenInfo().parse(tx.getData());
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		if (checkFormalTokenFields(throwExceptions, currentToken) == SolidityState.getFailState())
			return SolidityState.getFailState();

		// Check field correctness: amount
		if (!currentToken.getToken().getAmount().equals(block.getTransactions().get(0).getOutputSum())) {
			logger.debug("Incorrect amount field" + currentToken.getToken().getAmount() + " !="
					+ block.getTransactions().get(0).getOutputSum());
			if (throwExceptions)
				throw new InvalidTransactionDataException("Incorrect amount field");
			return SolidityState.getFailState();
		}

		// Check all token issuance transaction outputs are actually of the
		// given token or fee
		for (Transaction tx1 : block.getTransactions()) {
			for (TransactionOutput out : tx1.getOutputs()) {
				if (!out.getValue().getTokenHex().equals(currentToken.getToken().getTokenid())
						&& !out.getValue().isBIG()) {
					if (throwExceptions)
						throw new InvalidTokenOutputException();
					return SolidityState.getFailState();
				}
			}
		}

		// Check previous issuance hash exists or initial issuance
		if ((currentToken.getToken().getPrevblockhash() == null && currentToken.getToken().getTokenindex() != 0)
				|| (currentToken.getToken().getPrevblockhash() != null
						&& currentToken.getToken().getTokenindex() == 0)) {
			if (throwExceptions)
				throw new MissingDependencyException();
			return SolidityState.getFailState();
		}

		// Must define enough permissioned addresses
		if (currentToken.getToken().getSignnumber() > currentToken.getMultiSignAddresses().size()) {
			if (throwExceptions)
				throw new InvalidTransactionDataException(
						"Cannot fulfill required sign number from multisign address list");
			return SolidityState.getFailState();
		}

		// Must have a predecessing domain definition
		if (currentToken.getToken().getDomainNameBlockHash() == null) {
			if (throwExceptions)
				throw new InvalidDependencyException("Domain predecessor is empty");
			return SolidityState.getFailState();
		}

		// Requires the predecessing domain definition block to exist and be a
		// legal domain
		Token prevDomain = null;

		if (!currentToken.getToken().getDomainNameBlockHash()
				.equals(networkParameters.getGenesisBlock().getHashAsString())) {

			prevDomain = store.getTokenByBlockHash(Sha256Hash.wrap(currentToken.getToken().getDomainNameBlockHash()));
			if (prevDomain == null) {
				if (throwExceptions)
					throw new MissingDependencyException();
				return SolidityState.from(Sha256Hash.wrap(currentToken.getToken().getDomainNameBlockHash()), true);
			}

		}
		// Ensure signatures exist
		int signatureCount = 0;
		if (tx.getDataSignature() == null) {
			if (throwExceptions)
				throw new MissingTransactionDataException();
			return SolidityState.getFailState();
		}

		// Get signatures from transaction
		String jsonStr = new String(tx.getDataSignature());
		MultiSignByRequest txSignatures;
		try {
			txSignatures = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
		} catch (IOException e) {
			if (throwExceptions)
				throw new MalformedTransactionDataException();
			return SolidityState.getFailState();
		}

		// Get permissioned addresses
		Token prevToken = null;
		List<MultiSignAddress> permissionedAddresses = new ArrayList<MultiSignAddress>();
		// If not initial issuance, we check according to the previous token
		if (currentToken.getToken().getTokenindex() != 0) {
			try {
				// Previous issuance must exist to check solidity
				prevToken = store.getTokenByBlockHash(currentToken.getToken().getPrevblockhash());
				if (prevToken == null) {
					return SolidityState.from(currentToken.getToken().getPrevblockhash(), true);
				}

				// Compare members of previous and current issuance
				if (!currentToken.getToken().getTokenid().equals(prevToken.getTokenid())) {
					if (throwExceptions)
						throw new InvalidDependencyException("Wrong token ID");
					return SolidityState.getFailState();
				}
				if (currentToken.getToken().getTokenindex() != prevToken.getTokenindex() + 1) {
					if (throwExceptions)
						throw new InvalidDependencyException("Wrong token index");
					return SolidityState.getFailState();
				}

				if (!currentToken.getToken().getTokenname().equals(prevToken.getTokenname())) {
					if (throwExceptions)
						throw new PreviousTokenDisallowsException("Cannot change token name");
					return SolidityState.getFailState();
				}

				if (currentToken.getToken().getDomainName() != null
						&& !currentToken.getToken().getDomainName().equals(prevToken.getDomainName())) {
					if (throwExceptions)
						throw new PreviousTokenDisallowsException("Cannot change token domain name");
					return SolidityState.getFailState();
				}

				if (currentToken.getToken().getDecimals() != prevToken.getDecimals()) {
					if (throwExceptions)
						throw new PreviousTokenDisallowsException("Cannot change token decimal");
					return SolidityState.getFailState();
				}
				if (currentToken.getToken().getTokentype() != prevToken.getTokentype()) {
					if (throwExceptions)
						throw new PreviousTokenDisallowsException("Cannot change token type");
					return SolidityState.getFailState();
				}
				if (!currentToken.getToken().getDomainNameBlockHash().equals(prevToken.getDomainNameBlockHash())) {
					if (throwExceptions)
						throw new PreviousTokenDisallowsException("Cannot change token domain");
					return SolidityState.getFailState();
				}

				// Must allow more issuances
				if (prevToken.isTokenstop()) {
					if (throwExceptions)
						throw new PreviousTokenDisallowsException("Previous token does not allow further issuance");
					return SolidityState.getFailState();
				}

				// Get addresses allowed to reissue
				permissionedAddresses.addAll(store.getMultiSignAddressListByTokenidAndBlockHashHex(
						prevToken.getTokenid(), prevToken.getBlockHash()));

			} catch (BlockStoreException e) {
				// Cannot happen, previous token must exist
				e.printStackTrace();
			}
		} else {
			// First time issuances must sign for the token id
			permissionedAddresses = currentToken.getMultiSignAddresses();

			// Any first time issuances also require the domain signatures
			List<MultiSignAddress> prevDomainPermissionedAddresses = queryDomainnameTokenMultiSignAddresses(
					prevDomain == null ? networkParameters.getGenesisBlock().getHash() : prevDomain.getBlockHash(),
					store);
			SolidityState domainPermission = checkDomainPermission(prevDomainPermissionedAddresses,
					txSignatures.getMultiSignBies(), 1,
					// TODO remove the high level domain sign
					// only one sign of prev domain needed
					// prevDomain == null ? 1 : prevDomain.getSignnumber(),
					throwExceptions, tx.getHash());
			if (domainPermission != SolidityState.getSuccessState())
				return domainPermission;
		}

		// Get permissioned pubkeys wrapped to check for bytearray equality
		Set<ByteBuffer> permissionedPubKeys = new HashSet<ByteBuffer>();
		for (MultiSignAddress multiSignAddress : permissionedAddresses) {
			byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
			permissionedPubKeys.add(ByteBuffer.wrap(pubKey));
		}

		// Ensure all multiSignBys pubkeys are from the permissioned list
		for (MultiSignBy multiSignBy : new ArrayList<>(txSignatures.getMultiSignBies())) {
			ByteBuffer pubKey = ByteBuffer.wrap(Utils.HEX.decode(multiSignBy.getPublickey()));
			if (!permissionedPubKeys.contains(pubKey)) {
				// If a pubkey is not from the list, drop it.
				txSignatures.getMultiSignBies().remove(multiSignBy);
				continue;
			} else {
				// Otherwise the listed address is used. Cannot use same address
				// multiple times.
				permissionedPubKeys.remove(pubKey);
			}
		}

		// For first issuance, ensure the tokenid pubkey signature exists to
		// prevent others from generating conflicts
		if (currentToken.getToken().getTokenindex() == 0) {
			if (permissionedPubKeys.contains(ByteBuffer.wrap(Utils.HEX.decode(currentToken.getToken().getTokenid())))) {
				if (throwExceptions)
					throw new MissingSignatureException();
				return SolidityState.getFailState();
			}
		}

		// Verify signatures
		for (MultiSignBy multiSignBy : txSignatures.getMultiSignBies()) {
			byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
			byte[] data = tx.getHash().getBytes();
			byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());

			if (ECKey.verify(data, signature, pubKey)) {
				signatureCount++;
			} else {
				if (throwExceptions)
					throw new InvalidSignatureException();
				return SolidityState.getFailState();
			}
		}

		// Return whether sufficient signatures exist
		int requiredSignatureCount = prevToken != null ? prevToken.getSignnumber() : 1;
		// int requiredSignatureCount = signNumberCount;
		if (signatureCount >= requiredSignatureCount)
			return SolidityState.getSuccessState();

		if (throwExceptions)
			throw new InsufficientSignaturesException();
		return SolidityState.getFailState();
	}

	private SolidityState checkDomainPermission(List<MultiSignAddress> permissionedAddresses,
			List<MultiSignBy> multiSignBies_0, int requiredSignatures, boolean throwExceptions, Sha256Hash txHash) {

		// Make original list inaccessible by cloning list
		List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>(multiSignBies_0);

		// Get permissioned pubkeys wrapped to check for bytearray equality
		Set<ByteBuffer> permissionedPubKeys = new HashSet<ByteBuffer>();
		for (MultiSignAddress multiSignAddress : permissionedAddresses) {
			byte[] pubKey = Utils.HEX.decode(multiSignAddress.getPubKeyHex());
			permissionedPubKeys.add(ByteBuffer.wrap(pubKey));
		}

		// Ensure all multiSignBys pubkeys are from the permissioned list
		for (MultiSignBy multiSignBy : new ArrayList<MultiSignBy>(multiSignBies)) {
			ByteBuffer pubKey = ByteBuffer.wrap(Utils.HEX.decode(multiSignBy.getPublickey()));
			if (!permissionedPubKeys.contains(pubKey)) {
				// If a pubkey is not from the list, drop it.
				multiSignBies.remove(multiSignBy);
				continue;
			} else {
				// Otherwise the listed address is used. Cannot use same address
				// multiple times.
				permissionedPubKeys.remove(pubKey);
			}
		}

		// Verify signatures
		int signatureCount = 0;
		for (MultiSignBy multiSignBy : multiSignBies) {
			byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
			byte[] data = txHash.getBytes();
			byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());

			if (ECKey.verify(data, signature, pubKey)) {
				signatureCount++;
			} else {
				if (throwExceptions)
					throw new InvalidSignatureException();
				return SolidityState.getFailState();
			}
		}

		// Return whether sufficient signatures exist
		if (signatureCount >= requiredSignatures)
			return SolidityState.getSuccessState();
		else {
			if (throwExceptions)
				throw new InsufficientSignaturesException();
			return SolidityState.getFailState();
		}
	}

	private SolidityState checkFormalTokenFields(boolean throwExceptions, TokenInfo currentToken) {
		if (currentToken.getToken() == null) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("getToken is null");
			return SolidityState.getFailState();
		}
		if (currentToken.getMultiSignAddresses() == null) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("getMultiSignAddresses is null");
			return SolidityState.getFailState();
		}
		if (currentToken.getToken().getTokenid() == null) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("getTokenid is null");
			return SolidityState.getFailState();
		}
		// if (currentToken.getToken().getPrevblockhash() == null) {
		// if (throwExceptions)
		// throw new InvalidTransactionDataException("getPrevblockhash is
		// null");
		// return SolidityState.getFailState();
		// }
		if (currentToken.getToken().getTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Not allowed");
			return SolidityState.getFailState();
		}

		if (currentToken.getToken().getDescription() != null
				&& currentToken.getToken().getDescription().length() > Token.TOKEN_MAX_DESC_LENGTH) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Too long description");
			return SolidityState.getFailState();
		}

		if (currentToken.getToken().getTokenid() != null
				&& currentToken.getToken().getTokenid().length() > Token.TOKEN_MAX_ID_LENGTH) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Too long tokenid");
			return SolidityState.getFailState();
		}

		if (currentToken.getToken().getLanguage() != null
				&& currentToken.getToken().getLanguage().length() > Token.TOKEN_MAX_LANGUAGE_LENGTH) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Too long language");
			return SolidityState.getFailState();
		}
		if (currentToken.getToken().getClassification() != null
				&& currentToken.getToken().getClassification().length() > Token.TOKEN_MAX_CLASSIFICATION_LENGTH) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Too long classification");
			return SolidityState.getFailState();
		}
		if (currentToken.getToken().getTokenname() == null || "".equals(currentToken.getToken().getTokenname())) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Token name cannot be null.");
		}
		if (currentToken.getToken().getTokenname() != null
				&& currentToken.getToken().getTokenname().length() > Token.TOKEN_MAX_NAME_LENGTH) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Too long token name");
			return SolidityState.getFailState();
		}

		if (currentToken.getToken().getDomainName() != null
				&& currentToken.getToken().getDomainName().length() > Token.TOKEN_MAX_URL_LENGTH) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Too long domainname");
			return SolidityState.getFailState();
		}
		if (currentToken.getToken().getSignnumber() < 0) {
			if (throwExceptions)
				throw new InvalidTransactionDataException("Invalid sign number");
			return SolidityState.getFailState();
		}

		return SolidityState.getSuccessState();
	}

	public SolidityState checkRewardBlockPow(Block block, boolean throwExceptions) throws BlockStoreException {
		try {
			RewardInfo rewardInfo = new RewardInfo().parse(block.getTransactions().get(0).getData());
			// Get difficulty from predecessors
			BigInteger target = Utils.decodeCompactBits(rewardInfo.getDifficultyTargetReward());
			// Check PoW
			boolean allOk = false;
			try {
				allOk = block.checkProofOfWork(throwExceptions, target);
			} catch (VerificationException e) {
				logger.warn("Failed to verify block: ", e);
				logger.warn(block.getHashAsString());
				throw e;
			}

			if (!allOk)
				return SolidityState.getFailState();
			else
				return SolidityState.getSuccessState();
		} catch (Exception e) {
			throw new UnsolidException();
		}
	}

	public SolidityState checkChainSolidity(Block block, boolean throwExceptions, FullBlockStore store)
			throws BlockStoreException {

		// Check the block fulfills PoW as chain
		checkRewardBlockPow(block, true);

		// Check the chain block formally valid
		checkFormalBlockSolidity(block, true);

		BlockWrap prevTrunkBlock = store.getBlockWrap(block.getPrevBlockHash());
		BlockWrap prevBranchBlock = store.getBlockWrap(block.getPrevBranchBlockHash());
		if (prevTrunkBlock == null)
			SolidityState.from(block.getPrevBlockHash(), true);
		if (prevBranchBlock == null)
			SolidityState.from(block.getPrevBranchBlockHash(), true);

		long difficulty = calculateNextBlockDifficulty(block.getRewardInfo());
		if (difficulty != block.getDifficultyTarget()) {
			throw new VerificationException("calculateNextBlockDifficulty does not match.");
		}

		if (block.getLastMiningRewardBlock() != block.getRewardInfo().getChainlength()) {
			if (throwExceptions)
				throw new DifficultyConsensusInheritanceException();
			return SolidityState.getFailState();
		}

		SolidityState difficultyResult = checkRewardDifficulty(block, store);
		if (!difficultyResult.isSuccessState()) {
			return difficultyResult;
		}

		SolidityState referenceResult = checkRewardReferencedBlocks(block, store);
		if (!referenceResult.isSuccessState()) {
			return referenceResult;
		}

		// Solidify referenced blocks
		solidifyBlocks(block.getRewardInfo(), store);

		return SolidityState.getSuccessState();
	}

	/**
	 * Checks if the block has all of its dependencies to fully determine its
	 * validity. Then checks if the block is valid based on its dependencies. If
	 * SolidityState.getSuccessState() is returned, the block is valid. If
	 * SolidityState.getFailState() is returned, the block is invalid. Otherwise,
	 * appropriate solidity states are returned to imply missing dependencies.
	 *
	 * @param block
	 * @return SolidityState
	 * @throws BlockStoreException
	 */
	public SolidityState checkSolidity(Block block, boolean throwExceptions, FullBlockStore store)
			throws BlockStoreException {
		return checkSolidity(block, throwExceptions, store, true);
	}

	public SolidityState checkSolidity(Block block, boolean throwExceptions, FullBlockStore store,
			boolean predecessorsSolid) throws BlockStoreException {
		try {
			// Check formal correctness of the block
			SolidityState formalSolidityResult = checkFormalBlockSolidity(block, throwExceptions);
			if (formalSolidityResult.isFailState())
				return formalSolidityResult;
			final Set<Sha256Hash> allPredecessorBlockHashes = getAllRequiredBlockHashes(block, false);
			List<BlockWrap> allRequirements = getAllRequirements(block, allPredecessorBlockHashes, store);		
			// Predecessors must exist and be ok
			SolidityState predecessorsExist = checkPredecessorsExistAndOk(block, throwExceptions,
					allRequirements, store);
			if (!predecessorsExist.isSuccessState()) {
	 			return predecessorsExist;
			}
		
			// Inherit solidity from predecessors if they are not solid
			SolidityState minPredecessorSolidity = getMinPredecessorSolidity(block, throwExceptions, allRequirements,
					store, predecessorsSolid);

			// For consensus blocks, it works as follows:
			// If solid == 1 or solid == 2, we also check for PoW now
			// since it is possible to do so
			if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
				if (minPredecessorSolidity.getState() == State.MissingCalculation
						|| minPredecessorSolidity.getState() == State.Success) {
					SolidityState state = checkRewardBlockPow(block, throwExceptions);
					if (!state.isSuccessState()) {
						return state;
					}
				}
			}

			// Inherit solidity from predecessors if they are not solid
			switch (minPredecessorSolidity.getState()) {
			case MissingCalculation:
			case Invalid:
			case MissingPredecessor:
				return minPredecessorSolidity;
			case Success:
				break;
			}

			// Otherwise, the solidity of the block itself is checked
			return checkFullBlockSolidity(block, throwExceptions, allRequirements, store);

		} catch (IllegalArgumentException e) {
			throw new VerificationException(e);
		}

	}

	public GetTXRewardResponse getMaxConfirmedReward(Map<String, Object> request, FullBlockStore store)
			throws BlockStoreException {

		return GetTXRewardResponse.create(store.getMaxConfirmedReward());

	}

	public GetTXRewardListResponse getAllConfirmedReward(Map<String, Object> request, FullBlockStore store)
			throws BlockStoreException {

		return GetTXRewardListResponse.create(store.getAllConfirmedReward());

	}

	/**
	 * DOES NOT CHECK FOR SOLIDITY. Computes eligibility of rewards + data tx +
	 * Pair.of(new difficulty + new perTxReward) here for new reward blocks. This is
	 * a prototype implementation. In the actual Spark implementation, the
	 * computational cost is not a problem, since it is instead backpropagated and
	 * calculated for free with delay. For more info, see notes.
	 * 
	 * @param prevTrunk      a predecessor block in the db
	 * @param prevBranch     a predecessor block in the db
	 * @param prevRewardHash the predecessor reward
	 * @return eligibility of rewards + data tx + Pair.of(new difficulty + new
	 *         perTxReward)
	 */
	public RewardBuilderResult makeReward(Sha256Hash prevTrunk, Sha256Hash prevBranch, Sha256Hash prevRewardHash,
			long currentTime, FullBlockStore store) throws BlockStoreException {

		// Read previous reward block's data
		long prevChainLength = store.getRewardChainLength(prevRewardHash);

		// Build transaction for block
		Transaction tx = new Transaction(networkParameters);

		Set<Sha256Hash> blocks = new HashSet<Sha256Hash>();
		long cutoffheight = getRewardCutoffHeight(prevRewardHash, store);

		// Count how many blocks from miners in the reward interval are approved
		BlockWrap prevTrunkBlock = store.getBlockWrap(prevTrunk);
		BlockWrap prevBranchBlock = store.getBlockWrap(prevBranch);

		addRequiredNonContainedBlockHashesTo(blocks, prevBranchBlock, cutoffheight, prevChainLength, true, store);
		addRequiredNonContainedBlockHashesTo(blocks, prevTrunkBlock, cutoffheight, prevChainLength, true, store);

		long difficultyReward = calculateNextChainDifficulty(prevRewardHash, prevChainLength + 1, currentTime, store);

		// Build the type-specific tx data
		RewardInfo rewardInfo = new RewardInfo(prevRewardHash, difficultyReward, blocks, prevChainLength + 1);
		tx.setData(rewardInfo.toByteArray());
		tx.setMemo(new MemoInfo("Reward"));
		return new RewardBuilderResult(tx, difficultyReward);
	}

	public long calculateNextBlockDifficulty(RewardInfo currRewardInfo) {
		BigInteger difficultyTargetReward = Utils.decodeCompactBits(currRewardInfo.getDifficultyTargetReward());
		BigInteger difficultyChain = difficultyTargetReward
				.multiply(BigInteger.valueOf(NetworkParameters.TARGET_MAX_TPS));
		difficultyChain = difficultyChain.multiply(BigInteger.valueOf(NetworkParameters.TARGET_SPACING));

		if (difficultyChain.compareTo(networkParameters.getMaxTarget()) > 0) {
			// logger.info("Difficulty hit proof of work limit: {}",
			// difficultyChain.toString(16));
			difficultyChain = networkParameters.getMaxTarget();
		}

		return Utils.encodeCompactBits(difficultyChain);
	}

	public void buildRewardChain(Block newMilestoneBlock, FullBlockStore store) throws BlockStoreException {

		RewardInfo currRewardInfo = new RewardInfo().parseChecked(newMilestoneBlock.getTransactions().get(0).getData());
		Set<Sha256Hash> milestoneSet = currRewardInfo.getBlocks();
		long cutoffHeight = getRewardCutoffHeight(currRewardInfo.getPrevRewardHash(), store);

		// Check all referenced blocks have their requirements
		SolidityState solidityState = checkReferencedBlockRequirements(newMilestoneBlock, cutoffHeight, store);
		if (!solidityState.isSuccessState())
			throw new VerificationException(" checkReferencedBlockRequirements is failed: " + solidityState.toString());

		// Solidify referenced blocks
		solidifyBlocks(currRewardInfo, store);

		// Ensure the new difficulty and tx is set correctly
		checkGeneratedReward(newMilestoneBlock, store);

		// Sanity check: No reward blocks are approved
 	checkContainsNoRewardBlocks(newMilestoneBlock, store);

		// Check: At this point, predecessors must be solid
		solidityState = checkSolidity(newMilestoneBlock, false, store, false);
		if (!solidityState.isSuccessState())
			throw new VerificationException(" .checkSolidity is failed: " + solidityState.toString()
					+ "\n with block = " + newMilestoneBlock.toString());

		// Unconfirm anything not confirmed by milestone
		List<Sha256Hash> wipeBlocks = store.getWhereConfirmedNotMilestone();
		HashSet<Sha256Hash> traversedBlockHashes = new HashSet<>();
		for (Sha256Hash wipeBlock : wipeBlocks)
			unconfirm(wipeBlock, traversedBlockHashes, store);

		// Find conflicts in the dependency set
		HashSet<BlockWrap> allApprovedNewBlocks = new HashSet<>();
		for (Sha256Hash hash : milestoneSet)
			allApprovedNewBlocks.add(store.getBlockWrap(hash));
		allApprovedNewBlocks.add(store.getBlockWrap(newMilestoneBlock.getHash()));

		// If anything is already spent, no-go
		boolean anySpentInputs = hasSpentInputs(allApprovedNewBlocks, store);
		// Optional<ConflictCandidate> spentInput =
		// findFirstSpentInput(allApprovedNewBlocks);

		if (anySpentInputs) {
			// solidityState = SolidityState.getFailState();
			throw new VerificationException("there are hasSpentInputs in allApprovedNewBlocks ");
		}
		// If any conflicts exist between the current set of
		// blocks, no-go
		boolean anyCandidateConflicts = allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates())
				.flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream()
				.anyMatch(l -> l.size() > 1);
		// List<List<ConflictCandidate>> candidateConflicts =
		// allApprovedNewBlocks.stream()
		// .map(b -> b.toConflictCandidates()).flatMap(i -> i.stream())
		// .collect(Collectors.groupingBy(i ->
		// i.getConflictPoint())).values().stream().filter(l -> l.size() > 1)
		// .collect(Collectors.toList());

		// Did we fail? Then we stop now and rerun consensus
		// logic on the new longest chain.
		if (anyCandidateConflicts) {
			solidityState = SolidityState.getFailState();
			throw new VerificationException("conflicts exist between the current set of ");
		}

		// Otherwise, all predecessors exist and were at least
		// solid > 0, so we should be able to confirm everything
		solidifyBlock(newMilestoneBlock, solidityState, true, store);
		HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
		long milestoneNumber = store.getRewardChainLength(newMilestoneBlock.getHash());
		for (BlockWrap approvedBlock : allApprovedNewBlocks)
			confirm(approvedBlock.getBlockEvaluation().getBlockHash(), traversedConfirms, milestoneNumber, store);

	}

	public void solidifyBlocks(RewardInfo currRewardInfo, FullBlockStore store) throws BlockStoreException {
		Comparator<Block> comparator = Comparator.comparingLong((Block b) -> b.getHeight())
				.thenComparing((Block b) -> b.getHash());
		TreeSet<Block> referencedBlocks = new TreeSet<Block>(comparator);
		for (Sha256Hash hash : currRewardInfo.getBlocks()) {
			referencedBlocks.add(store.get(hash));
		}
		for (Block block : referencedBlocks) {
			solidifyWaiting(block, store);
		}
	}

	/**
	 * Called as part of connecting a block when the new block results in a
	 * different chain having higher total work.
	 * 
	 */
	public void handleNewBestChain(Block newChainHead, FullBlockStore store)
			throws BlockStoreException, VerificationException {
		// checkState(lock.isHeldByCurrentThread());
		// This chain has overtaken the one we currently believe is best.
		// Reorganize is required.
		//
		// Firstly, calculate the block at which the chain diverged. We only
		// need to examine the
		// chain from beyond this block to find differences.
		Block head = getChainHead(store);
		final Block splitPoint = findSplit(newChainHead, head, store);
		if (splitPoint == null) {
			logger.info(" splitPoint is null, the chain ist not complete: ", newChainHead);
			return;
		}

		logger.info("Re-organize after split at height {}", splitPoint.getHeight());
		logger.info("Old chain head: \n {}", head);
		logger.info("New chain head: \n {}", newChainHead);
		logger.info("Split at block: \n {}", splitPoint);
		// Then build a list of all blocks in the old part of the chain and the
		// new part.
		LinkedList<Block> oldBlocks = new LinkedList<Block>();
		if (!head.getHash().equals(splitPoint.getHash())) {
			oldBlocks = getPartialChain(head, splitPoint, store);
		}
		final LinkedList<Block> newBlocks = getPartialChain(newChainHead, splitPoint, store);
		// Disconnect each transaction in the previous best chain that is no
		// longer in the new best chain
		for (Block oldBlock : oldBlocks) {
			// Sanity check:
			if (!oldBlock.getHash().equals(networkParameters.getGenesisBlock().getHash())) {
				// Unset the milestone (Chain length) of this one
				long milestoneNumber = oldBlock.getRewardInfo().getChainlength();
				List<BlockWrap> blocksInMilestoneInterval = store.getBlocksInMilestoneInterval(milestoneNumber,
						milestoneNumber);
				// Unconfirm anything not in milestone
				for (BlockWrap wipeBlock : blocksInMilestoneInterval)
					unconfirm(wipeBlock.getBlockHash(), new HashSet<>(), store);
			}
		}
		Block cursor;
		// Walk in ascending chronological order.
		for (Iterator<Block> it = newBlocks.descendingIterator(); it.hasNext();) {
			cursor = it.next();
			buildRewardChain(cursor, store);
			// if we build a chain longer than head, do a commit, even it may be
			// failed after this.
			if (cursor.getRewardInfo().getChainlength() > head.getRewardInfo().getChainlength()) {
				store.commitDatabaseBatchWrite();
				store.beginDatabaseBatchWrite();
			}
		}

		// Update the pointer to the best known block.
		// setChainHead(storedNewHead);
	}

	private Block getChainHead(FullBlockStore store) throws BlockStoreException {
		return store.get(store.getMaxConfirmedReward().getBlockHash());
	}

	/**
	 * Locates the point in the chain at which newBlock and chainHead diverge.
	 * Returns null if no split point was found (ie they are not part of the same
	 * chain). Returns newChainHead or chainHead if they don't actually diverge but
	 * are part of the same chain. return null, if the newChainHead is not complete
	 * locally.
	 */
	public Block findSplit(Block newChainHead, Block oldChainHead, FullBlockStore store) throws BlockStoreException {
		Block currentChainCursor = oldChainHead;
		Block newChainCursor = newChainHead;
		// Loop until we find the reward block both chains have in common.
		// Example:
		//
		// A -> B -> C -> D
		// *****\--> E -> F -> G
		//
		// findSplit will return block B. oldChainHead = D and newChainHead = G.
		while (!currentChainCursor.equals(newChainCursor)) {
			if (currentChainCursor.getRewardInfo().getChainlength() > newChainCursor.getRewardInfo().getChainlength()) {
				currentChainCursor = store.get(currentChainCursor.getRewardInfo().getPrevRewardHash());
				checkNotNull(currentChainCursor, "Attempt to follow an orphan chain");

			} else {
				newChainCursor = store.get(newChainCursor.getRewardInfo().getPrevRewardHash());
				checkNotNull(newChainCursor, "Attempt to follow an orphan chain");

			}
		}
		return currentChainCursor;
	}

	/**
	 * Returns the set of contiguous blocks between 'higher' and 'lower'. Higher is
	 * included, lower is not.
	 */
	private LinkedList<Block> getPartialChain(Block higher, Block lower, FullBlockStore store)
			throws BlockStoreException {
		checkArgument(higher.getHeight() > lower.getHeight(), "higher and lower are reversed");
		LinkedList<Block> results = new LinkedList<>();
		Block cursor = higher;
		while (true) {
			results.add(cursor);
			cursor = checkNotNull(store.get(cursor.getRewardInfo().getPrevRewardHash()),
					"Ran off the end of the chain");
			if (cursor.equals(lower))
				break;
		}
		return results;
	}

	@SuppressWarnings("unused")
	private Optional<ConflictCandidate> findFirstSpentInput(HashSet<BlockWrap> allApprovedNewBlocks,
			FullBlockStore store) {
		return allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates()).flatMap(i -> i.stream()).filter(c -> {
			try {
				return hasSpentDependencies(c, store);
			} catch (BlockStoreException e) {
				e.printStackTrace();
				return true;
			}
		}).findFirst();
	}

	public boolean hasSpentInputs(HashSet<BlockWrap> allApprovedNewBlocks, FullBlockStore store) {
		return allApprovedNewBlocks.stream().map(b -> b.toConflictCandidates()).flatMap(i -> i.stream()).anyMatch(c -> {
			try {
				return hasSpentDependencies(c, store);
			} catch (BlockStoreException e) {
				// e.printStackTrace();
				return true;
			}
		});
	}

	/*
	 * check blocks are in not in milestone
	 */
	private SolidityState checkReferencedBlockRequirements(Block newMilestoneBlock, long cutoffHeight,
			FullBlockStore store) throws BlockStoreException {

		RewardInfo currRewardInfo = new RewardInfo().parseChecked(newMilestoneBlock.getTransactions().get(0).getData());

		for (Sha256Hash hash : currRewardInfo.getBlocks()) {
			BlockWrap block = store.getBlockWrap(hash);
			if (block == null)
				return SolidityState.from(hash, true);
			if (block.getBlock().getHeight() <= cutoffHeight)
				throw new VerificationException("Referenced blocks are below cutoff height.");

			Set<Sha256Hash> requiredBlocks = getAllRequiredBlockHashes(block.getBlock(), false);
			for (Sha256Hash reqHash : requiredBlocks) {
				BlockWrap req = store.getBlockWrap(reqHash);
				if (req == null)
					return SolidityState.from(reqHash, true);

				if (req != null && req.getBlockEvaluation().getMilestone() < 0
						&& !currRewardInfo.getBlocks().contains(reqHash)) {
					// FIXME blocks problem with 4046309 throw new
					// VerificationException("Predecessors are not in
					// milestone." + req.toString());
				}
			}
		}

		return SolidityState.getSuccessState();
	}

	private void checkContainsNoRewardBlocks(Block newMilestoneBlock, FullBlockStore store) throws BlockStoreException {

		RewardInfo currRewardInfo = new RewardInfo().parseChecked(newMilestoneBlock.getTransactions().get(0).getData());
		for (Sha256Hash hash : currRewardInfo.getBlocks()) {
			BlockWrap block = store.getBlockWrap(hash);
			if (block.getBlock().getBlockType() == Type.BLOCKTYPE_REWARD)
				throw new VerificationException("Reward block approves other reward blocks");
		}
	}

	private void checkGeneratedReward(Block newMilestoneBlock, FullBlockStore store) throws BlockStoreException {

		RewardInfo currRewardInfo = new RewardInfo().parseChecked(newMilestoneBlock.getTransactions().get(0).getData());

		RewardBuilderResult result = makeReward(newMilestoneBlock.getPrevBlockHash(),
				newMilestoneBlock.getPrevBranchBlockHash(), currRewardInfo.getPrevRewardHash(),
				newMilestoneBlock.getTimeSeconds(), store);
		if (currRewardInfo.getDifficultyTargetReward() != result.getDifficulty()) {
			throw new VerificationException("Incorrect difficulty target");
		}

		OrderMatchingResult ordermatchresult = generateOrderMatching(newMilestoneBlock, store);

		// Only check the Hash of OrderMatchingResult
		if (!currRewardInfo.getOrdermatchingResult().equals(ordermatchresult.getOrderMatchingResultHash())) {
			// if(currRewardInfo.getChainlength()!=197096)
			// throw new VerificationException("OrderMatchingResult transactions output is
			// wrong.");
		}

		Transaction miningTx = generateVirtualMiningRewardTX(newMilestoneBlock, store);

		// Only check the Hash of OrderMatchingResult
		if (!currRewardInfo.getMiningResult().equals(miningTx.getHash())) {
			throw new VerificationException("generateVirtualMiningRewardTX transactions output is wrong.");
		}
	}

	public boolean solidifyWaiting(Block block, FullBlockStore store) throws BlockStoreException {

		SolidityState solidityState = checkSolidity(block, false, store, false);
		// allow here unsolid block, as sync may do only the referenced blocks
		if (SolidityState.State.MissingPredecessor.equals(solidityState.getState())) {
		solidifyBlock(block, SolidityState.getSuccessState(), false, store);
		} else {
			solidifyBlock(block, solidityState, false, store);
		}
		return true;
	}

	public SolidityState checkRewardDifficulty(Block rewardBlock, FullBlockStore store) throws BlockStoreException {
		RewardInfo rewardInfo = new RewardInfo().parseChecked(rewardBlock.getTransactions().get(0).getData());

		// Check previous reward blocks exist and get their approved sets
		Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
		if (prevRewardHash == null)
			throw new VerificationException("Missing previous reward block: " + prevRewardHash);

		Block prevRewardBlock = store.get(prevRewardHash);
		if (prevRewardBlock == null)
			return SolidityState.from(prevRewardHash, true);
		if (prevRewardBlock.getBlockType() != Type.BLOCKTYPE_REWARD
				&& prevRewardBlock.getBlockType() != Type.BLOCKTYPE_INITIAL)
			throw new VerificationException("Previous reward block is not reward block.");

		checkRewardDifficulty(rewardBlock, rewardInfo, prevRewardBlock, store);

		return SolidityState.getSuccessState();
	}

	public SolidityState checkRewardReferencedBlocks(Block rewardBlock, FullBlockStore store)
			throws BlockStoreException {
		try {
			RewardInfo rewardInfo = new RewardInfo().parseChecked(rewardBlock.getTransactions().get(0).getData());

			// Check previous reward blocks exist and get their approved sets
			Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
			if (prevRewardHash == null)
				throw new VerificationException("Missing previous block reference." + prevRewardHash);

			Block prevRewardBlock = store.get(prevRewardHash);
			if (prevRewardBlock == null)
				return SolidityState.from(prevRewardHash, true);
			if (prevRewardBlock.getBlockType() != Type.BLOCKTYPE_REWARD
					&& prevRewardBlock.getBlockType() != Type.BLOCKTYPE_INITIAL)
				throw new VerificationException("Previous reward block is not reward block.");

			// Get all blocks approved by previous reward blocks
			long cutoffHeight = getRewardCutoffHeight(prevRewardHash, store);

			for (Sha256Hash hash : rewardInfo.getBlocks()) {
				BlockWrap block = store.getBlockWrap(hash);
				if (block == null)
					return SolidityState.from(hash, true);
				if (block.getBlock().getHeight() <= cutoffHeight)
					throw new VerificationException("Referenced blocks are below cutoff height.");

				SolidityState requirementResult = checkRequiredBlocks(rewardInfo, block, store);
				if (!requirementResult.isSuccessState()) {
					return requirementResult;
				}
			}

		} catch (Exception e) {
			throw new VerificationException("checkRewardReferencedBlocks not completed:", e);
		}

		return SolidityState.getSuccessState();
	}

	/*
	 * check only if the blocks in database
	 */
	private SolidityState checkRequiredBlocks(RewardInfo rewardInfo, BlockWrap block, FullBlockStore store)
			throws BlockStoreException {
		Set<Sha256Hash> requiredBlocks = getAllRequiredBlockHashes(block.getBlock(), false);
		for (Sha256Hash reqHash : requiredBlocks) {
			BlockWrap req = store.getBlockWrap(reqHash);
			// the required block must be in this referenced blocks or in
			// milestone
			if (req == null) {
				return SolidityState.from(reqHash, true);
			}
		}

		return SolidityState.getSuccessState();
	}

	private void checkRewardDifficulty(Block rewardBlock, RewardInfo rewardInfo, Block prevRewardBlock,
			FullBlockStore store) throws BlockStoreException {

		// check difficulty
		Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
		long difficulty = calculateNextChainDifficulty(prevRewardHash, rewardInfo.getChainlength(),
				rewardBlock.getTimeSeconds(), store);

		if (difficulty != rewardInfo.getDifficultyTargetReward()) {
			throw new VerificationException("getDifficultyTargetReward does not match.");
		}

	}

	public long calculateNextChainDifficulty(Sha256Hash prevRewardHash, long currChainLength, long currentTime,
			FullBlockStore store) throws BlockStoreException {

		if (currChainLength % NetworkParameters.INTERVAL != 0) {
			return store.getRewardDifficulty(prevRewardHash);
		}

		// Get the block INTERVAL ago
		for (int i = 0; i < NetworkParameters.INTERVAL - 1; i++) {
			prevRewardHash = store.getRewardPrevBlockHash(prevRewardHash);
		}
		Block oldBlock = store.get(prevRewardHash);

		int timespan = (int) Math.max(1, (currentTime - oldBlock.getTimeSeconds()));
		long prevDifficulty = store.getRewardDifficulty(prevRewardHash);

		// Limit the adjustment step.
		int targetTimespan = NetworkParameters.TARGET_TIMESPAN;
		if (timespan < targetTimespan / 4)
			timespan = targetTimespan / 4;
		if (timespan > targetTimespan * 4)
			timespan = targetTimespan * 4;

		BigInteger newTarget = Utils.decodeCompactBits(prevDifficulty);
		newTarget = newTarget.multiply(BigInteger.valueOf(timespan));
		newTarget = newTarget.divide(BigInteger.valueOf(targetTimespan));

		if (newTarget.compareTo(networkParameters.getMaxTargetReward()) > 0) {
			// logger.info("Difficulty hit proof of work limit: {}",
			// newTarget.toString(16));
			newTarget = networkParameters.getMaxTargetReward();
		}

		if (prevDifficulty != (Utils.encodeCompactBits(newTarget))) {
			logger.info("Difficulty  change from {} to: {} and diff={}", prevDifficulty,
					Utils.encodeCompactBits(newTarget), prevDifficulty - Utils.encodeCompactBits(newTarget));

		}

		return Utils.encodeCompactBits(newTarget);
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

	private void synchronizationUserData(Sha256Hash blockhash, DataClassName dataClassName, byte[] data, String pubKey,
			long blocktype, FullBlockStore blockStore) throws BlockStoreException {
		UserData userData = blockStore.queryUserDataWithPubKeyAndDataclassname(dataClassName.name(), pubKey);
		if (userData == null) {
			userData = new UserData();
			userData.setBlockhash(blockhash);
			userData.setData(data);
			userData.setDataclassname(dataClassName.name());
			userData.setPubKey(pubKey);
			userData.setBlocktype(blocktype);
			blockStore.insertUserData(userData);
			return;
		}
		userData.setBlockhash(blockhash);
		userData.setData(data);
		blockStore.updateUserData(userData);
	}

	/**
	 * Adds the specified block and all approved blocks to the confirmed set. This
	 * will connect all transactions of the block by marking used UTXOs spent and
	 * adding new UTXOs to the db.
	 * 
	 * @param blockHash
	 * @param milestoneNumber
	 * @throws BlockStoreException
	 */
	public void confirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes, long milestoneNumber,
			FullBlockStore blockStore) throws BlockStoreException {
		confirm(blockHash, traversedBlockHashes, milestoneNumber, blockStore, false);
	}

	public void confirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes, long milestoneNumber,
			FullBlockStore blockStore, Boolean accountBalance) throws BlockStoreException {
		// If already confirmed, return
		if (traversedBlockHashes.contains(blockHash))
			return;

		BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
		BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();

		// If already confirmed, return
		if (blockEvaluation.isConfirmed())
			return;

		// Set confirmed, only if it is not confirmed
		if (!blockEvaluation.isConfirmed()) {
			blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), true);
		}
		blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), milestoneNumber);

		// Confirm the block
		confirmBlock(blockWrap, blockStore, accountBalance);

		// Keep track of confirmed blocks
		traversedBlockHashes.add(blockHash);
	}

	/**
	 * Calculates and inserts any virtual transaction outputs so dependees can
	 * become solid
	 * 
	 * @param block
	 * @return
	 * @throws BlockStoreException
	 */
	public Optional<OrderMatchingResult> calculateBlockOrderMatchingResult(Block block, FullBlockStore blockStore)
			throws BlockStoreException {

		Transaction tx = null;
		OrderMatchingResult matchingResult = null;

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
			tx = generateVirtualMiningRewardTX(block, blockStore);
			insertVirtualUTXOs(block, tx, blockStore);

			// Get list of consumed orders, virtual order matching tx and newly
			// generated remaining order book
			matchingResult = generateOrderMatching(block, blockStore);
			tx = matchingResult.getOutputTx();

			insertVirtualUTXOs(block, tx, blockStore);
			insertVirtualOrderRecords(block, matchingResult.getRemainingOrders(), blockStore);
			break;
		case BLOCKTYPE_TOKEN_CREATION:
			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_OPEN:
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		default:
			throw new RuntimeException("Not Implemented");

		}

		// Return the computation result
		return Optional.ofNullable(matchingResult);
	}

	private void confirmBlock(BlockWrap block, FullBlockStore blockStore, Boolean accountBalance)
			throws BlockStoreException {

		// Update block's transactions in db
		for (final Transaction tx : block.getBlock().getTransactions()) {
			confirmTransaction(block.getBlock(), tx, blockStore);
		}
		if (accountBalance)
			calculateAccount(block.getBlock(), blockStore);

		// type-specific updates
		switch (block.getBlock().getBlockType()) {
		case BLOCKTYPE_CROSSTANGLE:
			break;
		case BLOCKTYPE_FILE:
			break;
		case BLOCKTYPE_GOVERNANCE:
			break;
		case BLOCKTYPE_INITIAL:
			break;
		case BLOCKTYPE_REWARD:
			// For rewards, update reward to be confirmed now
			confirmReward(block, blockStore);
			confirmOrderMatching(block, blockStore);
			break;
		case BLOCKTYPE_TOKEN_CREATION:
			// For token creations, update token db
			confirmToken(block, blockStore);
			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			confirmVOSOrUserData(block, blockStore);
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			confirmContractEvent(block.getBlock(), blockStore);
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			confirmContractExecute(block.getBlock(), blockStore);
			break;
		case BLOCKTYPE_ORDER_OPEN:
			confirmOrderOpen(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		default:
			throw new RuntimeException("Not Implemented");

		}
	}

	private void confirmVOSOrUserData(BlockWrap block, FullBlockStore blockStore) {
		Transaction tx = block.getBlock().getTransactions().get(0);
		if (tx.getData() != null && tx.getDataSignature() != null) {
			try {
				@SuppressWarnings("unchecked")
				List<HashMap<String, Object>> multiSignBies = Json.jsonmapper().readValue(tx.getDataSignature(),
						List.class);
				Map<String, Object> multiSignBy = multiSignBies.get(0);
				byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
				byte[] data = tx.getHash().getBytes();
				byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
				boolean success = ECKey.verify(data, signature, pubKey);
				if (!success) {
					throw new BlockStoreException("multisign signature error");
				}
				synchronizationUserData(block.getBlock().getHash(), DataClassName.valueOf(tx.getDataClassName()),
						tx.getData(), (String) multiSignBy.get("publickey"), block.getBlock().getBlockType().ordinal(),
						blockStore);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void confirmOrderMatching(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {
		// Get list of consumed orders, virtual order matching tx and newly
		// generated remaining order book
		// TODO don't calculate again, it should already have been calculated
		// before
		OrderMatchingResult actualCalculationResult = generateOrderMatching(block.getBlock(), blockStore);

		// All consumed order records are now spent by this block

		for (OrderRecord o : actualCalculationResult.getSpentOrders()) {
			o.setSpent(true);
			o.setSpenderBlockHash(block.getBlock().getHash());
		}
		blockStore.updateOrderSpent(actualCalculationResult.getSpentOrders());

		// Set virtual outputs confirmed
		confirmVirtualCoinbaseTransaction(block, blockStore);

		// Set new orders confirmed

		blockStore.updateOrderConfirmed(actualCalculationResult.getRemainingOrders(), true);

		// Update the matching history in db
		addMatchingEvents(actualCalculationResult, actualCalculationResult.getOutputTx().getHashAsString(),
				block.getBlock().getTimeSeconds(), blockStore);
	}

	public void addMatchingEvents(OrderMatchingResult orderMatchingResult, String transactionHash, long matchBlockTime,
			FullBlockStore store) throws BlockStoreException {
		// collect the spend order volumes and ticker to write to database
		List<MatchResult> matchResultList = new ArrayList<MatchResult>();
		try {
			for (Entry<TradePair, List<Event>> entry : orderMatchingResult.getTokenId2Events().entrySet()) {
				for (Event event : entry.getValue()) {
					if (event instanceof Match) {
						MatchResult f = new MatchResult(transactionHash, entry.getKey().getOrderToken(),
								entry.getKey().getOrderBaseToken(), ((OrderBookEvents.Match) event).price,
								((OrderBookEvents.Match) event).executedQuantity, matchBlockTime);
						matchResultList.add(f);

					}
				}
			}
			if (!matchResultList.isEmpty())
				store.insertMatchingEvent(matchResultList);

		} catch (Exception e) {
			// this is analysis data and is not consensus relevant
		}
	}

	/*
	 * connect from the contract Execution
	 */
	public void confirmContractExecute(Block block, FullBlockStore blockStore) throws BlockStoreException {

		try {
			ContractResult result = new ContractResult().parse(block.getTransactions().get(0).getData());
			ContractResult check = new ServiceContract(serverConfiguration, networkParameters).executeContract(block,
					blockStore, result.getContracttokenid());
			if (check != null && result.getOutputTxHash().equals(check.getOutputTxHash())
					&& result.getSpentContractEventRecord().equals(check.getSpentContractEventRecord())) {
				blockStore.updateContractEventSpent(check.getSpentContractEventRecord(), block.getHash(), true);
				blockStore.updateContractResultConfirmed(result.getBlockHash(), true);
				blockStore.updateContractResultSpent(result.getPrevblockhash(), result.getBlockHash(), true);
				confirmTransaction(block, check.getOutputTx(), blockStore);
				// Set virtual outputs confirmed
			} else {
				throw new InvalidTransactionException(result.toString());
			}

			// blockStore.updateContractEvent( );
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void unConfirmContractExecute(Block block, FullBlockStore blockStore) throws BlockStoreException {

		try {
			ContractResult result = new ContractResult().parse(block.getTransactions().get(0).getData());
			blockStore.updateContractResultSpent(result.getBlockHash(), null, false);
			blockStore.updateContractEventSpent(result.getSpentContractEventRecord(), null, false);
			blockStore.updateContractResultConfirmed(result.getBlockHash(), false);
			blockStore.updateTransactionOutputConfirmed(block.getHash(), result.getOutputTxHash(), 0, false);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void confirmOrderOpen(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {
		// Set own output confirmed
		blockStore.updateOrderConfirmed(block.getBlock().getHash(), Sha256Hash.ZERO_HASH, true);
	}

	private void confirmReward(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {
		// Set virtual reward tx outputs confirmed
		confirmVirtualCoinbaseTransaction(block, blockStore);

		// Set used other output spent
		blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getBlock().getHash()), true,
				block.getBlock().getHash());

		// Set own output confirmed
		blockStore.updateRewardConfirmed(block.getBlock().getHash(), true);
	}

	private void insertVirtualOrderRecords(Block block, Collection<OrderRecord> orders, FullBlockStore blockStore) {
		try {

			blockStore.insertOrder(orders);

		} catch (BlockStoreException e) {
			// Expected after reorgs
			logger.warn("Probably reinserting orders: ", e);
		}
	}

	private void insertVirtualUTXOs(Block block, Transaction virtualTx, FullBlockStore blockStore) {
		try {
			ArrayList<Transaction> txs = new ArrayList<Transaction>();
			txs.add(virtualTx);
			connectUTXOs(block, txs, blockStore);
		} catch (BlockStoreException e) {
			// Expected after reorgs
			logger.warn("Probably reinserting reward: ", e);
		}
	}

	private void confirmToken(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {
		// Set used other output spent
		if (blockStore.getTokenPrevblockhash(block.getBlock().getHash()) != null)
			blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(block.getBlock().getHash()), true,
					block.getBlock().getHash());

		// Set own output confirmed
		blockStore.updateTokenConfirmed(block.getBlock().getHash(), true);
	}

	private void confirmContractEvent(Block block, FullBlockStore blockStore) throws BlockStoreException {

		// Set own output confirmed
		List<Sha256Hash> bs = new ArrayList<>();
		bs.add(block.getHash());
		blockStore.updateContractEventConfirmed(bs, true);
	}

	private void unConfirmContractEvent(Block block, FullBlockStore blockStore) throws BlockStoreException {

		// Set own output confirmed
		List<Sha256Hash> bs = new ArrayList<>();
		bs.add(block.getHash());
		blockStore.updateContractEventConfirmed(bs, false);
		// unconfirm the Contractexecution
		Sha256Hash result = blockStore.getContractEventSpent(block.getHash());
		if (result != null) {
			// unconfirm the contract result
			unConfirmContractExecute(getBlock(result, blockStore), blockStore);
		}
	}

	private void confirmTransaction(Block block, Transaction tx, FullBlockStore blockStore) throws BlockStoreException {
		// Set used other outputs spent
		if (!tx.isCoinBase()) {
			for (TransactionInput in : tx.getInputs()) {
				UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getBlockHash(),
						in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());

				// Sanity check
				if (prevOut == null)
					throw new RuntimeException("Attempted to spend a non-existent output!");
				// FIXME transaction check at connected if (prevOut.isSpent())
				// throw new RuntimeException("Attempted to spend an already spent output!");

				blockStore.updateTransactionOutputSpent(prevOut.getBlockHash(), prevOut.getTxHash(), prevOut.getIndex(),
						true, block.getHash());
			}
		}

		// Set own outputs confirmed
		for (TransactionOutput out : tx.getOutputs()) {
			blockStore.updateTransactionOutputConfirmed(block.getHash(), tx.getHash(), out.getIndex(), true);
		}
	}

	public void calculateAccount(Block block, FullBlockStore blockStore) throws BlockStoreException {
		List<UTXO> utxos = new ArrayList<UTXO>();
		for (final Transaction tx : block.getTransactions()) {
			boolean isCoinBase = tx.isCoinBase();
			for (TransactionOutput out : tx.getOutputs()) {
				Script script = getScript(out.getScriptBytes());
				String fromAddress = fromAddress(tx, isCoinBase);
				int minsignnumber = 1;

				UTXO newOut = new UTXO(tx.getHash(), out.getIndex(), out.getValue(), isCoinBase, script,
						getScriptAddress(script), block.getHash(), fromAddress, tx.getMemo(),
						Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, minsignnumber, 0,
						block.getTimeSeconds(), null);
				utxos.add(newOut);

			}

		}
		// calculate balance
		blockStore.calculateAccount(utxos);
	}

	private void confirmVirtualCoinbaseTransaction(BlockWrap block, FullBlockStore blockStore)
			throws BlockStoreException {
		// Set own outputs confirmed
		blockStore.updateAllTransactionOutputsConfirmed(block.getBlock().getHash(), true);
	}

	public void unconfirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes, FullBlockStore blockStore)
			throws BlockStoreException {
		unconfirm(blockHash, traversedBlockHashes, blockStore, false);
	}

	public void unconfirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes, FullBlockStore blockStore,
			Boolean accountBalance) throws BlockStoreException {
		// If already unconfirmed, return
		if (traversedBlockHashes.contains(blockHash))
			return;

		BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
		BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
		Block block = blockWrap.getBlock();

		// If already unconfirmed, return
		if (!blockEvaluation.isConfirmed())
			return;

		// Then unconfirm the block outputs
		unconfirmBlockOutputs(block, blockStore, accountBalance);

		// Set unconfirmed
		blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);
		blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), -1);

		// Keep track of unconfirmed blocks
		traversedBlockHashes.add(blockHash);
	}

	public void unconfirmRecursive(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes,
			FullBlockStore blockStore) throws BlockStoreException {
		// If already confirmed, return
		if (traversedBlockHashes.contains(blockHash))
			return;

		BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
		BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
		Block block = blockWrap.getBlock();

		// If already unconfirmed, return
		if (!blockEvaluation.isConfirmed())
			return;

		// Unconfirm all dependents
		unconfirmDependents(block, traversedBlockHashes, blockStore);

		// Then unconfirm the block itself
		unconfirmBlockOutputs(block, blockStore, true);

		// Set unconfirmed
		blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);
		blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), -1);

		// Keep track of unconfirmed blocks
		traversedBlockHashes.add(blockHash);
	}

	private void unconfirmDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes, FullBlockStore blockStore)
			throws BlockStoreException {
		// Unconfirm all approver blocks first
		for (Sha256Hash approver : blockStore.getSolidApproverBlockHashes(block.getHash())) {
			unconfirmRecursive(approver, traversedBlockHashes, blockStore);
		}

		// Disconnect all transaction output dependents
		for (Transaction tx : block.getTransactions()) {
			for (TransactionOutput txout : tx.getOutputs()) {
				UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
				if (utxo != null && utxo.isSpent()) {
					unconfirmRecursive(
							blockStore.getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex())
									.getBlockHash(),
							traversedBlockHashes, blockStore);
				}
			}
		}

		// Disconnect all type-specific dependents
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
			// Unconfirm dependents
			unconfirmRewardDependents(block, traversedBlockHashes, blockStore);
			unconfirmOrderMatchingDependents(block, traversedBlockHashes, blockStore);
			break;
		case BLOCKTYPE_TOKEN_CREATION:
			// Unconfirm dependents
			unconfirmTokenDependents(block, traversedBlockHashes, blockStore);
			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			unconfirmContractEventDependents(block, traversedBlockHashes, blockStore);
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_OPEN:
			unconfirmOrderOpenDependents(block, traversedBlockHashes, blockStore);
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		default:
			throw new RuntimeException("Not Implemented");

		}
	}

	private void unconfirmOrderMatchingDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
			FullBlockStore blockStore) throws BlockStoreException {
		// Get list of consumed orders, virtual order matching tx and newly
		// generated remaining order book
		OrderMatchingResult matchingResult = generateOrderMatching(block, blockStore);

		// Disconnect all virtual transaction output dependents
		Transaction tx = matchingResult.getOutputTx();
		for (TransactionOutput txout : tx.getOutputs()) {
			UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
			if (utxo != null && utxo.isSpent()) {
				unconfirmRecursive(blockStore
						.getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
						traversedBlockHashes, blockStore);
			}
		}
	}

	private void unconfirmOrderOpenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
			FullBlockStore blockStore) throws BlockStoreException {

		// Disconnect order record spender
		if (blockStore.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH)) {
			unconfirmRecursive(blockStore.getOrderSpender(block.getHash(), Sha256Hash.ZERO_HASH), traversedBlockHashes,
					blockStore);
		}
	}

	private void unconfirmContractEventDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
			FullBlockStore blockStore) throws BlockStoreException {
		Sha256Hash contractEventSpent = blockStore.getContractEventSpent(block.getHash());
		if (contractEventSpent != null) {
			unconfirmRecursive(contractEventSpent, traversedBlockHashes, blockStore);
		}
	}

	private void unconfirmTokenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
			FullBlockStore blockStore) throws BlockStoreException {

		// Disconnect token record spender
		if (blockStore.getTokenSpent(block.getHash())) {
			unconfirmRecursive(blockStore.getTokenSpender(block.getHashAsString()), traversedBlockHashes, blockStore);
		}

		// If applicable: Disconnect all domain definitions that were based on
		// this domain
		Token token = blockStore.getTokenByBlockHash(block.getHash());

		List<String> dependents = blockStore.getDomainDescendantConfirmedBlocks(token.getBlockHashHex());
		for (String b : dependents) {
			unconfirmRecursive(Sha256Hash.wrap(b), traversedBlockHashes, blockStore);

		}
	}

	private void unconfirmRewardDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
			FullBlockStore blockStore) throws BlockStoreException {

		// Disconnect reward record spender
		if (blockStore.getRewardSpent(block.getHash())) {
			unconfirmRecursive(blockStore.getRewardSpender(block.getHash()), traversedBlockHashes, blockStore);
		}

		// Disconnect all virtual transaction output dependents
		Transaction tx = generateVirtualMiningRewardTX(block, blockStore);
		for (TransactionOutput txout : tx.getOutputs()) {
			UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
			if (utxo != null && utxo.isSpent()) {
				unconfirmRecursive(blockStore
						.getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
						traversedBlockHashes, blockStore);
			}
		}
	}

	/**
	 * Disconnect the block, unconfirming all UTXOs and UTXO-like constructs.
	 * 
	 * @throws BlockStoreException if the block store had an underlying error or
	 *                             block does not exist in the block store at all.
	 */
	private void unconfirmBlockOutputs(Block block, FullBlockStore blockStore, Boolean accountBalance)
			throws BlockStoreException {
		// Unconfirm all transactions of the block
		for (Transaction tx : block.getTransactions()) {
			unconfirmTransaction(tx, block, blockStore);
		}
		if (accountBalance)
			calculateAccount(block, blockStore);
		// Then unconfirm type-specific stuff
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
			unconfirmReward(block, blockStore);
			unconfirmOrderMatching(block, blockStore);
			break;
		case BLOCKTYPE_TOKEN_CREATION:
			unconfirmToken(block, blockStore);
			break;
		case BLOCKTYPE_TRANSFER:
			break;
		case BLOCKTYPE_USERDATA:
			break;
		case BLOCKTYPE_CONTRACT_EVENT:
			unConfirmContractEvent(block, blockStore);
			break;
		case BLOCKTYPE_CONTRACT_EXECUTE:
			unConfirmContractExecute(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_OPEN:
			unconfirmOrderOpen(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		default:
			throw new RuntimeException("Not Implemented");

		}
	}

	private void unconfirmOrderMatching(Block block, FullBlockStore blockStore) throws BlockStoreException {
		// Get list of consumed orders, virtual order matching tx and newly
		// generated remaining order book
		OrderMatchingResult matchingResult = generateOrderMatching(block, blockStore);

		// All consumed order records are now unspent by this block
		Set<OrderRecord> updateOrder = new HashSet<OrderRecord>(matchingResult.getSpentOrders());
		for (OrderRecord o : updateOrder) {
			o.setSpent(false);
			o.setSpenderBlockHash(null);
		}
		blockStore.updateOrderSpent(updateOrder);

		// Set virtual outputs unconfirmed
		unconfirmVirtualCoinbaseTransaction(block, blockStore);

		blockStore.updateOrderConfirmed(matchingResult.getRemainingOrders(), false);

		// Update the matching history in db
		removeMatchingEvents(matchingResult.getOutputTx(), blockStore);
	}

	public void removeMatchingEvents(Transaction outputTx, FullBlockStore store) throws BlockStoreException {
		store.deleteMatchingEvents(outputTx.getHashAsString());
	}

	private void unconfirmOrderOpen(Block block, FullBlockStore blockStore) throws BlockStoreException {
		// Set own output unconfirmed

		blockStore.updateOrderConfirmed(block.getHash(), Sha256Hash.ZERO_HASH, false);
	}

	private void unconfirmReward(Block block, FullBlockStore blockStore) throws BlockStoreException {
		// Unconfirm virtual tx
		unconfirmVirtualCoinbaseTransaction(block, blockStore);

		// Set used other output unspent
		blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getHash()), false, null);

		// Set own output unconfirmed
		blockStore.updateRewardConfirmed(block.getHash(), false);
	}

	private void unconfirmToken(Block block, FullBlockStore blockStore) throws BlockStoreException {
		// Set used other output unspent
		if (blockStore.getTokenPrevblockhash(block.getHash()) != null)
			blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(block.getHash()), false, null);

		// Set own output unconfirmed
		blockStore.updateTokenConfirmed(block.getHash(), false);
	}

	/**
	 * Disconnects the UTXOs of the transaction
	 * 
	 * @param tx
	 * @param parentBlock
	 * @throws BlockStoreException
	 */
	private void unconfirmTransaction(Transaction tx, Block parentBlock, FullBlockStore blockStore)
			throws BlockStoreException {
		// Set used outputs as unspent
		if (!tx.isCoinBase()) {
			for (TransactionInput txin : tx.getInputs()) {
				blockStore.updateTransactionOutputSpent(txin.getOutpoint().getBlockHash(),
						txin.getOutpoint().getTxHash(), txin.getOutpoint().getIndex(), false, null);
			}
		}

		// Set own outputs unconfirmed
		for (TransactionOutput txout : tx.getOutputs()) {
			blockStore.updateTransactionOutputConfirmed(parentBlock.getHash(), tx.getHash(), txout.getIndex(), false);
		}
	}

	private void unconfirmVirtualCoinbaseTransaction(Block parentBlock, FullBlockStore blockStore)
			throws BlockStoreException {
		// Set own outputs unconfirmed
		blockStore.updateAllTransactionOutputsConfirmed(parentBlock.getHash(), false);
	}

	public void solidifyBlock(Block block, SolidityState solidityState, boolean setMilestoneSuccess,
			FullBlockStore blockStore) throws BlockStoreException {
		if (block.getBlockType() == Type.BLOCKTYPE_ORDER_OPEN) {
			// logger.debug(block.toString());
		}
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
			calculateBlockOrderMatchingResult(block, blockStore);

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

			}
			blockStore.addUnspentTransactionOutput(utxos);
			// calculate balance
			// TODO blockStore.calculateAccount(utxos);
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
			connectContractExecute(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_OPEN:
			connectOrder(block, blockStore);
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

	public void connectOrder(Block block, FullBlockStore blockStore) throws BlockStoreException {
		try {
			OrderOpenInfo reqInfo = new OrderOpenInfo().parse(block.getTransactions().get(0).getData());
			// calculate the offervalue for version == 1
			if (reqInfo.getVersion() == 1) {
				Coin burned = countBurnedToken(block, blockStore);
				reqInfo.setOfferValue(burned.getValue().longValue());
				reqInfo.setOfferTokenid(burned.getTokenHex());
			}
			boolean buy = reqInfo.buy();
			Side side = buy ? Side.BUY : Side.SELL;
			int decimals = 0;
			if (buy) {
				decimals = blockStore.getTokenID(reqInfo.getTargetTokenid()).get(0).getDecimals();
			} else {
				decimals = blockStore.getTokenID(reqInfo.getOfferTokenid()).get(0).getDecimals();
			}
			OrderRecord record = new OrderRecord(block.getHash(), Sha256Hash.ZERO_HASH, reqInfo.getOfferValue(),
					reqInfo.getOfferTokenid(), false, false, null, reqInfo.getTargetValue(), reqInfo.getTargetTokenid(),
					reqInfo.getBeneficiaryPubKey(), reqInfo.getValidToTime(), reqInfo.getValidFromTime(), side.name(),
					reqInfo.getBeneficiaryAddress(), reqInfo.getOrderBaseToken(), reqInfo.getPrice(), decimals);
			versionPrice(record, reqInfo);
			List<OrderRecord> orders = new ArrayList<OrderRecord>();
			orders.add(record);
			blockStore.insertOrder(orders);
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

			ContractEventRecord record = new ContractEventRecord(block.getHash(), reqInfo.getContractTokenid(), false,
					false, null, reqInfo.getOfferValue(), reqInfo.getOfferTokenid(), reqInfo.getValidToTime(),
					reqInfo.getValidFromTime(), reqInfo.getBeneficiaryAddress());
			List<ContractEventRecord> events = new ArrayList<ContractEventRecord>();
			events.add(record);
			blockStore.insertContractEvent(events);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void connectContractExecute(Block block, FullBlockStore blockStore) throws BlockStoreException {
		try {
			ContractResult result = new ContractResult().parse(block.getTransactions().get(0).getData());
			ContractResult check = new ServiceContract(serverConfiguration, networkParameters).executeContract(block,
					blockStore, result.getContracttokenid());
			if (check != null && result.getOutputTxHash().equals(check.getOutputTxHash())
					&& result.getSpentContractEventRecord().equals(check.getSpentContractEventRecord())) {
				blockStore.insertContractResult(result);
				insertVirtualUTXOs(block, check.getOutputTx(), blockStore);
				// Set virtual outputs confirmed
			} else {
				throw new InvalidTransactionException(result.toString());
			}
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

	private void insertIntoOrderBooks(OrderRecord o, TreeMap<TradePair, OrderBook> orderBooks,
			ArrayList<OrderRecord> orderId2Order, long orderId, FullBlockStore blockStore) throws BlockStoreException {

		Side side = o.getSide();
		// must be in in base unit ;

		long price = o.getPrice();
		if (price <= 0)
			logger.warn(" price is wrong " + price);
		// throw new RuntimeException(" price is wrong " +price);
		String tradetokenId = o.getOfferTokenid().equals(o.getOrderBaseToken()) ? o.getTargetTokenid()
				: o.getOfferTokenid();

		long size = o.getOfferTokenid().equals(o.getOrderBaseToken()) ? o.getTargetValue() : o.getOfferValue();

		TradePair tokenPaar = new TradePair(tradetokenId, o.getOrderBaseToken());

		OrderBook orderBook = orderBooks.get(tokenPaar);
		if (orderBook == null) {
			orderBook = new OrderBook(new OrderBookEvents());
			orderBooks.put(tokenPaar, orderBook);
		}
		orderId2Order.add(o);
		orderBook.enter(orderId, side, price, size);
	}

	/**
	 * Deterministically execute the order matching algorithm on this block.
	 * 
	 * @return new consumed orders, virtual order matching tx and newly generated
	 *         remaining MODIFIED order book
	 * @throws BlockStoreException
	 */
	public OrderMatchingResult generateOrderMatching(Block rewardBlock, FullBlockStore blockStore)
			throws BlockStoreException {

		RewardInfo rewardInfo = new RewardInfo().parseChecked(rewardBlock.getTransactions().get(0).getData());

		return generateOrderMatching(rewardBlock, rewardInfo, blockStore);
	}

	public OrderMatchingResult generateOrderMatching(Block block, RewardInfo rewardInfo, FullBlockStore blockStore)
			throws BlockStoreException {
		TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts = new TreeMap<>();

		// Get previous order matching block
		Sha256Hash prevHash = rewardInfo.getPrevRewardHash();
		Set<Sha256Hash> collectedBlocks = rewardInfo.getBlocks();
		final Block prevMatchingBlock = blockStore.getBlockWrap(prevHash).getBlock();

		// Deterministic randomization
		byte[] randomness = Utils.xor(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes());

		// Collect all orders approved by this block in the interval
		List<OrderCancelInfo> cancels = new ArrayList<>();
		Map<Sha256Hash, OrderRecord> sortedNewOrders = new TreeMap<>(
				Comparator.comparing(hash -> Sha256Hash.wrap(Utils.xor(((Sha256Hash) hash).getBytes(), randomness))));
		HashMap<Sha256Hash, OrderRecord> remainingOrders = blockStore.getOrderMatchingIssuedOrders(prevHash);
		Set<OrderRecord> toBeSpentOrders = new HashSet<>();
		Set<OrderRecord> cancelledOrders = new HashSet<>();
		for (OrderRecord r : remainingOrders.values()) {
			toBeSpentOrders.add(OrderRecord.cloneOrderRecord(r));
		}
		collectOrdersWithCancel(block, collectedBlocks, cancels, sortedNewOrders, toBeSpentOrders, blockStore);
		// sort order for execute in deterministic randomness
		Map<Sha256Hash, OrderRecord> sortedOldOrders = new TreeMap<>(
				Comparator.comparing(hash -> Sha256Hash.wrap(Utils.xor(((Sha256Hash) hash).getBytes(), randomness))));
		sortedOldOrders.putAll(remainingOrders);
		remainingOrders.putAll(sortedNewOrders);

		// Issue timeout cancels, set issuing order blockhash
		setIssuingBlockHash(block, remainingOrders);
		timeoutOrdersToCancelled(block, remainingOrders, cancelledOrders);
		cancelOrderstoCancelled(cancels, remainingOrders, cancelledOrders);

		// Remove the now cancelled orders from rest of orders
		for (OrderRecord c : cancelledOrders) {
			remainingOrders.remove(c.getBlockHash());
			sortedOldOrders.remove(c.getBlockHash());
			sortedNewOrders.remove(c.getBlockHash());
		}

		// Add to proceeds all cancelled orders going back to the beneficiary
		payoutCancelledOrders(payouts, cancelledOrders);

		// From all orders and ops, begin order matching algorithm by filling
		// order books
		int orderId = 0;
		ArrayList<OrderRecord> orderId2Order = new ArrayList<>();
		TreeMap<TradePair, OrderBook> orderBooks = new TreeMap<TradePair, OrderBook>();

		// Add old orders first without not valid yet
		for (OrderRecord o : sortedOldOrders.values()) {
			if (o.isValidYet(block.getTimeSeconds()) && o.isValidYet(prevMatchingBlock.getTimeSeconds()))
				insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
		}

		// Now orders not valid before but valid now
		for (OrderRecord o : sortedOldOrders.values()) {
			if (o.isValidYet(block.getTimeSeconds()) && !o.isValidYet(prevMatchingBlock.getTimeSeconds()))
				insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
		}

		// Now new orders that are valid yet
		for (OrderRecord o : sortedNewOrders.values()) {
			if (o.isValidYet(block.getTimeSeconds()))
				insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
		}

		// Collect and process all matching events
		Map<TradePair, List<Event>> tokenId2Events = new HashMap<>();
		for (Entry<TradePair, OrderBook> orderBook : orderBooks.entrySet()) {
			processOrderBook(payouts, remainingOrders, orderId2Order, tokenId2Events, orderBook);
		}

		for (OrderRecord o : remainingOrders.values()) {
			o.setDefault();
		}

		// Make deterministic tx with proceeds
		Transaction tx = createOrderPayoutTransaction(block, payouts);
		// logger.debug(tx.toString());
		return new OrderMatchingResult(toBeSpentOrders, tx, remainingOrders.values(), tokenId2Events);
	}

	public Transaction createOrderPayoutTransaction(Block block,
			TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts) {
		Transaction tx = new Transaction(networkParameters);
		for (Entry<ByteBuffer, TreeMap<String, BigInteger>> payout : payouts.entrySet()) {
			byte[] beneficiaryPubKey = payout.getKey().array();

			for (Entry<String, BigInteger> tokenProceeds : payout.getValue().entrySet()) {
				String tokenId = tokenProceeds.getKey();
				BigInteger proceedsValue = tokenProceeds.getValue();

				if (proceedsValue.signum() != 0)
					tx.addOutput(new Coin(proceedsValue, tokenId), ECKey.fromPublicOnly(beneficiaryPubKey));
			}
		}

		// The coinbase input does not really need to be a valid signature
		TransactionInput input = new TransactionInput(networkParameters, tx, Script
				.createInputScript(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
		tx.addInput(input);
		tx.setMemo(new MemoInfo("Order Payout"));

		return tx;
	}

	private void processOrderBook(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
			HashMap<Sha256Hash, OrderRecord> remainingOrders, ArrayList<OrderRecord> orderId2Order,
			Map<TradePair, List<Event>> tokenId2Events, Entry<TradePair, OrderBook> orderBook)
			throws BlockStoreException {
		String orderBaseToken = orderBook.getKey().getOrderBaseToken();

		List<Event> events = ((OrderBookEvents) orderBook.getValue().listener()).collect();

		for (Event event : events) {
			if (!(event instanceof Match))
				continue;

			Match matchEvent = (Match) event;
			OrderRecord restingOrder = orderId2Order.get(Integer.parseInt(matchEvent.restingOrderId));
			OrderRecord incomingOrder = orderId2Order.get(Integer.parseInt(matchEvent.incomingOrderId));
			byte[] restingPubKey = restingOrder.getBeneficiaryPubKey();
			byte[] incomingPubKey = incomingOrder.getBeneficiaryPubKey();

			// Now disburse proceeds accordingly
			long executedPrice = matchEvent.price;
			long executedAmount = matchEvent.executedQuantity;
			// if
			// (!orderBook.getKey().getOrderToken().equals(incomingOrder.getTargetTokenid()))

			if (matchEvent.incomingSide == Side.BUY) {
				processIncomingBuy(payouts, remainingOrders, orderBaseToken, restingOrder, incomingOrder, restingPubKey,
						incomingPubKey, executedPrice, executedAmount);
			} else {
				processIncomingSell(payouts, remainingOrders, orderBaseToken, restingOrder, incomingOrder,
						restingPubKey, incomingPubKey, executedPrice, executedAmount);

			}
		}
		tokenId2Events.put(orderBook.getKey(), events);
	}

	private void processIncomingSell(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
			HashMap<Sha256Hash, OrderRecord> remainingOrders, String baseToken, OrderRecord restingOrder,
			OrderRecord incomingOrder, byte[] restingPubKey, byte[] incomingPubKey, long executedPrice,
			long executedAmount) {
		long sellableAmount = incomingOrder.getOfferValue();
		long buyableAmount = restingOrder.getTargetValue();
		long incomingPrice = incomingOrder.getPrice();
		Integer priceshift = networkParameters.getOrderPriceShift(baseToken);
		// The resting order receives the tokens
		payout(payouts, restingPubKey, restingOrder.getTargetTokenid(), executedAmount);

		// The incoming order receives the base token according to the
		// resting price
		payout(payouts, incomingPubKey, baseToken,
				totalAmount(executedAmount, executedPrice, incomingOrder.getTokenDecimals() + priceshift));

		// Finally, the orders could be fulfilled now, so we can
		// remove them from the order list
		// Otherwise, we will make the orders smaller by the
		// executed amounts
		incomingOrder.setOfferValue(incomingOrder.getOfferValue() - executedAmount);
		incomingOrder.setTargetValue(incomingOrder.getTargetValue()
				- totalAmount(executedAmount, incomingPrice, incomingOrder.getTokenDecimals() + priceshift));
		restingOrder.setOfferValue(restingOrder.getOfferValue()
				- totalAmount(executedAmount, executedPrice, restingOrder.getTokenDecimals() + priceshift));
		restingOrder.setTargetValue(restingOrder.getTargetValue() - executedAmount);
		if (sellableAmount == executedAmount) {
			remainingOrders.remove(incomingOrder.getBlockHash());
		}
		if (buyableAmount == executedAmount) {
			remainingOrders.remove(restingOrder.getBlockHash());
		}
	}

	private void processIncomingBuy(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
			HashMap<Sha256Hash, OrderRecord> remainingOrders, String baseToken, OrderRecord restingOrder,
			OrderRecord incomingOrder, byte[] restingPubKey, byte[] incomingPubKey, long executedPrice,
			long executedAmount) {
		long sellableAmount = restingOrder.getOfferValue();
		long buyableAmount = incomingOrder.getTargetValue();
		long incomingPrice = incomingOrder.getPrice();

		// The resting order receives the basetoken according to its price
		// resting is sell order
		Integer priceshift = networkParameters.getOrderPriceShift(baseToken);
		payout(payouts, restingPubKey, baseToken,
				totalAmount(executedAmount, executedPrice, restingOrder.getTokenDecimals() + priceshift));

		// The incoming order receives the tokens
		payout(payouts, incomingPubKey, incomingOrder.getTargetTokenid(), executedAmount);

		// The difference in price is returned to the incoming
		// beneficiary
		payout(payouts, incomingPubKey, baseToken, totalAmount(executedAmount, (incomingPrice - executedPrice),
				incomingOrder.getTokenDecimals() + priceshift));

		// Finally, the orders could be fulfilled now, so we can
		// remove them from the order list
		restingOrder.setOfferValue(restingOrder.getOfferValue() - executedAmount);
		restingOrder.setTargetValue(restingOrder.getTargetValue()
				- totalAmount(executedAmount, executedPrice, restingOrder.getTokenDecimals() + priceshift));
		incomingOrder.setOfferValue(incomingOrder.getOfferValue()
				- totalAmount(executedAmount, incomingPrice, incomingOrder.getTokenDecimals() + priceshift));
		incomingOrder.setTargetValue(incomingOrder.getTargetValue() - executedAmount);
		if (sellableAmount == executedAmount) {
			remainingOrders.remove(restingOrder.getBlockHash());
		}
		if (buyableAmount == executedAmount) {
			remainingOrders.remove(incomingOrder.getBlockHash());
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
			// logger.debug("Price and quantity value with remainder " + remainder
			// + "/"
			// + BigInteger.valueOf(LongMath.checkedPow(10, tokenDecimal)));
		}

		if (re.compareTo(BigInteger.ZERO) < 0 || re.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
			throw new InvalidTransactionDataException("Invalid target total value: " + re);
		}
		return re.longValue();
	}

	private void payoutCancelledOrders(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
			Set<OrderRecord> cancelledOrders) {
		for (OrderRecord o : cancelledOrders) {
			byte[] beneficiaryPubKey = o.getBeneficiaryPubKey();
			String offerTokenid = o.getOfferTokenid();
			long offerValue = o.getOfferValue();

			payout(payouts, beneficiaryPubKey, offerTokenid, offerValue);
		}
	}

	protected void payout(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts, byte[] beneficiaryPubKey,
			String tokenid, long tokenValue) {
		TreeMap<String, BigInteger> proceeds = payouts.get(ByteBuffer.wrap(beneficiaryPubKey));
		if (proceeds == null) {
			proceeds = new TreeMap<>();
			payouts.put(ByteBuffer.wrap(beneficiaryPubKey), proceeds);
		}
		BigInteger offerTokenProceeds = proceeds.get(tokenid);
		if (offerTokenProceeds == null) {
			offerTokenProceeds = BigInteger.ZERO;
			proceeds.put(tokenid, offerTokenProceeds);
		}
		proceeds.put(tokenid, offerTokenProceeds.add(BigInteger.valueOf(tokenValue)));
	}

	private void cancelOrderstoCancelled(List<OrderCancelInfo> cancels,
			HashMap<Sha256Hash, OrderRecord> remainingOrders, Set<OrderRecord> cancelledOrders) {
		for (OrderCancelInfo c : cancels) {
			if (remainingOrders.containsKey(c.getBlockHash())) {
				cancelledOrders.add(remainingOrders.get(c.getBlockHash()));
			}
		}
	}

	private void setIssuingBlockHash(Block block, HashMap<Sha256Hash, OrderRecord> remainingOrders) {
		Iterator<Entry<Sha256Hash, OrderRecord>> it = remainingOrders.entrySet().iterator();
		while (it.hasNext()) {
			OrderRecord order = it.next().getValue();
			order.setIssuingMatcherBlockHash(block.getHash());
		}
	}

	private void timeoutOrdersToCancelled(Block block, HashMap<Sha256Hash, OrderRecord> remainingOrders,
			Set<OrderRecord> cancelledOrders) {
		// Issue timeout cancels, set issuing order blockhash
		Iterator<Entry<Sha256Hash, OrderRecord>> it = remainingOrders.entrySet().iterator();
		while (it.hasNext()) {
			OrderRecord order = it.next().getValue();
			if (order.isTimeouted(block.getTimeSeconds())) {
				cancelledOrders.add(order);
			}
		}
	}

	private void collectOrdersWithCancel(Block block, Set<Sha256Hash> collectedBlocks, List<OrderCancelInfo> cancels,
			Map<Sha256Hash, OrderRecord> newOrders, Set<OrderRecord> spentOrders, FullBlockStore blockStore)
			throws BlockStoreException {
		for (Sha256Hash bHash : collectedBlocks) {
			BlockWrap b = blockStore.getBlockWrap(bHash);
			if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OPEN) {

				OrderRecord order = blockStore.getOrder(b.getBlock().getHash(), Sha256Hash.ZERO_HASH);
				// order is null, write it to
				if (order == null) {
					if (b.getBlockEvaluation().getSolid() > 0) {
						connectUTXOs(b.getBlock(), blockStore);
						connectTypeSpecificUTXOs(b.getBlock(), blockStore);
						order = blockStore.getOrder(b.getBlock().getHash(), Sha256Hash.ZERO_HASH);
					}
				}
				if (order != null) {
					newOrders.put(b.getBlock().getHash(), OrderRecord.cloneOrderRecord(order));
					spentOrders.add(order);
				}
			} else if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_CANCEL) {
				OrderCancelInfo info = new OrderCancelInfo()
						.parseChecked(b.getBlock().getTransactions().get(0).getData());
				cancels.add(info);
			}
		}
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
		for (BlockEvaluation block : blocksToRemove) {

			try {
				blockStore.beginDatabaseBatchWrite();
				unconfirm(block.getBlockHash(), traversedUnconfirms, blockStore);
				blockStore.commitDatabaseBatchWrite();
			} catch (Exception e) {
				blockStore.abortDatabaseBatchWrite();
				throw e;
			} finally {
				blockStore.defaultDatabaseBatchWrite();
			}
		}
		// TXReward maxConfirmedReward = blockStore.getMaxConfirmedReward();
		long cutoffHeight = getCurrentCutoffHeight(maxConfirmedReward, blockStore);
		long maxHeight = getCurrentMaxHeight(maxConfirmedReward, blockStore);

		// Now try to find blocks that can be added to the milestone.
		// DISALLOWS UNSOLID
		TreeSet<BlockWrap> blocksToAdd = blockStore.getBlocksToConfirm(cutoffHeight, maxHeight);

		// VALIDITY CHECKS
		resolveAllConflicts(blocksToAdd, cutoffHeight, blockStore);

		// Finally add the resolved new blocks to the confirmed set
		HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
		for (BlockWrap block : blocksToAdd) {
			try {
				blockStore.beginDatabaseBatchWrite();
				confirm(block.getBlockEvaluation().getBlockHash(), traversedConfirms, (long) -1, blockStore);
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

	public PermissionedAddressesResponse queryDomainnameTokenPermissionedAddresses(String domainNameBlockHash,
			FullBlockStore store) throws BlockStoreException {
		if (domainNameBlockHash.equals(networkParameters.getGenesisBlock().getHashAsString())) {
			List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
			for (Iterator<PermissionDomainname> iterator = networkParameters.getPermissionDomainnameList()
					.iterator(); iterator.hasNext();) {
				PermissionDomainname permissionDomainname = iterator.next();
				ECKey ecKey = permissionDomainname.getOutKey();
				multiSignAddresses.add(new MultiSignAddress("", "", ecKey.getPublicKeyAsHex()));
			}
			PermissionedAddressesResponse response = (PermissionedAddressesResponse) PermissionedAddressesResponse
					.create("", false, multiSignAddresses);
			return response;
		} else {
			Token token = store.getTokenByBlockHash(Sha256Hash.wrap(domainNameBlockHash));
			final String domainName = token.getTokenname();

			List<MultiSignAddress> multiSignAddresses = this
					.queryDomainnameTokenMultiSignAddresses(token.getBlockHash(), store);

			PermissionedAddressesResponse response = (PermissionedAddressesResponse) PermissionedAddressesResponse
					.create(domainName, false, multiSignAddresses);
			return response;
		}
	}

	/**
	 * get domainname token multi sign address
	 * 
	 * @param domainNameBlockHash
	 * @return
	 * @throws BlockStoreException
	 */
	public List<MultiSignAddress> queryDomainnameTokenMultiSignAddresses(Sha256Hash domainNameBlockHash,
			FullBlockStore store) throws BlockStoreException {
		if (domainNameBlockHash.equals(networkParameters.getGenesisBlock().getHash())) {
			List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
			for (Iterator<PermissionDomainname> iterator = networkParameters.getPermissionDomainnameList()
					.iterator(); iterator.hasNext();) {
				PermissionDomainname permissionDomainname = iterator.next();
				ECKey ecKey = permissionDomainname.getOutKey();
				multiSignAddresses.add(new MultiSignAddress("", "", ecKey.getPublicKeyAsHex()));
			}
			return multiSignAddresses;
		} else {
			Token token = store.queryDomainnameToken(domainNameBlockHash);
			if (token == null)
				throw new BlockStoreException("token not found");

			final String tokenid = token.getTokenid();
			List<MultiSignAddress> multiSignAddresses = store.getMultiSignAddressListByTokenidAndBlockHashHex(tokenid,
					token.getBlockHash());
			return multiSignAddresses;
		}
	}

}
