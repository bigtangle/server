package net.bigtangle.server.service;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Block.Type;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.store.FullBlockStoreImpl;

/**
 * <p>
 * Provides services for blocks.
 * </p>
 */
@Service
public class BlockSaveService {

	@Autowired
	protected StoreService storeService;

	@Autowired
	protected NetworkParameters networkParameters;
	@Autowired
	FullBlockStoreImpl blockgraph;
	@Autowired
	protected KafkaConfiguration kafkaConfiguration;
	@Autowired
	protected CacheBlockPrototypeService cacheBlockPrototypeService;
	@Autowired
	protected ServerConfiguration serverConfiguration;
	private static final Logger logger = LoggerFactory.getLogger(BlockSaveService.class);

	public void saveBlock(Block block, FullBlockStore store) throws Exception {
		blockgraph.add(block, false, store);
		// no broadcastBlock, if there is error of blockgraph.add
		broadcastBlock(block);
		if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
			cacheBlockPrototypeService.evictBlockPrototypeByte();
		}
	}
	public void broadcastBlock(Block block) {
		try {
			if ("".equalsIgnoreCase(kafkaConfiguration.getBootstrapServers()))
				return;
			KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);

			kafkaMessageProducer.sendMessage(block.bitcoinSerialize(), serverConfiguration.getMineraddress());
		} catch (InterruptedException | ExecutionException | IOException e) {
			logger.warn(block.toString(), e);
		}
	}

}
