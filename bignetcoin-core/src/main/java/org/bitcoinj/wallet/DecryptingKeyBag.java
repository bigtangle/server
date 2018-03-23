/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.wallet;

import org.bitcoinj.core.ECKey;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A DecryptingKeyBag filters a pre-existing key bag, decrypting keys as they are requested using the provided
 * AES key. If the keys are encrypted and no AES key provided, {@link org.bitcoinj.core.ECKey.KeyIsEncryptedException}
 * will be thrown.
 */
public class DecryptingKeyBag implements KeyBag {
    protected final KeyBag target;
    protected final KeyParameter aesKey;

    public DecryptingKeyBag(KeyBag target, @Nullable KeyParameter aesKey) {
        this.target = checkNotNull(target);
        this.aesKey = aesKey;
    }

    @Nullable
    public ECKey maybeDecrypt(ECKey key) {
        if (key == null)
            return null;
        else if (key.isEncrypted()) {
            if (aesKey == null)
                throw new ECKey.KeyIsEncryptedException();
            return key.decrypt(aesKey);
        } else {
            return key;
        }
    }

    private RedeemData maybeDecrypt(RedeemData redeemData) {
        List<ECKey> decryptedKeys = new ArrayList<ECKey>();
        for (ECKey key : redeemData.keys) {
            decryptedKeys.add(maybeDecrypt(key));
        }
        return RedeemData.of(decryptedKeys, redeemData.redeemScript);
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        return maybeDecrypt(target.findKeyFromPubHash(pubkeyHash));
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        return maybeDecrypt(target.findKeyFromPubKey(pubkey));
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
        return maybeDecrypt(target.findRedeemDataFromScriptHash(scriptHash));
    }
}
