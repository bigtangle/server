/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.signers;

import net.bigtangle.core.Transaction;
import net.bigtangle.crypto.ChildNumber;
import net.bigtangle.script.Script;
import net.bigtangle.wallet.KeyBag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>Implementations of this interface are intended to sign inputs of the given transaction. Given transaction may already
 * be partially signed or somehow altered by other signers.</p>
 * <p>To make use of the signer, you need to add it into the  wallet by
 * calling {@link net.bigtangle.wallet.Wallet#addTransactionSigner(TransactionSigner)}. Signer will be serialized
 * along with the wallet data. In order for a wallet to recreate signer after deserialization, each signer
 * should have no-args constructor</p>
 */
public interface TransactionSigner {

    /**
     * This class wraps transaction proposed to complete keeping a metadata that may be updated, used and effectively
     * shared by transaction signers.
     */
    class ProposedTransaction {

        public final Transaction partialTx;

        /**
         * HD key paths used for each input to derive a signing key. It's useful for multisig inputs only.
         * The keys used to create a single P2SH address have the same derivation path, so to use a correct key each signer
         * has to know a derivation path of signing keys used by previous signers. For each input signers will use the
         * same derivation path and we need to store only one key path per input. As TransactionInput is mutable, inputs
         * are identified by their scriptPubKeys (keys in this map).
         */
        public final Map<Script, List<ChildNumber>> keyPaths;

        public ProposedTransaction(Transaction partialTx) {
            this.partialTx = partialTx;
            this.keyPaths = new HashMap<Script, List<ChildNumber>>();
        }
    }

    class MissingSignatureException extends RuntimeException {
    }

    /**
     * Returns true if this signer is ready to be used.
     */
    boolean isReady();

    /**
     * Returns byte array of data representing state of this signer. It's used to serialize/deserialize this signer
     */
    byte[] serialize();

    /**
     * Uses given byte array of data to reconstruct internal state of this signer
     */
    void deserialize(byte[] data);

    /**
     * Signs given transaction's inputs.
     * Returns true if signer is compatible with given transaction (can do something meaningful with it).
     * Otherwise this method returns false
     */
    boolean signInputs(ProposedTransaction propTx, KeyBag keyBag);

}
