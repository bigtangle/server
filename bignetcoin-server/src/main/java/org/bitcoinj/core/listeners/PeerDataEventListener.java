/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core.listeners;

import org.bitcoinj.core.*;
import org.bitcoinj.store.Peer;
import org.bitcoinj.store.PeerGroup;

/**
 * <p>Implementors can listen to events like blocks being downloaded/transactions being broadcast/connect/disconnects,
 * they can pre-filter messages before they are processed by a {@link Peer} or {@link PeerGroup}, and they can
 * provide transactions to remote peers when they ask for them.</p>
 */
public interface PeerDataEventListener extends BlocksDownloadedEventListener, ChainDownloadStartedEventListener,
        GetDataEventListener, PreMessageReceivedEventListener {
}
