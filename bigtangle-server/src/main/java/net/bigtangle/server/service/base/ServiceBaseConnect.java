/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.base;

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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.common.math.LongMath;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ContractEventCancel;
import net.bigtangle.core.ContractEventCancelInfo;
import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSignAddress;
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
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.CutoffException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.core.ordermatch.OrderBookEvents;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;
import net.bigtangle.core.ordermatch.OrderBookEvents.Match;
import net.bigtangle.core.ordermatch.TradePair;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.core.response.PermissionedAddressesResponse;
import net.bigtangle.script.Script;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.core.ConflictCandidate;
import net.bigtangle.server.data.ContractEventRecord;
import net.bigtangle.server.data.ContractResult;
import net.bigtangle.server.data.OrderExecutionResult;
import net.bigtangle.server.data.OrderMatchingResult;
import net.bigtangle.server.data.SolidityState;
import net.bigtangle.server.service.CacheBlockService;
import net.bigtangle.server.utils.OrderBook;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Json;

public class ServiceBaseConnect extends ServiceBase {

	private static final Logger logger = LoggerFactory.getLogger(ServiceBaseConnect.class);

	public ServiceBaseConnect(ServerConfiguration serverConfiguration, NetworkParameters networkParameters,
			CacheBlockService cacheBlockService) {
		super(serverConfiguration, networkParameters, cacheBlockService);

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
					BlockWrap pred =  getBlockWrap(req,store);
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
					BlockWrap pred = getBlockWrap(req,store);
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
			long cutoffHeight, long prevMilestoneNumber, boolean throwException, List<Block.Type> blocktypes,
			FullBlockStore store) throws BlockStoreException {

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

			// Add this block and recursive referenced.
			if (blocktypes == null) {
				blocks.add(block.getBlockHash());
			} else {
				for (Block.Type type : blocktypes) {
					if (type.equals(block.getBlock().getBlockType())) {
						blocks.add(block.getBlockHash());
						// contract execution add all referenced blocks
						if (Block.Type.BLOCKTYPE_CONTRACT_EXECUTE.equals(block.getBlock().getBlockType())
								|| Block.Type.BLOCKTYPE_ORDER_EXECUTE.equals(block.getBlock().getBlockType())) {
							blocks.addAll(getReferrencedBlockHashes(block.getBlock()));
						}
					}
				}
			}

			// Queue all of its required blocks if not already queued.
			for (Sha256Hash req : getAllRequiredBlockHashes(block.getBlock(), false)) {
				if (!blockQueueSet.contains(req)) {
					BlockWrap pred =  getBlockWrap(req,store);
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
					BlockWrap pred =  getBlockWrap(req,store);
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


	public boolean getUTXOSpent(TransactionOutPoint txout, FullBlockStore store) throws BlockStoreException {
		UTXO a = store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
		// the TransactionOutPoint does not exist, try do the calculation
		if (a == null) {
			solidifyWaiting(getBlock(txout.getBlockHash(), store), store);
			a = store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
		}
		// the TransactionOutPoint does not exist, the conflict would be ok, but it will
		// not confirmed later.
		if (a == null)
			return false;
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
		case ORDEREXECUTE:
			final OrderExecutionResult connectedOrderExecute = c.getConflictPoint().getConnectedOrderExecute();
			if (connectedOrderExecute.getPrevblockhash().equals(networkParameters.getGenesisBlock().getHash())) {
				return false;
			} else {
				return store.checkContractResultSpent(connectedOrderExecute.getPrevblockhash()) != null;
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
			if (connectedContracExecute.getPrevblockhash().equals(Sha256Hash.ZERO_HASH)) {
				return true;
			} else {
				return store.checkContractResultConfirmed(connectedContracExecute.getPrevblockhash());
			}
		case ORDEREXECUTE:
			final OrderExecutionResult connectedOrderExecute = c.getConflictPoint().getConnectedOrderExecute();
			if (connectedOrderExecute.getPrevblockhash().equals(Sha256Hash.ZERO_HASH)) {
				return true;
			} else {
				return store.checkOrderResultConfirmed(connectedOrderExecute.getPrevblockhash());
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
			List<BlockWrap> allRequirements = getAllBlocks(b.getBlock(), getAllRequiredBlockHashes(b.getBlock(), true),
					store);
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
			return getBlockWrap(utxoSpender.getBlockHash(),store);
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
			return  getBlockWrap(txRewardSpender,store);
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
			return  getBlockWrap(t,store);
		case ORDEREXECUTE:
			final OrderExecutionResult connectedOrderExecute = c.getConflictPoint().getConnectedOrderExecute();
			Sha256Hash spent = connectedOrderExecute.getSpenderBlockHash();
			if (spent == null)
				return null;
			return  getBlockWrap(spent,store);
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

	public Set<Sha256Hash> getMissingPredecessors(Block block, FullBlockStore store) throws BlockStoreException {
		Set<Sha256Hash> missingPredecessorBlockHashes = new HashSet<>();
		final Set<Sha256Hash> allPredecessorBlockHashes = getAllRequiredBlockHashes(block, false);
		for (Sha256Hash predecessorReq : allPredecessorBlockHashes) {
			Block pred = getBlock(predecessorReq,store);
			if (pred == null)
				missingPredecessorBlockHashes.add(predecessorReq);

		}
		return missingPredecessorBlockHashes;
	}

	public GetTXRewardResponse getMaxConfirmedReward(Map<String, Object> request, FullBlockStore store)
			throws BlockStoreException {

		return GetTXRewardResponse.create(cacheBlockService.getMaxConfirmedReward(store));

	}

	public GetTXRewardListResponse getAllConfirmedReward(Map<String, Object> request, FullBlockStore store)
			throws BlockStoreException {

		return GetTXRewardListResponse.create(store.getAllConfirmedReward());

	}

	protected void showConflict(HashSet<BlockWrap> allApprovedNewBlocks) {
		List<List<ConflictCandidate>> candidateConflicts = allApprovedNewBlocks.stream()
				.map(b -> b.toConflictCandidates()).flatMap(i -> i.stream())
				.collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream().filter(l -> l.size() > 1)
				.collect(Collectors.toList());
		for (List<ConflictCandidate> l : candidateConflicts) {
			for (ConflictCandidate c : l) {
				logger.debug(" conflict list: " + c.toString());
			}
		}
	}

	public void solidifyBlocks(RewardInfo currRewardInfo, FullBlockStore store) throws BlockStoreException {
		Comparator<Block> comparator = Comparator.comparingLong((Block b) -> b.getHeight())
				.thenComparing((Block b) -> b.getHash());
		TreeSet<Block> referencedBlocks = new TreeSet<Block>(comparator);
		for (Sha256Hash hash : currRewardInfo.getBlocks()) {
			Block block = getBlock(hash, store);
			if (block != null)
				referencedBlocks.add(block);
		}
		for (Block block : referencedBlocks) {
			solidifyWaiting(block, store);
		}
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

	public boolean solidifyWaiting(Block block, FullBlockStore store) throws BlockStoreException {

		SolidityState solidityState = new ServiceBaseCheck(serverConfiguration, networkParameters, cacheBlockService)
				.checkSolidity(block, false, store, false);
		// allow here unsolid block, as sync may do only the referenced blocks
		if (SolidityState.State.MissingPredecessor.equals(solidityState.getState())) {
			solidifyBlock(block, SolidityState.getSuccessState(), false, store);
		} else {
			solidifyBlock(block, solidityState, false, store);
		}
		return true;
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
			FullBlockStore store ) throws BlockStoreException {
		// If already confirmed, return
		if (traversedBlockHashes.contains(blockHash))
			return;

		BlockWrap blockWrap =  getBlockWrap(blockHash,store);
		BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();

		// If already confirmed, return
		if (blockEvaluation.isConfirmed())
			return;

		// Set confirmed, only if it is not confirmed
		if (!blockEvaluation.isConfirmed()) {
			store.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), true);
		}
		store.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), milestoneNumber);

		// Confirm the block
		confirmBlock(blockWrap, store );
		evictTransactions(blockWrap.getBlock(), store);
 
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
			if (!enableFee(block)) {
				matchingResult = generateOrderMatching(block, blockStore);
				tx = matchingResult.getOutputTx();
				insertVirtualUTXOs(block, tx, blockStore);
				insertVirtualOrderRecords(block, matchingResult.getRemainingOrders(), blockStore);
			}
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
		case BLOCKTYPE_ORDER_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_OPEN:
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		case BLOCKTYPE_CONTRACTEVENT_CANCEL:
			break;
		default:
			throw new RuntimeException("Not Implemented");

		}

		// Return the computation result
		return Optional.ofNullable(matchingResult);
	}

	private void confirmBlock(BlockWrap block, FullBlockStore blockStore)
			throws BlockStoreException {

		// Update block's transactions in db
		for (final Transaction tx : block.getBlock().getTransactions()) {
			confirmTransaction(block.getBlock(), tx, blockStore);
		}

	
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
			if (!enableFee(block.getBlock())) {
				confirmOrderMatching(block, blockStore);
			}
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
		case BLOCKTYPE_ORDER_EXECUTE:
			confirmOrderExecute(block.getBlock(), blockStore);
			break;
		case BLOCKTYPE_ORDER_OPEN:
			confirmOrderOpen(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		case BLOCKTYPE_CONTRACTEVENT_CANCEL:
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
		confirmOrderMatching(block.getBlock(), actualCalculationResult, blockStore);

	}

	private void confirmOrderMatching(Block block, OrderMatchingResult actualCalculationResult,
			FullBlockStore blockStore) throws BlockStoreException {

		// All consumed order records are now spent by this block
		for (OrderRecord o : actualCalculationResult.getSpentOrders()) {
			o.setSpent(true);
			o.setSpenderBlockHash(block.getHash());
		}

		blockStore.updateOrderSpent(actualCalculationResult.getSpentOrders());
		// Set virtual outputs confirmed
		confirmVirtualCoinbaseTransaction(block, blockStore);
		// Set new orders confirmed

		blockStore.updateOrderConfirmed(actualCalculationResult.getRemainingOrders(), true);

		// Update the matching history in db
		addMatchingEvents(actualCalculationResult, actualCalculationResult.getOutputTx().getHashAsString(),
				block.getTimeSeconds(), blockStore);
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

	public void addMatchingEventsOrderExecution(OrderExecutionResult orderExecutionResult, String transactionHash,
			long matchBlockTime, FullBlockStore store) throws BlockStoreException {
		// collect the spend order volumes and ticker to write to database
		List<MatchResult> matchResultList = new ArrayList<MatchResult>();
		try {
			for (Entry<TradePair, List<Event>> entry : orderExecutionResult.getTokenId2Events().entrySet()) {
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
	public void confirmOrderExecute(Block block, FullBlockStore blockStore) throws BlockStoreException {

		try {
			OrderExecutionResult result = new OrderExecutionResult().parse(block.getTransactions().get(0).getData());
			OrderExecutionResult check = new ServiceOrderExecution(serverConfiguration, networkParameters,
					cacheBlockService)
					.orderMatching(block, result.getPrevblockhash(), result.getReferencedBlocks(), blockStore);
			if (check != null && result.getOutputTxHash().equals(check.getOutputTxHash())
					&& result.getAllRecords().equals(check.getAllRecords())
					&& result.getRemainderRecords().equals(check.getRemainderRecords())
					&& result.getCancelRecords().equals(check.getCancelRecords())) {
				blockStore.updateOrderSpent(check.getAllRecords(), check.getPrevblockhash(), true);
				blockStore.updateOrderConfirmed(check.getRemainderRecords(), block.getHash(), true);
				blockStore.updateOrderCancelSpent(check.getCancelRecords(), block.getHash(), true);
				blockStore.updateOrderResultConfirmed(block.getHash(), true);
				confirmTransaction(block, check.getOutputTx(), blockStore);
				//
				evictTransactions(block.getHash() , blockStore);
				// Update the matching
				addMatchingEventsOrderExecution(check, check.getOutputTx().getHashAsString(), block.getTimeSeconds(),
						blockStore);
			}
			// blockStore.updateContractEvent( );
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * connect from the contract Execution
	 */
	public void confirmContractExecute(Block block, FullBlockStore blockStore) throws BlockStoreException {

		try {
			ContractResult result = new ContractResult().parse(block.getTransactions().get(0).getData());
			ContractResult check = new ServiceContract(serverConfiguration, networkParameters, cacheBlockService)
					.executeContract(block, blockStore, result.getContracttokenid(), result.getPrevblockhash(),
							result.getReferencedBlocks());
			Sets.difference(result.getRemainderRecords(), check.getRemainderRecords());
			Sets.difference(check.getRemainderRecords(), result.getRemainderRecords());
			if (check != null && result.getOutputTxHash().equals(check.getOutputTxHash())
					&& result.getAllRecords().equals(check.getAllRecords())
					&& result.getRemainderRecords().equals(check.getRemainderRecords())
					&& result.getCancelRecords().equals(check.getCancelRecords())) {

				blockStore.updateContractEventSpent(check.getAllRecords(), block.getHash(), true);

				blockStore.updateContractResultConfirmed(block.getHash(), true);
				blockStore.updateContractResultSpent(result.getPrevblockhash(), block.getHash(), true);
				confirmTransaction(block, check.getOutputTx(), blockStore);
				// reset cache
				evictTransactions(block.getHash(), blockStore);
				
			}
			// blockStore.updateContractEvent( );
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void unConfirmContractExecute(Block block, FullBlockStore blockStore) throws BlockStoreException {

		try {
			ContractResult result = new ContractResult().parse(block.getTransactions().get(0).getData());
			blockStore.updateContractResultSpent(block.getHash(), null, false);
			blockStore.updateContractResultConfirmed(block.getHash(), false);
			blockStore.updateContractEventSpent(result.getAllRecords(), block.getHash(), false);
			blockStore.updateTransactionOutputConfirmed(block.getHash(), result.getOutputTxHash(), 0, false);
			evictTransactions(block.getHash() , blockStore);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void unConfirmOrderExecute(Block block, FullBlockStore blockStore) throws BlockStoreException {

		try {
			OrderExecutionResult result = new OrderExecutionResult().parse(block.getTransactions().get(0).getData());
			blockStore.updateOrderResultSpent(block.getHash(), result.getPrevblockhash(), false);
			blockStore.updateOrderResultConfirmed(block.getHash(), false);
			blockStore.updateOrderSpent(result.getAllRecords(), block.getHash(), false);
			blockStore.updateTransactionOutputConfirmed(block.getHash(), result.getOutputTxHash(), 0, false);
			evictTransactions(block.getHash() , blockStore);
			
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
		confirmVirtualCoinbaseTransaction(block.getBlock(), blockStore);

		// Set used other output spent
		blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getBlock().getHash()), true,
				block.getBlock().getHash());

		// Set own output confirmed
		blockStore.updateRewardConfirmed(block.getBlock().getHash(), true);
		cacheBlockService.evictMaxConfirmedReward( );
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
				if (prevOut == null) {
					Block b = getBlock(in.getOutpoint().getBlockHash(), blockStore);
					throw new RuntimeException("Attempted to spend a non-existent output from block" + b.toString());
				}
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

	public void evictTransactions(Block block, FullBlockStore blockStore) throws BlockStoreException {

		for (final Transaction tx : block.getTransactions()) {
			boolean isCoinBase = tx.isCoinBase();
			for (TransactionOutput out : tx.getOutputs()) {
				Script script = getScript(out.getScriptBytes());
				String fromAddress = fromAddress(tx, isCoinBase);
				cacheBlockService.evictAccountBalance(getScriptAddress(script), blockStore);
				cacheBlockService.evictAccountBalance(fromAddress, blockStore);
				cacheBlockService.evictOutputs(getScriptAddress(script), blockStore);
				cacheBlockService.evictOutputs(fromAddress, blockStore);
			}

		}
		cacheBlockService.evictBlockEvaluation(block.getHash());
	}

	public void evictTransactions(Sha256Hash blockHash , FullBlockStore blockStore)  {
 
		try {
			List<UTXO> utxos=	blockStore.getOpenOutputsByBlockhash(blockHash); 
			for( UTXO u: utxos) {
				cacheBlockService.evictOutputs(u.getAddress(), blockStore);
				cacheBlockService.evictOutputs(u.getFromaddress(), blockStore);
			}
		} catch ( Exception e) {
			 
		}
	}
	
	private void confirmVirtualCoinbaseTransaction(Block block, FullBlockStore blockStore) throws BlockStoreException {
		// Set own outputs confirmed
		blockStore.updateAllTransactionOutputsConfirmed(block.getHash(), true);
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

		BlockWrap blockWrap = getBlockWrap(blockHash,blockStore);
		BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
		Block block = blockWrap.getBlock();

		// If already unconfirmed, return
		if (!blockEvaluation.isConfirmed())
			return;

		// Then unconfirm the block outputs
		unconfirmBlockOutputs(block, blockStore );

		// Set unconfirmed
		blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);
		blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), -1);

		evictTransactions(block, blockStore);
		 
		// Keep track of unconfirmed blocks
		traversedBlockHashes.add(blockHash);
	}

	public void unconfirmRecursive(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes,
			FullBlockStore blockStore) throws BlockStoreException {
		// If already confirmed, return
		if (traversedBlockHashes.contains(blockHash))
			return;

		BlockWrap blockWrap = getBlockWrap(blockHash,blockStore);
		BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
		Block block = blockWrap.getBlock();

		// If already unconfirmed, return
		if (!blockEvaluation.isConfirmed())
			return;

		// Unconfirm all dependents
		unconfirmDependents(block, traversedBlockHashes, blockStore);

		// Then unconfirm the block itself
		unconfirmBlockOutputs(block, blockStore);

		evictTransactions(block, blockStore);
		// Set unconfirmed
		blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);
		blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), -1);
		evictTransactions(block, blockStore);
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
		case BLOCKTYPE_ORDER_EXECUTE:
			break;
		case BLOCKTYPE_ORDER_OPEN:
			unconfirmOrderOpenDependents(block, traversedBlockHashes, blockStore);
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		case BLOCKTYPE_CONTRACTEVENT_CANCEL:
			break;
		default:
			throw new RuntimeException("Not Implemented");

		}
	}

	private void unconfirmOrderMatchingDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
			FullBlockStore blockStore) throws BlockStoreException {
		// Get list of consumed orders, virtual order matching tx and newly
		// generated remaining order book
		if (!enableFee(block)) {
			OrderMatchingResult matchingResult = generateOrderMatching(block, blockStore);

			// Disconnect all virtual transaction output dependents
			Transaction tx = matchingResult.getOutputTx();
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
	private void unconfirmBlockOutputs(Block block, FullBlockStore blockStore)
			throws BlockStoreException {
		// Unconfirm all transactions of the block
		for (Transaction tx : block.getTransactions()) {
			unconfirmTransaction(tx, block, blockStore);
		}

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
			if (!enableFee(block)) {
				unconfirmOrderMatching(block, blockStore);
			}
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
		case BLOCKTYPE_ORDER_EXECUTE:
			unConfirmOrderExecute(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_OPEN:
			unconfirmOrderOpen(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			break;
		case BLOCKTYPE_CONTRACTEVENT_CANCEL:
			break;
		default:
			throw new RuntimeException("Not Implemented");

		}
	}

	private void unconfirmOrderMatching(Block block, FullBlockStore blockStore) throws BlockStoreException {
		// Get list of consumed orders, virtual order matching tx and newly
		// generated remaining order book
		if (!enableFee(block)) {
			OrderMatchingResult matchingResult = generateOrderMatching(block, blockStore);
			unconfirmOrderMatching(block, matchingResult, blockStore);
		}
	}

	private void unconfirmOrderMatching(Block block, OrderMatchingResult matchingResult, FullBlockStore blockStore)
			throws BlockStoreException {

		// All consumed order records are now unspent by this block
		Set<OrderRecord> updateOrder = new HashSet<OrderRecord>(matchingResult.getSpentOrders());
		for (OrderRecord o : updateOrder) {
			o.setSpent(false);
			o.setSpenderBlockHash(null);
		}
		blockStore.updateOrderSpent(updateOrder);

		// Set virtual outputs unconfirmed
		unconfirmVirtualCoinbaseTransaction(block.getHash(), blockStore);

		blockStore.updateOrderConfirmed(matchingResult.getRemainingOrders(), false);

		// Update the matching history in db
		removeMatchingEvents(matchingResult.getOutputTx().getHash(), blockStore);
	}

	public void removeMatchingEvents(Sha256Hash h, FullBlockStore store) throws BlockStoreException {
		store.deleteMatchingEvents(h.toString());
	}

	private void unconfirmOrderOpen(Block block, FullBlockStore blockStore) throws BlockStoreException {
		// Set own output unconfirmed

		blockStore.updateOrderConfirmed(block.getHash(), Sha256Hash.ZERO_HASH, false);
	}

	private void unconfirmReward(Block block, FullBlockStore blockStore) throws BlockStoreException {
		// Unconfirm virtual tx
		unconfirmVirtualCoinbaseTransaction(block.getHash(), blockStore);

		// Set used other output unspent
		blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getHash()), false, null);

		// Set own output unconfirmed
		blockStore.updateRewardConfirmed(block.getHash(), false);
		cacheBlockService.evictMaxConfirmedReward( );
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

	private void unconfirmVirtualCoinbaseTransaction(Sha256Hash hash, FullBlockStore blockStore)
			throws BlockStoreException {
		// Set own outputs unconfirmed
		blockStore.updateAllTransactionOutputsConfirmed(hash, false);
	}

	public void solidifyBlock(Block block, SolidityState solidityState, boolean setMilestoneSuccess,
			FullBlockStore blockStore) throws BlockStoreException {
		if (block.getBlockType() == Type.BLOCKTYPE_ORDER_EXECUTE) {
			logger.debug(block.toString());
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
					&& getBlockWrap(block.getHash(),blockStore).getBlockEvaluation().getSolid() > 0) {
				throw new RuntimeException("Should not happen");
			}

			blockStore.updateBlockEvaluationSolid(block.getHash(), 0);

			// Insert into waiting list
			// insertUnsolidBlock(block, solidityState, blockStore);
			break;
		case Success:
			// If already set, nothing to do here...
			if ( getBlockWrap(block.getHash(),blockStore).getBlockEvaluation().getSolid() == 2)
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
		cacheBlockService.evictBlockEvaluation(block.getHash());
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
					// logger.debug(newOut.toString());
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

	protected void connectTypeSpecificUTXOs(Block block, FullBlockStore blockStore) throws BlockStoreException {
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
		case BLOCKTYPE_ORDER_EXECUTE:
			connectOrderExecute(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_OPEN:
			connectOrder(block, blockStore);
			break;
		case BLOCKTYPE_ORDER_CANCEL:
			connectCancelOrder(block, blockStore);
			break;
		case BLOCKTYPE_CONTRACTEVENT_CANCEL:
			connectContractEventCancel(block, blockStore);
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

	private void connectContractEventCancel(Block block, FullBlockStore blockStore) throws BlockStoreException {
		try {
			ContractEventCancelInfo info = new ContractEventCancelInfo().parse(block.getTransactions().get(0).getData());
			ContractEventCancel record = new ContractEventCancel(info.getBlockHash());
			record.setBlockHash(block.getHash());
			blockStore.insertContractEventCancel(record);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public void connectOrder(Block block, FullBlockStore blockStore) throws BlockStoreException {
		try {
			OrderOpenInfo reqInfo = new OrderOpenInfo().parse(block.getTransactions().get(0).getData());
			// calculate the offervalue for version == 1
			if (reqInfo.getVersion() == 1) {
				Coin burned = new ServiceBaseCheck(serverConfiguration, networkParameters, cacheBlockService)
						.countBurnedToken(block, blockStore);
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

			ContractEventRecord record = new ContractEventRecord(block.getHash(), Sha256Hash.ZERO_HASH,
					reqInfo.getContractTokenid(), false, false, null, reqInfo.getOfferValue(),
					reqInfo.getOfferTokenid(), reqInfo.getBeneficiaryAddress());
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
			ContractResult check = new ServiceContract(serverConfiguration, networkParameters, cacheBlockService)
					.executeContract(block, blockStore, result.getContracttokenid(), result.getPrevblockhash(),
							result.getReferencedBlocks());
			// check.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);

			if (check != null && result.getOutputTxHash().equals(check.getOutputTxHash())
					&& result.getAllRecords().equals(check.getAllRecords())
					&& result.getRemainderRecords().equals(check.getRemainderRecords())
					&& result.getCancelRecords().equals(check.getCancelRecords())) {
				result.setBlockHash(block.getHash());
				blockStore.insertContractResult(result);
				insertVirtualUTXOs(block, check.getOutputTx(), blockStore);
				for (ContractEventRecord c : check.getRemainderContractEventRecord()) {
					c.setCollectinghash(block.getHash());
				}
				blockStore.insertContractEvent(check.getRemainderContractEventRecord());

			} else {
				// the ContractExecute can not be reproduced here
				logger.debug("ContractResult check failed  from result " + result.toString() + " compare to check "
						+ check.toString());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void connectOrderExecute(Block block, FullBlockStore blockStore) throws BlockStoreException {
		try {
			OrderExecutionResult result = new OrderExecutionResult().parse(block.getTransactions().get(0).getData());
			OrderExecutionResult check = new ServiceOrderExecution(serverConfiguration, networkParameters,
					cacheBlockService)
					.orderMatching(block, result.getPrevblockhash(), result.getReferencedBlocks(), blockStore);

			if (check != null && result.getOutputTxHash().equals(check.getOutputTxHash())
					&& result.getAllRecords().equals(check.getAllRecords())
					&& result.getRemainderRecords().equals(check.getRemainderRecords())
					&& result.getCancelRecords().equals(check.getCancelRecords())) {
				result.setBlockHash(block.getHash());
				blockStore.insertOrderResult(result);
				insertVirtualUTXOs(block, check.getOutputTx(), blockStore);
				for (OrderRecord c : check.getSpentOrderRecord()) {
					c.setSpent(true);
					c.setSpenderBlockHash(block.getHash());
				}
				blockStore.updateOrderSpent(check.getSpentOrderRecord());
				for (OrderRecord c : check.getRemainderOrderRecord()) {
					c.setIssuingMatcherBlockHash(block.getHash());
				}
				blockStore.insertOrder(check.getRemainderOrderRecord());

			} else {
				// the ContractExecute can not be reproduced here
				logger.warn("OrderExecutionResult check failed  from result " + result.toString() + " compare to check "
						+ check.toString());
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

	protected void insertIntoOrderBooks(OrderRecord o, TreeMap<TradePair, OrderBook> orderBooks,
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
		final Block prevMatchingBlock =  getBlock(prevHash,blockStore) ;

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

	public Set<Sha256Hash> getOrderRecordHash(Set<OrderRecord> orders) {
		Set<Sha256Hash> hashs = new HashSet<>();
		for (OrderRecord o : orders) {
			hashs.add(o.getBlockHash());
		}
		return hashs;
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

	protected void processOrderBook(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
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

	protected void payoutCancelledOrders(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
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

	protected void cancelOrderstoCancelled(List<OrderCancelInfo> cancels,
			HashMap<Sha256Hash, OrderRecord> remainingOrders, Set<OrderRecord> cancelledOrders) {
		for (OrderCancelInfo c : cancels) {
			if (remainingOrders.containsKey(c.getBlockHash())) {
				cancelledOrders.add(remainingOrders.get(c.getBlockHash()));
			}
		}
	}
 

	protected void setIssuingBlockHash(Block block, HashMap<Sha256Hash, OrderRecord> remainingOrders) {
		Iterator<Entry<Sha256Hash, OrderRecord>> it = remainingOrders.entrySet().iterator();
		while (it.hasNext()) {
			OrderRecord order = it.next().getValue();
			order.setIssuingMatcherBlockHash(block.getHash());
		}
	}

	protected void timeoutOrdersToCancelled(Block block, HashMap<Sha256Hash, OrderRecord> remainingOrders,
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

	protected void collectOrdersWithCancel(Block block, Set<Sha256Hash> collectedBlocks, List<OrderCancelInfo> cancels,
			Map<Sha256Hash, OrderRecord> newOrders, Set<OrderRecord> spentOrders, FullBlockStore blockStore)
			throws BlockStoreException {
		for (Sha256Hash bHash : collectedBlocks) {
			BlockWrap b =  getBlockWrap(bHash,blockStore);
			if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OPEN) {

				OrderRecord order = blockStore.getOrder(b.getBlock().getHash(), Sha256Hash.ZERO_HASH);
				// order is null, write it to
				if (order == null) {

					connectUTXOs(b.getBlock(), blockStore);
					connectTypeSpecificUTXOs(b.getBlock(), blockStore);
					order = blockStore.getOrder(b.getBlock().getHash(), Sha256Hash.ZERO_HASH);

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

	//
	public Transaction generateVirtualMiningRewardTX(Block block, FullBlockStore blockStore)
			throws BlockStoreException {
		if (enableFee(block)) {
			return generateVirtualMiningRewardTXFeeBased(block, blockStore);
		} else {
			return generateVirtualMiningRewardTX1(block, blockStore);
		}
	}

	public Transaction generateVirtualMiningRewardTXFeeBased(Block block, FullBlockStore blockStore)
			throws BlockStoreException {
		RewardInfo rewardInfo = new RewardInfo().parseChecked(block.getTransactions().get(0).getData());
		Set<Sha256Hash> candidateBlocks = rewardInfo.getBlocks();

		// Build transaction outputs sorted by addresses
		Transaction tx = new Transaction(networkParameters);

		// Reward the consensus block with the static reward
		tx.addOutput(Coin.FEE_DEFAULT.times(candidateBlocks.size()),
				new Address(networkParameters, block.getMinerAddress()));

		return tx;
	}

	/**
	 * Deterministically creates a mining reward transaction based on the previous
	 * blocks and previous reward transaction. DOES NOT CHECK FOR SOLIDITY. You have
	 * to ensure that the approved blocks result in an eligible reward block.
	 * 
	 * @return mining reward transaction
	 * @throws BlockStoreException
	 */
	public Transaction generateVirtualMiningRewardTX1(Block block, FullBlockStore blockStore)
			throws BlockStoreException {

		RewardInfo rewardInfo = new RewardInfo().parseChecked(block.getTransactions().get(0).getData());
		Set<Sha256Hash> candidateBlocks = rewardInfo.getBlocks();

		// Count how many blocks from miners in the reward interval are approved
		// and build rewards
		Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
				Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
		for (Sha256Hash bHash : candidateBlocks) {
			blockQueue.add(getBlockWrap(bHash,blockStore));
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

}
