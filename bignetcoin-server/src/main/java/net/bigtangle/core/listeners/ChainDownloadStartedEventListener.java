/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.listeners;

import com.bignetcoin.store.Peer;
import com.bignetcoin.store.PeerGroup;

import net.bigtangle.core.*;

/**
 * <p>Implementors can listen to events like blocks being downloaded/transactions being broadcast/connect/disconnects,
 * they can pre-filter messages before they are procesesed by a {@link Peer} or {@link PeerGroup}, and they can
 * provide transactions to remote peers when they ask for them.</p>
 */
public interface ChainDownloadStartedEventListener {

    /**
     * Called when a download is started with the initial number of blocks to be downloaded.
     *
     * @param peer       the peer receiving the block
     * @param blocksLeft the number of blocks left to download
     */
    void onChainDownloadStarted(Peer peer, int blocksLeft);
}
