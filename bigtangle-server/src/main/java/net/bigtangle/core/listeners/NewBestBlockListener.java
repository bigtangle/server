/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.listeners;

import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.VerificationException;

/**
 * Listener interface for when a new block on the best chain is seen.
 */
public interface NewBestBlockListener {
    /**
     * Called when a new block on the best chain is seen, after relevant
     * transactions are extracted and sent to us via either
     * {@link TransactionReceivedInBlockListener#receiveFromBlock(org.bitcoinj.core.Transaction, org.bitcoinj.core.StoredBlock, org.bitcoinj.core.BlockChain.NewBlockType, int relativityOffset)}
     * or {@link TransactionReceivedInBlockListener#notifyTransactionIsInBlock(net.bigtangle.core.Sha256Hash, net.bigtangle.core.StoredBlock, org.bitcoinj.core.BlockChain.NewBlockType, int)}.
     * If this block is causing a re-organise to a new chain, this method is NOT
     * called even though the block may be the new best block: your reorganize
     * implementation is expected to do whatever would normally be done do for a
     * new best block in this case.
     */
    void notifyNewBestBlock(final StoredBlock block) throws VerificationException;
}
