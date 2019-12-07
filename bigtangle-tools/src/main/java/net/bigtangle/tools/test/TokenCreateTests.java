/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Token;
import net.bigtangle.core.Utils;

public class TokenCreateTests extends HelpTest {

    // 18TiXgUW913VFs3nqak6QAadTS7EYL6mGg

    @Test
    public void testTokens() throws JsonProcessingException, Exception {

        Token domain = walletAppKit1.wallet().getDomainNameBlockHash("bigtangle", "token").getdomainNameToken();

        testCreateMultiSigToken(ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(yuanTokenPriv),
                Utils.HEX.decode(yuanTokenPub)), "人民币", 2, domain, "人民币 CNY", 1000000);
        // testCreateMultiSigToken(
        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(BTCTokenPriv),
        // Utils.HEX.decode(BTCTokenPub)),
        // "BTC", 8, domain, "Bitcoin ETF");
        // testCreateMultiSigToken(
        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(ETHTokenPriv),
        // Utils.HEX.decode(ETHTokenPub)),
        // "ETH", 8, domain, "Ethereum ETF");
        testCreateMultiSigToken(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(EURTokenPriv), Utils.HEX.decode(EURTokenPub)),
                "EUR", 2, domain, "Euro", 100000);
        testCreateMultiSigToken(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(USDTokenPriv), Utils.HEX.decode(USDTokenPub)),
                "USD", 2, domain, "US Dollar", 100000);
        testCreateMultiSigToken(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(JPYTokenPriv), Utils.HEX.decode(JPYTokenPub)),
                "JPY", 2, domain, "Japan Yuan", 100000000);

    }

    @Test
    public void testMyshop() throws JsonProcessingException, Exception {
        ECKey preKey = ECKey.fromPrivate(Utils.HEX.decode(ShopDomainPriv));
        final ECKey myshop =new ECKey();
 
        walletAppKit1.wallet().publishDomainName(myshop, myshop.getPublicKeyAsHex(), "myshop.shop", null, "");
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(preKey); 
        for (int i = 0; i < keys.size(); i++) {
            walletAppKit1.wallet().multiSign( myshop.getPublicKeyAsHex(), keys.get(i), null);
        }

    }
    @Test
    public void testMyproduct() throws JsonProcessingException, Exception {

        Token domain = walletAppKit1.wallet().getDomainNameBlockHash("myshop.shop", "token").getdomainNameToken();

        testCreateMultiSigToken(new ECKey() , "My币", 2, domain, "My币", 1000000);

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

}
