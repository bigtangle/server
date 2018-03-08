package com.bignetcoin.server;

import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.MySQLFullPrunedBlockChainTest;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.MySQLFullPrunedBlockStore;
import org.junit.Before;
import org.junit.runner.RunWith;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = { })

//TODO Fix for incompatible mockito version. We use 2+ but spring 1.4.2 only supports 1.X.
//ResetMocksTestExecutionListener would be loaded by default and crashes on runtime.
//So we have to define our TestExecutionListeners manually.
//Remove this as soon as we have Spring >= 1.5.0
@TestExecutionListeners(value = {
        DependencyInjectionTestExecutionListener.class,
        MockitoTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class

})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public   class AbstractIntegrationTest extends MySQLFullPrunedBlockChainTest {
 
    private static final String CONTEXT_ROOT_TEMPLATE = "http://localhost:%s/";
    
    public String contextRoot;
    
    private MockMvc mockMvc;
    private static ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext webContext;

    @Autowired
    private ConfigurableApplicationContext applicationContext;
    @Autowired
    private GlobalConfigurationProperties globalConfigurationProperties;

    
    @Autowired
    public void prepareContextRoot(@Value("${local.server.port}") int port) {
        contextRoot = String.format(CONTEXT_ROOT_TEMPLATE, port);
         
    }
    
  
    @Before
    public void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext).build();
       // objectMapper = new ObjectMapper().registerModule(new Jackson2HalModule());
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);

        blockgraph = new FullPrunedBlockGraph(PARAMS, store);
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

    public MockMvc getMockMvc() {
        return mockMvc;
    }
}
