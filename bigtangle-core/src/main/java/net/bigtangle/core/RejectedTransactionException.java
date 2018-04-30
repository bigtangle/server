/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

/**
 * This exception is used by the TransactionBroadcast class to indicate that a broadcast
 * Transaction has been rejected by the network, for example because it violates a
 * protocol rule. Note that not all invalid transactions generate a reject message, and
 * some peers may never do so.
 */
public class RejectedTransactionException extends Exception {
    private Transaction tx;
    private RejectMessage rejectMessage;

    public RejectedTransactionException(Transaction tx, RejectMessage rejectMessage) {
        super(rejectMessage.toString());
        this.tx = tx;
        this.rejectMessage = rejectMessage;
    }

    /** Return the original Transaction object whose broadcast was rejected. */
    public Transaction getTransaction() { return tx; }

    /** Return the RejectMessage object representing the broadcast rejection. */
    public RejectMessage getRejectMessage() { return rejectMessage; }
}
