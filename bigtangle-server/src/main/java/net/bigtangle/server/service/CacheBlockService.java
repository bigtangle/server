package net.bigtangle.server.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Gzip;
import net.bigtangle.utils.Json;

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
		// logger.debug("cachePut {} ", block.getHeight());
		return Gzip.compress(block.unsafeBitcoinSerialize());
	}

	@CacheEvict(value = "blocksCache", key = "#block.hash")
	public void evictBlock(final Block block, FullBlockStore store) throws BlockStoreException {
		logger.debug("evictBlock {}", block.toString());
	}

	public TXReward getMaxConfirmedReward(FullBlockStore store) throws BlockStoreException {

		try {
			return new TXReward().parse(getMaxConfirmedRewardByte(store));
		} catch (IOException | BlockStoreException e) {
			throw new BlockStoreException(e);
		}

	}

	@Cacheable(value = "reward", key = "#store.getParams.getId")
	public byte[] getMaxConfirmedRewardByte(FullBlockStore store) throws BlockStoreException {
		// store.getParams().getId()
		return store.getMaxConfirmedReward().toByteArray();
	}

	@CacheEvict(value = "reward", allEntries = true)
	public synchronized void evictMaxConfirmedReward() {
	}

	@Cacheable(value = "accountBalance", key = "#address")
	public List<Coin> getAccountBalance(String address, FullBlockStore store) throws BlockStoreException {
		logger.debug("getAccountBalance from database and no cache for: " + address);
		store.calculateAccount(address, null);
		List<Coin> accountBalance = store.getAccountBalance(address, null);
		if (accountBalance == null)
			return new ArrayList<Coin>();
		return accountBalance;

	}

	@CacheEvict(value = "accountBalance", key = "#address")
	public void evictAccountBalance(String address, FullBlockStore store) throws BlockStoreException {
		// logger.debug("evictAccountBalance {}", address);
	}

	@Cacheable(value = "outputs", key = "#address")
	public List<byte[]> getOpenTransactionOutputs(String address, FullBlockStore store)
			throws UTXOProviderException, JsonProcessingException {

		List<UTXO> utxos = store.getOpenTransactionOutputs(address);
		List<byte[]> re = new ArrayList<>();
		for (UTXO u : utxos) {
			re.add(Json.jsonmapper().writeValueAsBytes(u));
		}
		logger.debug("getOpenTransactionOutputs from database and no cache for: " + address + " size " + re.size());
		return re;
	}

	@CacheEvict(value = "outputs", key = "#address")
	public void evictOutputs(String address, FullBlockStore store) throws BlockStoreException {
		// logger.debug("evictAccountBalance {}", address);
	}

	@CacheEvict(value = "blocksCache", allEntries = true)
	public void evictBlock() throws BlockStoreException {
		logger.debug("evictBlock");
	}

	@CacheEvict(value = "accountBalance", allEntries = true)
	public void evictAccountBalance() throws BlockStoreException {
		logger.debug("evictAccountBalance");
	}

	@CacheEvict(value = "outputs", allEntries = true)
	public void evictOutputs() throws BlockStoreException {
		logger.debug("evictOutputs");
	}

	@Cacheable(value = "BlockMCMC", key = "#blockhash")
	public byte[] getBlockMCMC(Sha256Hash blockhash, FullBlockStore store)
			throws BlockStoreException, JsonProcessingException {
		// store.getParams().getId()
		return Json.jsonmapper().writeValueAsBytes(store.getMCMC(blockhash));
	}

	@CacheEvict(value = "BlockMCMC", allEntries = true)
	public synchronized void evictBlockMCMC() {
	}

	@Cacheable(value = "BlockEvaluation", key = "#blockhash")
	public byte[] getBlockEvaluation(Sha256Hash blockhash, FullBlockStore store)
			throws BlockStoreException, JsonProcessingException { 
		BlockEvaluation value = store.getBlockEvaluationsByhashs(blockhash);
	 
		return Json.jsonmapper().writeValueAsBytes(value);
	}

	@CacheEvict(value = "BlockEvaluation", key = "#blockhash")
	public void evictBlockEvaluation(Sha256Hash blockhash) throws BlockStoreException {

	}

	@CacheEvict(value = "BlockEvaluation", allEntries = true)
	public synchronized void evictBlockEvaluation() {
	}

}