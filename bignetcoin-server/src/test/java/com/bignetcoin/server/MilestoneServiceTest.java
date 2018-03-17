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
import org.bitcoinj.core.BlockForTest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.bignetcoin.server.config.GlobalConfigurationProperties;
import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.MilestoneService;
import com.bignetcoin.server.service.TipsService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MilestoneServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MilestoneServiceTest.class);

    @Autowired
    private TipsService tipsManager;

    @Autowired
    private BlockService blockService;

    @Autowired
    private MilestoneService milestoneService;

    ECKey outKey = new ECKey();
    int height = 1;

    public List<Block> createTestTangle1() throws Exception {

        blockgraph.add(PARAMS.getGenesisBlock());
        BlockEvaluation genesisEvaluation = blockService.getBlockEvaluation(PARAMS.getGenesisBlock().getHash());
        blockService.updateMilestone(genesisEvaluation, true);
        blockService.updateSolid(genesisEvaluation, true);
    	
        Block b0 = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        Block b1 = BlockForTest.createNextBlockWithCoinbase(b0, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                height++, PARAMS.getGenesisBlock().getHash());
        Block b2 = BlockForTest.createNextBlockWithCoinbase(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                height++, b0.getHash());
        Block b3 = BlockForTest.createNextBlockWithCoinbase(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                height++, b2.getHash());
        Block b4 = BlockForTest.createNextBlockWithCoinbase(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                height++, b2.getHash());
        Block b5 = BlockForTest.createNextBlockWithCoinbase(b4, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                height++, b1.getHash());
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
           log.debug("create  " + i + " block:" + block.getHashAsString());
            i++;

        }
        return blocks;
    }

    @Test
    public void test() throws Exception {
        List<Block> re = createTestTangle1();
        milestoneService.update();
    }
    
//    @Test
//    public void cumulativweigthLinearBlock() throws Exception {
//        List<Block> re = createLinearBlock ();
//        Map<Sha256Hash, Long> cumulativweigths = new HashMap<Sha256Hash, Long>();
//        tipsManager.recursiveUpdateCumulativeweights(re.get(0).getHash(), cumulativweigths, new HashSet<>());
//        int i = 0;
//        for (Block block : re) {
//           log.debug("  " + i + " block:" + block.getHashAsString() + " cumulativweigth : "
//                    + cumulativweigths.get(re.get(i).getHash()));
//            i++;
//        }
//        Iterator<Map.Entry<Sha256Hash, Long>> it = cumulativweigths.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry<Sha256Hash, Long> pair = (Map.Entry<Sha256Hash, Long>) it.next();
//           log.debug("hash : " + pair.getKey() + " -> " + pair.getValue());
//            this.store.updateBlockEvaluationCumulativeweight(pair.getKey(), pair.getValue().intValue());
//        }
//    }
//    @Test
//    public void depth() throws Exception {
//        List<Block> re = createBlock();
//        Map<Sha256Hash, Long> depths = new HashMap<Sha256Hash, Long>();
//        tipsManager.recursiveUpdateDepth(re.get(0).getHash(), depths);
//        int i = 0;
//        for (Block block : re) {
//           log.debug(
//                    "  " + i + " block:" + block.getHashAsString() + " depth : " + depths.get(re.get(i).getHash()));
//            Sha256Hash blockhash = block.getHash();
//            Long depth = depths.get(re.get(i).getHash());
//            this.store.updateBlockEvaluationDepth(blockhash, depth.intValue());
//            i++;
//        }
//    }
//
//    @Test
//    public void depth1() throws Exception {
//        List<Block> re = createLinearBlock();
//        Map<Sha256Hash, Long> depths = new HashMap<Sha256Hash, Long>();
//        tipsManager.recursiveUpdateDepth(re.get(0).getHash(), depths);
//        int i = 0;
//        for (Block block : re) {
//           log.debug(
//                    "  " + i + " block:" + block.getHashAsString() + " depth : " + depths.get(re.get(i).getHash()));
//            Sha256Hash blockhash = block.getHash();
//            Long depth = depths.get(re.get(i).getHash());
//            this.store.updateBlockEvaluationDepth(blockhash, depth.intValue());
//            i++;
//        }
//    }
//    @Test
//    public void updateLinearCumulativeweightsTestWorks() throws Exception {
//        createLinearBlock();
//        Map<Sha256Hash, Set<Sha256Hash>> blockCumulativeweights1 = new HashMap<Sha256Hash, Set<Sha256Hash>>();
//        tipsManager.updateHashCumulativeweights(PARAMS.getGenesisBlock().getHash(), blockCumulativeweights1,
//                new HashSet<>());
//
//        Iterator<Map.Entry<Sha256Hash, Set<Sha256Hash>>> iterator = blockCumulativeweights1.entrySet().iterator();
//        while (iterator.hasNext()) {
//            Map.Entry<Sha256Hash, Set<Sha256Hash>> pair = (Map.Entry<Sha256Hash, Set<Sha256Hash>>) iterator.next();
//           log.debug(
//                    "hash : " + pair.getKey() + " \n  size " + pair.getValue().size() + "-> " + pair.getValue());
//            Sha256Hash blockhash = pair.getKey();
//            int cumulativeweight = pair.getValue().size();
//            this.store.updateBlockEvaluationCumulativeweight(blockhash, cumulativeweight);
//        }
//    }
//
//    @Test
//    public void getBlockToApprove() throws Exception {
//        final SecureRandom random = new SecureRandom();
//        for (int i = 1; i < 20; i++) {
//            Sha256Hash b0Sha256Hash = tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27,
//                    random);
//            Sha256Hash b1Sha256Hash = tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27,
//                    random);
//           log.debug("b0Sha256Hash : " + b0Sha256Hash.toString());
//           log.debug("b1Sha256Hash : " + b1Sha256Hash.toString());
//        }
//    }
//
//    @Test
//    public void getBlockToApproveTest2() throws Exception {
//        createBlock();
//        ECKey outKey = new ECKey();
//        int height = 1;
//
//        for (int i = 1; i < 20; i++) {
//            Block r1 = blockService.getBlock(getNextBlockToApprove());
//            Block r2 = blockService.getBlock(getNextBlockToApprove());
//            Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(r2, Block.BLOCK_VERSION_GENESIS,
//                    outKey.getPubKey(), height++, r1.getHash());
//            blockgraph.add(rollingBlock);
//           log.debug("create block  : " + i + " " + rollingBlock);
//        }
//
//    }
//
//    public Sha256Hash getNextBlockToApprove() throws Exception {
//        final SecureRandom random = new SecureRandom();
//        return tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null, 27, 27, random);
//        // Sha256Hash b1Sha256Hash =
//        // tipsManager.blockToApprove(PARAMS.getGenesisBlock().getHash(), null,
//        // 27, 27, random);
//
//    }

}