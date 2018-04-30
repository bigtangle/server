/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.tomcat.jni.Time;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockForTest;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.server.service.TipsService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TipsServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(TipsServiceTest.class);
    
	@Autowired
	private MilestoneService milestoneService;
    
    @Test
    public void testBlockEvaluationDb() throws Exception {
        List<Block> blocks = this.createBlock();
        
        milestoneService.update();
        
        Block block1 = blocks.get(0);
        BlockEvaluation blockEvaluation = this.store.getBlockEvaluation(block1.getHash());
        assertEquals(block1.getHash(), blockEvaluation.getBlockhash());
        assertEquals(6, blockEvaluation.getCumulativeWeight());
        assertEquals(5, blockEvaluation.getDepth());
        assertEquals(1, blockEvaluation.getHeight());
        assertEquals(false, blockEvaluation.isMilestone());
        assertEquals(true, blockEvaluation.isSolid());
        assertEquals(0, blockEvaluation.getMilestoneDepth());
            
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
 
    public List<Block> createLinearBlock() throws Exception {
        List<Block> blocks = new ArrayList<Block>();
        Block rollingBlock1 = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock1);
        blocks.add(rollingBlock1);
       //log.debug("create block, hash : " + rollingBlock1.getHashAsString());

        Block rollingBlock = rollingBlock1;
        for (int i = 1; i < 5; i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
           log.debug("create block, hash : " + rollingBlock.getHashAsString());
            blocks.add(rollingBlock);
        }
        milestoneService.update();
        return blocks;
    }

    public List<Block> createBlock() throws Exception {

        Block b0 = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
        Block b1 = BlockForTest.createNextBlockWithCoinbase(b0, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                height++, networkParameters.getGenesisBlock().getHash());
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
        //milestoneService.update();
        return blocks;
    }

    @Test
    public void getBlockToApprove() throws Exception {
        for (int i = 1; i < 20; i++) {
        	Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsManager.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
           log.debug("b0Sha256Hash : " + r1.toString());
           log.debug("b1Sha256Hash : " + r2.toString());
        }
    }

    @Test
    public void getBlockToApproveTest2() throws Exception {
        createBlock();
        ECKey outKey = new ECKey();
        int height = 1;

        for (int i = 1; i < 20; i++) {
        	Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsManager.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(r2, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, r1.getHash());
            blockgraph.add(rollingBlock);
           log.debug("create block  : " + i + " " + rollingBlock);
        }

    }
}