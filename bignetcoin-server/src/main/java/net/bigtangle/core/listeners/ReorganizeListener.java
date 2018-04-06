/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.listeners;

import java.util.List;

import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.VerificationException;

/**
 * Listener interface for when the best chain has changed.
 */
public interface ReorganizeListener {

    /**
     * Called by the {@link net.bigtangle.store.BlockGraph} when the best chain
     * (representing total work done) has changed. In this case,
     * we need to go through our transactions and find out if any have become invalid. It's possible for our balance
     * to go down in this case: money we thought we had can suddenly vanish if the rest of the network agrees it
     * should be so.<p>
     *
     * The oldBlocks/newBlocks lists are ordered height-wise from top first to bottom last (i.e. newest blocks first).
     */
    void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks,
                    List<StoredBlock> newBlocks) throws VerificationException;
}
