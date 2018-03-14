/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.util.Collection;
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

	public List<BlockEvaluation> getBlockEvaluations(List<Sha256Hash> approverBlocks) {
		// TODO same as getBlockEvaluation above but multiple
		return null;
	}

    public void updateSolidBlocks(Set<Sha256Hash> analyzedHashes) {
		// TODO ?
    }

	public List<BlockEvaluation> getAllTips() {
		// TODO get all current tips including nonsolid ones
		return null;
	}

    public void updateSolid(BlockEvaluation blockEvaluation, boolean b) {
		blockEvaluation.setSolid(b);
		// TODO set blockEvaluation.solid in database
    }

	public void updateHeight(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setHeight(i);
		// TODO set blockEvaluation.height in database
	}

	public void updateCumulativeWeight(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setCumulativeweight(i);
		// TODO set blockEvaluation.cumulativeWeight in database
	}

	public void updateDepth(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setDepth(i);
		// TODO set blockEvaluation.cumulativeWeight in database
	}

	public void updateRating(BlockEvaluation blockEvaluation, long i) {
		blockEvaluation.setRating(i);
		// TODO set blockEvaluation.rating in database
	}
	
	public long getMaxSolidHeight() {
		// TODO get max(height) over all block evaluations
		return 0;
	}

	public List<BlockEvaluation> getSolidBlocksOfHeight(long currentHeight) {
		// TODO get all blocks where height == currentHeight
		return null;
	}

	public List<BlockEvaluation> getLastSolidTips() {
		// TODO get solid tips, can include tips that are not actually tips anymore if they are referenced by non-solid new blocks
		return null;
	}

	public Collection<BlockEvaluation> getBlocksToRemoveFromMilestone() {
		// TODO Select from blockevaluation where (solid && milestone && rating < 50)
		return null;
	}

	public Collection<BlockEvaluation> getBlocksToAddToMilestone() {
		// TODO select from blockevaluation where (solid && not milestone && rating >= 75 && depth > MINDEPTH) 
		return null;
	}

	public void updateMilestone(BlockEvaluation block, boolean b) {
		// TODO Set milestone to specified parameter and update latestMilestoneUpdateTime to current local time
	}
}
