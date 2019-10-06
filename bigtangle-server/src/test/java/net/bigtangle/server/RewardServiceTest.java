/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RewardServiceTest extends AbstractIntegrationTest {

 
 

    @Test
    public void testReorgMiningReward() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
            blockGraph.add(rollingBlock2, true);
        }

        Block fusingBlock = rollingBlock1.createNextBlock(rollingBlock2);
        blockGraph.add(fusingBlock, true);

        // Generate mining reward block
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        mcmcService.update();

        // Mining reward block should go through
        mcmcService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        Block rewardBlock3 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        mcmcService.update();

        // No change
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        mcmcService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // Third mining reward block should now instead go through since longer
        rollingBlock = rewardBlock3;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
       // syncBlockService. reCheckUnsolidBlock();
        rewardService.createAndAddMiningRewardBlock(rewardBlock3.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // Check that not both mining blocks get approved
        for (int i = 1; i < 10; i++) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            Block b = r2.createNextBlock(r1);
            blockGraph.add(b, true);
        }
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
    }


 

    @Test
    public void blocksFromChainlength() throws Exception {
        // create some blocks
        //testReorgMiningReward();
        mcmcService.update();
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("start", "0");
        request.put("end", "0");
        String response = OkHttp3Util.post(contextRoot + ReqCmd.blocksFromChainLength.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());

        GetBlockListResponse blockListResponse = Json.jsonmapper().readValue(response, GetBlockListResponse.class);

   
        // log.info("searchBlock resp : " + response);
        assertTrue(blockListResponse.getBlockbytelist().size() > 0);

        for (byte[] data : blockListResponse.getBlockbytelist()) {
            blockService.addConnected(data, false);
        }
    }

    @Test
    public void testReorgMiningReward2() throws Exception {
        store.resetStore();
        
        List<Block> blocksAdded = new ArrayList<>();

        // Generate blocks until passing first reward interval parallel
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);
        blocksAdded.add(rollingBlock);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
            blocksAdded.add(rollingBlock1);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
            blockGraph.add(rollingBlock2, true);
            blocksAdded.add(rollingBlock2);
        }

        // Generate mining reward block on chain 1
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        blocksAdded.add(rewardBlock1);
        mcmcService.update();

        // Mining reward block should go through
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock2.getHash(), rollingBlock1.getHash());
        Block rewardBlock3 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock2.getHash(), rollingBlock2.getHash());
        blocksAdded.add(rewardBlock2);
        blocksAdded.add(rewardBlock3);
        mcmcService.update();

        // No change
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        mcmcService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // Third mining reward block should now instead go through since longer
        Block rollingBlock3 = rewardBlock3;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock3 = rollingBlock3.createNextBlock(rollingBlock3);
            blockGraph.add(rollingBlock3, true);
            blocksAdded.add(rollingBlock3);
        }
        Block rewardBlock4 = rewardService.createAndAddMiningRewardBlock(rewardBlock3.getHash(),
                rollingBlock3.getHash(), rollingBlock3.getHash());
        blocksAdded.add(rewardBlock4);
        mcmcService.update();

        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        
        // The same state should be reached if getting the blocks in a different order
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        // Incomplete out-of-order blocks are added on second add
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        // Now try switching to second
        Block rollingBlock4 = rewardBlock2;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock4 = rollingBlock4.createNextBlock(rollingBlock4);
            blockGraph.add(rollingBlock4, true);
            blocksAdded.add(rollingBlock4);
        }
        Block rewardBlock5 = rewardService.createAndAddMiningRewardBlock(rewardBlock2.getHash(),
                rollingBlock4.getHash(), rollingBlock4.getHash());
        blocksAdded.add(rewardBlock5);
        mcmcService.update();

        Block rollingBlock5 = rewardBlock5;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock5 = rollingBlock5.createNextBlock(rollingBlock5);
            blockGraph.add(rollingBlock5, true);
            blocksAdded.add(rollingBlock5);
        }
        Block rewardBlock6 = rewardService.createAndAddMiningRewardBlock(rewardBlock5.getHash(),
                rollingBlock5.getHash(), rollingBlock5.getHash());
        blocksAdded.add(rewardBlock6);
        mcmcService.update();

        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        // The same state should be reached if getting the blocks in a different order
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        // Now try switching to third again
        Block rollingBlock6 = rewardBlock4;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock6 = rollingBlock6.createNextBlock(rollingBlock6);
            blockGraph.add(rollingBlock6, true);
            blocksAdded.add(rollingBlock6);
        }
        Block rewardBlock7 = rewardService.createAndAddMiningRewardBlock(rewardBlock4.getHash(),
                rollingBlock6.getHash(), rollingBlock6.getHash());
        blocksAdded.add(rewardBlock7);
        mcmcService.update();

        Block rollingBlock7 = rewardBlock7;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock7 = rollingBlock7.createNextBlock(rollingBlock7);
            blockGraph.add(rollingBlock7, true);
            blocksAdded.add(rollingBlock7);
        }
        Block rewardBlock8 = rewardService.createAndAddMiningRewardBlock(rewardBlock7.getHash(),
                rollingBlock7.getHash(), rollingBlock7.getHash());
        blocksAdded.add(rewardBlock8);
        mcmcService.update();

        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        // The same state should be reached if getting the blocks in a different order
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        Collections.shuffle(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
    }

    @Test
    public void testReorgMiningReward3() throws Exception {
        store.resetStore();
        
        List<Block> blocksAdded = new ArrayList<>();

        // Generate blocks until passing first reward interval parallel
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);
        blocksAdded.add(rollingBlock);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
            blocksAdded.add(rollingBlock1);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
            blockGraph.add(rollingBlock2, true);
            blocksAdded.add(rollingBlock2);
        }

        // Generate mining reward block on chain 1
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        blocksAdded.add(rewardBlock1);
        mcmcService.update();

        // Mining reward block should go through
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock2.getHash(), rollingBlock1.getHash());
        Block rewardBlock3 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock2.getHash(), rollingBlock2.getHash());
        blocksAdded.add(rewardBlock2);
        blocksAdded.add(rewardBlock3);
        mcmcService.update();

        // No change
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        mcmcService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        
        // Now try switching to second
        Block rollingBlock4 = rewardBlock2;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock4 = rollingBlock4.createNextBlock(rollingBlock4);
            blockGraph.add(rollingBlock4, true);
            blocksAdded.add(rollingBlock4);
        }
        Block rewardBlock5 = rewardService.createAndAddMiningRewardBlock(rewardBlock2.getHash(),
                rollingBlock4.getHash(), rollingBlock4.getHash());
        blocksAdded.add(rewardBlock5);
        mcmcService.update();

        Block rollingBlock5 = rewardBlock5;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock5 = rollingBlock5.createNextBlock(rollingBlock5);
            blockGraph.add(rollingBlock5, true);
            blocksAdded.add(rollingBlock5);
        }
        Block rewardBlock6 = rewardService.createAndAddMiningRewardBlock(rewardBlock5.getHash(),
                rollingBlock5.getHash(), rollingBlock5.getHash());
        blocksAdded.add(rewardBlock6);
        mcmcService.update();

        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        // The same state should be reached if getting the blocks in a different order
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());

        // Now try switching to third 
        Block rollingBlock3 = rewardBlock3;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock3 = rollingBlock3.createNextBlock(rollingBlock3);
            blockGraph.add(rollingBlock3, true);
            blocksAdded.add(rollingBlock3);
        }
        Block rewardBlock4 = rewardService.createAndAddMiningRewardBlock(rewardBlock3.getHash(),
                rollingBlock3.getHash(), rollingBlock3.getHash());
        blocksAdded.add(rewardBlock4);
        mcmcService.update();

        Block rollingBlock6 = rewardBlock4;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock6 = rollingBlock6.createNextBlock(rollingBlock6);
            blockGraph.add(rollingBlock6, true);
            blocksAdded.add(rollingBlock6);
        }
        Block rewardBlock7 = rewardService.createAndAddMiningRewardBlock(rewardBlock4.getHash(),
                rollingBlock6.getHash(), rollingBlock6.getHash());
        blocksAdded.add(rewardBlock7);
        mcmcService.update();

        Block rollingBlock7 = rewardBlock7;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock7 = rollingBlock7.createNextBlock(rollingBlock7);
            blockGraph.add(rollingBlock7, true);
            blocksAdded.add(rollingBlock7);
        }
        Block rewardBlock8 = rewardService.createAndAddMiningRewardBlock(rewardBlock7.getHash(),
                rollingBlock7.getHash(), rollingBlock7.getHash());
        blocksAdded.add(rewardBlock8);
        mcmcService.update();

        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        // The same state should be reached if getting the blocks in a different order
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        Collections.shuffle(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        syncBlockService.updateSolidity();
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
    }

}