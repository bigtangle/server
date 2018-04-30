/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet.listeners;

import net.bigtangle.wallet.Wallet;

/**
 * <p>Implementors are called when the contents of the wallet changes, for instance due to receiving/sending money
 * or a block chain re-organize. It may be convenient to derive from {@link AbstractWalletEventListener} instead.</p>
 */
public interface WalletChangeEventListener {
    /**
     * <p>Designed for GUI applications to refresh their transaction lists. This callback is invoked in the following
     * situations:</p>
     *
     * <ol>
     *     <li>A new block is received (and thus building transactions got more confidence)</li>
     *     <li>A pending transaction is received</li>
     *     <li>A pending transaction changes confidence due to some non-new-block related event, such as being
     *     announced by more peers or by  a double-spend conflict being observed.</li>
     *     <li>A re-organize occurs. Call occurs only if the re-org modified any of our transactions.</li>
     *     <li>A new spend is committed to the wallet.</li>
     *     <li>The wallet is reset and all transactions removed.<li>
     * </ol>
     *
     * <p>When this is called you can refresh the UI contents from the wallet contents. It's more efficient to use
     * this rather than onTransactionConfidenceChanged() + onReorganize() because you only get one callback per block
     * rather than one per transaction per block. Note that this is <b>not</b> called when a key is added. </p>
     */
    void onWalletChanged(Wallet wallet);
}
