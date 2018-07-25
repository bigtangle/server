/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.directory.api.util.exception.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.script.Script;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class ValidatorService {

	@Autowired
	protected FullPrunedBlockStore store;
	@Autowired
	private BlockService blockService;
	@Autowired
	protected NetworkParameters networkParameters;
	@Autowired
	private TransactionService transactionService;

	private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

	/**
	 * Deterministically creates a mining reward transaction based on the previous
	 * blocks and previous reward transaction.
	 * 
	 * @return Pair of mining transaction and whether it is eligible to be voted on
	 *         at this moment of time.
	 * @throws BlockStoreException
	 */
	public Pair<Transaction, Boolean> generateMiningRewardTX(Sha256Hash prevTrunkHash, Sha256Hash prevBranchHash,
			Sha256Hash prevRewardHash) throws BlockStoreException {
		// Count how many blocks from miners in the reward interval are approved
		// and build rewards
		boolean eligibility = true;
		PriorityQueue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
				Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
		HashSet<BlockWrap> queuedBlocks = new HashSet<>();
		HashMap<Address, Long> rewardCount = new HashMap<>();
		long approveCount = 0;

		BlockWrap prevTrunkBlock = store.getBlockWrap(prevTrunkHash);
		BlockWrap prevBranchBlock = store.getBlockWrap(prevBranchHash);
		blockQueue.add(prevTrunkBlock);
		blockQueue.add(prevBranchBlock);
		queuedBlocks.add(prevTrunkBlock);
		queuedBlocks.add(prevBranchBlock);

		// Read previous reward block's data
		BlockWrap prevRewardBlock = store.getBlockWrap(prevRewardHash);
		long fromHeight = 0, toHeight = 0, minHeight = 0, perTxReward = 0;
		try {
			if (prevRewardBlock.getBlock().getHash().equals(networkParameters.getGenesisBlock().getHash())) {
				fromHeight = 0;
				toHeight = fromHeight + networkParameters.getRewardHeightInterval() - 1;
				minHeight = toHeight;
				perTxReward = 100;

			} else {
				ByteBuffer bb = ByteBuffer.wrap(prevRewardBlock.getBlock().getTransactions().get(0).getData());
				fromHeight = bb.getLong() + networkParameters.getRewardHeightInterval();
				toHeight = fromHeight + networkParameters.getRewardHeightInterval() - 1;
				minHeight = toHeight;
				perTxReward = bb.getLong();
			}

			if (prevTrunkBlock.getBlockEvaluation().getHeight() < minHeight - 1
					&& prevBranchBlock.getBlockEvaluation().getHeight() < minHeight - 1)
				return Pair.of(null, false);

		} catch (Exception e) {
			e.printStackTrace();
			return Pair.of(null, false);
		}

		// Go backwards by height
		// TODO penalize lowest cumulative weight
		BlockWrap currentBlock = null, tmpBlock = null;
		while ((currentBlock = blockQueue.poll()) != null) {
			// Stop criterion: Block height lower than approved interval height
			if (currentBlock.getBlockEvaluation().getHeight() < fromHeight)
				continue;

			if (currentBlock.getBlockEvaluation().getHeight() <= toHeight) {
				// Failure criterion: rewarding non-milestone blocks
				if (currentBlock.getBlockEvaluation().isMilestone() == false)
					eligibility = false;

				// Count rewards, try to find prevRewardBlock
				approveCount++;
				Address miner = new Address(networkParameters, currentBlock.getBlock().getMinerAddress());
				if (!rewardCount.containsKey(miner))
					rewardCount.put(miner, 1L);
				else
					rewardCount.put(miner, rewardCount.get(miner) + 1);
			}

			// Continue with approved blocks
			tmpBlock = store.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
			if (!queuedBlocks.contains(tmpBlock) && tmpBlock != null) {
				queuedBlocks.add(tmpBlock);
				blockQueue.add(tmpBlock);
			}

			tmpBlock = store.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
			if (!queuedBlocks.contains(tmpBlock) && tmpBlock != null) {
				queuedBlocks.add(tmpBlock);
				blockQueue.add(tmpBlock);
			}
		}

		// TX reward adjustments for next rewards
		long nextPerTxReward = Math.max(1, 20000000 * 365 * 24 * 60 * 60 / approveCount / Math.min(1,
				(Math.max(prevTrunkBlock.getBlock().getTimeSeconds(), prevBranchBlock.getBlock().getTimeSeconds())
						- prevRewardBlock.getBlock().getTimeSeconds())));
		nextPerTxReward = Math.max(nextPerTxReward, perTxReward / 4);
		nextPerTxReward = Math.min(nextPerTxReward, perTxReward * 4);

		// Build transaction outputs
		Transaction tx = new Transaction(networkParameters);
		for (Entry<Address, Long> entry : rewardCount.entrySet())
			tx.addOutput(Coin.SATOSHI.times(entry.getValue() * perTxReward), entry.getKey());

		// The input does not really need to be a valid signature, as long
		// as it has the right general form.
		TransactionInput input = new TransactionInput(networkParameters, tx,
				Script.createInputScript(Block.EMPTY_BYTES, Block.EMPTY_BYTES));
		tx.addInput(input);

		// Add the type-specific tx data (fromHeight, nextPerTxReward, prevRewardHash)
		ByteBuffer bb = ByteBuffer.allocate(48);
		bb.putLong(fromHeight);
		bb.putLong(nextPerTxReward);
		bb.put(prevRewardHash.getBytes());
		tx.setData(bb.array());

		// Check eligibility: sufficient amount of milestone blocks approved?
		if (approveCount < store.getCountMilestoneBlocksInInterval(fromHeight, toHeight) * 99 / 100)
			eligibility = false;

		return Pair.of(tx, eligibility);
	}

	/**
	 * Remove blocks from blocksToAdd that have at least one transaction input with
	 * its corresponding output not found in the outputs table and remove their
	 * approvers recursively too
	 * 
	 * @param blocksToAdd
	 * @return true if a block was removed
	 * @throws BlockStoreException
	 */
	public boolean removeWherePreconditionsUnfulfilled(Collection<BlockWrap> blocksToAdd) throws BlockStoreException {
		return removeWherePreconditionsUnfulfilled(blocksToAdd, false);
	}

	public boolean removeWherePreconditionsUnfulfilled(Collection<BlockWrap> blocksToAdd, boolean returnOnFirstRemoval)
			throws BlockStoreException {
		boolean removed = false;

		for (BlockWrap b : new HashSet<BlockWrap>(blocksToAdd)) {
			Block block = blockService.getBlock(b.getBlock().getHash());
			for (TransactionInput in : block.getTransactions().stream().flatMap(t -> t.getInputs().stream())
					.collect(Collectors.toList())) {
				if (in.isCoinBase())
					continue;
				UTXO utxo = transactionService.getUTXO(in.getOutpoint());
				if (utxo == null || !utxo.isConfirmed()) {
					removed = true;
					blockService.removeBlockAndApproversFrom(blocksToAdd, b);
					continue;
				}
			}

			if (block.getBlockType() == NetworkParameters.BLOCKTYPE_REWARD) {
				// Previous reward must have been confirmed
				Sha256Hash prevRewardHash = null;
				try {
					byte[] hashBytes = new byte[32];
					ByteBuffer bb = ByteBuffer.wrap(block.getTransactions().get(0).getData());
					bb.getLong();
					bb.getLong();
					bb.get(hashBytes, 0, 32);
					prevRewardHash = Sha256Hash.wrap(hashBytes);

					if (!store.getTxRewardConfirmed(prevRewardHash)) {
						removed = true;
						blockService.removeBlockAndApproversFrom(blocksToAdd, b);
						continue;
					}
				} catch (Exception c) {
					c.printStackTrace();
					removed = true;
					blockService.removeBlockAndApproversFrom(blocksToAdd, b);
					continue;
				}

				// If ineligible, preconditions are sufficient age and milestone rating range
				if (!store.getTxRewardEligible(block.getHash())
						&& !(b.getBlockEvaluation().getRating() > NetworkParameters.MILESTONE_UPPER_THRESHOLD
								&& b.getBlockEvaluation().getInsertTime() < System.currentTimeMillis() / 1000 - 30)) {
					removed = true;
					blockService.removeBlockAndApproversFrom(blocksToAdd, b);
					continue;
				}
			}

			if (block.getBlockType() == NetworkParameters.BLOCKTYPE_TOKEN_CREATION) {
				// TODO add prev token issuance confirmed
			}
		}

		return removed;
	}

	public void resolveValidityConflicts(Set<BlockWrap> blocksToAdd, boolean unconfirmLosingMilestones)
			throws BlockStoreException {
		// Remove blocks and their approvers that have at least one input
		// with its corresponding output not confirmed yet / nonexistent
		removeWherePreconditionsUnfulfilled(blocksToAdd);

		// Resolve conflicting block combinations
		// resolvePrunedConflicts(blocksToAdd);
		resolveUndoableConflicts(blocksToAdd, unconfirmLosingMilestones);

		// Remove blocks and their approvers that have at least one input
		// with its corresponding output not confirmed yet / nonexistent
		// Needed since while resolving undoable conflicts, milestone blocks are
		// unconfirmed
		removeWherePreconditionsUnfulfilled(blocksToAdd);
	}

	/**
	 * Resolves conflicts between milestone blocks and milestone candidates as well
	 * as conflicts among milestone candidates.
	 * 
	 * @param blocksToAdd
	 * @param unconfirmLosingMilestones
	 * @throws BlockStoreException
	 */
	public void resolveUndoableConflicts(Collection<BlockWrap> blocksToAdd, boolean unconfirmLosingMilestones)
			throws BlockStoreException {
		HashSet<ConflictCandidate> conflictingOutPoints = new HashSet<ConflictCandidate>();
		HashSet<BlockWrap> conflictingMilestoneBlocks = new HashSet<BlockWrap>();

		// Find all conflicts in the new blocks
		findConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);

		// Resolve all conflicts by grouping by UTXO ordered by descending
		// rating
		HashSet<BlockWrap> losingBlocks = resolveConflicts(conflictingOutPoints, unconfirmLosingMilestones);

		// For milestone blocks that have been eliminated call disconnect
		// procedure
		for (BlockWrap b : conflictingMilestoneBlocks.stream().filter(b -> losingBlocks.contains(b))
				.collect(Collectors.toList())) {
			if (!unconfirmLosingMilestones) {
				logger.error("Cannot unconfirm milestone blocks when not allowing unconfirmation!");
				break;
			}

			blockService.unconfirm(b.getBlockEvaluation());
		}

		// For candidates that have been eliminated (conflictingOutPoints in
		// blocksToAdd
		// \ winningBlocks) remove them from blocksToAdd
		for (ConflictCandidate b : conflictingOutPoints.stream()
				.filter(b -> blocksToAdd.contains(b.getBlock()) && losingBlocks.contains(b.getBlock()))
				.collect(Collectors.toList())) {
			blockService.removeBlockAndApproversFrom(blocksToAdd, b.getBlock());
		}
	}

	/**
	 * Resolve all conflicts by grouping by UTXO ordered by descending rating.
	 * 
	 * @param blockEvaluationsToAdd
	 * @param conflictingOutPoints
	 * @param unconfirmLosingMilestones
	 * @param conflictingMilestoneBlocks
	 * @return losingBlocks: blocks that have been removed due to conflict
	 *         resolution
	 * @throws BlockStoreException
	 */
	public HashSet<BlockWrap> resolveConflicts(Collection<ConflictCandidate> conflictingOutPoints,
			boolean unconfirmLosingMilestones) throws BlockStoreException {
		// Initialize blocks that will survive the conflict resolution
		HashSet<BlockWrap> initialBlocks = conflictingOutPoints.stream().map(c -> c.getBlock())
				.collect(Collectors.toCollection(HashSet::new));
		HashSet<BlockWrap> winningBlocks = new HashSet<>();
		for (BlockWrap winningBlock : initialBlocks) {
			blockService.addApprovedNonMilestoneBlocksTo(winningBlocks, winningBlock);
			blockService.addMilestoneApproversTo(winningBlocks, winningBlock);
		}
		HashSet<BlockWrap> losingBlocks = new HashSet<>(winningBlocks);

		// Sort conflicts internally by descending rating, then cumulative
		// weight. If not unconfirming, prefer milestones first.
		Comparator<ConflictCandidate> byDescendingRating = getConflictComparator(unconfirmLosingMilestones)
				.thenComparingLong((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getRating())
				.thenComparingLong((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getCumulativeWeight())
				.thenComparingLong((ConflictCandidate e) -> -e.getBlock().getBlockEvaluation().getInsertTime())
				.thenComparing((ConflictCandidate e) -> e.getBlock().getBlockEvaluation().getBlockHash()).reversed();

		Supplier<TreeSet<ConflictCandidate>> conflictTreeSetSupplier = () -> new TreeSet<ConflictCandidate>(
				byDescendingRating);

		Map<Object, TreeSet<ConflictCandidate>> conflicts = conflictingOutPoints.stream().collect(
				Collectors.groupingBy(i -> i.getConflictPoint(), Collectors.toCollection(conflictTreeSetSupplier)));

		// Sort conflicts among each other by descending max(rating). If not
		// unconfirming, prefer milestones first.
		Comparator<TreeSet<ConflictCandidate>> byDescendingSetRating = getConflictSetComparator(
				unconfirmLosingMilestones)
						.thenComparingLong(
								(TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation().getRating())
						.thenComparingLong((TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation()
								.getCumulativeWeight())
						.thenComparingLong((TreeSet<ConflictCandidate> s) -> -s.first().getBlock().getBlockEvaluation()
								.getInsertTime())
						.thenComparing((TreeSet<ConflictCandidate> s) -> s.first().getBlock().getBlockEvaluation()
								.getBlockHash())
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
			// all other
			// blocks in this conflict from winning Blocks
			if (maxRatingPair != null) {
				for (ConflictCandidate c : conflict) {
					if (c != maxRatingPair) {
						blockService.removeBlockAndApproversFrom(winningBlocks, c.getBlock());
					}
				}
			}
		}

		losingBlocks.removeAll(winningBlocks);

		return losingBlocks;
	}

	private Comparator<TreeSet<ConflictCandidate>> getConflictSetComparator(boolean unconfirmLosingMilestones) {
		if (!unconfirmLosingMilestones)
			return new Comparator<TreeSet<ConflictCandidate>>() {
				@Override
				public int compare(TreeSet<ConflictCandidate> o1, TreeSet<ConflictCandidate> o2) {
					if (o1.first().getBlock().getBlockEvaluation().isMilestone()
							&& o2.first().getBlock().getBlockEvaluation().isMilestone())
						return 0;
					if (o1.first().getBlock().getBlockEvaluation().isMilestone())
						return 1;
					if (o2.first().getBlock().getBlockEvaluation().isMilestone())
						return -1;
					return 0;
				}
			};
		else
			return new Comparator<TreeSet<ConflictCandidate>>() {
				@Override
				public int compare(TreeSet<ConflictCandidate> o1, TreeSet<ConflictCandidate> o2) {
					return 0;
				}
			};
	}

	private Comparator<ConflictCandidate> getConflictComparator(boolean unconfirmLosingMilestones) {
		if (!unconfirmLosingMilestones)
			return new Comparator<ConflictCandidate>() {
				@Override
				public int compare(ConflictCandidate o1, ConflictCandidate o2) {
					if (o1.getBlock().getBlockEvaluation().isMilestone()
							&& o2.getBlock().getBlockEvaluation().isMilestone()) {
						if (o1.equals(o2))
							return 0;
						else {
							logger.warn("Inconsistent Milestone: Conflicting blocks in milestone" + ": \n"
									+ o1.getBlock().getBlock().getHash() + "\n" + o2.getBlock().getBlock().getHash());
							return 0;
						}
					}
					if (o1.getBlock().getBlockEvaluation().isMilestone())
						return 1;
					if (o2.getBlock().getBlockEvaluation().isMilestone())
						return -1;
					return 0;
				}
			};
		else
			return new Comparator<ConflictCandidate>() {
				@Override
				public int compare(ConflictCandidate o1, ConflictCandidate o2) {
					return 0;
				}
			};
	}

	/**
	 * Finds conflicts in blocksToAdd
	 * 
	 * @param blocksToAdd
	 * @param conflictingOutPoints
	 * @throws BlockStoreException
	 */
	public void findConflicts(Collection<BlockWrap> blocksToAdd, Collection<ConflictCandidate> conflictingOutPoints,
			Collection<BlockWrap> conflictingMilestoneBlocks) throws BlockStoreException {

		findMilestoneConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);
		findCandidateConflicts(blocksToAdd, conflictingOutPoints);
	}

	/**
	 * Finds conflicts among blocks to add themselves
	 * 
	 * @param blocksToAdd
	 * @param conflictingOutPoints
	 * @throws BlockStoreException
	 */
	private void findCandidateConflicts(Collection<BlockWrap> blocksToAdd,
			Collection<ConflictCandidate> conflictingOutPoints) throws BlockStoreException {
		// Get conflicts that are spent more than once in the
		// candidates
		List<ConflictCandidate> candidateCandidateConflicts = blocksToAdd.stream().map(b -> toConflictCandidates(b))
				.flatMap(i -> i.stream()).collect(Collectors.groupingBy(i -> i.getConflictPoint())).values().stream()
				.filter(l -> l.size() > 1).flatMap(l -> l.stream()).collect(Collectors.toList());

		// Add the conflicting candidates
		for (ConflictCandidate c : candidateCandidateConflicts) {
			conflictingOutPoints.add(c);
		}
	}

	/**
	 * Finds conflicts between current milestone and blocksToAdd
	 * 
	 * @param blocksToAdd
	 * @param conflictingOutPoints
	 * @throws BlockStoreException
	 */
	private void findMilestoneConflicts(Collection<BlockWrap> blocksToAdd,
			Collection<ConflictCandidate> conflictingOutPoints, Collection<BlockWrap> conflictingMilestoneBlocks)
			throws BlockStoreException {
		// Find all conflict candidates in blocks to add
		List<ConflictCandidate> conflicts = blocksToAdd.stream().map(b -> toConflictCandidates(b))
				.flatMap(i -> i.stream()).collect(Collectors.toList());

		// Find only those that are conflicting with milestone
		filterConflictingWithMilestone(conflicts);

		// Add the conflicting candidates and milestone blocks to given set
		for (ConflictCandidate c : conflicts) {

			// Add milestone block
			BlockWrap milestoneBlock = null;
			switch (c.getConflictPoint().getType()) {
			case TXOUT:
				milestoneBlock = getSpendingBlock(c.getConflictPoint().getConnectedOutpoint());
				break;
			case TOKENISSUANCE:
				milestoneBlock = getTokenIssuingBlock(c.getConflictPoint().getConnectedTokenSerial());
				break;
			case REWARDISSUANCE:
				milestoneBlock = getIntervalRewardingBlock(c.getConflictPoint().getConnectedRewardHeight());
				break;
			default:
				throw new NotImplementedException();
			}
			conflictingOutPoints.add(new ConflictCandidate(milestoneBlock, c.getConflictPoint()));
			conflictingMilestoneBlocks.add(milestoneBlock);

			// Then add corresponding new block
			conflictingOutPoints.add(c);
		}
	}

	private boolean alreadySpent(TransactionOutPoint transactionOutPoint) {
		return transactionService.getUTXOSpent(transactionOutPoint) && getSpendingBlock(transactionOutPoint) != null
				&& getSpendingBlock(transactionOutPoint).getBlockEvaluation().isMaintained();
	}

	private BlockWrap getIntervalRewardingBlock(long height) throws BlockStoreException {
		return store.getBlockWrap(store.getConfirmedRewardBlock(height));
	}

	private boolean alreadyRewarded(long height) throws BlockStoreException {
		return height != store.getMaxPrevTxRewardHeight() + NetworkParameters.REWARD_HEIGHT_INTERVAL;
	}

	private BlockWrap getSpendingBlock(TransactionOutPoint transactionOutPoint) {
		try {
			return store.getBlockWrap(transactionService.getUTXOSpender(transactionOutPoint).getBlockHash());
		} catch (BlockStoreException e) {
			return null;
		}
	}

	private boolean alreadyIssued(TokenSerial tokenSerial) {
		// TODO where token has been issued already
		return false;
	}

	private BlockWrap getTokenIssuingBlock(TokenSerial tokenSerial) {
		// TODO get milestone block that issued the specified token id and
		// sequence number
		throw new NotImplementedException();
	}

	public boolean isIneligibleForSelection(BlockWrap block, HashSet<BlockWrap> currentApprovedNonMilestoneBlocks) {
		if (block.getBlockEvaluation().isMilestone())
			return false;

		@SuppressWarnings("unchecked")
		HashSet<BlockWrap> newApprovedNonMilestoneBlocks = (HashSet<BlockWrap>) currentApprovedNonMilestoneBlocks
				.clone();
		try {
			blockService.addApprovedNonMilestoneBlocksTo(newApprovedNonMilestoneBlocks, block);
		} catch (BlockStoreException e) {
			e.printStackTrace();
			return true;
		}

		// Remove blocks and their approvers that have at least one input
		// with its corresponding output not confirmed yet
		try {
			if (removeWherePreconditionsUnfulfilled(newApprovedNonMilestoneBlocks, true))
				return true;
		} catch (BlockStoreException e) {
			e.printStackTrace();
			return true;
		}

		// Find all conflict candidates in blocks to add
		List<ConflictCandidate> conflicts = newApprovedNonMilestoneBlocks.stream().map(b -> toConflictCandidates(b))
				.flatMap(i -> i.stream()).collect(Collectors.toList());

		// Find only those that are conflicting with milestone
		return isConflictingWithMilestone(conflicts);
	}

	public HashSet<ConflictCandidate> toConflictCandidates(BlockWrap b) {
		HashSet<ConflictCandidate> blockConflicts = new HashSet<>();

		// Create pairs of blocks and used non-coinbase utxos from block
		// Dynamic conflicts: conflicting transactions
		b.getBlock().getTransactions().stream().flatMap(t -> t.getInputs().stream()).filter(in -> !in.isCoinBase())
				.map(in -> new ConflictCandidate(b, in.getOutpoint())).forEach(c -> blockConflicts.add(c));

		// Dynamic conflicts: mining reward height intervals
		if (b.getBlock().getBlockType() == NetworkParameters.BLOCKTYPE_REWARD)
			blockConflicts
					.add(new ConflictCandidate(b, Utils.readInt64(b.getBlock().getTransactions().get(0).getData(), 0)));

		// Dynamic conflicts: token issuance ids
		if (b.getBlock().getBlockType() == NetworkParameters.BLOCKTYPE_TOKEN_CREATION)
			blockConflicts.add(new ConflictCandidate(b,
					new TokenInfo().parse(b.getBlock().getTransactions().get(0).getData()).getTokenSerial()));

		return blockConflicts;
	}

	public boolean isConflictingWithMilestone(Collection<ConflictCandidate> blockConflicts) {
		filterConflictingWithMilestone(blockConflicts);
		return !blockConflicts.isEmpty();
	}

	public void filterConflictingWithMilestone(Collection<ConflictCandidate> blockConflicts) {
		blockConflicts.removeIf(c -> {
			switch (c.getConflictPoint().getType()) {
			case TXOUT:
				return !alreadySpent(c.getConflictPoint().getConnectedOutpoint());
			case TOKENISSUANCE:
				return !alreadyIssued(c.getConflictPoint().getConnectedTokenSerial());
			case REWARDISSUANCE:
				try {
					return !alreadyRewarded(c.getConflictPoint().getConnectedRewardHeight());
				} catch (BlockStoreException e) {
					e.printStackTrace();
				}
			default:
				throw new NotImplementedException();
			}
		});
	}
}
