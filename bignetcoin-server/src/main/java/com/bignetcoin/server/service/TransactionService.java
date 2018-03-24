/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.store.BlockStoreException;
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

        System.out.println("send, r1 : " + r1.getHashAsString() + ", r2 : " + r2.getHashAsString());

        byte[] r1Data = r1.bitcoinSerialize();
        byte[] r2Data = r2.bitcoinSerialize();

        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + r1Data.length + 4 + r2Data.length);
        byteBuffer.putInt(r1Data.length);
        byteBuffer.put(r1Data);
        byteBuffer.putInt(r2Data.length);
        byteBuffer.put(r2Data);

        System.out.print(r1.toString());

        return byteBuffer;
    }

    public byte[] createGenesisBlock(byte[] bytes) throws Exception {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        int amount = byteBuffer.getInt();
        int len = byteBuffer.getInt();
        byte[] pubKey = new byte[len];
        byteBuffer.get(pubKey);
        long tokenid = blockService.getNextTokenId();
        Coin coin = Coin.valueOf(amount, tokenid);

        return createGenesisBlock(coin, tokenid, pubKey);
    }

    public byte[] createGenesisBlock(Coin coin, long tokenid, byte[] pubKey) throws Exception {

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
        return tipsManager.blockToApprove(networkParameters.getGenesisBlock().getHash(), null, 27, 27, random);
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
