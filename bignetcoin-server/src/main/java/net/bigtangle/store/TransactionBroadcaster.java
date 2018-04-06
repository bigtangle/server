/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import net.bigtangle.core.Transaction;

/**
 * A general interface which declares the ability to broadcast transactions. This is implemented
 * by {@link net.bigtangle.store.PeerGroup}.
 */
public interface TransactionBroadcaster {
    /** Broadcast the given transaction on the network */
    TransactionBroadcast broadcastTransaction(final Transaction tx);
}
