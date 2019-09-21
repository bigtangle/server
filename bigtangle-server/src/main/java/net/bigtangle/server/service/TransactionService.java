/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.exception.VerificationException.DifficultyTargetException;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.utils.Gzip;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.wallet.CoinSelector;
import net.bigtangle.wallet.DefaultCoinSelector;

/**
 * <p>
 * A TransactionService provides service for transactions that send and receive
 * value from user keys. Using these, it is able to create new transactions that
 * spend the recorded transactions, and this is the fundamental operation of the
 * protocol.
 * </p>
 */
@Service
public class TransactionService {

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected FullPrunedBlockGraph blockgraph;
    @Autowired
    private BlockService blockService;

    @Autowired
    protected TipsService tipService;
    @Autowired
    protected MilestoneService milestoneService;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    protected KafkaConfiguration kafkaConfiguration;

    @Autowired
    protected ServerConfiguration serverConfiguration;

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    public Block askTransactionBlock() throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());

        return r1.createNextBlock(r2,
                Address.fromBase58(networkParameters, serverConfiguration.getMineraddress()).getHash160());
    }

    public boolean getUTXOSpent(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex()).isSpent();
    }

    public boolean getUTXOConfirmed(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutput(txout.getBlockHash(), txout.getTxHash(), txout.getIndex()).isConfirmed();
    }

    public BlockEvaluation getUTXOSpender(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutputSpender(txout.getBlockHash(), txout.getTxHash(), txout.getIndex());
    }

    public UTXO getUTXO(TransactionOutPoint out) throws BlockStoreException {
        return store.getTransactionOutput(out.getBlockHash(), out.getTxHash(), out.getIndex());
    }

    /*
     * Block byte[] bytes
     */
    public Optional<Block> addConnectedFromKafka(byte[] key, byte[] bytes) {

        try {
            // logger.debug(" bytes " +bytes.length);
            return addConnected(Gzip.decompress(bytes), true);
        } catch (DifficultyTargetException e) {

            return null;
        } catch (Exception e) {
            logger.warn("addConnectedFromKafka with sendkey:" + key.toString(), e);
            return null;
        }

    }

    /*
     * Block byte[] bytes
     */
    public Optional<Block> addConnected(byte[] bytes, boolean allowUnsolid)
            throws ProtocolException, BlockStoreException, NoBlockException {
        if (bytes == null)
            return null;

        return addConnectedBlock((Block) networkParameters.getDefaultSerializer().makeBlock(bytes), allowUnsolid);
    }

    private Optional<Block> addConnectedBlock(Block block, boolean allowUnsolid)
            throws BlockStoreException, NoBlockException {
        if (store.getBlockEvaluation(block.getHash()) == null) {

            boolean added = blockgraph.add(block, allowUnsolid);

            if (!added) {
                logger.debug(" can not added block  Blockhash=" + block.getHashAsString() + " height ="
                        + block.getHeight() + " block: " + block.toString());
                return Optional.empty();

            } else {
                return Optional.of(block);
            }
        }
        return Optional.empty();
    }

    // private boolean blockTimeRange(Block block) {
    // return System.currentTimeMillis() - block.getTimeSeconds() * 1000 < 8 *
    // 3600 * 1000;
    // }

    /*
     * check before add Block from kafka , the block can be already exists.
     */
    public boolean checkBlockExists(Block block) throws BlockStoreException, NoBlockException {
        return store.get(block.getHash()) != null;
    }

    public void streamBlocks(Long heightstart) throws BlockStoreException {
        KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
        store.streamBlocks(heightstart, kafkaMessageProducer, serverConfiguration.getMineraddress());
    }

    public void streamBlocks(Long heightstart, String kafka) throws BlockStoreException {
        KafkaMessageProducer kafkaMessageProducer;
        if (kafka == null || "".equals(kafka)) {
            kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
        } else {
            kafkaMessageProducer = new KafkaMessageProducer(kafka, kafkaConfiguration.getTopicOutName(), true);
        }
        store.streamBlocks(heightstart, kafkaMessageProducer, serverConfiguration.getMineraddress());
    }

}
