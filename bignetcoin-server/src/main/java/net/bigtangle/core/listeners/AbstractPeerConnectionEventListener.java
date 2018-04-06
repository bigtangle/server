/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.listeners;

import net.bigtangle.core.*;
import net.bigtangle.store.Peer;

import java.util.*;

/**
 * Deprecated: implement the more specific event listener interfaces instead to fill out only what you need
 */
@Deprecated
public abstract class AbstractPeerConnectionEventListener implements PeerConnectionEventListener {
    @Override
    public void onPeersDiscovered(Set<PeerAddress> peerAddresses) {
        // Do nothing
    }

    @Override
    public void onPeerConnected(Peer peer, int peerCount) {
        // Do nothing
    }

    @Override
    public void onPeerDisconnected(Peer peer, int peerCount) {
        // Do nothing
    }
}
