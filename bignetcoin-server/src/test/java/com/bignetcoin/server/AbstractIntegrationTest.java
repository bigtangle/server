/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.store.FullPrunedBlockGraph;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.MySQLFullPrunedBlockStore;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.bignetcoin.server.config.GlobalConfigurationProperties;
import com.bignetcoin.server.service.BlockService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {})

// TODO Fix for incompatible mockito version. We use 2+ but spring 1.4.2 only
// supports 1.X.
// ResetMocksTestExecutionListener would be loaded by default and crashes on
// runtime.
// So we have to define our TestExecutionListeners manually.
// Remove this as soon as we have Spring >= 1.5.0
@TestExecutionListeners(value = { DependencyInjectionTestExecutionListener.class, MockitoTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class

})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest   {

    private static final String CONTEXT_ROOT_TEMPLATE = "http://localhost:%s/";
    private static final Logger log = LoggerFactory.getLogger(TipsServiceTest.class);
    public String contextRoot;

    private MockMvc mockMvc;
    private static ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webContext;

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
    
    public FullPrunedBlockStore createStore(NetworkParameters params, int blockCount) throws BlockStoreException {
        try {
            String DB_HOSTNAME = globalConfigurationProperties.getHostname();
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
        @Override public int getInterval() {
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
		// TODO connectTransactions of genesisblock too
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
}
