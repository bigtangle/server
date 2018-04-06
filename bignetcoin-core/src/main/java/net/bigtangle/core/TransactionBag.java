/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import net.bigtangle.script.Script;
import net.bigtangle.wallet.WalletTransaction;

import java.util.Map;

/**
 * This interface is used to abstract the {@link net.bigtangle.wallet.Wallet} and the {@link net.bigtangle.core.Transaction}
 */
public interface TransactionBag {
    /** Returns true if this wallet contains a public key which hashes to the given hash. */
    boolean isPubKeyHashMine(byte[] pubkeyHash);

    /** Returns true if this wallet is watching transactions for outputs with the script. */
    boolean isWatchedScript(Script script);

    /** Returns true if this wallet contains a keypair with the given public key. */
    boolean isPubKeyMine(byte[] pubkey);

    /** Returns true if this wallet knows the script corresponding to the given hash. */
    boolean isPayToScriptHashMine(byte[] payToScriptHash);

    /** Returns transactions from a specific pool. */
    Map<Sha256Hash, Transaction> getTransactionPool(WalletTransaction.Pool pool);
}
