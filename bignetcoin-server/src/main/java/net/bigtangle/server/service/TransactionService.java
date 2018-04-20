/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
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

        return new Block(this.networkParameters, r1.getHash(), r2.getHash(), NetworkParameters.BIGNETCOIN_TOKENID,
                NetworkParameters.BLOCKTYPE_TRANSFER, Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));

    }

    public byte[] createGenesisBlock(Map<String, Object> request) throws Exception {
        String pubKeyHex = (String) request.get("pubKeyHex");
        long amount = new Long(request.get("amount").toString());
        String tokenname = (String) request.get("tokenname");
        String description = (String) request.get("description");
        String tokenHex = (String) request.get("tokenHex");
        boolean blocktype = (Boolean) request.get("blocktype");

        byte[] pubKey = Utils.HEX.decode(pubKeyHex);
        byte[] tokenid = Utils.HEX.decode(tokenHex);
        Coin coin = Coin.valueOf(amount, tokenid);
        Block block = createGenesisBlock(coin, tokenid, pubKey, blocktype);
        block.toString();
        // log.debug(networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize()).toString());
        Tokens tokens = new Tokens();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setAmount(amount);
        if (blocktype) {
            tokens.setBlocktype((int) NetworkParameters.BLOCKTYPE_GENESIS);
        } else {
            tokens.setBlocktype((int) NetworkParameters.BLOCKTYPE_GENESIS_MULTIPLE);
        }
        tokens.setDescription(description);
        store.saveTokens(tokens);

        return block.bitcoinSerialize();
    }

    public Block createGenesisBlock(Coin coin, byte[] tokenid, byte[] pubKey, boolean blocktype) throws Exception {
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        long blocktype0 = blocktype ? NetworkParameters.BLOCKTYPE_GENESIS
                : NetworkParameters.BLOCKTYPE_GENESIS_MULTIPLE;
        Block block = new Block(networkParameters, r1.getHash(), r2.getHash(), tokenid, blocktype0,
                Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()));
        block.addCoinbaseTransaction(pubKey, coin);
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
            e.printStackTrace();
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

    public void kafkaSend(Block block) {

    }
}
