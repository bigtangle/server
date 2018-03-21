/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * <p>
 * A Block provides service for blocks with store.
 * </p>
 */
@Service
public class BlockGraphService extends FullPrunedBlockGraph {

	protected FullPrunedBlockStore store;

	protected NetworkParameters networkParameters;

	@Autowired
	public BlockGraphService(NetworkParameters params, FullPrunedBlockStore blockStore) throws BlockStoreException {
		super(params, blockStore);

	}

}
