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
import org.bitcoinj.core.BlockEvaluation;
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

    @Test
    public void testBlockEvaluationDb() throws Exception {
        List<Block> blocks = this.createBlock();
        for (Block block : blocks) {
            BlockEvaluation blockEvaluation = new BlockEvaluation();
            blockEvaluation.setBlockhash(block.getHash());
            blockEvaluation.setCumulativeweight(100);
            blockEvaluation.setDepth(99);
            blockEvaluation.setRating(98);
            blockEvaluation.setSolid(true);
            this.store.saveBlockEvaluation(blockEvaluation);
        }
        int i = 0;
        for (Block block : blocks) {
            if (i % 2 == 0) {
                this.store.removeBlockEvaluation(block.getHash());
            }
            i++;
        }
    }

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
            // ((MySQLFullPrunedBlockStore)store).initFromDatabase();
            // delete + create +initFromDatabase
            ((MySQLFullPrunedBlockStore) store).resetStore();
        } catch (Exception e) {
            System.out.println(e);
        }
        // reset pro @test

        return store;
    }

    // create simple linear blocks
    // each block point to genesis and prev block
    @Before
    public void setup() throws Exception {
        super.setUp();
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);

        blockgraph = new FullPrunedBlockGraph(PARAMS, store);
    }

    public List<Block> createLinearBlock() throws Exception {
        List<Block> blocks = new ArrayList<Block>();
        Block rollingBlock1 = PARAMS.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock1);
        blocks.add(rollingBlock1);
        // System.out.println("create block, hash : " +
        // rollingBlock1.getHashAsString());

        Block rollingBlock = rollingBlock1;
        for (int i = 1; i < 5; i++) {
            rollingBlock = rollingBlock.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
            // System.out.println("create block, hash : " +
            // rollingBlock.getHashAsString());
            blocks.add(rollingBlock);
        }
        return blocks;
    }

    public List<Block> createBlock() throws Exception {

        Block b0 = PARAMS.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                height++, PARAMS.getGenesisBlock().getHash());
        Block b1 = b0.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                PARAMS.getGenesisBlock().getHash());
        Block b2 = b1.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                b0.getHash());
        Block b3 = b1.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                b2.getHash());
        Block b4 = b3.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                b2.getHash());
        Block b5 = b4.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,
                b1.getHash());
        List<Block> blocks = new ArrayList<Block>();
        blocks.add(b0);
        blocks.add(b1);
        blocks.add(b2);
        blocks.add(b3);
        blocks.add(b4);
        blocks.add(b5);
        int i = 0;
        for (Block block : blocks) {
            this.blockgraph.add(block);
            System.out.println("create  " + i + " block:" + block.getHashAsString());
            i++;

        }
        return blocks;
    }

    @Test
    public void cumulativweigth() throws Exception {
        List<Block> re = createBlock();
        Map<Sha256Hash, Long> cumulativweigths = new HashMap<Sha256Hash, Long>();
        tipsManager.recursiveUpdateCumulativeweights(re.get(0).getHash(), cumulativweigths, new HashSet<>());
        int i = 0;
        for (Block block : re) {
            System.out.println("  " + i + " block:" + block.getHashAsString() + " cumulativweigth : "
                    + cumulativweigths.get(re.get(i).getHash()));
            i++;
        } 
        Iterator it = cumulativweigths.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Sha256Hash, Long> pair = (Map.Entry<Sha256Hash, Long>) it.next();
            System.out.println(
                    "hash : " + pair.getKey() +    " -> " + pair.getValue());
        }

    }

    @Test
    public void depth() throws Exception {
        List<Block> re = createLinearBlock();
        Map<Sha256Hash, Long> depths = new HashMap<Sha256Hash, Long>();
        tipsManager.recursiveUpdateDepth(re.get(0).getHash(), depths);
        int i = 0;
        for (Block block : re) {
            System.out.println(
                    "  " + i + " block:" + block.getHashAsString() + " depth : " + depths.get(re.get(i).getHash()));
            Sha256Hash blockhash = block.getHash();
            Long depth = depths.get(re.get(i).getHash());
            this.store.updateBlockEvaluationDepth(blockhash, depth.intValue());
            i++;
        }
    }

    @Test
    public void updateLinearCumulativeweightsTestWorks() throws Exception {
        createLinearBlock();
        Map<Sha256Hash, Set<Sha256Hash>> blockCumulativeweights1 = new HashMap<Sha256Hash, Set<Sha256Hash>>();
        tipsManager.updateHashCumulativeweights(PARAMS.getGenesisBlock().getHash(), blockCumulativeweights1,
                new HashSet<>());

        Iterator<Map.Entry<Sha256Hash, Set<Sha256Hash>>> iterator = blockCumulativeweights1.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Sha256Hash, Set<Sha256Hash>> pair = (Map.Entry<Sha256Hash, Set<Sha256Hash>>) iterator.next();
            System.out.println(
                    "hash : " + pair.getKey() + " \n  size " + pair.getValue().size() + "-> " + pair.getValue());
            Sha256Hash blockhash = pair.getKey();
            int cumulativeweight = pair.getValue().size();
            this.store.updateBlockEvaluationCumulativeweight(blockhash, cumulativeweight);
        }

    }

    @Test
    public void getBlockToApprove() throws Exception {
        final SecureRandom random = new SecureRandom();
        for (int i = 1; i < 20; i++) {
            Sha256Hash b0Sha256Hash = tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27,
                    random);
            Sha256Hash b1Sha256Hash = tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27,
                    random);
            System.out.println("b0Sha256Hash : " + b0Sha256Hash.toString());
            System.out.println("b1Sha256Hash : " + b1Sha256Hash.toString());
        }
    }

    @Test
    public void getBlockToApproveTest2() throws Exception {
        createBlock();
        ECKey outKey = new ECKey();
        int height = 1;

        for (int i = 1; i < 20; i++) {
            Block r1 = blockService.getBlock(getNextBlockToApprove());
            Block r2 = blockService.getBlock(getNextBlockToApprove());
            Block rollingBlock = r2.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    height++, r1.getHash());
            blockgraph.add(rollingBlock);
            System.out.println("create block  : " + i + " " + rollingBlock);
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