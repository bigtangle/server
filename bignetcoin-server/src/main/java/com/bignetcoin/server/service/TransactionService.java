/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Map;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Tokens;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.store.FullPrunedBlockGraph;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.wallet.CoinSelector;
import org.bitcoinj.wallet.DefaultCoinSelector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private TipsService tipsManager;

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    @Autowired
    private BlockService blockService;

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected NetworkParameters networkParameters;

    public ByteBuffer askTransaction() throws Exception {
        Block r1 = blockService.getBlock(getNextBlockToApprove());
        Block r2 = blockService.getBlock(getNextBlockToApprove());

        Block rollingBlock = new Block(this.networkParameters, r1.getHash(), r2.getHash(),
                NetworkParameters.BIGNETCOIN_TOKENID);
        
        byte[] data = rollingBlock.bitcoinSerialize();
        
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        return byteBuffer;
    }

    public byte[] createGenesisBlock(Map<String, Object> request) throws Exception {
        String pubKeyHex = (String) request.get("pubKeyHex");
        long amount = (Integer) request.get("amount");
        String tokenname = (String) request.get("tokenname");
        String description = (String) request.get("description");
        
        byte[] pubKey = Utils.HEX.decode(pubKeyHex);
        byte[] tokenid = pubKey;
        Coin coin = Coin.valueOf(amount, tokenid);
        byte[] data = createGenesisBlock(coin, tokenid, pubKey);
        
        Tokens tokens = new Tokens();
        tokens.setTokenid(tokenid);
        tokens.setTokenname(tokenname);
        tokens.setAmount(amount);
        tokens.setDescription(description);
        store.saveTokens(tokens);
        
        return data;
    }

    public byte[] createGenesisBlock(Coin coin, byte[] tokenid, byte[] pubKey) throws Exception {
        Block r1 = blockService.getBlock(getNextBlockToApprove());
        Block r2 = blockService.getBlock(getNextBlockToApprove());
        Block block = new Block(networkParameters, r1.getHash(), r2.getHash(), tokenid);
        block.addCoinbaseTransaction(pubKey, coin);
        block.solve();
        FullPrunedBlockGraph blockgraph = new FullPrunedBlockGraph(networkParameters, store);
        blockgraph.add(block);
        return block.bitcoinSerialize();
    }

    public Sha256Hash getNextBlockToApprove() throws Exception {
        final SecureRandom random = new SecureRandom();
        return tipsManager.blockToApprove(27, random);
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
}
