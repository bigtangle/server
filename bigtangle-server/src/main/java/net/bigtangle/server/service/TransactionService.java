/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.kafka.KafkaConfiguration;
import net.bigtangle.kafka.KafkaMessageProducer;
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
    private ValidatorService validatorService;
    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    protected KafkaConfiguration kafkaConfiguration;

    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    @Autowired
    TaskExecutor taskExecutor;

    public ByteBuffer askTransaction() throws Exception {

        Block rollingBlock = askTransactionBlock();

        byte[] data = rollingBlock.bitcoinSerialize();

        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        return byteBuffer;
    }

    public Block askTransactionBlock() throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());

        return new Block(this.networkParameters, r1.getHash(), r2.getHash(), NetworkParameters.BLOCKTYPE_TRANSFER,
                Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

    }

    public byte[] createGenesisBlock(Map<String, Object> request) throws Exception {
        String tokenHex = (String) request.get("tokenHex");
        Tokens tokens_ = this.store.getTokensInfo(tokenHex);
        if (tokens_ != null) {
            throw new BlockStoreException("tokens already existed");
        }
        String pubKeyHex = (String) request.get("pubKeyHex");
        long amount = new Long(request.get("amount").toString());
        String tokenname = (String) request.get("tokenname");
        String description = (String) request.get("description");
        boolean asmarket = request.get("asmarket") == null ? false : (boolean) request.get("asmarket");
        boolean multiserial = request.get("multiserial") == null ? false : (boolean) request.get("multiserial");
        boolean tokenstop = request.get("tokenstop") == null ? false : (boolean) request.get("tokenstop");
        String url = (String) request.get("url");
        long signnumber = 1;
        Tokens tokens = new Tokens(tokenHex, tokenname, description, url, signnumber, multiserial, asmarket, tokenstop);
        store.saveTokens(tokens);

        byte[] pubKey = Utils.HEX.decode(pubKeyHex);
        byte[] tokenid = Utils.HEX.decode(tokenHex);
        Coin coin = Coin.valueOf(amount, tokenid);
        // TODO why is there no TokenInfo here?
        Block block = createGenesisBlock(coin, tokenid, pubKey, null);
        return block.bitcoinSerialize();
    }

    public Block createGenesisBlock(Coin coin, byte[] tokenid, byte[] pubKey, TokenInfo tokenInfo) throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        long blocktype0 = NetworkParameters.BLOCKTYPE_TOKEN_CREATION;
        Block block = new Block(networkParameters, r1.getHash(), r2.getHash(), blocktype0,
                Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
        block.addCoinbaseTransaction(pubKey, coin, tokenInfo);
        block.solve();
        blockgraph.add(block);
        return block;
    }

    public Block createMiningRewardBlock(long fromHeight) throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        long blocktype0 = NetworkParameters.BLOCKTYPE_REWARD;
        Block block = new Block(networkParameters, r1.getHash(), r2.getHash(), blocktype0,
                Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

        block.addTransaction(validatorService.generateMiningRewardTX(block, fromHeight));
        block.solve();
        blockgraph.add(block);
        return block;
    }

    public boolean getUTXOSpent(TransactionOutPoint txout) {
        try {
            if (txout.isCoinBase())
                return false;
            return store.getTransactionOutput(txout.getHash(), txout.getIndex()).isSpent();
        } catch (BlockStoreException e) {
            logger.debug("", e);
        }
        return true;
    }

    public BlockEvaluation getUTXOSpender(TransactionOutPoint txout) {
        try {
            return store.getTransactionOutputSpender(txout.getHash(), txout.getIndex());
        } catch (BlockStoreException e) {
            e.printStackTrace();
        }
        return null;
    }

    public UTXO getUTXO(TransactionOutPoint out) throws BlockStoreException {
        return store.getTransactionOutput(out.getHash(), out.getIndex());
    }

    // TODO use add instead of addConnected?
    public Optional<Block> addConnected(byte[] bytes, boolean emptyBlock) {
        try {
            Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
            if (!checkBlockExists(block)) {
                blockgraph.add(block);
                logger.debug("addConnected from kafka " + block);
                // if(!block.getTransactions().isEmpty() && emptyBlock)
                // saveEmptyBlock(3);
                return Optional.of(block);
            } else {
                logger.debug("addConnected   BlockExists " + block);
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
     * check before add Block from kafka , the block can be already exists. TODO the
     * block may be cached for performance
     */
    public boolean checkBlockExists(Block block) throws BlockStoreException {
        return store.get(block.getHash()) != null;
    }

    public void streamBlocks(Long heightstart) throws BlockStoreException {
        KafkaMessageProducer kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
        store.streamBlocks(heightstart, kafkaMessageProducer);
    }

    public void streamBlocks(Long heightstart, String kafka) throws BlockStoreException {
        KafkaMessageProducer kafkaMessageProducer;
        if (kafka == null || "".equals(kafka)) {
            kafkaMessageProducer = new KafkaMessageProducer(kafkaConfiguration);
        } else {
            kafkaMessageProducer = new KafkaMessageProducer(kafka, kafkaConfiguration.getTopicOutName(), true);
        }
        store.streamBlocks(heightstart, kafkaMessageProducer);
    }
}
