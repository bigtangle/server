package com.bignetcoin.server;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.MySQLFullPrunedBlockChainTest;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.MySQLFullPrunedBlockStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.bignetcoin.server.config.GlobalConfigurationProperties;
import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.TipsService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class InitTest extends MySQLFullPrunedBlockChainTest {

    @Autowired
    private TipsService tipsManager;

    @Autowired
    private BlockService blockService;

    ECKey outKey = new ECKey();
    int height = 1;

    @Autowired
    private GlobalConfigurationProperties globalConfigurationProperties;

    @Override
    public FullPrunedBlockStore createStore(NetworkParameters params, int blockCount) throws BlockStoreException {
        try {
            String DB_HOSTNAME = globalConfigurationProperties.getHostname();
            String DB_NAME = globalConfigurationProperties.getDbName();
            String DB_USERNAME = globalConfigurationProperties.getUsername();
            String DB_PASSWORD = globalConfigurationProperties.getPassword();
            store = new MySQLFullPrunedBlockStore(params, blockCount, DB_HOSTNAME, DB_NAME, DB_USERNAME, DB_PASSWORD);
        } catch (RuntimeException e) {
        }
        // reset pro @test
         resetStore(store);
        return store;
    }

    // create simple linear blcoks
    // each block point to genesis and prev block
    @Before
    public void setup() throws Exception {
        super.setUp();
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);

        blockgraph = new FullPrunedBlockGraph(PARAMS, store);
    }
 

    @Test
    public void init() throws Exception { }

     
}