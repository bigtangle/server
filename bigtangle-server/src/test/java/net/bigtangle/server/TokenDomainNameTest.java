package net.bigtangle.server;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.collect.ImmutableList;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TokenDomainNameTest extends AbstractIntegrationTest {

    @Test
    public void testCreateDomainTokenBatch() throws Exception {
        store.resetStore();

        wallet1();
        wallet2();

        List<ECKey> walletKeys = wallet2Keys;
        ECKey preKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "bigtangle.bc",
                    "bigtangle.bc", aesKey, 678900000, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }

        {
            final String tokenid = walletKeys.get(1).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(1), tokenid, "www.bigtangle.bc",
                    "www.bigtangle.bc", aesKey, 678900000, "");
            walletAppKit1.wallet().multiSign(tokenid, preKey, aesKey);

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            keys.add(walletKeys.get(0));
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }

        {
            final String tokenid = walletKeys.get(2).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(ImmutableList.of(walletKeys.get(2), walletKeys.get(3)),
                    walletKeys.get(2), tokenid, "info.www.bigtangle.bc", "info.www.bigtangle.bc", aesKey, 678900000,
                    "");
            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(walletKeys.get(3));
            keys.add(preKey);
            keys.add(walletKeys.get(0));
            keys.add(walletKeys.get(1));
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }
    }
}
