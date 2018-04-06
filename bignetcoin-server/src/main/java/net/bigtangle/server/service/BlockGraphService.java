/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.store.FullPrunedBlockStore;

/**
 * <p>
 * A Block provides service for blocks with store.
 * </p>
 */
@Service
public class BlockGraphService extends net.bigtangle.store.FullPrunedBlockGraph {

	protected FullPrunedBlockStore store;

	protected NetworkParameters networkParameters;

	@Autowired
	public BlockGraphService(NetworkParameters params, FullPrunedBlockStore blockStore) throws BlockStoreException {
		super(params, blockStore);

	}

}
