/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import java.io.File;
import java.math.BigInteger;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.TestParams;

public class PartsToOne extends HelpTest {

    private static final Logger log = LoggerFactory.getLogger(PartsToOne.class);

    // 18TiXgUW913VFs3nqak6QAadTS7EYL6mGg

    @Test
    public void testTokens() throws JsonProcessingException, Exception {

        setUp();
        importKeys(walletAppKit2.wallet());
        importKeys(walletAppKit1.wallet());

        Block b = walletAppKit1.wallet().payPartsToOne(null,
                walletAppKit1.wallet().walletKeys().get(0).toAddress(networkParameters),
                NetworkParameters.BIGTANGLE_TOKENID, "parts to one", new BigInteger("200000000"));
        log.debug("  " + b.toString());
        Block b2 = walletAppKit2.wallet().payPartsToOne(null,
                walletAppKit2.wallet().walletKeys().get(0).toAddress(networkParameters),
                NetworkParameters.BIGTANGLE_TOKENID, "parts to one", new BigInteger("200000000"));
        log.debug("  " + b2.toString());
    }

    @Test
    public void payParts() throws JsonProcessingException, Exception {

        for (int i = 0; i < 5; i++) {
            Block b = walletAdmin().wallet().pay(null,
                    Address.fromBase58(MainNetParams.get(), "163LYA8FiLm4kVNLyvURavML6okkhaFPTf"),
                    Coin.valueOf(8888000000l), "mining together");

            log.debug("  " + b.toString());
            Thread.sleep(50000);
        }
    }

    protected WalletAppKit walletAdmin() throws Exception {
        KeyParameter aesKey = null;

        WalletAppKit w = new WalletAppKit(MainNetParams.get(), new File("/home/cui/Downloads"), "201811210100000002");
        w.wallet().setServerURL("https://p.bigtangle.org:8088/");

        return w;
        // .wallet().walletKeys(aesKey).get(0) ;
    }

}
