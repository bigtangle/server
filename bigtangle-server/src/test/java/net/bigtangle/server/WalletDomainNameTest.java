package net.bigtangle.server;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WalletDomainNameTest extends AbstractIntegrationTest {

    @Test
    public void walletCreateDomain() throws Exception {
        store.resetStore();

        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(this.walletKeys.get(1));
        keys.add(this.walletKeys.get(2));
        keys.add(this.walletKeys.get(3));

        final String tokenid = new ECKey().getPublicKeyAsHex();
        final String tokenname = "Test COIN";
        final String domainname = "bigtangle.de";

        final String domainPredecessorBlockHash = networkParameters.getGenesisBlock().getHashAsString();

        // don't use the first key which is in the wallet
        ECKey signKey = this.walletKeys.get(3);
        this.walletAppKit.wallet().publishDomainName(keys, signKey, tokenid, tokenname, domainname,
                domainPredecessorBlockHash, aesKey, 6789000, "", 3);

        this.walletAppKit.wallet().multiSign(tokenid, this.walletKeys.get(1), aesKey);
        this.walletAppKit.wallet().multiSign(tokenid, this.walletKeys.get(2), aesKey);

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        this.walletAppKit.wallet().multiSign(tokenid, genesiskey, null);
    }

}
