/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet.listeners;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

/**
 * <p>Implementors are called when the contents of the wallet changes, for instance due to receiving/sending money
 * or a block chain re-organize. It may be convenient to derive from {@link AbstractWalletEventListener} instead.</p>
 */
public interface WalletCoinsSentEventListener {

    /**
     * This is called when a transaction is seen that sends coins <b>from</b> this wallet, either
     * because it was broadcast across the network or because a block was received. This may at first glance seem
     * useless, because in the common case you already know about such transactions because you created them with
     * the Wallets createSend/sendCoins methods. However when you have a wallet containing only keys, and you wish
     * to replay the block chain to fill it with transactions, it's useful to find out when a transaction is discovered
     * that sends coins from the wallet.<p>
     *
     * It's safe to modify the wallet from inside this callback, but if you're replaying the block chain you should
     * be careful to avoid such modifications. Otherwise your changes may be overridden by new data from the chain.
     *
     * @param wallet       The wallet object that this callback relates to (that sent the coins).
     * @param tx           The transaction that sent the coins to someone else.
     * @param prevBalance  The wallets balance before this transaction was seen.
     * @param newBalance   The wallets balance after this transaction was seen. This is the 'estimated' balance.
     */
    void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance);
}
