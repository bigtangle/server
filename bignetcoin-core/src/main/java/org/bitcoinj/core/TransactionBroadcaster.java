/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

/**
 * A general interface which declares the ability to broadcast transactions. This is implemented
 * by {@link org.bitcoinj.core.PeerGroup}.
 */
public interface TransactionBroadcaster {
    /** Broadcast the given transaction on the network */
    TransactionBroadcast broadcastTransaction(final Transaction tx);
}
