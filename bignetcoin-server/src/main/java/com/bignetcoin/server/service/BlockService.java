/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
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

    /**
     * @param blockhash
     * @return
     * @throws BlockStoreException
     */
    public Block getBlock(Sha256Hash blockhash) throws BlockStoreException {
        // Genesis Block is hardcoded is not saved in database
        if (networkParameters.getGenesisBlock().getHash().equals(blockhash))
            return networkParameters.getGenesisBlock();
        return store.get(blockhash).getHeader();
    }

    /**
     * @param prevblockhash
     * @return
     * @return
     * @throws BlockStoreException
     */

    public List<StoredBlock> getApproverBlocks(Sha256Hash blockhash) throws BlockStoreException {
        return store.getApproverBlocks(blockhash);
    }

    public List<Sha256Hash> getApproverBlockHash(Sha256Hash blockhash) throws BlockStoreException {
        return store.getApproverBlockHash(blockhash);
    }

    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
        BlockEvaluation blockEvaluation = store.getBlockEvaluation(hash);
        return blockEvaluation;
    }

	public List<BlockEvaluation> getBlockEvaluations(List<Sha256Hash> approverBlocks) {
		return null;
	}

    public void updateSolidBlocks(Set<Sha256Hash> analyzedHashes) {
    }

	public List<BlockEvaluation> getAllTips() {
		return null;
	}

    public void updateSolid(BlockEvaluation blockEvaluation, boolean b) {
		blockEvaluation.setSolid(b);
    }

	public void updateHeight(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setHeight(i);
	}

	public void updateCumulativeWeight(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setCumulativeweight(i);
	}

	public void updateDepth(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setDepth(i);
	}

	public void updateRating(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setRating(i);
	}
	
	public long getMaxSolidHeight() {
		return 0;
	}

	public List<BlockEvaluation> getSolidBlocksOfHeight(long currentHeight) {
		return null;
	}

	public List<BlockEvaluation> getLastSolidTips() {
		return null;
	}

	public Collection<BlockEvaluation> getBlocksToRemoveFromMilestone() {
		return null;
	}

	public Collection<BlockEvaluation> getBlocksToAddToMilestone() {
		return null;
	}

	public void updateMilestone(BlockEvaluation block, boolean b) {
	}

    public void saveBinaryArrayToBlock(byte[] bytes) throws Exception {
        Block block = (Block) networkParameters.getDefaultSerializer().deserialize(ByteBuffer.wrap(bytes));
        FullPrunedBlockGraph blockgraph = new FullPrunedBlockGraph(networkParameters, store);
        blockgraph.add(block);
    }
}
