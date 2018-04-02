/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockForTest;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.MapToBeanMapperUtil;
import org.bitcoinj.utils.OkHttp3Util;
import org.junit.Before;
import org.junit.Test;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bignetcoin.server.config.GlobalConfigurationProperties;
import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.MilestoneService;
import com.bignetcoin.store.FullPrunedBlockGraph;
import com.bignetcoin.store.FullPrunedBlockStore;
import com.bignetcoin.store.MySQLFullPrunedBlockStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    protected MockMvc mockMvc;
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
    private GlobalConfigurationProperties globalConfigurationProperties;

    protected FullPrunedBlockGraph blockgraph;

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    private BlockService blockService;

    @Autowired
    private MilestoneService milestoneService;

    public FullPrunedBlockStore createStore(NetworkParameters params, int blockCount) throws BlockStoreException {
        try {
            String DB_HOSTNAME = globalConfigurationProperties.getHostname() + ":" + globalConfigurationProperties.getPort();
            String DB_NAME = globalConfigurationProperties.getDbName();
            String DB_USERNAME = globalConfigurationProperties.getUsername();
            String DB_PASSWORD = globalConfigurationProperties.getPassword();
            store = new MySQLFullPrunedBlockStore(params, blockCount, DB_HOSTNAME, DB_NAME, DB_USERNAME, DB_PASSWORD);
            // ((MySQLFullPrunedBlockStore)store).initFromDatabase();
            // delete + create +initFromDatabase
            ((MySQLFullPrunedBlockStore) store).resetStore();
        } catch (Exception e) {
            log.debug("", e);
        }
        // reset pro @test

        return store;
    }

    protected static final NetworkParameters PARAMS = new UnitTestParams() {
        @Override
        public int getInterval() {
            return 10000;
        }
    };

    @Before
    public void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        objectMapper = new ObjectMapper();
        // registerModule(new Jackson2HalModule());
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(networkParameters, UNDOABLE_BLOCKS_STORED);

        blockgraph = new FullPrunedBlockGraph(networkParameters, store);

        // Add genesis block
        blockgraph.add(networkParameters.getGenesisBlock());
        BlockEvaluation genesisEvaluation = blockService
                .getBlockEvaluation(networkParameters.getGenesisBlock().getHash());
        blockService.updateMilestone(genesisEvaluation, true);
        blockService.updateSolid(genesisEvaluation, true);
        walletKeys();
        testInitWallet();
        wallet1();
    }

    @Autowired
    private NetworkParameters networkParameters;

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

    public MockMvc getMockMvc() {
        return mockMvc;
    }

    public void walletKeys() throws Exception {
        KeyParameter aesKey = null;
        walletAppKit = new WalletAppKit(PARAMS, new File("../bignetcoin-wallet"), "bignetcoin");
        walletAppKit.wallet().setServerURL(contextRoot);
        walletKeys = walletAppKit.wallet().walletKeys(aesKey);
    }

    public void wallet1() throws Exception {
       
        walletAppKit1 = new WalletAppKit(PARAMS, new File("../bignetcoin-wallet"), "bignetcoin1");
        walletAppKit1.wallet().setServerURL(contextRoot);
 
    }
    
    public List<UTXO> testTransactionAndGetBalances() throws Exception {
        return testTransactionAndGetBalances(false);
    }

    // get balance for the walleKeys
    public List<UTXO> testTransactionAndGetBalances(boolean withZero) throws Exception {
    return testTransactionAndGetBalances(withZero, walletKeys);
    }

    // get balance for the walleKeys
    public List<UTXO> testTransactionAndGetBalances(boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        for (ECKey toKey : keys) {
            MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name())
                    .content(toKey.getPubKeyHash());
            MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk())
                    .andReturn();
            String response = mvcResult.getResponse().getContentAsString();
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
        }
        return listUTXO;
    }
    
    public void testInitWallet() throws Exception {

        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);

        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
       
        for (int i = 1; i < networkParameters.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        // Create bitcoin spend of 1 BTC.

        ECKey myKey = walletKeys.get(0);
        milestoneService.update();
        Block b = createGenesisBlock(myKey);
        milestoneService.update();

        rollingBlock = BlockForTest.createNextBlock(b, null, networkParameters.getGenesisBlock().getHash());
        System.out.println("rollingBlock : " + rollingBlock.toString());
        rollingBlock = networkParameters.getDefaultSerializer().makeBlock(rollingBlock.bitcoinSerialize());
        System.out.println("rollingBlock : " + rollingBlock.toString());

        System.out.println("key " + myKey.getPublicKeyAsHex());

        Coin amount = Coin.valueOf(100000, NetworkParameters.BIGNETCOIN_TOKENID);
        // Address address = new Address(PARAMS, toKey.getPubKeyHash());

        Transaction t = new Transaction(networkParameters);
        t.addOutput(new TransactionOutput(networkParameters, t, amount, myKey.toAddress(networkParameters)));
        t.addSignedInput(spendableOutput,  transaction.getOutputs().get(0).getScriptPubKey (), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);

        milestoneService.update();
        testTransactionAndGetBalances();
    }

    public Block createGenesisBlock(ECKey outKey) throws Exception {
        byte[] pubKey = outKey.getPubKey();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
        requestParam.put("amount", 164385643856L);
        requestParam.put("tokenname", "Test");
        requestParam.put("description", "Test");
        requestParam.put("blocktype", false);
        requestParam.put("tokenHex", Utils.HEX.encode(outKey.getPubKeyHash()));

        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.createGenesisBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        log.info("createGenesisBlock resp : " + block);

        return block;
    }

}
