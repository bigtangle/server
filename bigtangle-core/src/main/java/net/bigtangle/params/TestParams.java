/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.params;

import java.math.BigInteger;

/**
 * Parameters for the main production network on which people trade goods and
 * services.
 */
public class TestParams extends AbstractBitcoinNetParams {

    public TestParams() {
        super();

        id = ID_UNITTESTNET;

        maxTarget = new BigInteger("5789603771691175092122170506958809164960953988171130984934223684143234100");

        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        packetMagic = 0xf9beb4d9L;
        bip32HeaderPub = 0x0488B21E; // The 4 byte header that serializes in
                                     // base58 to "xpub".
        bip32HeaderPriv = 0x0488ADE4; // The 4 byte header that serializes in
                                      // base58 to "xprv"

        // Equihash Settings
        equihashN = 100;
        equihashK = 4;

        genesisBlock = createGenesis(this);
    }

    private static TestParams instance;

    public static synchronized TestParams get() {
        if (instance == null) {
            instance = new TestParams();
        }
        return instance;
    }

}
