/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import org.bitcoinj.core.Block;
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
     * 取得当前block
     * @param blockhash
     * @return
     * @throws BlockStoreException
     */
    public Block getBlock(Sha256Hash blockhash) throws BlockStoreException {
        return store.get(blockhash).getHeader();
    }

    /**
     * 获取前置block
     * @param prevblockhash
     * @return
     * @throws BlockStoreException
     */
    public StoredBlock getPrevBlock(Sha256Hash prevblockhash) throws BlockStoreException {
        StoredBlock storedBlock = store.getStoredBlockFromPrev(prevblockhash);
        return storedBlock;
    }

    public Sha256Hash[] getBlockToApprove() {
        return null;
    }

}
