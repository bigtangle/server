/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.server.config.ServerConfiguration;
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
    private UnsolidBlockService unsolidBlockService;
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

        return r1.createNextBlock(r2);
    }

    public boolean getUTXOSpent(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutput(txout.getHash(), txout.getIndex()).isSpent();
    }

    public boolean getUTXOConfirmed(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutput(txout.getHash(), txout.getIndex()).isConfirmed();
    }

    public BlockEvaluation getUTXOSpender(TransactionOutPoint txout) throws BlockStoreException {
        return store.getTransactionOutputSpender(txout.getHash(), txout.getIndex());
    }

    public UTXO getUTXO(TransactionOutPoint out) throws BlockStoreException {
        return store.getTransactionOutput(out.getHash(), out.getIndex());
    }

    public Optional<Block> addConnected(byte[] bytes, boolean emptyBlock, boolean request) {
        try {
            StoredBlock storedBlock = StoredBlock.deserializeCompact(networkParameters, ByteBuffer.wrap(bytes));
            Block block = storedBlock.getHeader();
            if (!checkBlockExists(block)) {
                StoredBlock added = blockgraph.add(block, true);
                if (added != null) {
                    logger.trace("addConnected from kafka ");
                } else {
                    logger.debug(" unsolid block from kafka height " + storedBlock.getHeight() + " Blockhash="
                            + block.getHashAsString());
                    if (request)
                        unsolidBlockService.requestPrev(block);

                }
                return Optional.of(block);
            } else {
                // logger.debug("addConnected BlockExists " + block);
            }
        } catch (VerificationException e) {
            logger.debug("addConnected from kafka ", e);
            return Optional.empty();
        } catch (Exception e) {
            logger.debug("addConnected from kafka ", e);
            return Optional.empty();
        }
        return Optional.empty();
    }

    /*
     * check before add Block from kafka , the block can be already exists.
     */
    public boolean checkBlockExists(Block block) throws BlockStoreException, NoBlockException {
        try {
            return store.get(block.getHash()) != null;
        } catch (NoBlockException e) {
            return false;
        }

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
