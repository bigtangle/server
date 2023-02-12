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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.InvalidCipherTextException;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.ECKey2;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.encrypt.ECIESCoder;
import net.bigtangle.wallet.KeyChainGroup;
import net.bigtangle.wallet.Protos;
import net.bigtangle.wallet.UnreadableWalletException;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.WalletProtobufSerializer;

public class WalletUtil {
    protected static final Logger log = LoggerFactory.getLogger(WalletUtil.class);
 

    public static byte[] createWallet(NetworkParameters params) throws Exception {

        Wallet wallet =   Wallet.fromKeys(params, new ECKey()); // default

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        new WalletProtobufSerializer().writeWallet(wallet, outStream);
        return outStream.toByteArray();

    }

    public static byte[] createWallet(NetworkParameters params, int size) throws IOException {
        KeyChainGroup kcg;
        kcg = new KeyChainGroup(params);
        kcg.setLookaheadSize(size);
 
        Wallet wallet = new Wallet(params, kcg); // default

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        new WalletProtobufSerializer().writeWallet(wallet, outStream);
        return outStream.toByteArray();

    }

    public static Wallet loadWallet(boolean shouldReplayWallet, InputStream walletStream, NetworkParameters params)
            throws IOException, UnreadableWalletException {
 
        Wallet wallet;
        try {

            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer = new WalletProtobufSerializer();
            wallet = serializer.readWallet(params, null, proto);

        } finally {
            walletStream.close();
        }
        return wallet;
    }

   

    public static byte[] encrypt( ECKey2 ecKey , byte[] payload ) throws InvalidCipherTextException, IOException   {
      return  ECIESCoder.encrypt(ecKey.getPubKeyPoint(), payload);
     
    }

    public static byte[] decrypt( ECKey2 ecKey , byte[] cipher) throws InvalidCipherTextException, IOException    {
      return    ECIESCoder.decrypt(ecKey.getPrivKey(), cipher);
    }

}
