/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package com.bignetcoin.store;

import org.bitcoinj.core.Transaction;

/**
 * A general interface which declares the ability to broadcast transactions. This is implemented
 * by {@link com.bignetcoin.store.PeerGroup}.
 */
public interface TransactionBroadcaster {
    /** Broadcast the given transaction on the network */
    TransactionBroadcast broadcastTransaction(final Transaction tx);
}
