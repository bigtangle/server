package net.bigtangle.server;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.ECKey;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TokenDomainNameTest extends AbstractIntegrationTest {

    @Test
    public void testCreateDomainTokenBatch() throws Exception {
        store.resetStore();

        wallet1();
        wallet2();

        {
            List<ECKey> walletKeys = new ArrayList<ECKey>();
            walletKeys.add(walletAppKit1.wallet().walletKeys().get(0));
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys, walletKeys.get(0), tokenid, "de", "", aesKey,
                    678900000, "");

            for (int i = 1; i < walletKeys.size(); i++) {
                ECKey ecKey = walletKeys.get(i);
                walletAppKit1.wallet().multiSign(tokenid, ecKey, aesKey);
            }
        }
        
        {
            List<ECKey> walletKeys = new ArrayList<ECKey>();
            walletKeys.add(walletAppKit1.wallet().walletKeys().get(1));
            walletKeys.add(walletAppKit1.wallet().walletKeys().get(2));
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys, walletKeys.get(0), tokenid, "bund.de", "de", aesKey,
                    678900000, "");

            for (int i = 1; i < walletKeys.size(); i++) {
                ECKey ecKey = walletKeys.get(i);
                walletAppKit1.wallet().multiSign(tokenid, ecKey, aesKey);
            }
        }
        
        {
            List<ECKey> walletKeys = new ArrayList<ECKey>();
            walletKeys.add(walletAppKit1.wallet().walletKeys().get(3));
            walletKeys.add(walletAppKit1.wallet().walletKeys().get(4));
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys, walletKeys.get(0), tokenid, "www.bund.de", "bund.de", aesKey,
                    678900000, "");

            for (int i = 1; i < walletKeys.size(); i++) {
                ECKey ecKey = walletKeys.get(i);
                walletAppKit1.wallet().multiSign(tokenid, ecKey, aesKey);
            }
        }
        
        {
            List<ECKey> walletKeys = new ArrayList<ECKey>();
            walletKeys.add(walletAppKit1.wallet().walletKeys().get(5));
            walletKeys.add(walletAppKit1.wallet().walletKeys().get(6));
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys, walletKeys.get(0), tokenid, "mmm.bund.de", "bund.de", aesKey,
                    678900000, "");

            for (int i = 1; i < walletKeys.size(); i++) {
                ECKey ecKey = walletKeys.get(i);
                walletAppKit1.wallet().multiSign(tokenid, ecKey, aesKey);
            }
        }
    }

//    @Test
//    public void testCreateDomainToken() throws Exception {
//        store.resetStore();
//        this.initWalletKeysMapper();
//        String tokenid = walletKeys.get(1).getPublicKeyAsHex();
//        int amount = 678900000;
//        final String domainname = "de";
//        this.createDomainToken(tokenid, "中央银行token - 000", "de", amount, this.walletKeys);
//        this.checkTokenAssertTrue(tokenid, domainname);
//    }

}
