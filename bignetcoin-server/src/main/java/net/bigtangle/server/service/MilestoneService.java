/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.store.FullPrunedBlockStore;

/*
 *  This service offers update functions to include newest tangle updates
 */
@Service
public class MilestoneService {

	private static final Logger log = LoggerFactory.getLogger(MilestoneService.class);

	private static final int WARNING_MILESTONE_UPDATE_LOOPS = 20;

	@Autowired
	protected BlockGraphService blockGraphService;
	@Autowired
	protected FullPrunedBlockStore store;
	@Autowired
	private BlockService blockService;
	@Autowired
	private TipsService tipsService;
	@Autowired
	private TransactionService transactionService;	
	@Autowired
	private ValidatorService validatorService;

	/**
	 * Scheduled update function that updates the Tangle
	 * 
	 * @throws Exception
	 */
	public void update() throws Exception {
		// TODO test pruning here for now
		
		log.info("Milestone Update started");
		Stopwatch watch = Stopwatch.createStarted();
		updateSolidityAndHeight();
		log.info("Solidity and height update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		watch.stop();
		watch = Stopwatch.createStarted();
		updateCumulativeWeightAndDepth();
		log.info("Weight and depth update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		watch.stop();
		watch = Stopwatch.createStarted();
		updateRating();
		log.info("Rating update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));

		watch.stop();
		watch = Stopwatch.createStarted();
		updateMilestone();
		log.info("Milestone update time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
		
		// Optional: Trigger batched tip pair selection here

		watch.stop();
	}

	/**
	 * Update solid, true if all directly or indirectly approved blocks exist. If
	 * solid, update height to be the max of previous heights + 1
	 * 
	 * @throws Exception
	 */
	public void updateSolidityAndHeight() throws Exception {
		List<Sha256Hash> nonSolidBlocks = blockService.getNonSolidBlocks();
		for (Sha256Hash nonSolidBlock : nonSolidBlocks)
			updateSolidityAndHeightRecursive(nonSolidBlock);
	}

	private boolean updateSolidityAndHeightRecursive(Sha256Hash hash) throws BlockStoreException {
		BlockEvaluation blockEvaluation = blockService.getBlockEvaluation(hash);

		// Missing blocks -> not solid, request from network
		if (blockEvaluation == null) {
			// TODO broken graph, download the missing remote block needed
			return false;
		}

		// Solid blocks stay solid
		if (blockEvaluation.isSolid()) {
			return true;
		}

		Block block = blockService.getBlock(hash);
		boolean prevBlockSolid = false;
		boolean prevBranchBlockSolid = false;

		// Check previous trunk block exists and is solid
		BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
		if (prevBlockEvaluation == null) {
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBlockSolid = updateSolidityAndHeightRecursive(block.getPrevBlockHash());
		}

		// Check previous branch block exists and is solid
		BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
		if (prevBranchBlockEvaluation == null) {
			// TODO broken graph, download the missing remote block needed
		} else {
			prevBranchBlockSolid = updateSolidityAndHeightRecursive(block.getPrevBranchBlockHash());
		}

		// If both previous blocks are solid, our block should be solidified
		if (prevBlockSolid && prevBranchBlockSolid) {
			prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
			prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
			blockGraphService.solidifyBlock(block, prevBlockEvaluation, prevBranchBlockEvaluation);
			return true;
		} 
		return false;
	}

	/**
	 * Update cumulative weight, the amount of blocks a block is approved by
	 * 
	 * @throws BlockStoreException
	 */
	public void updateCumulativeWeightAndDepth() throws BlockStoreException {
		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = getSolidTipsDescendingAsPriorityQueue();
		HashMap<Sha256Hash, HashSet<Sha256Hash>> approverHashSets = new HashMap<>();
		HashMap<Sha256Hash, Long> depths = new HashMap<>();
		HashMap<Sha256Hash, Long> milestoneDepths = new HashMap<>();
		for (BlockEvaluation tip : blocksByDescendingHeight) {
			approverHashSets.put(tip.getBlockhash(), new HashSet<>());
			depths.put(tip.getBlockhash(), 0L);
			milestoneDepths.put(tip.getBlockhash(), 0L);
		}

		BlockEvaluation currentBlock = null;
		while ((currentBlock = blocksByDescendingHeight.poll()) != null) {
			// Abort if unmaintained
			if (!currentBlock.isMaintained())
				continue;
			
			// Add your own hash to approver hashes of current approver hashes
			HashSet<Sha256Hash> approverHashes = approverHashSets.get(currentBlock.getBlockhash());
			approverHashes.add(currentBlock.getBlockhash());
			long depth = depths.get(currentBlock.getBlockhash());
			long milestoneDepth = milestoneDepths.get(currentBlock.getBlockhash());
			
			// Add all current references to both approved blocks (initialize if not yet initialized)
			Block block = blockService.getBlock(currentBlock.getBlockhash());
			
			if (!approverHashSets.containsKey(block.getPrevBlockHash())) {
				BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
				if (prevBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBlockEvaluation);
					approverHashSets.put(prevBlockEvaluation.getBlockhash(), new HashSet<>());
					depths.put(prevBlockEvaluation.getBlockhash(), 0L);
					milestoneDepths.put(prevBlockEvaluation.getBlockhash(), 0L);
				}
			}
			
			if (!approverHashSets.containsKey(block.getPrevBranchBlockHash())) {
				BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
				if (prevBranchBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBranchBlockEvaluation);
					approverHashSets.put(prevBranchBlockEvaluation.getBlockhash(), new HashSet<>());
					depths.put(prevBranchBlockEvaluation.getBlockhash(), 0L);
					milestoneDepths.put(prevBranchBlockEvaluation.getBlockhash(), 0L);
				}
			}
			
			if (approverHashSets.containsKey(block.getPrevBlockHash())) {
				approverHashSets.get(block.getPrevBlockHash()).addAll(approverHashes);
				if (depth + 1 > depths.get(block.getPrevBlockHash())) {
					depths.put(block.getPrevBlockHash(), depth + 1);					
				}
				if (currentBlock.isMilestone() && milestoneDepth + 1 > milestoneDepths.get(block.getPrevBlockHash())) {
					milestoneDepths.put(block.getPrevBlockHash(), milestoneDepth + 1);					
				}
			}
			
			if (approverHashSets.containsKey(block.getPrevBranchBlockHash())) {
				approverHashSets.get(block.getPrevBranchBlockHash()).addAll(approverHashes);
				if (depth + 1 > depths.get(block.getPrevBranchBlockHash())) {
					depths.put(block.getPrevBranchBlockHash(), depth + 1);					
				}
				if (currentBlock.isMilestone() && milestoneDepth + 1 > milestoneDepths.get(block.getPrevBranchBlockHash())) {
					milestoneDepths.put(block.getPrevBranchBlockHash(), milestoneDepth + 1);					
				}
			}

			// Update and dereference
			blockService.updateCumulativeWeight(currentBlock, approverHashes.size());
			blockService.updateDepth(currentBlock, depth);
			blockService.updateMilestoneDepth(currentBlock, milestoneDepth);
			approverHashSets.remove(currentBlock.getBlockhash());
			depths.remove(currentBlock.getBlockhash());
			milestoneDepths.remove(currentBlock.getBlockhash());
		}
	}

	/**
	 * Update the percentage of times that tips selected by MCMC approve a block
	 * 
	 * @throws Exception
	 */
	public void updateRating() throws Exception {
		// Select #tipCount solid tips via MCMC
		HashMap<BlockEvaluation, HashSet<UUID>> selectedTips = new HashMap<BlockEvaluation, HashSet<UUID>>(NetworkParameters.MAX_RATING_TIP_COUNT);
		List<Sha256Hash> selectedTipHashes = tipsService.getRatingTips(NetworkParameters.MAX_RATING_TIP_COUNT);
		for (Sha256Hash selectedTipHash : selectedTipHashes) {
			BlockEvaluation selectedTip = blockService.getBlockEvaluation(selectedTipHash);
			HashSet<UUID> result;
			if (selectedTips.containsKey(selectedTip))  
				result = selectedTips.get(selectedTip);
			else 
				result = new HashSet<>();
			result.add(UUID.randomUUID());
			selectedTips.put(selectedTip, result);
		}
		
		// Begin from the highest solid height tips and go backwards from there
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = getSolidTipsDescendingAsPriorityQueue();
		HashMap<Sha256Hash, HashSet<UUID>> approverHashSets = new HashMap<>();
		for (BlockEvaluation tip : blockService.getSolidTips()) {
			approverHashSets.put(tip.getBlockhash(), new HashSet<>());
		}

		BlockEvaluation currentBlock = null;
		while ((currentBlock = blocksByDescendingHeight.poll()) != null) {
			// Abort if unmaintained
			if (!currentBlock.isMaintained())
				continue;
			
			// Add your own hashes as reference if current block is one of the selected tips
			HashSet<UUID> approverHashes = approverHashSets.get(currentBlock.getBlockhash());
			if (selectedTips.containsKey(currentBlock)) {
				approverHashes.addAll(selectedTips.get(currentBlock));
			}
			
			// Add all current references to both approved blocks (initialize if not yet initialized)
			Block block = blockService.getBlock(currentBlock.getBlockhash());
			
			if (!approverHashSets.containsKey(block.getPrevBlockHash())) {
				BlockEvaluation prevBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBlockHash());
				if (prevBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBlockEvaluation);
					approverHashSets.put(prevBlockEvaluation.getBlockhash(), new HashSet<>());
				}
			}
			
			if (!approverHashSets.containsKey(block.getPrevBranchBlockHash())) {
				BlockEvaluation prevBranchBlockEvaluation = blockService.getBlockEvaluation(block.getPrevBranchBlockHash());
				if (prevBranchBlockEvaluation != null) {
					blocksByDescendingHeight.add(prevBranchBlockEvaluation);
					approverHashSets.put(prevBranchBlockEvaluation.getBlockhash(), new HashSet<>());
				}
			}
			
			if (approverHashSets.containsKey(block.getPrevBlockHash()))
				approverHashSets.get(block.getPrevBlockHash()).addAll(approverHashes);
			
			if (approverHashSets.containsKey(block.getPrevBranchBlockHash()))
				approverHashSets.get(block.getPrevBranchBlockHash()).addAll(approverHashes);


			// Update your rating 
			blockService.updateRating(currentBlock, approverHashes.size());
			approverHashSets.remove(currentBlock.getBlockhash());
		}
	}

