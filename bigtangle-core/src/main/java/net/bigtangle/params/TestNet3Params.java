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

package net.bigtangle.params;

import net.bigtangle.core.Utils;

/**
 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
 * and testing of applications and new Bitcoin versions.
 */
public class TestNet3Params extends AbstractBitcoinNetParams {
    public TestNet3Params() {
        super();
        id = ID_TESTNET;
        packetMagic = 0x0b110907;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
 
        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        dumpedPrivateKeyHeader = 239;
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = 210000;
       // checkState(genesisHash.equals("5dc7ca354ffb33d9b1bddd6769ea56e5272216ed306f180cffa7ec7169e441cf"));
        alertSigningKey = Utils.HEX.decode("04302390343f91cc401d56d68b123028bf52e5fca1939df127f63c6467cdf9c8e2c14b61104cf817d0b780da337893ecc4aaff1309e536162dabbdb45200ca2b0a");

        dnsSeeds = new String[] {
                "testnet-seed.bitcoin.jonasschnelli.ch", // Jonas Schnelli
                "testnet-seed.bluematt.me",              // Matt Corallo
                "testnet-seed.bitcoin.petertodd.org",    // Peter Todd
                "testnet-seed.bitcoin.schildbach.de",    // Andreas Schildbach
                "bitcoin-testnet.bloqseeds.net",         // Bloq
        };
        addrSeeds = null;
        bip32HeaderPub = 0x043587CF;
        bip32HeaderPriv = 0x04358394;

        
        // Equihash Settings
        equihashN = 40;
        equihashK = 4;

        genesisBlock = createGenesis(this);
    }

    private static TestNet3Params instance;
    public static synchronized TestNet3Params get() {
        if (instance == null) {
            instance = new TestNet3Params();
        }
        return instance;
    }
}
    
