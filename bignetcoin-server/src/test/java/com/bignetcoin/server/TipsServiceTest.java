/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.junit.Assert.assertEquals;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tomcat.jni.Time;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockForTest;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.TipsService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TipsServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(TipsServiceTest.class);
    
    @Test
    public void testBlockEvaluationDb() throws Exception {
        List<Block> blocks = this.createBlock();
        for (Block block : blocks) {
            BlockEvaluation blockEvaluation1 = BlockEvaluation.buildInitial(block.getHash());
            BlockEvaluation blockEvaluation2 = this.store.getBlockEvaluation(block.getHash());
            assertEquals(blockEvaluation1.getBlockhash(), blockEvaluation2.getBlockhash());
            assertEquals(blockEvaluation1.getCumulativeWeight(), blockEvaluation2.getCumulativeWeight());
            assertEquals(blockEvaluation1.getDepth(), blockEvaluation2.getDepth());
            assertEquals(blockEvaluation1.getHeight(), blockEvaluation2.getHeight());
            assertEquals(blockEvaluation1.getRating(), blockEvaluation2.getRating());
            assertEquals(blockEvaluation1.isMilestone(), blockEvaluation2.isMilestone());
            assertEquals(blockEvaluation1.isSolid(), blockEvaluation2.isSolid());
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
 
    public List<Block> createLinearBlock() throws Exception {
        List<Block> blocks = new ArrayList<Block>();
        Block rollingBlock1 = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock1);
        blocks.add(rollingBlock1);
       //log.debug("create block, hash : " + rollingBlock1.getHashAsString());

        Block rollingBlock = rollingBlock1;
        for (int i = 1; i < 5; i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
           log.debug("create block, hash : " + rollingBlock.getHashAsString());
            blocks.add(rollingBlock);
        }
        return blocks;
    }

    public List<Block> createBlock() throws Exception {

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
    public void getBlockToApprove() throws Exception {
        final SecureRandom random = new SecureRandom();
        for (int i = 1; i < 20; i++) {
            Sha256Hash b0Sha256Hash = tipsManager.blockToApprove(27, random);
            Sha256Hash b1Sha256Hash = tipsManager.blockToApprove(27, random);
           log.debug("b0Sha256Hash : " + b0Sha256Hash.toString());
           log.debug("b1Sha256Hash : " + b1Sha256Hash.toString());
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
            Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(r2, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, r1.getHash());
            blockgraph.add(rollingBlock);
           log.debug("create block  : " + i + " " + rollingBlock);
        }

    }

    public Sha256Hash getNextBlockToApprove() throws Exception {
        final SecureRandom random = new SecureRandom();
        return tipsManager.blockToApprove(27, random);
    }

}