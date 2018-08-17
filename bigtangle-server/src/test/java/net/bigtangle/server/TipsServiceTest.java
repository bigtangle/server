/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
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

    @Autowired
    private TipsService tipsService;

    @Autowired
    private BlockService blockService;

    ECKey outKey = new ECKey();
    int height = 1;
 
    public List<Block> createLinearBlock() throws Exception {
        List<Block> blocks = new ArrayList<Block>();
        Block rollingBlock1 = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
        blockgraph.add(rollingBlock1,true);
        blocks.add(rollingBlock1);
       //log.debug("create block, hash : " + rollingBlock1.getHashAsString());

        Block rollingBlock = rollingBlock1;
        for (int i = 1; i < 5; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS,networkParameters.getGenesisBlock());
            blockgraph.add(rollingBlock,true);
           log.debug("create block, hash : " + rollingBlock.getHashAsString());
            blocks.add(rollingBlock);
        }
        milestoneService.update();
        return blocks;
    }

    public List<Block> createBlock() throws Exception {

        Block b0 = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                networkParameters.getGenesisBlock());
        Block b1 = BlockForTest.createNextBlock(b0, Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
        Block b2 = BlockForTest.createNextBlock(b1, Block.BLOCK_VERSION_GENESIS, b0);
        Block b3 = BlockForTest.createNextBlock(b1, Block.BLOCK_VERSION_GENESIS, b2);
        Block b4 = BlockForTest.createNextBlock(b3, Block.BLOCK_VERSION_GENESIS,b2);
        Block b5 = BlockForTest.createNextBlock(b4, Block.BLOCK_VERSION_GENESIS, b1);
        List<Block> blocks = new ArrayList<Block>();
        blocks.add(b0);
        blocks.add(b1);
        blocks.add(b2);
        blocks.add(b3);
        blocks.add(b4);
        blocks.add(b5);
        int i = 0;
        for (Block block : blocks) {
            this.blockgraph.add(block,true);
           log.debug("create  " + i + " block:" + block.getHashAsString());
            i++;

        }
        //milestoneService.update();
        return blocks;
    }

    @Test
    public void getBlockToApprove() throws Exception {
        for (int i = 1; i < 20; i++) {
        	Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
           log.debug("b0Sha256Hash : " + r1.toString());
           log.debug("b1Sha256Hash : " + r2.toString());
        }
    }

    @Test
    public void getBlockToApproveTest2() throws Exception {
        createBlock();
        for (int i = 1; i < 20; i++) {
        	Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            Block rollingBlock = BlockForTest.createNextBlock(r2, Block.BLOCK_VERSION_GENESIS,r1);
            blockgraph.add(rollingBlock,true);
           log.debug("create block  : " + i + " " + rollingBlock);
        }

    }
}