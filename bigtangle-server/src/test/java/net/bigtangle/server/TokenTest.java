package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.collect.ImmutableList;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

/*
 * ## permission of token creation 

### new type of token with domain name
 server configuration parameter defines the root permission for single name as cn, com,  de etc.
 the creation of top name need the signature of root permission and user signature
 domain name is tree of permission
 the other domain need the signature of parent signature and user signature
 domain name is unique in system  -> ValidationService

example
 tokentype:domainname
 tokenname=de
 domainname=""
 signatures: user + root 
 check: tokenname must be unique for tokentype domainname
 
 tokentype:domainname
 tokenname=bund.de
 domainname=de
 
 signatures: user + domainname of de
 check: tokenname must be unique for tokentype domainname
 
 
### other type of token must be have a domain name
   the token must be signed by domain name signature and user signature
example
 tokentype:token
 tokenname=product1
 domainname=bund.de
 signatures: user + domainname token
   

### display with tokenname +"@" + domainname +":"+ tokenid
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TokenTest extends AbstractIntegrationTest {

    @Test
    public void testCreateDomainToken() throws Exception {
        store.resetStore();

        wallet1();
        wallet2();

        List<ECKey> walletKeys = wallet2Keys;
        ECKey preKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "de", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "bc", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "bigtangle.bc", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }

        {
            final String tokenid = walletKeys.get(1).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(1), tokenid, "www.bigtangle.bc", aesKey, "");
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
                    walletKeys.get(2), tokenid, "info.www.bigtangle.bc", aesKey, BigInteger.valueOf(1), "");
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

    @Test
    public void testCreateTokenWithDomain() throws Exception {

        store.resetStore();

        wallet1();
        wallet2();
        List<ECKey> walletKeys = wallet2Keys;
        ECKey preKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "de", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }
        mcmcService.update();
        {

            Block token = testCreateToken(walletKeys.get(1), "de",
                    walletAppKit1.wallet().getDomainNameBlockHash("de", "token").getdomainNameBlockHash());
            TokenInfo currentToken = TokenInfo.parseChecked(token.getTransactions().get(0).getData());
            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), keys.get(i), aesKey);
            }
            mcmcService.update();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("tokenid", currentToken.getToken().getTokenid());
            String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);

            assertTrue(getTokensResponse.getTokens().size() == 1);
            assertTrue(getTokensResponse.getTokens().get(0).getTokennameDisplay().equals(currentToken.getToken().getTokenname() + "@de"));
            assertTrue(!getTokensResponse.getTokens().get(0).getDomainNameBlockHash()
                    .equals(networkParameters.getGenesisBlock().getHashAsString()));

        }

    }

    @Test
    public void testGetTokenById() throws Exception {

        testCreateToken(walletKeys.get(0), "test");

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletKeys.get(0).getPublicKeyAsHex());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() > 0);

        mcmcService.update();
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        log.info("getOutputsResponse : " + getOutputsResponse);

        assertTrue(getOutputsResponse.getOutputs().size() > 0);
        assertTrue(getOutputsResponse.getOutputs().get(0).getValue()
                .equals(Coin.valueOf(77777L, walletKeys.get(0).getPubKey())));
    }

    @Test
    public void testGetTokennameConflict() throws Exception {

        wallet1();
        wallet2();

        List<ECKey> walletKeys = wallet2Keys;
        ECKey preKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "de", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "de", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }

        mcmcService.update();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletKeys.get(0).getPublicKeyAsHex());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        log.info("getOutputsResponse : " + getOutputsResponse);

        assertTrue(getOutputsResponse.getOutputs().size() == 1);
        assertTrue(getOutputsResponse.getOutputs().get(0).getValue()
                .equals(Coin.valueOf(1, walletKeys.get(0).getPubKey())));

    }

    @Test
    public void testGetTokenConflict() throws Exception {

        testCreateToken(walletKeys.get(0),"test");
        // same token id and index
        testCreateToken(walletKeys.get(0), "test");

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletKeys.get(0).getPublicKeyAsHex());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() > 0);

        mcmcService.update();
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        log.info("getOutputsResponse : " + getOutputsResponse);

        assertTrue(getOutputsResponse.getOutputs().size() > 0);
        assertTrue(getOutputsResponse.getOutputs().get(0).getValue()
                .equals(Coin.valueOf(77777L, walletKeys.get(0).getPubKey())));

    }

    @Test
    public void walletCreateDomain() throws Exception {
        store.resetStore();

        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(this.walletKeys.get(1));
        keys.add(this.walletKeys.get(2));
        keys.add(this.walletKeys.get(3));

        final String tokenid = new ECKey().getPublicKeyAsHex();
        final String tokenname = "bigtangle.de";

        final String domainNameBlockHash = networkParameters.getGenesisBlock().getHashAsString();

        // don't use the first key which is in the wallet
        ECKey signKey = this.walletKeys.get(3);
        this.walletAppKit.wallet().publishDomainName(keys, signKey, tokenid, tokenname, domainNameBlockHash, aesKey, "",
                3);

        this.walletAppKit.wallet().multiSign(tokenid, this.walletKeys.get(1), aesKey);
        this.walletAppKit.wallet().multiSign(tokenid, this.walletKeys.get(2), aesKey);

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        this.walletAppKit.wallet().multiSign(tokenid, genesiskey, null);
    }

    @Test
    public void testTokenConflicts() throws Exception {
        // all token has the same name, but different id, tokenname and
        // domainBlockHash are unique

        testCreateToken(walletAppKit.wallet().walletKeys().get(0), "test");
        mcmcService.update();
        testCreateToken(walletAppKit.wallet().walletKeys().get(1),"test");
        mcmcService.update();
        testCreateToken(walletAppKit.wallet().walletKeys().get(2),"test");
        mcmcService.update();
        testCreateToken(walletAppKit.wallet().walletKeys().get(3),"test");
        mcmcService.update();
        sendEmpty(20);
        //only one is ok.

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletAppKit.wallet().walletKeys().get(0).getPublicKeyAsHex());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() ==1);
        assertTrue(blockService.getBlockEvaluation(getTokensResponse.getTokens().get(0).getBlockHash()).isConfirmed());

       
        requestParam.put("tokenid", walletAppKit.wallet().walletKeys().get(1).getPublicKeyAsHex());
          resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
          getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() ==1);
        assertTrue(!blockService.getBlockEvaluation(getTokensResponse.getTokens().get(0).getBlockHash()).isConfirmed());
  
    }

}
