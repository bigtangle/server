/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet.listeners;

import net.bigtangle.wallet.Wallet;

/**
 * <p>Implementors are called when the wallet is reorganized.</p>
 */
public interface WalletReorganizeEventListener {
    // TODO: Finish onReorganize to be more useful.
    /**
     * <p>This is called when a block is received that triggers a block chain re-organization.</p>
     *
     * <p>A re-organize means that the consensus (chain) of the network has diverged and now changed from what we
     * believed it was previously. Usually this won't matter because the new consensus will include all our old
     * transactions assuming we are playing by the rules. However it's theoretically possible for our balance to
     * change in arbitrary ways, most likely, we could lose some money we thought we had.</p>
     *
     * <p>It is safe to use methods of wallet whilst inside this callback.</p>
     */
    void onReorganize(Wallet wallet);
}
