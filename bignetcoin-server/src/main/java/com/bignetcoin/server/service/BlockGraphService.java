/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.NetworkParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.bignetcoin.store.FullPrunedBlockStore;

/**
 * <p>
 * A Block provides service for blocks with store.
 * </p>
 */
@Service
public class BlockGraphService extends com.bignetcoin.store.FullPrunedBlockGraph {

	protected FullPrunedBlockStore store;

	protected NetworkParameters networkParameters;

	@Autowired
	public BlockGraphService(NetworkParameters params, FullPrunedBlockStore blockStore) throws BlockStoreException {
		super(params, blockStore);

	}

}
