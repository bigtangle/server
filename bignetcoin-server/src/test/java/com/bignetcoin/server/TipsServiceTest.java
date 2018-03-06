package com.bignetcoin.server;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
public class TipsServiceTest extends MySQLFullPrunedBlockChainTest {

    @Autowired
    private TipsService tipsManager;

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

     //@Before
    public void createBlock() throws Exception {
        System.out.println("-------------------- create block started -------------------------");
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        resetStore(store);

        blockgraph = new FullPrunedBlockGraph(PARAMS, store);
        ECKey outKey = new ECKey();
        int height = 1;

        Block rollingBlock1 = PARAMS.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock1);
        System.out.println("create block, hash : " + rollingBlock1.getHashAsString());

        Block rollingBlock = rollingBlock1;
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = rollingBlock.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
            System.out.println("create block, hash : " + rollingBlock.getHashAsString());
        }

        Block b0 = rollingBlock.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                PARAMS.getGenesisBlock().getHash());
        Block b1 = b0.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                rollingBlock.getHash());
        Block b2 = b1.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                b0.getHash());
        Block b3 = b1.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                b2.getHash());
        Block b4 = rollingBlock1.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                rollingBlock.getHash());
        Block b5 = b2.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                b0.getHash());
        List<Block> blocks = new ArrayList<Block>();
        blocks.add(b0);
        blocks.add(b1);
        blocks.add(b2);
        blocks.add(b3);
        blocks.add(b4);
        blocks.add(b5);
        for (Block block : blocks) {
            this.blockgraph.add(block);
            System.out.println("create block, hash : " + block.getHashAsString());
        }

        // Map<Sha256Hash, Set<Sha256Hash>> blockRatings1 = new
        // HashMap<Sha256Hash, Set<Sha256Hash>>();
        // tipsManager.updateHashRatings(b2.getHash(), blockRatings1, new
        // HashSet<>());
        //
        // for (Sha256Hash sha256Hash : blockRatings1.get(b2.getHash())) {
        // System.out.println("hash : " + sha256Hash.toString());
        // }

        Map<Sha256Hash, Set<Sha256Hash>> blockRatings1 = new HashMap<Sha256Hash, Set<Sha256Hash>>();
        tipsManager.updateHashRatings(b0.getHash(), blockRatings1, new HashSet<>());

        for (Sha256Hash sha256Hash : blockRatings1.get(b0.getHash())) {
            System.out.println("hash : " + sha256Hash.toString());
        }
        System.out.println("-------------------- create block end -------------------------");
    }

    @Test
    public void updateLinearRatingsTestWorks() throws Exception {
        // Map<Sha256Hash, Set<Sha256Hash>> blockRatings0 = new
        // HashMap<Sha256Hash, Set<Sha256Hash>>();
        // tipsManager.updateHashRatings(rollingBlock1.getHash(), blockRatings0,
        // new HashSet<>());
        // System.out.println(blockRatings0);

        Map<Sha256Hash, Set<Sha256Hash>> blockRatings1 = new HashMap<Sha256Hash, Set<Sha256Hash>>();
        tipsManager.updateHashRatings(PARAMS.getGenesisBlock().getHash(), blockRatings1, new HashSet<>());
        /*
         * for (Entry<Sha256Hash, Set<Sha256Hash>> entry :
         * blockRatings1.entrySet()) { System.out.println("hash : " +
         * entry.getKey().toString() + " rating"); for (Sha256Hash sha256Hash :
         * entry.getValue()) { System.out.println("hash : " +
         * sha256Hash.toString()); } }
         */
        for (Sha256Hash sha256Hash : blockRatings1.get(PARAMS.getGenesisBlock().getHash())) {
            System.out.println("hash : " + sha256Hash.toString());
        }
    }

    @Test
    public void getBlockToApprove() throws Exception {
        final SecureRandom random = new SecureRandom();
        Sha256Hash b0Sha256Hash = tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27, random);
        Sha256Hash b1Sha256Hash = tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27, random);
        System.out.println("b0Sha256Hash : " + b0Sha256Hash.toString());
        System.out.println("b1Sha256Hash : " + b1Sha256Hash.toString());
    }
    @Test
    public void getBlockToApproveTest2() throws Exception {
         createBlock();
        ECKey outKey = new ECKey();
        int height = 1;

        for (int i = 1; i < 200; i++) {
            Block r1 = blockService.getBlock(getNextBlockToApprove());
            Block r2 = blockService.getBlock(getNextBlockToApprove());
            Block rollingBlock = r2.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                    r1.getHash());
            blockgraph.add(rollingBlock);
            System.out.println("create block  : " + rollingBlock);
        }

    }

    public Sha256Hash getNextBlockToApprove() throws Exception {
        final SecureRandom random = new SecureRandom();
        return tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27, random);
        // Sha256Hash b1Sha256Hash =
        // tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null,
        // 27, 27, random);

    }
}