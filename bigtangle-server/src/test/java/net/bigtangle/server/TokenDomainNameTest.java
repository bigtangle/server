package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.ECKey;
import net.bigtangle.utils.DomainnameUtil;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TokenDomainNameTest extends AbstractIntegrationTest {

    @Test
    public void calcDomainname() {
        String de = "de";
        System.out.println(DomainnameUtil.matchParentDomainname(de));
    }

    @Test
    public void testDomainnameSubstr() {
        String domainname = "bigtangle.de";
        String str = DomainnameUtil.matchParentDomainname(domainname);
        System.out.println("domainname : " + domainname + ", str : " + str);
        assertTrue(str.contentEquals("de"));

        domainname = "www.bigtangle.de";
        str = DomainnameUtil.matchParentDomainname(domainname);
        System.out.println("domainname : " + domainname + ", str : " + str);
        assertTrue(str.contentEquals("bigtangle.de"));

        domainname = ".de";
        str = DomainnameUtil.matchParentDomainname(domainname);
        System.out.println("domainname : " + domainname + ", str : " + str);
        assertTrue(str.contentEquals("de"));
    }

    @Test
    public void testCreateDomainTokenBatch() throws Exception {
        store.resetStore();
        this.initWalletKeysMapper();

        Map<String, List<ECKey>> linkMap = new LinkedHashMap<String, List<ECKey>>();
        linkMap.put("de", this.walletKeys);
        linkMap.put("bigtangle.de", this.wallet1Keys);
        linkMap.put("m.bigtangle.de", this.wallet2Keys);

        int amount = 678900000;
        int index = 0;
        for (Map.Entry<String, List<ECKey>> entry : linkMap.entrySet()) {
            System.out.println("domainname : " + entry.getKey() + ", values : " + entry.getValue().size());
            List<ECKey> walletKeys = entry.getValue();
            final String domainname = entry.getKey();

            String tokenid = walletKeys.get(1).getPublicKeyAsHex();
            this.createDomainToken(tokenid, "中央银行token - 00" + (++index), domainname, amount, walletKeys);
            System.out.println(tokenid);
            this.checkTokenAssertTrue(tokenid, domainname);
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
