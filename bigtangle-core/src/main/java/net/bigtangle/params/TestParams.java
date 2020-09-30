/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.params;

import java.math.BigInteger;
import java.util.Arrays;

import com.google.common.collect.ImmutableList;

import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.wallet.ServerPool;

/**
 * Parameters for the main production network on which people trade goods and
 * services.
 */
public class TestParams extends AbstractBitcoinNetParams {

    public String CNY = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";

    public TestParams() {
        super();

        id = ID_UNITTESTNET;

        maxTarget = new BigInteger("578960377169117509212217050695880916496095398817113098493422368414323410000");
        maxTargetReward = maxTarget.subtract(new BigInteger("100"));

        dumpedPrivateKeyHeader = 128;
        addressHeader = 0;
        p2shHeader = 5;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        packetMagic = 0xf9beb4d9L;
        bip32HeaderPub = 0x0488B21E; // The 4 byte header that serializes in
                                     // base58 to "xpub".
        bip32HeaderPriv = 0x0488ADE4; // The 4 byte header that serializes in
                                      // base58 to "xprv"
        genesisPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
        permissionDomainname = ImmutableList.of(genesisPub);

        orderBaseTokens = ImmutableList.of(BIGTANGLE_TOKENID_STRING, CNY);

        // Equihash Settings
        equihashN = 100;
        equihashK = 4;

        genesisBlock = createGenesis(this);

    }

    public void serverSeeds() {
        String[] urls = new String[] { "https://test.bigtangle.de:8089/", "https://test.bigtangle.info:8089/" };
        serverPool = new ServerPool();
        serverPool.addServers(Arrays.asList(urls));

    }

    private static TestParams instance;

    public static synchronized TestParams get() {
        if (instance == null) {
            instance = new TestParams();
        }
        return instance;
    }

    @Override
    public Integer getOrderPriceShift(String orderBaseTokens) {
        if (CNY.equals(orderBaseTokens))
            return 6;
        if (BIGTANGLE_TOKENID_STRING.equals(orderBaseTokens))
            return 0;
        throw new VerificationException("orderBaseTokens is not allowed");
    }

}
