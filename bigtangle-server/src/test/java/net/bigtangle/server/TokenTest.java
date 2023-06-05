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
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.apps.data.Certificate;
import net.bigtangle.apps.data.IdentityCore;
import net.bigtangle.apps.data.IdentityData;
import net.bigtangle.apps.data.Prescription;
import net.bigtangle.apps.data.SignedData;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.KeyValueList;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.MultiSignResponse;
import net.bigtangle.core.response.SearchMultiSignResponse;
import net.bigtangle.encrypt.ECIESCoder;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.SignedDataWithToken;
import net.bigtangle.utils.WalletUtil;
import net.bigtangle.wallet.Wallet;

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
    public void addblock() throws Exception {
        makeRewardBlock();
    }

    @Test
    public void testCreateDomainToken() throws Exception {

        ECKey preKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "com", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }

            makeRewardBlock();

        }

        {
            final String tokenid = new ECKey().getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "金", aesKey, "金");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);

            }

            makeRewardBlock();
            //check uft8 
          log.debug( getToken(tokenid).toString());
            
        }
        {
            final String tokenid = new ECKey().getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "shop", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);

            }

            makeRewardBlock();

        }

        {
            ECKey key = new ECKey();
            final String tokenid = key.getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(key, tokenid, "myshopname.shop", aesKey, "");

            walletAppKit1.wallet().multiSign(tokenid, walletKeys.get(0), aesKey);

            makeRewardBlock();

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
            // sendEmpty(10);
            makeRewardBlock();

        }
    }

    public void testWrongSignnumber() throws Exception {

        ECKey preKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(0), tokenid, "de/de", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
            // sendEmpty(10);
            makeRewardBlock();

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

        // sendEmpty(10);
        makeRewardBlock();

        {

            ECKey productkey = new ECKey();
            walletAppKit1.wallet().importKey(productkey);
            Block block = createToken(productkey, "product", 0, "myshopname.shop", "test", BigInteger.ONE, true, null,
                    TokenType.token.ordinal(), productkey.getPublicKeyAsHex(), walletAppKit1.wallet());
            TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
            walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), key, aesKey);

            // sendEmpty(10);
            makeRewardBlock();

            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("tokenid", currentToken.getToken().getTokenid());
            byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
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

        ECKey key = prepareIdentity();

        ECKey issuer = new ECKey();
        ECKey userkey = new ECKey();
        TokenKeyValues kvs = getTokenKeyValues(issuer, userkey);
        walletAppKit1.wallet().importKey(issuer);
        Block block = createToken(issuer, userkey.getPublicKeyAsHex(), 0, "id.shop", "test", BigInteger.ONE, true, kvs,
                TokenType.identity.ordinal(), issuer.getPublicKeyAsHex(), walletAppKit1.wallet());
        TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
        walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), key, aesKey);
        // sendEmpty(10);
        makeRewardBlock();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", currentToken.getToken().getTokenid());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
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
                SignedData identity = new SignedData().parse(decryptedPayload);
                IdentityData id = new IdentityData().parse(Utils.HEX.decode(identity.getSerializedData()));
                assertTrue(id.getIdentificationnumber().equals("120123456789012345"));
                identity.verify();
            }
        }

    }

    @Test
    public void testCreateCertificate() throws Exception {

        ECKey domainkey = prepareIdentity();
        String domainAddress = domainkey.toAddress(networkParameters).toString();
        // issuer create the token for user public key and domain key must sign
        // the token
        ECKey issuer = new ECKey();
        ECKey userkey = new ECKey();
        SignedData signedata = signeddata(issuer);
        TokenKeyValues kvs = signedata.toTokenKeyValues(issuer, userkey);
        walletAppKit1.wallet().importKey(issuer);
        List<ECKey> keys = walletAppKit1.wallet().walletKeys();
        List<String> addresses = keys.stream().map(key -> key.toAddress(networkParameters).toBase58())
                .collect(Collectors.toList());
        String tokenid = new ECKey().getPublicKeyAsHex();
        Block block = createToken(issuer, userkey.getPublicKeyAsHex(), 0, "id.shop", "test", BigInteger.ONE, true, kvs,
                TokenType.identity.ordinal(), tokenid, walletAppKit1.wallet(), userkey.getPubKey(),
                signedata.encryptToMemo(userkey));
        String isserAddress = issuer.toAddress(networkParameters).toString();
        log.info("domain sign before : " + tokenid + "," + isserAddress);
        querySign(tokenid, isserAddress, true);
        querySignByTokenid(tokenid, addresses, true);
        List<String> tempList=new ArrayList<String>();
        tempList.add(domainAddress);
        querySign(tokenid, domainAddress, false);
        querySignByTokenid(tokenid, tempList, false);
        TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
        walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), domainkey, aesKey);

        log.info("domain sign end : " + tokenid + "," + domainAddress);
        querySign(tokenid, isserAddress, true);
        querySign(tokenid, domainAddress, true);
        querySignByTokenid(tokenid, tempList, true);
        // sendEmpty(10);
        makeRewardBlock();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", currentToken.getToken().getTokenid());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
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
                SignedData sdata = new SignedData().parse(decryptedPayload);
                sdata.verify();
                if (DataClassName.KeyValueList.name().equals(sdata.getDataClassName())) {
                    KeyValueList id = new KeyValueList().parse(Utils.HEX.decode(sdata.getSerializedData()));
                    assertTrue(id.getKeyvalues().size() == 2);
                }
            }
        }
        List<UTXO> ulist = getBalance(false, userkey);
        assertTrue(ulist.size() == 1);
        // assertTrue(ulist.size()==1);

    }

    @Test
    public void testSigneddata() throws Exception {

        ECKey domainkey = prepareIdentity();
        String domainAddress = domainkey.toAddress(networkParameters).toString();
        // issuer create the token for user public key and domain key must sign
        // the token
        ECKey issuer = new ECKey();
        ECKey userkey = new ECKey();
        SignedData signedata = signeddata(issuer);
        TokenKeyValues kvs = signedata.toTokenKeyValues(issuer, userkey);
        walletAppKit1.wallet().importKey(issuer);
        String tokenid = new ECKey().getPublicKeyAsHex();
        Block block = createToken(issuer, userkey.getPublicKeyAsHex(), 0, "id.shop", "test", BigInteger.ONE, true, kvs,
                TokenType.certificate.ordinal(), tokenid, walletAppKit1.wallet(), userkey.getPubKey(),
                signedata.encryptToMemo(userkey));

        TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
        walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), domainkey, aesKey);

        // sendEmpty(10);
        makeRewardBlock();

        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(userkey);
        List<SignedDataWithToken> data = WalletUtil.signedTokenList(keys, TokenType.certificate, contextRoot);
        assertTrue(data.size() > 0);
        for (SignedDataWithToken sdata : data) {
            Certificate certificate = new Certificate()
                    .parse(Utils.HEX.decode(sdata.getSignedData().getSerializedData()));
            assertTrue(certificate != null);
        }

    }

    @Test
    public void testPrescription() throws Exception {

        ECKey key = prepareIdentity();

        ECKey issuer = new ECKey();
        ECKey userkey = new ECKey();
        SignedData signedata = signeddata(key);
        TokenKeyValues kvs = signedata.toTokenKeyValues(key, userkey);
        walletAppKit1.wallet().importKey(issuer);
        Block block = createToken(issuer, userkey.getPublicKeyAsHex(), 0, "id.shop", "test", BigInteger.ONE, true, kvs,
                TokenType.identity.ordinal(), new ECKey().getPublicKeyAsHex(), walletAppKit1.wallet(),
                userkey.getPubKey(), signedata.encryptToMemo(userkey));
        TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
        walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), key, aesKey);
        // sendEmpty(10);
        makeRewardBlock();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", currentToken.getToken().getTokenid());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);

        assertTrue(getTokensResponse.getTokens().size() == 1);
        assertTrue(getTokensResponse.getTokens().get(0).getTokennameDisplay()
                .equals(currentToken.getToken().getTokenname() + "@id.shop"));
        Token token = getTokensResponse.getTokens().get(0);
        SignedData p = prescription(userkey, token);
        List<UTXO> ulist = getBalance(false, userkey);
        assertTrue(ulist.size() == 1);
        // pay the token to pharmacy
        ECKey pharmacy = new ECKey();
        // encrypt data as memo or
        Wallet userWallet = Wallet.fromKeys(networkParameters, userkey);

        MemoInfo memoInfo = p.encryptToMemo(pharmacy);
        userWallet.setServerURL(contextRoot);
        List<Block> b = userWallet.pay(null, pharmacy.toAddress(networkParameters).toString(), ulist.get(0).getValue(), memoInfo);
        // sendEmpty(10);
        makeRewardBlock();

        List<UTXO> pharmalist = getBalance(false, pharmacy);
        String jsonString = pharmalist.get(0).getMemo();
        MemoInfo m = MemoInfo.parse(jsonString);
        SignedData sdata = SignedData.decryptFromMemo(pharmacy, m);
        if (DataClassName.Prescription.name().equals(sdata.getDataClassName())) {
            Prescription pre = new Prescription().parse(Utils.HEX.decode(sdata.getSerializedData()));
            assertTrue(pre.getFilename() != null);

        }
    }

    public void querySign(String tokenid, String address, boolean sign) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("address", address);
        // requestParam.put("tokenid", tokenid);
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenSignByAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        List<MultiSign> multiSigns = multiSignResponse.getMultiSigns();
        assertTrue(multiSigns != null);
        for (MultiSign multiSign : multiSigns) {
            if (sign)
                assertTrue(multiSign.getSign() == 1);
            else {
                assertTrue(multiSign.getSign() == 0);
            }
        }

    }

    public void querySignByTokenid(String tokenid, List<String> addresses, boolean sign) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
      //  requestParam.put("addresses", addresses);
        requestParam.put("isSign", sign);
        requestParam.put("tokenid", tokenid);
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenSignByTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        SearchMultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, SearchMultiSignResponse.class);
      

    }

    private SignedData prescription(ECKey userkey, Token token)
            throws IOException, InvalidCipherTextException, SignatureException {
        byte[] decryptedPayload = null;
        for (KeyValue kvtemp : token.getTokenKeyValues().getKeyvalues()) {
            if (kvtemp.getKey().equals(userkey.getPublicKeyAsHex())) {
                decryptedPayload = ECIESCoder.decrypt(userkey.getPrivKey(), Utils.HEX.decode(kvtemp.getValue()));
                SignedData sdata = new SignedData().parse(decryptedPayload);
                sdata.verify();
                return sdata;
            }
        }
        return null;
    }

    public List<Prescription> prescriptionList(ECKey ecKey) throws Exception {
        List<Prescription> prescriptionlist = new ArrayList<Prescription>();
        Map<String, String> param = new HashMap<String, String>();
        param.put("toaddress", ecKey.toAddress(networkParameters).toString());

        byte[] response = OkHttp3Util.postString(contextRoot + ReqCmd.getOutputsHistory.name(),
                Json.jsonmapper().writeValueAsString(param));

        GetBalancesResponse balancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        Map<String, Token> tokennames = new HashMap<String, Token>();
        tokennames.putAll(balancesResponse.getTokennames());
        for (UTXO utxo : balancesResponse.getOutputs()) {
            if (checkPrescription(utxo, tokennames)) {
                Token token = tokennames.get(utxo.getTokenId());
                for (KeyValue kvtemp : token.getTokenKeyValues().getKeyvalues()) {
                    byte[] decryptedPayload = ECIESCoder.decrypt(ecKey.getPrivKey(),
                            Utils.HEX.decode(kvtemp.getValue()));
                    SignedData sdata = new SignedData().parse(decryptedPayload);
                    prescriptionlist.add(new Prescription().parse(Utils.HEX.decode(sdata.getSerializedData())));
                }
            }
        }
        return prescriptionlist;
    }

    private boolean checkPrescription(UTXO utxo, Map<String, Token> tokennames) {
        return TokenType.prescription.ordinal() == tokennames.get(utxo.getTokenId()).getTokentype();

    }

    @Test
    public void testTokenidNotInWallet() throws Exception {

        ECKey key = prepareIdentity();

        ECKey issuer = new ECKey();
        ECKey userkey = new ECKey();
        TokenKeyValues kvs = certificateTokenKeyValues(issuer, userkey);
        walletAppKit1.wallet().importKey(issuer);
        Block block = createToken(issuer, userkey.getPublicKeyAsHex(), 0, "id.shop", "test", BigInteger.ONE, true, kvs,
                TokenType.identity.ordinal(), new ECKey().getPublicKeyAsHex(), walletAppKit1.wallet(),
                userkey.getPubKey(), null);
        TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
        walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), key, aesKey);
        sendEmpty(10);
        makeRewardBlock();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", currentToken.getToken().getTokenid());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
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
                SignedData identity = new SignedData().parse(decryptedPayload);
                identity.verify();
                if (DataClassName.KeyValueList.name().equals(identity.getDataClassName())) {
                    KeyValueList id = new KeyValueList().parse(Utils.HEX.decode(identity.getSerializedData()));
                    assertTrue(id.getKeyvalues().size() == 2);
                }
            }
        }

    }

    private ECKey prepareIdentity()
            throws Exception, JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {
        createShopToken();

        ECKey key = new ECKey();

        walletAppKit1.wallet().importKey(key);
        // walletAppKit1.wallet().importKey(preKey) ;
        final String tokenid = key.getPublicKeyAsHex();
        walletAppKit1.wallet().publishDomainName(key, tokenid, "id.shop", aesKey, "");
        // check signs list?
        walletAppKit.wallet().multiSign(tokenid, walletKeys.get(0), aesKey);

        makeRewardBlock();

        return key;
    }

    private TokenKeyValues getTokenKeyValues(ECKey key, ECKey userkey)
            throws InvalidCipherTextException, IOException, SignatureException {
        SignedData signeddata = new SignedData();
        IdentityCore identityCore = new IdentityCore();
        identityCore.setSurname("zhang");
        identityCore.setForenames("san");
        identityCore.setSex("man");
        identityCore.setDateofissue("20200101");
        identityCore.setDateofexpiry("20201231");
        IdentityData identityData = new IdentityData();
        identityData.setIdentityCore(identityCore);
        identityData.setIdentificationnumber("120123456789012345");
        identityData.uniqueNameIdentity();
        byte[] photo = "readFile".getBytes();
        // readFile(new File("F:\\img\\cc_aes1.jpg"));
        identityData.setPhoto(photo);
        signeddata.signData(key, identityData.toByteArray(), DataClassName.IdentityData.name());
        return signeddata.toTokenKeyValues(key, userkey);
    }

    private TokenKeyValues certificateTokenKeyValues(ECKey key, ECKey userkey)
            throws InvalidCipherTextException, IOException, SignatureException {
        SignedData signeddata = new SignedData();
        KeyValueList kvs = new KeyValueList();

        byte[] first = "my first file".getBytes();
        KeyValue kv = new KeyValue();
        kv.setKey("myfirst.txt");
        kv.setValue(Utils.HEX.encode(first));
        kvs.addKeyvalue(kv);
        kv = new KeyValue();
        kv.setKey("second.pdf");
        kv.setValue(Utils.HEX.encode("second.pdf".getBytes()));
        kvs.addKeyvalue(kv);

        signeddata.signData(key, kvs.toByteArray(), DataClassName.KeyValueList.name());
        return signeddata.toTokenKeyValues(key, userkey);
    }

    private SignedData signeddata(ECKey key) throws SignatureException {
        SignedData signedata = new SignedData();
        Prescription p = new Prescription();
        p.setPrescription("my first prescription");
        p.setFilename("second.pdf");
        p.setFile("second.pdf".getBytes());
        p.getCoins().add(new Coin(10, key.getPubKey()));
        signedata.signData(key, p.toByteArray(), DataClassName.Prescription.name());
        return signedata;
    }

    @Test
    public void testGetTokenById() throws Exception {

        testCreateToken(walletKeys.get(0), "test");

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletKeys.get(0).getPublicKeyAsHex());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() > 0);

        makeRewardBlock();

        resp = OkHttp3Util.postString(contextRoot + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        log.info("getOutputsResponse : " + getOutputsResponse);

        assertTrue(getOutputsResponse.getOutputs().size() > 0);
        assertTrue(getOutputsResponse.getOutputs().get(0).getValue()
                .equals(Coin.valueOf(77777L, walletKeys.get(0).getPubKey())));
    }

    public List<ECKey> payKeys() throws Exception {
        List<ECKey> userkeys = new ArrayList<ECKey>();
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        for (int i = 1; i <= 10; i++) {
            ECKey key = new ECKey();
            giveMoneyResult.put(key.toAddress(networkParameters).toString(), i * 10000l);
            userkeys.add(key);
        }

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, NetworkParameters.BIGTANGLE_TOKENID,
                "pay to user" );
        makeRewardBlock();

        log.debug("block " + (b == null ? "block is null" : b.toString()));

        return userkeys;
    }

    @Test
    public void testPayTokenById() throws Exception {

        payKeys();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", NetworkParameters.BIGTANGLE_TOKENID_STRING);
        makeRewardBlock();

        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        log.info("getOutputsResponse : " + getOutputsResponse);
        List<UTXO> outputs = getOutputsResponse.getOutputs();
        Map<String, Token> tokennames = getOutputsResponse.getTokennames();
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        for (UTXO utxo : outputs) {
            giveMoneyResult.put(utxo.getAddress(), utxo.getValue().getValue().longValue() * 3 / 1000);
        }
        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, NetworkParameters.BIGTANGLE_TOKENID,
                "pay to user" );
        log.debug("block " + (b == null ? "block is null" : b.toString()));

        makeRewardBlock();

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
        makeRewardBlock();
        {
            final String tokenid = walletKeys.get(0).getPublicKeyAsHex();
            walletAppKit1.wallet().publishDomainName(walletKeys.get(1), tokenid, "de", aesKey, "");

            List<ECKey> keys = new ArrayList<ECKey>();
            keys.add(preKey);
            for (int i = 0; i < keys.size(); i++) {
                walletAppKit1.wallet().multiSign(tokenid, keys.get(i), aesKey);
            }
        }

        makeRewardBlock();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletKeys.get(0).getPublicKeyAsHex());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.outputsOfTokenid.name(),
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
        makeRewardBlock();
        // same token id and index
        testCreateToken(walletKeys.get(0), "test");

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletKeys.get(0).getPublicKeyAsHex());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() > 0);

        makeRewardBlock();

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
        makeRewardBlock();

        testCreateToken(new ECKey(), "test");
        makeRewardBlock();

        testCreateToken(new ECKey(), "test");
        makeRewardBlock();

        testCreateToken(new ECKey(), "test");
        makeRewardBlock();

        // only one is ok.

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", walletAppKit.wallet().walletKeys().get(0).getPublicKeyAsHex());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() == 1);
        assertTrue(blockService.getBlockEvaluation(getTokensResponse.getTokens().get(0).getBlockHash(), store)
                .isConfirmed());

        requestParam.put("tokenid", walletAppKit.wallet().walletKeys().get(1).getPublicKeyAsHex());
        resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        log.info("getTokenById resp : " + resp);
        getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        log.info("getTokensResponse : " + getTokensResponse);
        assertTrue(getTokensResponse.getTokens().size() == 1);
        assertTrue(!blockService.getBlockEvaluation(getTokensResponse.getTokens().get(0).getBlockHash(), store)
                .isConfirmed());

    }

    @Test
    public void testCreateTokenMulti() throws Exception {

        createShopToken();
        ECKey key = new ECKey();
        createProductToken(key);
        TokenInfo currentToken = createProductToken(key);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", currentToken.getToken().getTokenid());
        byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
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

        makeRewardBlock();

    }

    private TokenInfo createProductToken(ECKey key)
            throws Exception, JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {

        walletAppKit1.wallet().importKey(key);
        Block block = createToken(key, "product", 0, "shop", "test", BigInteger.ONE, true, null,
                TokenType.identity.ordinal(), key.getPublicKeyAsHex(), walletAppKit1.wallet());
        TokenInfo currentToken = new TokenInfo().parseChecked(block.getTransactions().get(0).getData());
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(walletKeys.get(0));
        for (int i = 0; i < keys.size(); i++) {
            walletAppKit1.wallet().multiSign(currentToken.getToken().getTokenid(), keys.get(i), aesKey);
        }
        // sendEmpty(10);
        makeRewardBlock();

        return currentToken;
    }

    public   Token getToken(String  idcom ) throws Exception { 
       // String idcom= "02ffa8c71c0dd200c82fb07323147b4aca5c3ac7b93c6bf53730a42008b72bffa3";
                //idcom: "0365cc54778405323781041a791a1048d3742234fe07e6cce041419d8038ab26ed";
       //   String tokenid = "03d109174d7b8aaab67d4090e58cde8a69906f85a292d26333f04ac81d99371798";
          HashMap<String, Object> requestParam = new HashMap<String, Object>();
          requestParam.put("tokenid", idcom);
          byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                  Json.jsonmapper().writeValueAsString(requestParam));
         
      return   Json.jsonmapper().readValue(resp, GetTokensResponse.class).getTokens().get(0);
   
    
     
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