	/**
	 * Updates milestone field in block evaluation and changes output table
	 * correspondingly
	 * 
	 * @throws BlockStoreException
	 */
	public void updateMilestone() throws BlockStoreException {
		// First remove any blocks that should no longer be in the milestone
		HashSet<BlockEvaluation> blocksToRemove = blockService.getBlocksToRemoveFromMilestone();
		for (BlockEvaluation block : blocksToRemove) {
			blockService.disconnect(block);
		}

		for (int i = 0; true; i++) {
			// Now try to find blocks that can be added to the milestone
			HashSet<BlockEvaluation> blocksToAdd = blockService.getBlocksToAddToMilestone();

			// Optional steps from later to lower computational cost
			if (blocksToAdd.isEmpty())
				break;
			removeWhereUTXONotFoundOrUnconfirmed(blocksToAdd);

			// Resolve conflicting UTXO spends that have been approved by the network
			// (improbable to occur)
			resolveUnundoableConflicts(blocksToAdd);
			resolveUndoableConflicts(blocksToAdd);

			// Remove blocks from blocksToAdd that have at least one transaction input with
			// its corresponding output not found in the outputs table and remove their
			// approvers recursively too
			removeWhereUTXONotFoundOrUnconfirmed(blocksToAdd);

			// Exit condition: there are no more blocks to add
			if (blocksToAdd.isEmpty())
				break;

			// Finally add the found new milestone blocks to the milestone
			for (BlockEvaluation block : blocksToAdd) {
				blockService.connect(block);
			}
			
			if (i == WARNING_MILESTONE_UPDATE_LOOPS) {
				log.warn("High amount of milestone updates per scheduled update. Can't keep up or reorganizing!");
			}
		}
	}

