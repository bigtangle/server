/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.testing;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.signers.CustomTransactionSigner;
import org.bitcoinj.wallet.DeterministicKeyChain;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * <p>Transaction signer which uses provided keychain to get signing keys from. It relies on previous signer to provide
 * derivation path to be used to get signing key and, once gets the key, just signs given transaction immediately.</p>
 * It should not be used in test scenarios involving serialization as it doesn't have proper serialize/deserialize
 * implementation.
 */
public class KeyChainTransactionSigner extends CustomTransactionSigner {

    private DeterministicKeyChain keyChain;

    public KeyChainTransactionSigner() {
    }

    public KeyChainTransactionSigner(DeterministicKeyChain keyChain) {
        this.keyChain = keyChain;
    }

    @Override
    protected SignatureAndKey getSignature(Sha256Hash sighash, List<ChildNumber> derivationPath) {
        ImmutableList<ChildNumber> keyPath = ImmutableList.copyOf(derivationPath);
        DeterministicKey key = keyChain.getKeyByPath(keyPath, true);
        return new SignatureAndKey(key.sign(sighash), key.dropPrivateBytes().dropParent());
    }
}
