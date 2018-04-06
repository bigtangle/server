/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import static com.google.common.base.Preconditions.checkNotNull;

import net.bigtangle.core.Transaction;

/**
 * Stores data about a transaction that is only relevant to the {@link net.bigtangle.wallet.Wallet} class.
 */
public class WalletTransaction {
    public enum Pool {
        UNSPENT, // unspent in best chain
        SPENT, // spent in best chain
        DEAD, // double-spend in alt chain
        PENDING, // a pending tx we would like to go into the best chain
    }
    private final Transaction transaction;
    private final Pool pool;
    
    public WalletTransaction(Pool pool, Transaction transaction) {
        this.pool = checkNotNull(pool);
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }
    
    public Pool getPool() {
        return pool;
    }
}

