/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet.listeners;

import net.bigtangle.script.Script;
import net.bigtangle.wallet.Wallet;

import java.util.List;

/**
 * <p>Implementors are called when the contents of the wallet changes, for instance due to receiving/sending money
 * or a block chain re-organize. It may be convenient to derive from {@link AbstractWalletEventListener} instead.</p>
 */
public interface ScriptsChangeEventListener {
    /**
     * Called whenever a new watched script is added to the wallet.
     *
     * @param isAddingScripts will be true if added scripts, false if removed scripts.
     */
    void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts);
}
