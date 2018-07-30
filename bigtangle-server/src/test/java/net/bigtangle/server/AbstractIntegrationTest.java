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
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.DBStoreConfiguration;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
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
    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
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
    static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";

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

    public UTXO testTransactionAndGetBalances(String tokenid, boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> ulist = testTransactionAndGetBalances(withZero, keys);

        for (UTXO u : ulist) {
            if (tokenid.equals(u.getTokenId())) {
                return u;
            }
        }

        throw new RuntimeException();
    }

    // get balance for the walleKeys
    public List<UTXO> testTransactionAndGetBalances(boolean withZero, List<ECKey> keys) throws Exception {
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
            } else if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
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

        testCreateMultiSig();
        testCreateMarket();
        milestoneService.update();

        List<UTXO> ux = testTransactionAndGetBalances();
        assertTrue(!ux.isEmpty());
        for (UTXO u : ux) {
            log.debug(u.toString());
        }

      //  testInitTransferWallet();
    }

    // transfer the coin deon test key to address in wallet
    @SuppressWarnings("deprecation")
    public void testInitTransferWallet() throws Exception {

        Wallet coinbaseWallet = new Wallet(networkParameters);
        coinbaseWallet.importKey(new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub)));
        coinbaseWallet.setServerURL(contextRoot);
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction,
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        Coin amount = Coin.parseCoin("3333333", NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(walletKeys.get(1).toAddress(networkParameters), amount);
        coinbaseWallet.completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize()));

        checkBalance(amount, walletKeys);

    }

  
    public void testCreateMultiSig() throws JsonProcessingException, Exception {

        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(Utils.HEX.encode(pubKey), "test", "", "", 1, false, false, false);
        tokenInfo.setTokens(tokens);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(77777L, pubKey);

        long amount = basecoin.getValue();
        tokenInfo.setTokenSerial(new TokenSerial(tokens.getTokenid(), 0, amount));

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
    }

 
    public void testCreateMarket() throws JsonProcessingException, Exception {

        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(Utils.HEX.encode(pubKey), "p2p", "", "http://localhost:80089", 1, false, true,
                false);
        tokenInfo.setTokens(tokens);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(0, pubKey);

        long amount = basecoin.getValue();
        tokenInfo.setTokenSerial(new TokenSerial(tokens.getTokenid(), 0, amount));

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
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

    public void checkBalance(Coin coin, List<ECKey> a) throws Exception {
        milestoneService.update();
        List<UTXO> ulist = testTransactionAndGetBalances(false, a);
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue() == u.getValue().getValue()) {
                myutxo = u;
                break;
            }
        }
        assertTrue(myutxo != null);
        assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
    }
}
