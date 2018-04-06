/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.listeners;

import net.bigtangle.core.Block;
import net.bigtangle.core.FilteredBlock;
import net.bigtangle.core.GetDataMessage;
import net.bigtangle.core.Message;
import net.bigtangle.core.PeerAddress;
import net.bigtangle.core.Transaction;
import net.bigtangle.store.Peer;

import javax.annotation.*;
import java.util.List;
import java.util.Set;

/**
 * Deprecated: implement the more specific event listener interfaces instead to fill out only what you need
 */
@Deprecated
public abstract class AbstractPeerEventListener extends AbstractPeerDataEventListener implements PeerConnectionEventListener, OnTransactionBroadcastListener {
    @Override
    public void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, int blocksLeft) {
    }

    @Override
    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
    }

    @Override
    public Message onPreMessageReceived(Peer peer, Message m) {
        // Just pass the message right through for further processing.
        return m;
    }

    @Override
    public void onTransaction(Peer peer, Transaction t) {
    }

    @Override
    public List<Message> getData(Peer peer, GetDataMessage m) {
        return null;
    }

    @Override
    public void onPeersDiscovered(Set<PeerAddress> peerAddresses) {
    }

    @Override
    public void onPeerConnected(Peer peer, int peerCount) {
    }

    @Override
    public void onPeerDisconnected(Peer peer, int peerCount) {
    }
}
