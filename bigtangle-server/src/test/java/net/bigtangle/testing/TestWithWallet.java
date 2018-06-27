/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.testing;

import static net.bigtangle.testing.FakeTxBuilder.createFakeTx;

import javax.annotation.Nullable;

import net.bigtangle.core.Address;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.VerificationException;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.store.MemoryBlockStore;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.wallet.Wallet;

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
 
    protected BlockStore blockStore;

    public void setUp() throws Exception {
        BriefLogFormatter.init();
        Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
        wallet = new Wallet(PARAMS);
        myKey = wallet.currentReceiveKey();
        myAddress = myKey.toAddress(PARAMS);
        blockStore = new MemoryBlockStore(PARAMS);
      
    }

    public void tearDown() throws Exception {
    }

    @Nullable
    protected Transaction sendMoneyToWallet(Wallet wallet, Transaction... transactions)
            throws VerificationException {
        if (transactions.length == 1)
            return wallet.getTransaction(transactions[0].getHash());  // Can be null if tx is a double spend that's otherwise irrelevant.
        else
            return null;
    }

    @Nullable
    protected Transaction sendMoneyToWallet(Wallet wallet,  Coin value, Address toAddress) throws VerificationException {
        return sendMoneyToWallet(wallet, createFakeTx(PARAMS, value, toAddress));
    }

    @Nullable
    protected Transaction sendMoneyToWallet(Wallet wallet,  Coin value, ECKey toPubKey) throws VerificationException {
        return sendMoneyToWallet(wallet, createFakeTx(PARAMS, value, toPubKey));
    }

    @Nullable
    protected Transaction sendMoneyToWallet( Transaction... transactions) throws VerificationException {
        return sendMoneyToWallet(this.wallet, transactions);
    }

    @Nullable
    protected Transaction sendMoneyToWallet( Coin value) throws VerificationException {
        return sendMoneyToWallet(this.wallet, value, myAddress);
    }

    @Nullable
    protected Transaction sendMoneyToWallet( Coin value, Address toAddress) throws VerificationException {
        return sendMoneyToWallet(this.wallet, value, toAddress);
    }

    @Nullable
    protected Transaction sendMoneyToWallet( Coin value, ECKey toPubKey) throws VerificationException {
        return sendMoneyToWallet(this.wallet, value, toPubKey);
    }
}
