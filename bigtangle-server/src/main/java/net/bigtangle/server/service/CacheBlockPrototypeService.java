package net.bigtangle.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.store.FullBlockStore;

@Service
public class CacheBlockPrototypeService {
	private static final Logger logger = LoggerFactory.getLogger(CacheBlockPrototypeService.class);

	@Autowired
	protected BlockService blockService;
	@Autowired
	protected NetworkParameters networkParameters;

	@Cacheable(value = "BlockPrototype", key = "#store.getParams.getId")
	public byte[] getBlockPrototypeByte(FullBlockStore store) throws BlockStoreException, NoBlockException {
		// logger.debug("blockService.getNewBlockPrototype(store " ) ;
		return blockService.getNewBlockPrototype(store).unsafeBitcoinSerialize();

	}

	@CacheEvict(value = "BlockPrototype", allEntries = true)
	public  void evictBlockPrototypeByte() {
		// logger.debug("evictBlockPrototypeByte" ) ;
	}

	public Block getBlockPrototype(FullBlockStore store)
			throws ProtocolException, BlockStoreException, NoBlockException {
		return networkParameters.getDefaultSerializer().makeBlock(getBlockPrototypeByte(store));
	}

}