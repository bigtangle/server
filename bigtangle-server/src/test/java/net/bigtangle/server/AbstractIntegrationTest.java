/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.web.context.WebApplicationContext;

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
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.server.config.DBStoreConfiguration;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {})

@TestExecutionListeners(value = { DependencyInjectionTestExecutionListener.class, MockitoTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class

})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    private static final String CONTEXT_ROOT_TEMPLATE = "http://localhost:%s/";
    private static final Logger log = LoggerFactory.getLogger(TipsServiceTest.class);
    public String contextRoot;
    public List<ECKey> walletKeys;
    public List<ECKey> wallet1Keys;

    WalletAppKit walletAppKit;
    protected static ObjectMapper objectMapper;

    WalletAppKit walletAppKit1;
    @Autowired
    protected WebApplicationContext webContext;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    public void prepareContextRoot(@Value("${local.server.port}") int port) {
        contextRoot = String.format(CONTEXT_ROOT_TEMPLATE, port);
    }

    @Autowired
    protected FullPrunedBlockGraph blockgraph;
    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    private MilestoneService milestoneService;
    @Autowired
    DBStoreConfiguration dbConfiguration;
    @Autowired
    NetworkParameters networkParameters;

    static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    static String testPiv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";

    @Before
    public void setUp() throws Exception {

        objectMapper = new ObjectMapper();

        store = dbConfiguration.store();
        store.resetStore();

        walletKeys();

        testInitWallet();
        wallet1();

    }

    public String toJson(Object object) throws JsonProcessingException {
        return getMapper().writeValueAsString(object);
    }

    public static ObjectMapper getMapper() {
        return objectMapper;
    }

    public String getContextRoot() {
        return contextRoot;
    }

    public ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public void walletKeys() throws Exception {
        KeyParameter aesKey = null;
        walletAppKit = new WalletAppKit(networkParameters, new File("../bigtangle-wallet"), "bigtangle");
        walletAppKit.wallet().setServerURL(contextRoot);
        walletKeys = walletAppKit.wallet().walletKeys(aesKey);
    }

    public void wallet1() throws Exception {
        KeyParameter aesKey = null;
        walletAppKit1 = new WalletAppKit(networkParameters, new File("../bigtangle-wallet"), "bigtangle1");
        walletAppKit1.wallet().setServerURL(contextRoot);

        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);
    }

    public List<UTXO> testTransactionAndGetBalances() throws Exception {
        return testTransactionAndGetBalances(false);
    }

    // get balance for the walleKeys
    public List<UTXO> testTransactionAndGetBalances(boolean withZero) throws Exception {
        return testTransactionAndGetBalances(withZero, walletKeys);
    }

    // get balance for the walleKeys
    @SuppressWarnings("unchecked")
    public List<UTXO> testTransactionAndGetBalances(boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : keys) {
            // keyStrHex000.add(ecKey.toAddress(networkParameters).toString());
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
        String response = OkHttp3Util.post(contextRoot + "batchGetBalances",
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        // String response = mvcResult.getResponse().getContentAsString();
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        if (data != null && !data.isEmpty()) {
            List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("outputs");
            if (list != null && !list.isEmpty()) {
                for (Map<String, Object> object : list) {
                    UTXO u = MapToBeanMapperUtil.parseUTXO(object);
                    if (withZero) {
                        listUTXO.add(u);
                    } else {
                        if (u.getValue().getValue() > 0)
                            listUTXO.add(u);
                    }

                }
            }

        }

        return listUTXO;
    }

    public List<UTXO> testTransactionAndGetBalances(boolean withZero, ECKey ecKey) throws Exception {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ecKey);
        return testTransactionAndGetBalances(withZero, keys);
    }

    public void testInitWallet() throws Exception {

        ECKey myKey = walletKeys.get(0);
        testCreateMultiSig();
        milestoneService.update();

        List<UTXO> ux = testTransactionAndGetBalances();
        assertTrue(!ux.isEmpty());
        for (UTXO u : ux) {
            log.debug(u.toString());
        }

        testInitTransferWallet();
    }

    // transfer the coin deon test key to address in wallet
    public void testInitTransferWallet() throws Exception {

        Wallet coinbaseWallet = new Wallet(networkParameters);
        coinbaseWallet.importKey(new ECKey(Utils.HEX.decode(testPiv), Utils.HEX.decode(testPub)));
        coinbaseWallet.setServerURL(contextRoot);
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        Coin amount = Coin.parseCoin("3", NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(walletKeys.get(1).toAddress(networkParameters), amount);
        coinbaseWallet.completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        checkResponse(OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize()));

        checkBalance(NetworkParameters.BIGNETCOIN_TOKENID_STRING, walletKeys);

    }

    @SuppressWarnings("unchecked")
    public void testCreateMultiSig() throws JsonProcessingException, Exception {
        // Setup transaction and signatures
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);

        String tokenid = keys.get(5).getPublicKeyAsHex();

        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(tokenid, UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", 3, true,
                true, false);
        tokenInfo.setTokens(tokens);

        ECKey key1 = keys.get(0);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        ECKey key3 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key3.getPublicKeyAsHex()));

        int amount = 678900000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + "getCalTokenIndex",
                Json.jsonmapper().writeValueAsString(requestParam00));
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp2, HashMap.class);
        Integer tokenindex_ = (Integer) result2.get("tokenindex");

        tokenInfo.setTokenSerial(new TokenSerial(tokenid, tokenindex_, 100000000));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlocktype( NetworkParameters.BLOCKTYPE_TOKEN_CREATION );
       
        block.addCoinbaseTransaction(key1.getPubKey(), basecoin, tokenInfo);
        block.solve();

        System.out.println("block hash : " + block.getHashAsString());

        // save block
        OkHttp3Util.post(contextRoot + "multiSign", block.bitcoinSerialize());

        List<ECKey> ecKeys = new ArrayList<ECKey>();
        ecKeys.add(key1);
        ecKeys.add(key2);
        ecKeys.add(key3);

        for (ECKey ecKey : ecKeys) {
            HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
            requestParam0.put("address", ecKey.toAddress(networkParameters).toBase58());
            String resp = OkHttp3Util.postString(contextRoot + "getMultiSignWithAddress",
                    Json.jsonmapper().writeValueAsString(requestParam0));
            System.out.println(resp);

            HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
            List<HashMap<String, Object>> multiSigns = (List<HashMap<String, Object>>) result.get("multiSigns");
            byte[] payloadBytes = Utils.HEX.decode((String) multiSigns.get((int) tokenindex_ - 1).get("blockhashHex"));
            Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
            Transaction transaction = block0.getTransactions().get(0);

            List<MultiSignBy> multiSignBies = null;
            if (transaction.getDatasignature() == null) {
                multiSignBies = new ArrayList<MultiSignBy>();
            } else {
                multiSignBies = Json.jsonmapper().readValue(transaction.getDatasignature(), List.class);
            }
            Sha256Hash sighash = transaction.getHash();
            ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
            byte[] buf1 = party1Signature.encodeToDER();

            MultiSignBy multiSignBy0 = new MultiSignBy();
            multiSignBy0.setTokenid(tokenid);
            multiSignBy0.setTokenindex(tokenindex_);
            multiSignBy0.setAddress(ecKey.toAddress(networkParameters).toBase58());
            multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
            multiSignBy0.setSignature(Utils.HEX.encode(buf1));
            multiSignBies.add(multiSignBy0);

            transaction.setDatasignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));
            checkResponse(OkHttp3Util.post(contextRoot + "multiSign", block0.bitcoinSerialize()));

        }
        // checkBalance(tokenid, ecKeys );
    }

    public void checkResponse(String resp) throws JsonParseException, JsonMappingException, IOException {
        checkResponse(resp, 0);
    }

    public void checkResponse(String resp, int code) throws JsonParseException, JsonMappingException, IOException {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int error = (Integer) result2.get("errorcode");
        assertTrue(error == code);
    }

    public void checkBalance(String tokenid, List<ECKey> a) throws Exception {
        List<UTXO> ulist = testTransactionAndGetBalances(false, a);
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (tokenid.equals(u.getTokenid())) {
                myutxo = u;
                break;
            }
        }
        assertTrue(myutxo != null);
        assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
    }
}
