/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import org.bitcoinj.core.listeners.*;
import org.bitcoinj.core.*;

import javax.annotation.*;
import java.util.List;
import java.util.Set;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativePeerEventListener implements PeerConnectionEventListener, PeerDataEventListener, OnTransactionBroadcastListener {
    public long ptr;

    @Override
    public native void onPeersDiscovered(Set<PeerAddress> peerAddresses);

    @Override
    public native void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft);

    @Override
    public native void onChainDownloadStarted(Peer peer, int blocksLeft);

    @Override
    public native void onPeerConnected(Peer peer, int peerCount);

    @Override
    public native void onPeerDisconnected(Peer peer, int peerCount);

    @Override
    public native Message onPreMessageReceived(Peer peer, Message m);

    @Override
    public native void onTransaction(Peer peer, Transaction t);

    @Override
    public native List<Message> getData(Peer peer, GetDataMessage m);
}
