/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet.listeners;

import net.bigtangle.core.Coin;
import net.bigtangle.core.Transaction;
import net.bigtangle.wallet.Wallet;

/**
 * <p>Implementors are called when the contents of the wallet changes, for instance due to receiving/sending money
 * or a block chain re-organize. It may be convenient to derive from {@link AbstractWalletEventListener} instead.</p>
 */
public interface WalletCoinsReceivedEventListener {
    /**
     * This is called when a transaction is seen that sends coins <b>to</b> this wallet, either because it
     * was broadcast across the network or because a block was received. If a transaction is seen when it was broadcast,
     * onCoinsReceived won't be called again when a block containing it is received. If you want to know when such a
     * transaction receives its first confirmation, register a {@link TransactionConfidence} event listener using
     * the object retrieved via {@link net.bigtangle.core.Transaction#getConfidence()}. It's safe to modify the
     * wallet in this callback, for example, by spending the transaction just received.
     *
     * @param wallet      The wallet object that received the coins
     * @param tx          The transaction which sent us the coins.
     * @param prevBalance Balance before the coins were received.
     * @param newBalance  Current balance of the wallet. This is the 'estimated' balance.
     */
    void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance);
}
