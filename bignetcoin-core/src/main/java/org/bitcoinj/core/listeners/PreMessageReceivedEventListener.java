/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core.listeners;

import org.bitcoinj.core.*;

/**
 * <p>Implementors can listen to events like blocks being downloaded/transactions being broadcast/connect/disconnects,
 * they can pre-filter messages before they are procesesed by a {@link Peer} or {@link PeerGroup}, and they can
 * provide transactions to remote peers when they ask for them.</p>
 */
public interface PreMessageReceivedEventListener {

    /**
     * <p>Called when a message is received by a peer, before the message is processed. The returned message is
     * processed instead. Returning null will cause the message to be ignored by the Peer returning the same message
     * object allows you to see the messages received but not change them. The result from one event listeners
     * callback is passed as "m" to the next, forming a chain.</p>
     *
     * <p>Note that this will never be called if registered with any executor other than
     * {@link org.bitcoinj.utils.Threading#SAME_THREAD}</p>
     */
    Message onPreMessageReceived(Peer peer, Message m);
}
