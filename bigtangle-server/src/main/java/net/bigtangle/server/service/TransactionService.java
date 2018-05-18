/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.nio.ByteBuffer;
import java.util.List;
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
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
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
    protected FullPrunedBlockStore blockStore;

    @Autowired
    private TipsService tipService;

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    @Autowired
    private BlockService blockService;

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    private KafkaConfiguration kafkaConfiguration;

    @Autowired
    MilestoneService milestoneService;

    private static final Logger logger = LoggerFactory.getLogger(BlockService.class);

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

        return new Block(this.networkParameters, r1.getHash(), r2.getHash(), 
                NetworkParameters.BLOCKTYPE_TRANSFER, Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

    }

    public byte[] createGenesisBlock(Map<String, Object> request) throws Exception {
        String pubKeyHex = (String) request.get("pubKeyHex");
        long amount = new Long(request.get("amount").toString());
        String tokenname = (String) request.get("tokenname");
        String description = (String) request.get("description");
        String tokenHex = (String) request.get("tokenHex");
        boolean blocktype = (Boolean) request.get("blocktype");

        String url = request.containsKey("url") ? (String) request.get("url") : "";
        long signnumber = request.containsKey("signnumber") ? Long.parseLong(request.get("signnumber").toString()) : 1L;
        if (signnumber <= 0) {
            throw new BlockStoreException("signnumber error");
        }
        boolean multiserial = request.containsKey("multiserial") ? Boolean.parseBoolean(request.get("multiserial").toString()) : false;
        boolean asmarket = request.containsKey("asmarket") ? Boolean.parseBoolean(request.get("asmarket").toString()) : false;
        boolean tokenstop = request.containsKey("tokenstop") ? Boolean.parseBoolean(request.get("tokenstop").toString()) : false;
        
        Tokens tokens = new Tokens(tokenHex, tokenname, description, url, signnumber, multiserial, asmarket, tokenstop);
        // store.saveTokens(tokens);
        
        ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyHex));
        String address = ecKey.toAddress(this.networkParameters).toString();
        MultiSignAddress multiSignAddress = new MultiSignAddress(tokenHex, address);
        //store.insertMultiSignAddress(multiSignAddress);
        
        TokenSerial tokenSerial = new TokenSerial(tokenHex, 0L, amount);
        //store.insertTokenSerial(tokenSerial);

        MultiSignBy multiSignBy = new MultiSignBy(tokenHex, 0L, address);
        //store.insertMultisignby(multiSignBy);
        
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses().add(multiSignAddress);
       // tokenInfo.getMultiSignBies().add(multiSignBy);
        tokenInfo.getTokenSerials().add(tokenSerial);
        
        if (tokens.getSignnumber() == 1L) {
            byte[] pubKey = Utils.HEX.decode(pubKeyHex);
            byte[] tokenid = Utils.HEX.decode(tokenHex);
            Coin coin = Coin.valueOf(amount, tokenid);
            Block block = createGenesisBlock(coin, tokenid, pubKey, blocktype, tokenInfo);
            block.toString();
            return block.bitcoinSerialize();
        }
        return new byte[0];
    }
    
    public void multiSign(Map<String, Object> request) throws BlockStoreException {
        String tokenid = (String) request.get("tokenid");
        String address = (String) request.get("address");
        long tokenindex = Long.parseLong(request.get("tokenindex").toString());
        Tokens tokens = this.store.getTokensInfo(tokenid);
        if (tokens == null) {
            return;
        }
        TokenSerial tokenSerial = this.store.getTokenSerialInfo(tokenid, tokenindex);
        if (tokenSerial == null) {
            return;
        }
        MultiSignAddress multiSignAddress = this.store.getMultiSignAddressInfo(tokenid, address);
        if (multiSignAddress == null) {
            return;
        }
        int count = this.store.getCountMultiSignByTokenIndexAndAddress(tokenid, tokenSerial.getTokenindex(), address);
        if (count > 0) {
            return;
        }
//        MultiSignBy multiSignBy = new MultiSignBy(tokenid, tokenSerial.getTokenindex(), address);
        // store.insertMultisignby(multiSignBy);
        if (canCreateGenesisBlock(tokens, tokenSerial.getTokenindex())) {
//            TokenInfo tokenInfo = new TokenInfo();
//            tokenInfo.setTokens(tokens);
//            List<TokenSerial> tokenSerials = this.store.getTokenSerialListByTokenid(tokenid);
//            List<MultiSignAddress> multiSignAddresses = this.store.getMultiSignAddressListByTokenid(tokenid);
//            List<MultiSignBy> multiSignBies = this.store.getMultiSignByListByTokenid(tokenid);
//            tokenInfo.setMultiSignAddresses(multiSignAddresses);
//            tokenInfo.setMultiSignBies(multiSignBies);
//            tokenInfo.setTokenSerials(tokenSerials);
            // long amount = tokenSerial.getAmount();
            // Coin coin = Coin.valueOf(amount, tokenid);
            // Block block = createGenesisBlock(coin, tokenid, pubKey, NetworkParameters.BLOCKTYPE_GENESIS_MULTIPLE, tokenInfo);
        }
    }

    public boolean canCreateGenesisBlock(Tokens tokens, long tokenindex) throws BlockStoreException {
        int count = this.store.getCountMultiSignByAlready(tokens.getTokenid(), tokenindex);
        if (count >= tokens.getSignnumber()) {
            return true;
        }
        return false;
    }

    public Block createGenesisBlock(Coin coin, byte[] tokenid, byte[] pubKey, boolean blocktype, TokenInfo tokenInfo) throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        long blocktype0 =  NetworkParameters.BLOCKTYPE_TOKEN_CREATION;
        Block block = new Block(networkParameters, r1.getHash(), r2.getHash(),blocktype0,
                Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
        block.addCoinbaseTransaction(pubKey, coin, tokenInfo);
        block.solve();
        FullPrunedBlockGraph blockgraph = new FullPrunedBlockGraph(networkParameters, store);
        blockgraph.add(block);
        return block;
    }

    public boolean getUTXOSpent(TransactionInput txinput) {
        try {
            if (txinput.isCoinBase())
                return false;
            return blockStore.getTransactionOutput(txinput.getOutpoint().getHash(), txinput.getOutpoint().getIndex())
                    .isSpent();
        } catch (BlockStoreException e) {
            logger.debug("", e);
        }
        return true;
    }

    public BlockEvaluation getUTXOSpender(TransactionOutPoint txout) {
        try {
            return blockStore.getTransactionOutputSpender(txout.getHash(), txout.getIndex());
        } catch (BlockStoreException e) {
            e.printStackTrace();
        }
        return null;
    }

    public UTXO getUTXO(TransactionOutPoint out) throws BlockStoreException {
        return blockStore.getTransactionOutput(out.getHash(), out.getIndex());
    }

    public Optional<Block> addConnected(byte[] bytes, boolean emptyBlock) {
        try {
            Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
            if (!checkBlockExists(block)) {
                FullPrunedBlockGraph blockgraph = new FullPrunedBlockGraph(networkParameters, store);
                blockgraph.add(block);
                logger.debug("addConnected from kafka " + block);
                // if(!block.getTransactions().isEmpty() && emptyBlock)
                // saveEmptyBlock(3);
                return Optional.of(block);
            }else {
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
     * check before add Block from kafka , the block can be already exists. TODO
     * the block may be cached for performance
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