	/**
	 * Remove blocks from blocksToAdd that have at least one transaction input with
	 * its corresponding output not found in the outputs table and remove their
	 * approvers recursively too
	 * 
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	private void removeWhereUTXONotFoundOrUnconfirmed(HashSet<BlockEvaluation> blocksToAdd) throws BlockStoreException {
		for (BlockEvaluation e : new HashSet<BlockEvaluation>(blocksToAdd)) {
			Block block = blockService.getBlock(e.getBlockhash());
			for (TransactionInput in : block.getTransactions().stream().flatMap(t -> t.getInputs().stream()).collect(Collectors.toList())) {
				if (in.isCoinBase())
					continue;
				UTXO utxo = transactionService.getUTXO(in.getOutpoint());
				if (utxo == null || !utxo.isConfirmed())
					blockService.removeBlockAndApproversFrom(blocksToAdd, e);
			}
		}
	}

	/**
	 * Resolves conflicts in new blocks to add that cannot be undone due to pruning.
	 * This method does not do anything if not pruning blocks.
	 * 
	 * @param blocksToAdd
	 * @throws BlockStoreException
	 */
	private void resolveUnundoableConflicts(HashSet<BlockEvaluation> blocksToAdd) throws BlockStoreException {
		// Get the blocks to add as actual blocks from blockService
		List<Block> blocks = blockService.getBlocks(blocksToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));

