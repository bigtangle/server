/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

package net.bigtangle.kits;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.CryptoException;

import net.bigtangle.core.Context;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.wallet.KeyChainGroup;
import net.bigtangle.wallet.Protos;
import net.bigtangle.wallet.UnreadableWalletException;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.WalletProtobufSerializer;

public class WalletUtil {
    protected static final Logger log = LoggerFactory.getLogger(WalletUtil.class);

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";

    public static byte[] createWallet(NetworkParameters params) throws IOException {

        return createWallet(params, 0);

    }

    public static byte[] createWallet(NetworkParameters params, int size) throws IOException {
        KeyChainGroup kcg;
        kcg = new KeyChainGroup(params);
        kcg.setLookaheadSize(size);
        Context context = new Context(params);
        Context.propagate(context);
        Wallet wallet = new Wallet(params, kcg); // default

        wallet.freshReceiveKey();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        new WalletProtobufSerializer().writeWallet(wallet, outStream);
        return outStream.toByteArray();

    }

    public static Wallet loadWallet(boolean shouldReplayWallet, InputStream walletStream, NetworkParameters params)
            throws IOException, UnreadableWalletException {
        Context context = new Context(params);
        Context.propagate(context);
        Wallet wallet;
        try {

            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer = new WalletProtobufSerializer();
            wallet = serializer.readWallet(params, null, proto);
            if (shouldReplayWallet)
                wallet.reset();
        } finally {
            walletStream.close();
        }
        return wallet;
    }

    public static byte[]  doCrypto(int cipherMode, byte[] key, byte[] inputBytes )
            throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException,
            BadPaddingException {

        Key secretKey = new SecretKeySpec(key , ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(cipherMode, secretKey);
        return  cipher.doFinal(inputBytes);
    }

    public static byte[] encrypt(byte[] key, byte[] inputBytes ) throws InvalidKeyException,
            NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
       return  doCrypto(Cipher.ENCRYPT_MODE, key, inputBytes );
    }

    public static byte[] decrypt(byte[] key, byte[] inputBytes) throws InvalidKeyException,
            NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
      return  doCrypto(Cipher.DECRYPT_MODE, key, inputBytes);
    }

}
