package net.bigtangle.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Gzip;

@Service
public class CacheBlockService {
	private static final Logger logger = LoggerFactory.getLogger(CacheBlockService.class);
	public static TXReward lastConfirmedChainBlock;

	@Cacheable(value = "blocksCache", key = "#blockhash")
	public byte[] getBlock(Sha256Hash blockhash, FullBlockStore store) throws BlockStoreException {
		logger.debug("read from database and no cache for: " + blockhash);
		return store.getByte(blockhash);

	}

	@CachePut(value = "blocksCache", key = "#block.hash")
	public byte[] cacheBlock(final Block block, FullBlockStore store) throws BlockStoreException {
		// logger.debug("cachePut {} ", block.getHeight());
		return Gzip.compress(block.unsafeBitcoinSerialize());
	}

	@CacheEvict(value = "blocksCache", key = "#block.hash")
	public void evictBlock(final Block block, FullBlockStore store) throws BlockStoreException {
		logger.debug("evictBlock {}" ,block.toString() );
	}

	@CacheEvict(value = "blocksCache", allEntries=true)
	public void evictBlock() throws BlockStoreException {
		logger.debug("evictBlock" );
	}

	public TXReward getMaxConfirmedReward(FullBlockStore store) throws BlockStoreException {

		if (lastConfirmedChainBlock == null) {
			lastConfirmedChainBlock = store.getMaxConfirmedReward();
		}
		return lastConfirmedChainBlock;
	}

	// reset the lastConfirmedChainBlock = null, for new calculation
	public synchronized void resetMaxConfirmedReward(Block aChainBlock, Boolean confirmed, FullBlockStore store)
			throws BlockStoreException {
		lastConfirmedChainBlock = null;
	}

}