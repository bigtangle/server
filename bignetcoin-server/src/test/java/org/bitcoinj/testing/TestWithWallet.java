/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.testing;

import static org.bitcoinj.testing.FakeTxBuilder.createFakeBlock;
import static org.bitcoinj.testing.FakeTxBuilder.createFakeTx;

import javax.annotation.Nullable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockStore;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.Wallet;

import com.bignetcoin.store.AbstractBlockGraph;
import com.bignetcoin.store.BlockGraph;
import com.bignetcoin.store.MemoryBlockStore;

// TODO: This needs to be somewhat rewritten - the "sendMoneyToWallet" methods aren't sending via the block chain object

/**
 * A utility class that you can derive from in your unit tests. TestWithWallet sets up an empty wallet,
 * an in-memory block store and a block chain object. It also provides helper methods for filling the wallet
 * with money in whatever ways you wish. Note that for simplicity with amounts, this class sets the default
 * fee per kilobyte to zero in setUp.
 */
public class TestWithWallet {
    protected static final NetworkParameters PARAMS = UnitTestParams.get();
    protected ECKey myKey;
    protected Address myAddress;
    protected Wallet wallet;
    protected BlockGraph chain;
    protected BlockStore blockStore;

    public void setUp() throws Exception {
        BriefLogFormatter.init();
        Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
        wallet = new Wallet(PARAMS);
        myKey = wallet.currentReceiveKey();
        myAddress = myKey.toAddress(PARAMS);
        blockStore = new MemoryBlockStore(PARAMS);
        chain = new BlockGraph(PARAMS,  blockStore);
    }

    public void tearDown() throws Exception {
    }

    @Nullable
    protected Transaction sendMoneyToWallet(Wallet wallet, AbstractBlockGraph.NewBlockType type, Transaction... transactions)
            throws VerificationException {
        if (type == null) {
            // Pending transaction
        
        }      
        if (transactions.length == 1)
            return wallet.getTransaction(transactions[0].getHash());  // Can be null if tx is a double spend that's otherwise irrelevant.
        else
            return null;
    }

    @Nullable
    protected Transaction sendMoneyToWallet(Wallet wallet, AbstractBlockGraph.NewBlockType type, Coin value, Address toAddress) throws VerificationException {
        return sendMoneyToWallet(wallet, type, createFakeTx(PARAMS, value, toAddress));
    }

    @Nullable
    protected Transaction sendMoneyToWallet(Wallet wallet, AbstractBlockGraph.NewBlockType type, Coin value, ECKey toPubKey) throws VerificationException {
        return sendMoneyToWallet(wallet, type, createFakeTx(PARAMS, value, toPubKey));
    }

    @Nullable
    protected Transaction sendMoneyToWallet(AbstractBlockGraph.NewBlockType type, Transaction... transactions) throws VerificationException {
        return sendMoneyToWallet(this.wallet, type, transactions);
    }

    @Nullable
    protected Transaction sendMoneyToWallet(AbstractBlockGraph.NewBlockType type, Coin value) throws VerificationException {
        return sendMoneyToWallet(this.wallet, type, value, myAddress);
    }

    @Nullable
    protected Transaction sendMoneyToWallet(AbstractBlockGraph.NewBlockType type, Coin value, Address toAddress) throws VerificationException {
        return sendMoneyToWallet(this.wallet, type, value, toAddress);
    }

    @Nullable
    protected Transaction sendMoneyToWallet(AbstractBlockGraph.NewBlockType type, Coin value, ECKey toPubKey) throws VerificationException {
        return sendMoneyToWallet(this.wallet, type, value, toPubKey);
    }
}
