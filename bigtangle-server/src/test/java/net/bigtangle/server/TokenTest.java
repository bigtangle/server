package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.data.identity.Identity;
import net.bigtangle.data.identity.IdentityCore;
import net.bigtangle.data.identity.IdentityData;
import net.bigtangle.encrypt.ECIESCoder;
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
 check: tokenname +domainname must be unique    
 
 tokentype:domainname
 tokenname=bund.de
 domainname=de
 
 signatures: user + domainname of de
 check: tokenname +domainname must be unique  
 
 
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
            final String tokenid = new ECKey().getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "金", aesKey, "");

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
            final String tokenid = new ECKey().getPublicKeyAsHex();
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
            ECKey key = new ECKey();
            final String tokenid = key.getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(key, tokenid, "myshopname.shop", aesKey, "");

            walletAppKit1.wallet().multiSign(tokenid, walletKeys.get(0), aesKey);

            sendEmpty(10);
            mcmcService.update();
            confirmationService.update();
        }

    }

    @Test
    public void testWrongDomainname() throws Exception {

        ECKey preKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "de/de", aesKey, "");

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

        createShopToken();

        ECKey key = new ECKey();

        walletAppKit1.wallet().importKey(key);
        // walletAppKit1.wallet().importKey(preKey) ;
        final String tokenid = key.getPublicKeyAsHex();
        walletAppKit1.wallet().publishDomainName(key, tokenid, "myshopname.shop", aesKey, "");
        walletAppKit1.wallet().multiSign(tokenid, walletKeys.get(0), aesKey);

        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();

        {

            ECKey productkey = new ECKey();
            walletAppKit1.wallet().importKey(productkey);
            Block block = walletAppKit1.wallet().createToken(productkey, "product", 0, "myshopname.shop", "test",
                    BigInteger.ONE, true, null);
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), key, aesKey);

            sendEmpty(10);
            mcmcService.update();
            confirmationService.update();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("tokenid", currentToken.getToken().getTokenid());
            String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);

            assertTrue(getTokensResponse.getTokens().size() == 1);
            assertTrue(getTokensResponse.getTokens().get(0).getTokennameDisplay()
                    .equals(currentToken.getToken().getTokenname() + "@myshopname.shop")
                    || getTokensResponse.getTokens().get(1).getTokennameDisplay()
                            .equals(currentToken.getToken().getTokenname() + "@myshopname.shop"));

        }

    }

    @Test
    public void testCreateIdentityTokenWithDomain() throws Exception {

        createShopToken();

        ECKey key = new ECKey();

        walletAppKit1.wallet().importKey(key);
        // walletAppKit1.wallet().importKey(preKey) ;
        final String tokenid = key.getPublicKeyAsHex();
        walletAppKit1.wallet().publishDomainName(key, tokenid, "id.shop", aesKey, "");
        walletAppKit1.wallet().multiSign(tokenid, walletKeys.get(0), aesKey);

        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();

        {

            ECKey issuer = new ECKey();
            ECKey userkey = new ECKey();
            TokenKeyValues kvs = getTokenKeyValues(issuer, userkey);

            walletAppKit1.wallet().importKey(issuer);
            Block block = walletAppKit1.wallet().createToken(issuer, userkey.getPublicKeyAsHex(), 0, "id.shop", "test",
                    BigInteger.ONE, true, kvs, TokenType.identity.ordinal());
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), key, aesKey);

            sendEmpty(10);
            mcmcService.update();
            confirmationService.update();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("tokenid", currentToken.getToken().getTokenid());
            String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);

            assertTrue(getTokensResponse.getTokens().size() == 1);
            assertTrue(getTokensResponse.getTokens().get(0).getTokennameDisplay()
                    .equals(currentToken.getToken().getTokenname() + "@id.shop"));
            Token token = getTokensResponse.getTokens().get(0);
            byte[] decryptedPayload = null;
            for (KeyValue kvtemp : token.getTokenKeyValues().getKeyvalues()) {
                if (kvtemp.getKey().equals(userkey.getPublicKeyAsHex())) {
                    decryptedPayload = ECIESCoder.decrypt(userkey.getPrivKey(), Utils.HEX.decode(kvtemp.getValue()));
                    Identity identity = new Identity().parse(decryptedPayload);
                    IdentityData id = new IdentityData().parse(Utils.HEX.decode(identity.getIdentityData()));
                    assertTrue(id.getIdentificationnumber().equals("120123456789012345"));
                    identity.verify();

                }
            }

        }

    }

    private TokenKeyValues getTokenKeyValues(ECKey key, ECKey userkey)
            throws InvalidCipherTextException, IOException, SignatureException {
        Identity identity = new Identity();
        IdentityCore identityCore = new IdentityCore();
        identityCore.setSurname("zhang");
        identityCore.setForenames("san");
        identityCore.setSex("man");
        identityCore.setDateofissue("20200101");
        identityCore.setDateofexpiry("20201231");
        IdentityData identityData = new IdentityData();
        identityData.setIdentityCore(identityCore);
        identityData.setIdentificationnumber("120123456789012345");
        byte[] photo = "readFile".getBytes();
        // readFile(new File("F:\\img\\cc_aes1.jpg"));
        identityData.setPhoto(photo); 
   
        return  identity. getTokenKeyValues(key, userkey, identityData);
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

        testCreateToken(walletKeys.get(0), "test");
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
        ECKey outKey3 = new ECKey();
        ECKey outKey4 = new ECKey();
        ECKey signKey = new ECKey();
        keys.add(outKey3);
        keys.add(outKey4);
        keys.add(signKey);

        final String tokenid = new ECKey().getPublicKeyAsHex();
        final String tokenname = "bigtangle.de";

        // don't use the first key which is in the wallet

        this.walletAppKit.wallet().publishDomainName(keys, signKey, tokenid, tokenname,
                Token.genesisToken(networkParameters), aesKey, "", 3);

        this.walletAppKit.wallet().multiSign(tokenid, outKey3, aesKey);

        this.walletAppKit.wallet().multiSign(tokenid, outKey4, aesKey);

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        this.walletAppKit.wallet().multiSign(tokenid, genesiskey, null);
    }

    // TODO not the exception at save, but test from network
    // @Test
    public void testTokenConflicts() throws Exception {
        // all token has the same name, but different id, tokenname and
        // domainBlockHash are unique

        testCreateToken(walletAppKit.wallet().walletKeys().get(0), "test");
        mcmcService.update();
        confirmationService.update();
        testCreateToken(new ECKey(), "test");
        mcmcService.update();
        confirmationService.update();
        testCreateToken(new ECKey(), "test");
        mcmcService.update();
        confirmationService.update();
        testCreateToken(new ECKey(), "test");
        mcmcService.update();
        confirmationService.update();
        sendEmpty(20);
        // only one is ok.

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletAppKit.wallet().walletKeys().get(0).getPublicKeyAsHex());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() == 1);
        assertTrue(blockService.getBlockEvaluation(getTokensResponse.getTokens().get(0).getBlockHash()).isConfirmed());

        requestParam.put("tokenid", walletAppKit.wallet().walletKeys().get(1).getPublicKeyAsHex());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() == 1);
        assertTrue(!blockService.getBlockEvaluation(getTokensResponse.getTokens().get(0).getBlockHash()).isConfirmed());

    }

    @Test
    public void testCreateTokenMulti() throws Exception {

        createShopToken();
        ECKey key = new ECKey();
        createProductToken(key);
        TokenInfo currentToken = createProductToken(key);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", currentToken.getToken().getTokenid());
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);

        assertTrue(getTokensResponse.getTokens().size() == 2);
        assertTrue(getTokensResponse.getTokens().get(0).getTokennameDisplay()
                .equals(currentToken.getToken().getTokenname() + "@shop")
                || getTokensResponse.getTokens().get(1).getTokennameDisplay()
                        .equals(currentToken.getToken().getTokenname() + "@shop"));

        resp = OkHttp3Util.postString(contextRoot + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        log.info("getOutputsResponse : " + getOutputsResponse);

        assertTrue(getOutputsResponse.getOutputs().size() == 2);
        assertTrue(getOutputsResponse.getOutputs().get(0).getValue()
                .equals(Coin.valueOf(1, currentToken.getToken().getTokenid())));
        assertTrue(getOutputsResponse.getOutputs().get(1).getValue()
                .equals(Coin.valueOf(1, currentToken.getToken().getTokenid())));

    }

    private void createShopToken()
            throws Exception, JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {
        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "shop", aesKey, "");

            ECKey preKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                    Utils.HEX.decode(testPub));

            walletAppKit1.wallet().multiSign(tokenid, preKey, aesKey);

        }
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
    }

    private TokenInfo createProductToken(ECKey key)
            throws Exception, JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {

        walletAppKit1.wallet().importKey(key);
        Block block = walletAppKit1.wallet().createToken(key, "product", 0, "shop", "test", BigInteger.ONE, true, null);
        TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(walletKeys.get(0));
        for (int i = 0; i < keys.size(); i++) {
            walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), keys.get(i), aesKey);
        }
        sendEmpty(10);
        mcmcService.update();
        confirmationService.update();
        return currentToken;
    }

    public byte[] readFile(File file) {
        byte[] buf = null;
        if (file != null) {
            ByteArrayOutputStream byteArrayOutputStream = null;
            BufferedInputStream bufferedInputStream = null;
            byteArrayOutputStream = new ByteArrayOutputStream((int) file.length());
            try {
                bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
                int buffSize = 1024;
                byte[] buffer = new byte[buffSize];
                int len = 0;
                while (-1 != (len = bufferedInputStream.read(buffer, 0, buffSize))) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
                buf = byteArrayOutputStream.toByteArray();
            } catch (Exception e) {
            } finally {
                if (bufferedInputStream != null) {
                    try {
                        bufferedInputStream.close();
                        if (byteArrayOutputStream != null) {
                            byteArrayOutputStream.close();
                        }
                    } catch (IOException e) {
                    }
                }
            }
        }
        return buf;
    }
}
