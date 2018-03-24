/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core.listeners;

import org.bitcoinj.core.*;
import org.bitcoinj.store.Peer;
import org.bitcoinj.store.PeerGroup;

import javax.annotation.*;
import java.util.*;

/**
 * <p>Implementors can listen to events like blocks being downloaded/transactions being broadcast/connect/disconnects,
 * they can pre-filter messages before they are procesesed by a {@link Peer} or {@link PeerGroup}, and they can
 * provide transactions to remote peers when they ask for them.</p>
 */
public interface GetDataEventListener {

    /**
     * <p>Called when a peer receives a getdata message, usually in response to an "inv" being broadcast. Return as many
     * items as possible which appear in the {@link GetDataMessage}, or null if you're not interested in responding.</p>
     *
     * <p>Note that this will never be called if registered with any executor other than
     * {@link org.bitcoinj.utils.Threading#SAME_THREAD}</p>
     */
    @Nullable
    List<Message> getData(Peer peer, GetDataMessage m);
}
