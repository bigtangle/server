package com.bignetcoin.server;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.MySQLFullPrunedBlockChainTest;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.MySQLFullPrunedBlockStore;
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
public class TipsServiceTest extends MySQLFullPrunedBlockChainTest {

    @Autowired
    TipsService tipsManager;
    @Autowired
    private BlockService blockService;

    @Test
    public void testECKey() {
        for (int i = 0; i < 1; i++) {
            ECKey outKey = new ECKey();
            System.out.println("prK : " + outKey.getPrivateKeyAsHex() + ", " + outKey.getPublicKeyAsHex());
        }
    }
    
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
        resetStore(store);
        return store;
    }

    @Test
    public void updateLinearRatingsTestWorks() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        resetStore(store);
        blockgraph = new FullPrunedBlockGraph(PARAMS, store);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock1 = PARAMS.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock1);
        Transaction transaction = rollingBlock1.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        Block rollingBlock = rollingBlock1;
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = rollingBlock.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
            
            
            System.out.println(rollingBlock.getHashAsString());
        }

        Map<Sha256Hash, Set<Sha256Hash>> ratings = new HashMap<>();
        tipsManager.updateHashRatings(rollingBlock1.getHash(), ratings, new HashSet<>());
        System.out.println(ratings);
        tipsManager.updateHashRatings(PARAMS.getGenesisBlock().getHash(), ratings, new HashSet<>());

        System.out.println(ratings);

    }

    @Test
    public void getBlockToApprove() throws Exception {
        updateLinearRatingsTestWorks();
        final SecureRandom random = new SecureRandom();

        Sha256Hash re = tipsManager.blockToApprove(null, null, 27, 27, random);
        Sha256Hash re2 = tipsManager.blockToApprove(null, null, 27, 27, random);
        System.out.println(re);
        System.out.println(blockService.getBlock(re));
        
 
         
    }

}