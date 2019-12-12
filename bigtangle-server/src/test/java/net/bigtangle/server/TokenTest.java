package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
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
            sendEmpty(10);
            mcmcService.update();
            confirmationService.update();
        }

        {
            final String tokenid =new ECKey().getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "shop", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
       
            }
            sendEmpty(10);
            mcmcService.update();
            confirmationService.update();
        }

        {
            final String tokenid =new ECKey().getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "myshopname.shop", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
         
            }
            sendEmpty(10);
            mcmcService.update();
            confirmationService.update();
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
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "shop", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
        {
            ECKey key= new ECKey();
            walletAppKit1.wallet().importKey(key) ;
         //   walletAppKit1.wallet().importKey(preKey) ;
            final String tokenid = key.getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(key, tokenid, "myshopname.shop", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            keys.add(key);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
         
            }
            sendEmpty(10);
            mcmcService.update();
            confirmationService.update();
        }
        
        {

            ECKey key= new ECKey();
            walletAppKit1.wallet().importKey(key) ;
            Block token = testCreateToken(key, "myproduct",
                    walletAppKit1.wallet().getDomainNameBlockHash("myshopname.shop", "token").getdomainNameToken().getBlockHashHex());
            TokenInfo currentToken = new TokenInfo().parseChecked(token.getTransactions().get(0).getData());
            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), keys.get(i), aesKey);
                }
            sendEmpty(10);
            mcmcService.update();
            confirmationService.update();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("tokenid", currentToken.getToken().getTokenid());
            String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);

            assertTrue(getTokensResponse.getTokens().size() == 2);
            assertTrue(getTokensResponse.getTokens().get(0).getTokennameDisplay().equals(currentToken.getToken().getTokenname() + "@myshopname.shop")
                    || getTokensResponse.getTokens().get(1).getTokennameDisplay().equals(currentToken.getToken().getTokenname() + "@myshopname.shop"));
    

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
        confirmationService.update();
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
        confirmationService.update();
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
        confirmationService.update();
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
        ECKey outKey3 =new ECKey();
        ECKey outKey4 = new ECKey();
        ECKey signKey = new ECKey();
        keys.add(outKey3);
        keys.add(outKey4);
        keys.add(signKey);

        final String tokenid = new ECKey().getPublicKeyAsHex();
        final String tokenname = "bigtangle.de";

        
        // don't use the first key which is in the wallet
       
        this.walletAppKit.wallet().publishDomainName(keys, signKey, tokenid, tokenname, Token.genesisToken(networkParameters), aesKey, "",
                3);

  
        this.walletAppKit.wallet().multiSign(tokenid, outKey3, aesKey);
  
        this.walletAppKit.wallet().multiSign(tokenid, outKey4, aesKey);

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        this.walletAppKit.wallet().multiSign(tokenid, genesiskey, null);
    }

   //TODO not the exception at save, but test from network
    //@Test
    public void testTokenConflicts() throws Exception {
        // all token has the same name, but different id, tokenname and
        // domainBlockHash are unique

        testCreateToken(walletAppKit.wallet().walletKeys().get(0), "test");
        mcmcService.update();
        confirmationService.update();
        testCreateToken(new ECKey(),"test");
        mcmcService.update();
        confirmationService.update();
        testCreateToken(new ECKey(),"test");
        mcmcService.update();
        confirmationService.update();
        testCreateToken(new ECKey(),"test");
        mcmcService.update();
        confirmationService.update();
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
