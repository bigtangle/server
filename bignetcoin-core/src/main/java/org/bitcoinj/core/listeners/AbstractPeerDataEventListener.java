/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core.listeners;

import org.bitcoinj.core.*;

import javax.annotation.*;
import java.util.*;

/**
 * Deprecated: implement the more specific event listener interfaces instead to fill out only what you need
 */
@Deprecated
public abstract class AbstractPeerDataEventListener implements PeerDataEventListener {
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
    public List<Message> getData(Peer peer, GetDataMessage m) {
        return null;
    }
}
