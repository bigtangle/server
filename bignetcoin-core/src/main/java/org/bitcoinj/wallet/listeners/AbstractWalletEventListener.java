/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet.listeners;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;

import java.util.List;

/**
 * Deprecated: implement the more specific event listener interfaces instead.
 */
@Deprecated
public abstract class AbstractWalletEventListener extends AbstractKeyChainEventListener implements WalletEventListener {
    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        onChange();
    }

    @Override
    public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        onChange();
    }
 
 
    @Override
    public void onKeysAdded(List<ECKey> keys) {
        onChange();
    }

    @Override
    public void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts) {
        onChange();
    }

    @Override
    public void onWalletChanged(Wallet wallet) {
        onChange();
    }

    /**
     * Default method called on change events.
     */
    public void onChange() {
    }
}
