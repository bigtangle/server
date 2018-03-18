/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service;

import static org.bitcoinj.core.Coin.FIFTY_COINS;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
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
    protected NetworkParameters networkParameters;

    public ByteBuffer askTransaction() throws Exception {
        Block r1 = blockService.getBlock(getNextBlockToApprove());
        Block r2 = blockService.getBlock(getNextBlockToApprove());
        byte[] r1Data = r1.bitcoinSerialize();
        byte[] r2Data = r2.bitcoinSerialize();
        
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + r1Data.length + 4 + r2Data.length);
        byteBuffer.putInt(r1Data.length);
        byteBuffer.put(r1Data);
        byteBuffer.putInt(r2Data.length);
        byteBuffer.put(r2Data);
        return byteBuffer;
    }

    public Block askTransaction4address(String pubkey, String toaddress, String amount, long tokenid) throws Exception {
        ECKey myKey = ECKey.fromPublicOnly(Utils.parseAsHexOrBase58(pubkey));
       
        Address address = Address.fromBase58(networkParameters, toaddress);
        Coin coin = Coin.parseCoin(amount, tokenid);
        int height = 1;

        Block r1 = blockService.getBlock(getNextBlockToApprove());
        Block r2 = blockService.getBlock(getNextBlockToApprove());
        Block rollingBlock = r2.createNextBlock(null, Block.BLOCK_VERSION_GENESIS, (TransactionOutPoint) null,
                Utils.currentTimeSeconds(), myKey.getPubKey(), FIFTY_COINS, height, r1.getHash(), address.getHash160());

        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());

        Transaction t = new Transaction(networkParameters);
        ECKey toKey = ECKey.fromPublicOnly(address.getHash160());
        t.addOutput(new TransactionOutput(networkParameters, t, coin, toKey));
        TransactionInput input = new TransactionInput(networkParameters, t, new byte[] {}, spendableOutput);

        // no signs first
        t.addInput(input);

        rollingBlock.addTransaction(t);
        // client rollingBlock.solve();
        // blockgraph.add(rollingBlock);
        return rollingBlock;
    }

    public Sha256Hash getNextBlockToApprove() throws Exception {
        final SecureRandom random = new SecureRandom();
        return tipsManager.blockToApprove(networkParameters.getGenesisBlock().getHash(), null, 27, 27, random);
    }

	public boolean getUTXOSpent(TransactionInput txinput) {
		try {
			if (txinput.isCoinBase())
				return false;
			return blockStore.getTransactionOutput(txinput.getOutpoint().getHash(), txinput.getOutpoint().getIndex()).isSpent();
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
