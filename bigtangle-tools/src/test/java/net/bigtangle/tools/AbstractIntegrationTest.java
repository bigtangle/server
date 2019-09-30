/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.TokenIndexResponse;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public abstract class AbstractIntegrationTest {

    public static final String HTTPS_BIGTANGLE_LOCAL = "http://localhost:8088/";
    public static boolean testnet = true;
    public static String HTTPS_BIGTANGLE_DE = "https://" + (testnet ? "test." : "") + "bigtangle.de/";
    public static String HTTPS_BIGTANGLE_INFO =
            // HTTPS_BIGTANGLE_LOCAL;
            "https://" + (testnet ? "test." : "") + "bigtangle.info/";
    public static String HTTPS_BIGTANGLE_ORG = "https://" + (testnet ? "test." : "") + "bigtangle.org/";

    public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
    public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";

    // 1GDsvV5Vgwa7VYULyDmW9Unj9v1hWFxBJ5
    public static String USDTokenPub = "02fbef0f3e1344f548abb7d4b6a799e372d2310ff13fe023b3ba0446f2e3f58e04";
    public static String USDTokenPriv = "6fb4cb30d593536e3c54ac17bfaa311cb0e2bdf219c89483aa8d7185f6c5c3b7";

    // 1FM6YGALJNTUmdoofxW6Rw5C2jW6AESEEs
    public static String EURTokenPub = "03163532b12879ff2f52e84ec032662f5d0ee0eee33355d9c0a833d172d3d3e4cb";
    public static String EURTokenPriv = "52bd881f650733448b20358ca4ab9bdbf394c150948c81eedc1b666c1c47f61f";

    // 1GZH9mf9w9K3nc58xR3dpTJUuJdiLnuwdW
    public static String ETHTokenPub = "02b8b21c6341872dda1f3a4f26d0d887283ad99f342d1dc35552db39c830919722";
    public static String ETHTokenPriv = "9eac170431a4c8cb188610cea2d40a3db5f656db5b52c0ac5aa9aa3a3fa8366f";

    // 1PttyezdBEgrfB8ZUg8YtQoqtHoYJyyadS
    public static String BTCTokenPub = "0352ac6c7fe48bff55b6976e70718a5c37fe9ddf5541473284bff2c72f51fb60e2";
    public static String BTCTokenPriv = "d82d565e4ad9e2c78610535cbe0a37d2e240192bf1d7f42f4276cd89351f45d0";

    // 1Q5ysrjmeEjJKFBrnEJBSAs6vBHYzsmN2H
    public static String GOLDTokenPub = "02b5fb501bdb5ea68949f7fd37a7a75728ca3bdd4b0aacd1a6febc0c34a7338694";
    public static String GOLDTokenPriv = "5adeeab95523100880b689fc9150650acca8c3a977552851bde75f85e1453bf2";

    // 13YjgF6Wa3v4i6NQQqn94XCg6CX79NVgWe
    public static String JPYTokenPub = "03e608ba3cbce11acc4a6b5e0b63b3381af7e9f50c1d43e6a6ce8cf16d3743891c";
    public static String JPYTokenPriv = "7a62e210952b6d49d545edb6fe4c322d68605c1b97102448a3439158ba9acd5f";

    // private static final String CONTEXT_ROOT_TEMPLATE =
    // "http://localhost:%s/";
    public static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    public String contextRoot = // HTTPS_BIGTANGLE_DE;
            // "http://localhost:8088/";
            "https://test.bigtangle.de/";

    public List<ECKey> wallet1Keys;
    public List<ECKey> wallet2Keys;

    WalletAppKit walletAppKit1;
    WalletAppKit walletAppKit2;

    protected static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    protected static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
    protected static ObjectMapper objectMapper = new ObjectMapper();

    NetworkParameters networkParameters = MainNetParams.get();

    boolean deleteWlalletFile = false;

    @Before
    public void setUp() throws Exception {
        // System.setProperty("https.proxyHost",
        // "anwproxy.anwendungen.localnet.de");
        // System.setProperty("https.proxyPort", "3128");

        wallet1();
        wallet2();
        // emptyBlocks(10);
    }

    protected void assertHasAvailableToken(ECKey testKey, String tokenId_, Long amount) throws Exception {
        // Asserts that the given ECKey possesses the given amount of tokens
        List<UTXO> balance = getBalance(false, testKey);
        HashMap<String, Long> hashMap = new HashMap<>();
        for (UTXO o : balance) {
            String tokenId = Utils.HEX.encode(o.getValue().getTokenid());
            if (!hashMap.containsKey(tokenId))
                hashMap.put(tokenId, 0L);
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().getValue().longValue());
        }

        assertEquals(amount, hashMap.get(tokenId_));
    }

    protected Sha256Hash getRandomSha256Hash() {
        byte[] rawHashBytes = new byte[32];
        new Random().nextBytes(rawHashBytes);
        Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
        return sha256Hash;
    }

    public void importKeys(Wallet w) throws Exception {
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(ETHTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(BTCTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(EURTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(JPYTokenPriv)));
    }

    protected void wallet1() throws Exception {
        KeyParameter aesKey = null;
        // delete first
        File f = new File("./logs/", "bigtangle1");
        if (f.exists() & deleteWlalletFile)
            f.delete();
        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle1");
        walletAppKit1.wallet().setServerURL(contextRoot);

        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);
    }

    protected void wallet2() throws Exception {
        KeyParameter aesKey = null;
        // delete first
        File f = new File("./logs/", "bigtangle2");
        if (f.exists() & deleteWlalletFile)
            f.delete();
        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle2");
        walletAppKit2.wallet().setServerURL(contextRoot);

        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);
    }

    protected UTXO getBalance(String tokenid, boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> ulist = getBalance(withZero, keys);

        for (UTXO u : ulist) {
            if (tokenid.equals(u.getTokenId())) {
                return u;
            }
        }

        throw new RuntimeException();
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : keys) {
            // keyStrHex000.add(ecKey.toAddress(networkParameters).toString());
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
        String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        // String response = mvcResult.getResponse().getContentAsString();
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (withZero) {
                listUTXO.add(utxo);
            } else if (utxo.getValue().getValue().signum() > 0) {
                listUTXO.add(utxo);
            }
        }

        return listUTXO;
    }

    protected List<UTXO> getBalance(boolean withZero, ECKey ecKey) throws Exception {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ecKey);
        return getBalance(withZero, keys);
    }

    protected void payToWallet(Wallet wallet) throws Exception {
        ECKey fromkey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        giveMoneyResult.put(wallet.walletKeys().get(1).toAddress(networkParameters).toString(), 3333333l);
        wallet.payMoneyToECKeyList(null, giveMoneyResult, fromkey);
    }

  
    protected void checkBalance(Coin coin, ECKey ecKey) throws Exception {
        ArrayList<ECKey> a = new ArrayList<ECKey>();
        a.add(ecKey);
        checkBalance(coin, a);
    }

    protected void checkBalance(Coin coin, List<ECKey> a) throws Exception {

        List<UTXO> ulist = getBalance(false, a);
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue() == u.getValue().getValue()) {
                myutxo = u;
                break;
            }
        }
        assertTrue(myutxo != null);
        assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
        log.debug(myutxo.toString());
    }

    // create a token with multi sign
    protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname,
            String description) throws JsonProcessingException, Exception {
        try {
            createMultisignToken(key, new TokenInfo(), tokename, 678900000, decimals, domainname, description);

        } catch (Exception e) {
            // TODO: handle exception
            log.warn("", e);
        }

    }

    protected void createMultisignToken(ECKey key, TokenInfo tokenInfo, String tokename, int amount, int decimals,
            String domainname, String description)
            throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
        String tokenid = key.getPublicKeyAsHex();

        Coin basecoin = Coin.valueOf(amount, tokenid);

        // contextRoot = "http://localhost:8088/";

        // TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        long tokenindex_ = tokenIndexResponse.getTokenindex();

        Token tokens = Token.buildSimpleTokenInfo(true, tokenIndexResponse.getBlockhash(), tokenid, tokename,
                description, 1, tokenindex_, basecoin.getValue(), false, 0,
                networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));

        walletAppKit1.wallet().setServerURL(contextRoot);
        walletAppKit1.wallet().saveToken(tokenInfo, basecoin, key, null);

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));

        walletAppKit1.wallet().multiSign(tokenid, genesiskey, null);
    }

}
