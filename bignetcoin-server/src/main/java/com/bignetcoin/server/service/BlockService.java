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
        return store.get(blockhash).getHeader();
    }

    /**
     * @param prevblockhash
     * @return 
     * @return
     * @throws BlockStoreException
     */ 

    public  List<StoredBlock> getApproverBlocks(Sha256Hash blockhash) throws BlockStoreException {
        return store.getApproverBlocks(blockhash);
    }

    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException {
        BlockEvaluation blockEvaluation = store.getBlockEvaluation(hash);
        return blockEvaluation;
    }

    public void updateSolidBlocks(Set<Sha256Hash> analyzedHashes) {
    }

    public void updateSolid(BlockEvaluation blockEvaluation, boolean b) {
    }
}
