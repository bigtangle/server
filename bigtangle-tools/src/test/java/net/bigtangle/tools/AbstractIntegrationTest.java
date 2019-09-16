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

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.core.http.server.resp.TokenIndexResponse;
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
    public String contextRoot = HTTPS_BIGTANGLE_DE;
    // "http://localhost:8088/";

    public List<ECKey> walletKeys;
    public List<ECKey> wallet1Keys;
    public List<ECKey> wallet2Keys;

    WalletAppKit walletAppKit;
    WalletAppKit walletAppKit1;
    WalletAppKit walletAppKit2;

    protected static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    protected static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
    protected static ObjectMapper objectMapper = new ObjectMapper();

    NetworkParameters networkParameters = MainNetParams.get();

    boolean deleteWlalletFile = false;

    @Before
    public void setUp() throws Exception {
     //   System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
     //   System.setProperty("https.proxyPort", "3128");
        walletKeys();
        wallet1();
        wallet2();
        // emptyBlocks(10);
    }

    protected Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            List<Block> addedBlocks) throws Exception {
        Thread.sleep(30000);
        Block block = walletAppKit.wallet().sellOrder(null, tokenId, sellPrice, sellAmount, null, null);
        addedBlocks.add(block);
        return block;

    }

    protected Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            List<Block> addedBlocks) throws Exception {
        // Thread.sleep(100000);
        Block block = walletAppKit.wallet().buyOrder(null, tokenId, buyPrice, buyAmount, null, null);
        addedBlocks.add(block);
        return block;

    }

    protected Block makeAndConfirmCancelOp(Block order, ECKey legitimatingKey, List<Block> addedBlocks)
            throws Exception {
        Block block = walletAppKit.wallet().cancelOrder(order.getHash(), legitimatingKey);
        addedBlocks.add(block);
        return block;
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

    protected void walletKeys() throws Exception {
        KeyParameter aesKey = null;
        File f = new File("./logs/", "bigtangle");
        if (f.exists() & deleteWlalletFile)
            f.delete();
        walletAppKit = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle");
        walletAppKit.wallet().importKey(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub)));
        // add ge
        walletAppKit.wallet().setServerURL(contextRoot);
        walletKeys = walletAppKit.wallet().walletKeys(aesKey);
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

    protected List<UTXO> getBalance() throws Exception {
        return getBalance(false);
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(boolean withZero) throws Exception {
        return getBalance(withZero, walletKeys);
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

    protected void testCreateToken() throws JsonProcessingException, Exception {
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        String tokenid = Utils.HEX.encode(pubKey);

        Coin basecoin = Coin.valueOf(77777L, pubKey);

        Token tokens = Token.buildSimpleTokenInfo(true, "", tokenid, "test", "", 1, 0, basecoin.getValue(), true, 0,
                networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
    }

    protected void checkResponse(String resp) throws JsonParseException, JsonMappingException, IOException {
        checkResponse(resp, 0);
    }

    protected void checkResponse(String resp, int code) throws JsonParseException, JsonMappingException, IOException {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int error = (Integer) result2.get("errorcode");
        assertTrue(error == code);
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
    protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname)
            throws JsonProcessingException, Exception {
        try {
            createMultisignToken(key, new TokenInfo(), tokename, 678900000, decimals, domainname);
                   
        } catch (Exception e) {
            // TODO: handle exception
            log.warn("", e);
        }

    }

    protected void createMultisignToken(ECKey key, TokenInfo tokenInfo, String tokename, int amount, int decimals,
            String domainname)
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
        String prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, tokename, tokename, 1, tokenindex_,
                basecoin.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);

        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));

        walletAppKit.wallet().setServerURL(contextRoot);
        walletAppKit.wallet().saveToken(tokenInfo, basecoin, key, null);

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));

        walletAppKit.wallet().multiSign(tokenid, genesiskey, null);
    }

    public void emptyBlocks(int number) throws JsonProcessingException, Exception {
        for (int i = 0; i < number; i++) {
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));

            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
            rollingBlock.solve();

            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        }
    }

    // for unit tests
    public Block saveTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws IOException {
        Block block = makeTokenUnitTest(tokenInfo, basecoin, outKey, aesKey);
        OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
        return block;
    }

    // for unit tests
    public Block makeTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws JsonProcessingException, IOException {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

        block.addCoinbaseTransaction(outKey.getPubKey(), basecoin, tokenInfo);

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();

        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        // save block
        block.solve();
        return block;
    }

}
