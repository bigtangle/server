package net.bigtangle.server.service;

import java.io.IOException;

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

	@Cacheable(value = "blocksCache", key = "#blockhash")
	public byte[] getBlock(Sha256Hash blockhash, FullBlockStore store) throws BlockStoreException {
		logger.debug("read from database and no cache for: " + blockhash);
		return store.getByte(blockhash);

	}

	@CachePut(value = "blocksCache", key = "#block.hash")
	public byte[] cacheBlock(final Block block, FullBlockStore store) throws BlockStoreException {
		// logger.debug("cachePut {} ", block.getHash());
		return Gzip.compress(block.unsafeBitcoinSerialize());
	}

	public TXReward getMaxConfirmedReward(FullBlockStore store) throws BlockStoreException {
		try {
			return new TXReward().parse(getMaxConfirmedRewardByte(store));
		} catch (IOException e) {
			throw new BlockStoreException(e);
		}
	}

	@Cacheable(value = "MaxConfirmedReward")
	public byte[] getMaxConfirmedRewardByte(FullBlockStore store) throws BlockStoreException {
		logger.debug("read from database getMaxConfirmedReward ");
		return store.getMaxConfirmedReward().toByteArray();
	}

	@CacheEvict(value = "MaxConfirmedReward", allEntries = true)
	public void evictAllCacheValuesMaxConfirmedReward() {
	}

}