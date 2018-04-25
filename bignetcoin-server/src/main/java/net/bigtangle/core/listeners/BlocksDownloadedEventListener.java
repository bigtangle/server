/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.listeners;

import net.bigtangle.core.*;
import net.bigtangle.store.Peer;
import net.bigtangle.store.PeerGroup;

import javax.annotation.*;

/**
 * <p>Implementors can listen to events like blocks being downloaded/transactions being broadcast/connect/disconnects,
 * they can pre-filter messages before they are procesesed by a {@link Peer} or {@link PeerGroup}, and they can
 * provide transactions to remote peers when they ask for them.</p>
 */
public interface BlocksDownloadedEventListener {

    // TODO: Fix the Block/FilteredBlock type hierarchy so we can avoid the stupid typeless API here.
    /**
     * <p>Called on a Peer thread when a block is received.</p>
     *
     * <p>The block may be a Block object that contains transactions, a Block object that is only a header when
     * fast catchup is being used. If set, filteredBlock can be used to retrieve the list of associated transactions.</p>
     *
     * @param peer       the peer receiving the block
     * @param block      the downloaded block
     * @param filteredBlock if non-null, the object that wraps the block header passed as the block param.
     * @param blocksLeft the number of blocks left to download
     */
    void onBlocksDownloaded(Peer peer, Block block, @Nullable FilteredBlock filteredBlock, long blocksLeft);
}
