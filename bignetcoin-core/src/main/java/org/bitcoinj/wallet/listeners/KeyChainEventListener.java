/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet.listeners;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.wallet.KeyChain;

import java.util.List;

public interface KeyChainEventListener {
    /**
     * Called whenever a new key is added to the key chain, whether that be via an explicit addition or due to some
     * other automatic derivation. See the documentation for your {@link KeyChain} implementation for details on what
     * can trigger this event.
     */
    void onKeysAdded(List<ECKey> keys);
}
