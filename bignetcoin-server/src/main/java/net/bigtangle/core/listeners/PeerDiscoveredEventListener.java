/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.listeners;

import com.bignetcoin.store.Peer;

import net.bigtangle.core.PeerAddress;

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
