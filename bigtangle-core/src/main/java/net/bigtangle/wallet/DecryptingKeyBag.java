/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2014 The bitcoinj authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bigtangle.wallet;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.ECKey2;

/**
 * A DecryptingKeyBag filters a pre-existing key bag, decrypting keys as they are requested using the provided
 * AES key. If the keys are encrypted and no AES key provided, {@link net.bigtangle.core.ECKey.KeyIsEncryptedException}
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
    public ECKey2 maybeDecrypt(ECKey2 key) {
        if (key == null)
            return null;
        else if (key.isEncrypted()) {
            if (aesKey == null)
                throw new ECKey2.KeyIsEncryptedException();
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
