/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.util.List;
import java.util.Set;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
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

    public void updateSolidBlocks(Set<Sha256Hash> analyzedHashes) {
		// TODO 
    }

	public List<BlockEvaluation> getTips() {
		// TODO get all current tips
		return null;
	}

    public void updateSolid(BlockEvaluation blockEvaluation, boolean b) {
		blockEvaluation.setSolid(b);
		// TODO set blockEvaluation.solid in database for now
    }

	public void updateHeight(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setHeight(i);
		// TODO set blockEvaluation.height in database for now
	}

	public void updateCumulativeWeight(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setCumulativeweight(i);
		// TODO set blockEvaluation.cumulativeWeight in database for now
	}

	public void updateDepth(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setDepth(i);
		// TODO set blockEvaluation.cumulativeWeight in database for now
	}

	public void updateRating(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setRating(i);
		// TODO set blockEvaluation.rating in database for now
	}
	
	public long getHeightMax() {
		// TODO get max(height) over all block evaluations
		return 0;
	}
}
