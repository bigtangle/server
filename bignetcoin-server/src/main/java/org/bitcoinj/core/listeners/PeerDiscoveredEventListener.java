/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core.listeners;

import org.bitcoinj.core.PeerAddress;

import com.bignetcoin.store.Peer;

import java.util.Set;

/**
 * <p>Implementors can listen to events for peers being discovered.</p>
 */
public interface PeerDiscoveredEventListener {
    /**
     * <p>Called when peers are discovered, this happens at startup of {@link PeerGroup} or if we run out of
     * suitable {@link Peer}s to connect to.</p>
     *
     * @param peerAddresses the set of discovered {@link PeerAddress}es
     */
    void onPeersDiscovered(Set<PeerAddress> peerAddresses);
}
