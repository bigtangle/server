/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.tomcat.jni.Time;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 * A Block provides service for blocks with store.
 * </p>
 */
@Service
public class BlockService {

	@Autowired
	protected FullPrunedBlockStore store;

	protected CoinSelector coinSelector = new DefaultCoinSelector();

	@Autowired
	protected NetworkParameters networkParameters;

	public Block getBlock(Sha256Hash blockhash) throws BlockStoreException {
		// Genesis Block is hardcoded is not saved in database
		if (networkParameters.getGenesisBlock().getHash().equals(blockhash))
			return networkParameters.getGenesisBlock();
		return store.get(blockhash).getHeader();
	}
	
	public List<Block> getBlocks(List<Sha256Hash> hashes) throws BlockStoreException {
		List<Block> blocks = new ArrayList<Block>();
		for (Sha256Hash hash : hashes) {
			blocks.add(getBlock(hash));
		}
		return blocks;
	}

	public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
		return store.getBlockEvaluation(hash);
	}

	public List<BlockEvaluation> getBlockEvaluations(List<Sha256Hash> hashes) throws BlockStoreException {
		List<BlockEvaluation> blocks = new ArrayList<BlockEvaluation>();
		for (Sha256Hash hash : hashes) {
			blocks.add(getBlockEvaluation(hash));
		}
		return blocks;
	}
	
	public List<StoredBlock> getApproverBlocks(Sha256Hash blockhash) throws BlockStoreException {
		return store.getApproverBlocks(blockhash);
	}

	public List<Sha256Hash> getApproverBlockHashes(Sha256Hash blockhash) throws BlockStoreException {
		return store.getApproverBlockHash(blockhash);
	}

	public long getMaxSolidHeight() throws BlockStoreException {
		return store.getMaxSolidHeight();
	}

	public List<BlockEvaluation> getNonSolidBlocks() throws BlockStoreException {
		return store.getNonSolidBlocks();
	}

	public List<BlockEvaluation> getSolidBlocksOfHeight(long currentHeight) throws BlockStoreException {
		return store.getSolidBlocksOfHeight(currentHeight);
	}

	public List<BlockEvaluation> getSolidTips() throws BlockStoreException {
		return store.getSolidTips();
	}

	public HashSet<BlockEvaluation> getBlocksToRemoveFromMilestone() throws BlockStoreException {
		return store.getBlocksToRemoveFromMilestone();
	}

	public HashSet<BlockEvaluation> getBlocksToAddToMilestone() throws BlockStoreException {
		return store.getBlocksToAddToMilestone(27);
	}

	public void updateSolidBlocks(Set<Sha256Hash> analyzedHashes) throws BlockStoreException {
		// unnecessary
	}

	public void updateSolid(BlockEvaluation blockEvaluation, boolean b) throws BlockStoreException {
		blockEvaluation.setSolid(b);
		store.updateBlockEvaluationSolid(blockEvaluation.getBlockhash(), b);
	}

	public void updateHeight(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
		blockEvaluation.setHeight(i);
		store.updateBlockEvaluationHeight(blockEvaluation.getBlockhash(), i);
	}

	public void updateCumulativeWeight(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
		blockEvaluation.setCumulativeWeight(i);
		store.updateBlockEvaluationCumulativeweight(blockEvaluation.getBlockhash(), i);
	}

	public void updateDepth(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
		blockEvaluation.setDepth(i);
		store.updateBlockEvaluationDepth(blockEvaluation.getBlockhash(), i);
	}

	public void updateRating(BlockEvaluation blockEvaluation, long i) throws BlockStoreException {
		blockEvaluation.setRating(i);
		store.updateBlockEvaluationRating(blockEvaluation.getBlockhash(), i);
	}

	public void updateMilestone(BlockEvaluation blockEvaluation, boolean b) throws BlockStoreException {
		blockEvaluation.setMilestone(b);
		store.updateBlockEvaluationMilestone(blockEvaluation.getBlockhash(), b);

		long now = Time.now();
		blockEvaluation.setMilestoneLastUpdateTime(now);
		store.updateBlockEvaluationMilestoneLastUpdateTime(blockEvaluation.getBlockhash(), now);
	}

	public void saveBinaryArrayToBlock(byte[] bytes) throws Exception {
		Block block = (Block) networkParameters.getDefaultSerializer().deserialize(ByteBuffer.wrap(bytes));
		FullPrunedBlockGraph blockgraph = new FullPrunedBlockGraph(networkParameters, store);
		blockgraph.add(block);
	}
}
