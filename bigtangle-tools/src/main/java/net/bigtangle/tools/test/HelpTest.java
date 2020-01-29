/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.params.TestParams;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public abstract class HelpTest {

    public static final String HTTPS_BIGTANGLE_LOCAL = "http://localhost:8088/";

    public static String HTTPS_BIGTANGLE_DE = "https://test.bigtangle.de/";
    public static String HTTPS_BIGTANGLE_INFO = "https://test1.bigtangle.info/";
    public static String HTTPS_BIGTANGLE_ORG = "https://test1.bigtangle.org/";

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

    // 13YjgF6Wa3v4i6NQQqn94XCg6CX79NVgWe
    public static String JPYTokenPub = "03e608ba3cbce11acc4a6b5e0b63b3381af7e9f50c1d43e6a6ce8cf16d3743891c";
    public static String JPYTokenPriv = "7a62e210952b6d49d545edb6fe4c322d68605c1b97102448a3439158ba9acd5f";

 
    // 1Q5ysrjmeEjJKFBrnEJBSAs6vBHYzsmN2H
    public static String ShopDomainPub = "02b5fb501bdb5ea68949f7fd37a7a75728ca3bdd4b0aacd1a6febc0c34a7338694";
    public static String ShopDomainPriv = "5adeeab95523100880b689fc9150650acca8c3a977552851bde75f85e1453bf2";

    
    public static String BigtangleDomainPub = "02122251e6e3cdbe3e4bbaa4bc0dcc12014c6cf0388abac61bf2c972579d790a68";
      public static String BigtangleDomainPriv = "dbee6582476dc44ac1e26c67733205ff4c50a1a6a6716667b4428b36f0dcb7bc";
    
      public static String  DomainComPriv =  "64a48e5a568e4498a51df1d35eced926b27d7bb29bfb0d4f6efb256c97381e07";
 
      public static String  DomainComPub = "022d607a37d3d4467557a003189531a8198abb9967adec542edea70305b4785324";
      
      
      // private static final String CONTEXT_ROOT_TEMPLATE =
    // "http://localhost:%s/";
    public static final Logger log = LoggerFactory.getLogger(HelpTest.class);

    public static String TESTSERVER1 = HTTPS_BIGTANGLE_DE;
            //"https://p.bigtangle.org:8088/";

    public static String TESTSERVER2 = HTTPS_BIGTANGLE_INFO;

    public String contextRoot = TESTSERVER1;
    // "http://localhost:8088/";
    // "https://test.bigtangle.de/";

    public List<ECKey> wallet1Keys;
    public List<ECKey> wallet2Keys;

    WalletAppKit walletAppKit1;
    WalletAppKit walletAppKit2;

    protected static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    protected static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
    protected static ObjectMapper objectMapper = new ObjectMapper();

    NetworkParameters networkParameters = TestParams.get();

    boolean deleteWlalletFile = true;

    @Before
    public void setUp() throws Exception {
//         System.setProperty("https.proxyHost",
//         "anwproxy.anwendungen.localnet.de");
//          System.setProperty("https.proxyPort", "3128");
        mkdir();
        wallet1();
        wallet2();
        // emptyBlocks(10);
    }
 
 
    public void importKeys(Wallet w) throws Exception {
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(ETHTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(BTCTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(EURTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(JPYTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(ShopDomainPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(BigtangleDomainPriv)));
    }

    
    protected void mkdir() throws Exception {
        File f = new File("./logs" );
        if (!f.exists()) 
               f.mkdirs();
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

    protected WalletAppKit createAndInitWallet(String walletName) {
        File f = new File("./logs/", walletName + ".wallet");
        if (f.exists() & deleteWlalletFile) {
            f.delete();
        }
        WalletAppKit walletAppKit = new WalletAppKit(networkParameters, new File("./logs/"), walletName);
        walletAppKit.wallet().setServerURL(contextRoot);
        return walletAppKit;
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
        wallet.payMoneyToECKeyList(null, giveMoneyResult, "payToWallet");
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

    protected Coin sumUTXOBalance(String tokenid, ECKey ecKey) throws Exception {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ecKey);

        Coin coinbase = Coin.ZERO;
        List<UTXO> outputs = this.getBalance(false, keys);
        for (UTXO utxo : outputs) {
            if (tokenid.equals(utxo.getTokenId())) {
                coinbase.add(utxo.getValue());
            }
        }
        return coinbase;
    }

    // create a token with multi sign
    protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals,  String domainname  ,
            String description, BigInteger amount) throws JsonProcessingException, Exception {
        try {
            walletAppKit1.wallet().setServerURL(contextRoot);
            walletAppKit1.wallet().createToken(key, tokename, decimals, domainname, description, amount, true, null) ;

            ECKey signkey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                    Utils.HEX.decode(testPub));

            walletAppKit1.wallet().multiSign(key.getPublicKeyAsHex(), signkey, null);
            
            signkey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(BigtangleDomainPriv),
                    Utils.HEX.decode(testPub));
            walletAppKit1.wallet().multiSign(key.getPublicKeyAsHex(), signkey, null);


        } catch (Exception e) {
            // TODO: handle exception
            log.warn("", e);
        }

    }

     protected void createMultisignToken(ECKey key, TokenInfo tokenInfo, String tokename, BigInteger amount, int decimals,
            Token domainname, String description)
            throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
       

  
           }

    public void testCheckToken(String server) throws JsonProcessingException, Exception {
        Wallet w = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(testPriv)));

        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(ETHTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(BTCTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(EURTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(JPYTokenPriv)));
        Map<String, BigInteger> tokensums = tokensum(server);
        Set<String> tokenids = tokensums.keySet();

        for (String tokenid : tokenids) {
            Coin tokensum = new Coin(tokensums.get(tokenid) == null ? BigInteger.ZERO : tokensums.get(tokenid),
                    tokenid);

            testCheckToken(server, tokenid, tokensum);
        }
    }

    public void testCheckToken(String server, String tokenid, Coin tokensum) throws JsonProcessingException, Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(server + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        // log.info("getOutputsResponse : " + getOutputsResponse);
        Coin sumUnspent = Coin.valueOf(0l, tokenid);
        // Coin sumCoinbase = Coin.valueOf(0l, tokenid);
        for (UTXO u : getOutputsResponse.getOutputs()) {

            if (u.isConfirmed() && !u.isSpent())
                sumUnspent = sumUnspent.add(u.getValue());
        }

        Coin ordersum = ordersum(tokenid, server);

        log.info("sumUnspent : " + sumUnspent);
        log.info("ordersum : " + ordersum);
        // log.info("sumCoinbase : " + sumCoinbase);

        log.info("tokensum : " + tokensum);

        log.info("  ordersum + : sumUnspent = " + sumUnspent.add(ordersum));

        if (!tokenid.equals(NetworkParameters.BIGTANGLE_TOKENID_STRING))
            assertTrue(tokensum.equals(sumUnspent.add(ordersum)));
        else {
            assertTrue(tokensum.compareTo(sumUnspent.add(ordersum)) <= 0);
        }
        // assertTrue(sumCoinbase.equals(sumUnspent.add(ordersum)));
        // assertTrue(getOutputsResponse.getOutputs().get(0).getValue()
        // .equals(Coin.valueOf(77777L, walletKeys.get(0).getPubKey())));

    }

    public Coin ordersum(String tokenid, String server) throws JsonProcessingException, Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response0 = OkHttp3Util.post(server + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        Coin sumUnspent = Coin.valueOf(0l, tokenid);
        for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
            if (orderRecord.getOfferTokenid().equals(tokenid)) {
                sumUnspent = sumUnspent.add(Coin.valueOf(orderRecord.getOfferValue(), tokenid));
            }
        }
        return sumUnspent;
    }

    public Map<String, BigInteger> tokensum(String server) throws JsonProcessingException, Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", null);
        String response = OkHttp3Util.post(server + ReqCmd.searchTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        GetTokensResponse orderdataResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);

        return orderdataResponse.getAmountMap();
    }
}
