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
 * they can pre-filter messages before they are processed by a {@link Peer} or {@link PeerGroup}, and they can
 * provide transactions to remote peers when they ask for them.</p>
 */
public interface PeerDataEventListener extends BlocksDownloadedEventListener, ChainDownloadStartedEventListener,
        GetDataEventListener, PreMessageReceivedEventListener {
}
