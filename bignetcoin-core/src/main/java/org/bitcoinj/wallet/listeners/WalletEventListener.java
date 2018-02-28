/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet.listeners;

import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;

/**
 * <p>Common interface for wallet changes and transactions.</p>
 * @deprecated Use the superinterfaces directly instead.
 */
@Deprecated
public interface WalletEventListener extends
        KeyChainEventListener, WalletChangeEventListener,
        WalletCoinsReceivedEventListener, WalletCoinsSentEventListener,
        WalletReorganizeEventListener, ScriptsChangeEventListener,
        TransactionConfidenceEventListener {
}
