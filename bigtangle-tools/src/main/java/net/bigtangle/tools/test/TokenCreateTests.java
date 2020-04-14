/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;

public class TokenCreateTests extends HelpTest {

    // 18TiXgUW913VFs3nqak6QAadTS7EYL6mGg

    @Test
    public void testTokens() throws JsonProcessingException, Exception {

        String domain = "bigtangle";

        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(yuanTokenPriv),
                Utils.HEX.decode(yuanTokenPub)), "人民币", 2, domain, "人民币 CNY", new BigInteger("1000000"));
        // testCreateMultiSigToken(
        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(BTCTokenPriv),
        // Utils.HEX.decode(BTCTokenPub)),
        // "BTC", 8, domain, "Bitcoin ETF");
        // testCreateMultiSigToken(
        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(ETHTokenPriv),
        // Utils.HEX.decode(ETHTokenPub)),
        // "ETH", 8, domain, "Ethereum ETF");
        testCreateMultiSigToken(ECKey.fromPrivate(Utils.HEX.decode(EURTokenPriv)), "EUR", 2, domain, "Euro",
                new BigInteger("1000000"));
        testCreateMultiSigToken(ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)), "USD", 2, domain, "US Dollar",
                new BigInteger("1000000"));
        testCreateMultiSigToken(ECKey.fromPrivate(Utils.HEX.decode(JPYTokenPriv)), "JPY", 2, domain, "Japan Yuan",
                new BigInteger("1000000"));
      }

    @Test
    public void testTokensETH() throws JsonProcessingException, Exception {

        String domain = "bigtangle";

        testCreateMultiSigToken(ECKey.fromPrivate(Utils.HEX.decode(ETHTokenPriv)
                ), "ETH", 18, domain, "ETH", new BigInteger("100000000000000000000000"));
        testCreateMultiSigToken(ECKey.fromPrivate(Utils.HEX.decode(ETHUSDTPriv)
                ), "ETH-USDT", 6, domain, "ETH-USDT", new BigInteger("100000000000000000"));
      
    }
    @Test
    public void testMyshop() throws JsonProcessingException, Exception {
        ECKey preKey = ECKey.fromPrivate(Utils.HEX.decode(ShopDomainPriv));
        final ECKey myshop = new ECKey();

        walletAppKit1.wallet().publishDomainName(myshop, myshop.getPublicKeyAsHex(), "myshop.shop", null, "");
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(preKey);
        for (int i = 0; i < keys.size(); i++) {
            walletAppKit1.wallet().multiSign(myshop.getPublicKeyAsHex(), keys.get(i), null);
        }

    }

    @Test
    public void testMyproduct() throws JsonProcessingException, Exception {

        String domain = "myshop.shop";

        testCreateMultiSigToken(new ECKey(), "My币", 2, domain, "My币", new BigInteger("1000000"));

    }

    @Test
    public void testDomain2() throws Exception {
        ECKey preKey = ECKey.fromPrivate(Utils.HEX.decode(BigtangleDomainPriv));
        {
            final String tokenid = preKey.getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(preKey, tokenid, "bigtangle", null, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            keys.add(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), null);
            }
        }
    }

    @Test
    public void testDomainShop() throws Exception {
        ECKey preKey = ECKey.fromPrivate(Utils.HEX.decode(ShopDomainPriv));
        {
            final String tokenid = preKey.getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(preKey, tokenid, "shop", null, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            keys.add(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), null);
            }
        }
    }

    @Test
    public void domainCom() throws Exception {

       // walletAppKit1.wallet().setServerURL("https://test.bigtangle.info:8089/");

        ECKey preKey = ECKey.fromPrivate(Utils.HEX.decode(DomainComPriv));
        // .fromPrivate(Utils.HEX.decode("85208f51dc3977bdca6bbcf6c7ad8c9988533ea84c8f99479987e10222c23b49"));
        {
            final String tokenid = preKey.getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(preKey, tokenid, "com", null, "com");
            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            keys.add(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), null);
            }

        }
    }

    @Test
    public void domainBigtangle() throws Exception {

      //  walletAppKit1.wallet().setServerURL("https://test.bigtangle.info:8089/");

        ECKey preKey = ECKey.fromPrivate(Utils.HEX.decode(BigtangleDomainPriv));
        // .fromPrivate(Utils.HEX.decode("85208f51dc3977bdca6bbcf6c7ad8c9988533ea84c8f99479987e10222c23b49"));
        {
            final String tokenid = preKey.getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(preKey, tokenid, "bigtangle", null, "bigtangle");
            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            keys.add(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), null);
            }

        }
    }

    @Test
    public void domainGov() throws Exception {

        walletAppKit1.wallet().setServerURL("https://test.bigtangle.info:8089/");

        ECKey preKey = ECKey.fromPrivate(Utils.HEX.decode(govpriv));
        {
            final String tokenid = preKey.getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(preKey, tokenid, "gov", null, "gov");
            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            keys.add(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), null);
            } 
        } 
    }
        @Test
        public void domainIdentityGov() throws Exception {
            ECKey preKey = ECKey.fromPrivate(Utils.HEX.decode(govpriv));
          ECKey idgov=  ECKey.fromPrivate(Utils.HEX.decode(idgovpriv));

            walletAppKit1.wallet().publishDomainName(idgov, idgov.getPublicKeyAsHex(), "id.gov", null, "");
            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            keys.add(idgov);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(idgov.getPublicKeyAsHex(), keys.get(i), null);
            }

        }
 
}
