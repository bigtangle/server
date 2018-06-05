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
import net.bigtangle.core.BlockForTest;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.server.config.DBStoreConfiguration;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;

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
    
    public List<UTXO> testTransactionAndGetBalances(boolean withZero,ECKey ecKey) throws Exception {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ecKey);
        return testTransactionAndGetBalances(withZero, keys);
    }

    public void testInitWallet() throws Exception {

        ECKey outKey = new ECKey();
        int height = 1;

        // TODO no more spendable mining outputs...
        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);

        ECKey myKey = walletKeys.get(0);
        Block b = createToken(myKey);
        // TODO why no milestone, the program hangs here
        milestoneService.update();
        // pay from outKey to mykey
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
        rollingBlock = BlockForTest.createNextBlock(b, null, networkParameters.getGenesisBlock().getHash());
        Coin amount = Coin.valueOf(10000, NetworkParameters.BIGNETCOIN_TOKENID);
        Transaction t = new Transaction(networkParameters);
        t.setMemo("test memo");

        t.addOutput(new TransactionOutput(networkParameters, t, amount, myKey.toAddress(networkParameters)));
        t.addSignedInput(spendableOutput, transaction.getOutputs().get(0).getScriptPubKey(), outKey);

        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);

        milestoneService.update();
        List<UTXO> ux = testTransactionAndGetBalances();
        assertTrue(!ux.isEmpty());
        for (UTXO u : ux) {
            log.debug(u.toString());
        }
    }

    public Block createToken(ECKey outKey) throws Exception {
        byte[] pubKey = outKey.getPubKey();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
        requestParam.put("amount", 77777L);
        requestParam.put("tokenname", "Test");
        requestParam.put("description", "Test");
        requestParam.put("multiserial", false);
        requestParam.put("asmarket", false);
        requestParam.put("tokenstop", false);
        requestParam.put("tokenHex", outKey.getPublicKeyAsHex());

        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.createGenesisBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        log.info("createGenesisBlock resp : " + block);

        return block;
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