		// To check for unundoable conflicts, we do the following:
		// Create tuples (block, txinput) of all blocksToAdd
		Stream<Pair<Block, TransactionInput>> blockInputTuples = blocks.stream()
				.flatMap(b -> b.getTransactions().stream().flatMap(t -> t.getInputs().stream()).map(in -> Pair.of(b, in)));

		// Now filter to only contain inputs that were already spent in the milestone
		// where the corresponding block has already been pruned
		Stream<Pair<Block, TransactionInput>> irresolvableConflicts = blockInputTuples
				.filter(pair -> transactionService.getUTXOSpent(pair.getRight()) && transactionService.getUTXOSpender(pair.getRight().getOutpoint()) == null);

		// These blocks cannot be added and must therefore be removed from blocksToAdd
		for (Pair<Block, TransactionInput> p : irresolvableConflicts.collect(Collectors.toList())) {
			blockService.removeBlockAndApproversFrom(blocksToAdd, blockService.getBlockEvaluation(p.getLeft().getHash()));
		}
	}

	/**
	 * Resolves conflicts between milestone blocks and milestone candidates as well
	 * as conflicts among milestone candidates.
	 * 
	 * @param blockEvaluationsToAdd
	 * @throws BlockStoreException
	 */
	private void resolveUndoableConflicts(HashSet<BlockEvaluation> blockEvaluationsToAdd) throws BlockStoreException {
		// TODO replace all transactionOutPoints in this class with new class conflictpoints (equals of transactionoutpoints, token issuance ids, mining reward height intervals) 
		HashSet<Pair<BlockEvaluation, TransactionOutPoint>> conflictingOutPoints = new HashSet<Pair<BlockEvaluation, TransactionOutPoint>>();
		HashSet<BlockEvaluation> conflictingMilestoneBlocks = new HashSet<BlockEvaluation>();
		List<Block> blocksToAdd = blockService.getBlocks(blockEvaluationsToAdd.stream().map(e -> e.getBlockhash()).collect(Collectors.toList()));
		
		// Find all conflicts between milestone and candidates 
		validatorService.findMilestoneCandidateConflicts(blocksToAdd, conflictingOutPoints, conflictingMilestoneBlocks);
		validatorService.findCandidateCandidateConflicts(blocksToAdd, conflictingOutPoints);

		// Resolve all conflicts by grouping by UTXO ordered by descending rating
		HashSet<BlockEvaluation> winningBlocks = validatorService.resolveConflictsByDescendingRating(conflictingOutPoints);

		// For milestone blocks that have been eliminated call disconnect procedure
		for (BlockEvaluation b : conflictingMilestoneBlocks.stream().filter(b -> !winningBlocks.contains(b)).collect(Collectors.toList())) {
			blockService.disconnect(b);
		}

		// For candidates that have been eliminated (conflictingOutPoints in blocksToAdd
		// \ winningBlocks) remove them from blocksToAdd
		for (Pair<BlockEvaluation, TransactionOutPoint> b : conflictingOutPoints.stream()
				.filter(b -> blockEvaluationsToAdd.contains(b.getLeft()) && !winningBlocks.contains(b.getLeft())).collect(Collectors.toList())) {
			blockService.removeBlockAndApproversFrom(blockEvaluationsToAdd, b.getLeft());
		}
	}

	/**
	 * Returns all solid tips ordered by descending height
	 * 
	 * @return solid tips by ordered by descending height
	 * @throws BlockStoreException
	 */
	private PriorityQueue<BlockEvaluation> getSolidTipsDescendingAsPriorityQueue() throws BlockStoreException {
		List<BlockEvaluation> solidTips = blockService.getSolidTips();
		PriorityQueue<BlockEvaluation> blocksByDescendingHeight = new PriorityQueue<BlockEvaluation>(solidTips.size(),
				Comparator.comparingLong(BlockEvaluation::getHeight).reversed());
		blocksByDescendingHeight.addAll(solidTips);
		return blocksByDescendingHeight;
	}
}
