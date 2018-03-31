/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core.listeners;

import com.bignetcoin.store.Peer;

/**
 * <p>Implementors can listen to events like peer discovery, connect or disconnects.</p>
 *
 * @deprecated Use the single event interfaces instead
 */
@Deprecated
public interface PeerConnectionEventListener extends PeerConnectedEventListener,
        PeerDiscoveredEventListener, PeerDisconnectedEventListener {

    /**
     * Called when a peer is disconnected. Note that this won't be called if the listener is registered on a
     * {@link PeerGroup} and the group is in the process of shutting down. If this listener is registered to a
     * {@link Peer} instead of a {@link PeerGroup}, peerCount will always be 0. This handler can be called without
     * a corresponding invocation of onPeerConnected if the initial connection is never successful.
     *
     * @param peer
     * @param peerCount the total number of connected peers
     */
    void onPeerDisconnected(Peer peer, int peerCount);
}
