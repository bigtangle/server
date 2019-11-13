/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2013 Google Inc.
 * Copyright 2015 Andreas Schildbach
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
package net.bigtangle.params;

import java.math.BigInteger;

/**
 * Parameters for the main production network on which people trade goods and services.
 */
public class MainNetParams extends AbstractBitcoinNetParams {
    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public MainNetParams() {
        super();
        //!!!this is initial value and used in genesis block hash, it can be changed only for height
        maxTarget =  new BigInteger(
                "578960377169117509212217050695880916496095398817113098493422368414323410");
        //!!!this is initial value and used in genesis block hash, it can be changed only for height
        maxTargetReward =  new BigInteger(
                "5789603771691175092122170506958809164960953988171130984934223684143236");

        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
    
        packetMagic = 0xf9beb4d9L;
        bip32HeaderPub = 0x0488B21E; //The 4 byte header that serializes in base58 to "xpub".
        bip32HeaderPriv = 0x0488ADE4; //The 4 byte header that serializes in base58 to "xprv"

        genesisPub = "03d6053241c5abca6621c238922e7473977320ef310be0a8538cc2df7ee5a0187c";

        
        // Equihash Settings
        equihashN = 100;
        equihashK = 4;

        id = ID_MAINNET;
        subsidyDecreaseBlockCount = 210000;
        spendableCoinbaseDepth = 100;
    
        dnsSeeds = new String[] {};
//        httpSeeds = new HttpDiscovery.Details[] {
//                // Andreas Schildbach
//                new HttpDiscovery.Details(
//                        ECKey.fromPublicOnly(Utils.HEX.decode("0238746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f")),
//                        URI.create("http://httpseed.bitcoin.schildbach.de/peers")
//                )
//        };

        addrSeeds = new int[] {};

        genesisBlock = createGenesis(this);
    }

    private static MainNetParams instance;
    public static synchronized MainNetParams get() {
        if (instance == null) {
            instance = new MainNetParams();
        }
        return instance;
    }

   
}
