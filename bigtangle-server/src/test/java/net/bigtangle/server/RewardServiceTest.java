/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RewardServiceTest extends AbstractIntegrationTest  {

    public Block createReward(List<Block> blocksAddedAll) throws Exception {

        Block rollingBlock1 = addBlocks(5, blocksAddedAll);

        // Generate mining reward block
        Block rewardBlock1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash());
        blocksAddedAll.add(rewardBlock1);

        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).getMilestone() == 1);

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        // blocksAddedAll.add(rewardBlock2);
        // second is false , as first win
     //   assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
     //   assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).getMilestone() > 0);
        return rewardBlock1;
    }

    public Block createReward2(List<Block> blocksAddedAll) throws Exception {
        addBlocks(5, blocksAddedAll);
        // Generate mining reward blocks
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash());
        blocksAddedAll.add(rewardBlock2);
        // add more reward to reward2
        // rewardBlock3 takes only referenced blocks not in reward2
        addBlocks(1, blocksAddedAll);
        Block rewardBlock3 = rewardService.createReward(rewardBlock2.getHash());
        blocksAddedAll.add(rewardBlock3);
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).getMilestone() == 1);
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).getMilestone() == 2);
        return rewardBlock3;
    }

    @Test
    // the switch to longest chain
    public void testReorgMiningReward() throws Exception {
        List<Block> a1 = new ArrayList<Block>();
        List<Block> a2 = new ArrayList<Block>();
        // first chains
        Block rewardBlock1 = createReward(a1);
        store.resetStore();
        // second chain
        Block rewardBlock3 = createReward2(a2);
        store.resetStore();
        // replay first chain
        for (Block b : a1)
            blockGraph.add(b, true);
        // add second chain
        for (Block b : a2)
            blockGraph.add(b, true);

        // assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).getMilestone() == -1);

        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).getMilestone() == 2);
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

    }

    @Test
    // out of order added blocks will have the same results
    public void testReorgMiningRewardShuffle() throws Exception {
        List<Block> blocksAddedAll = new ArrayList<Block>();
        List<Block> a1 = new ArrayList<Block>();
        List<Block> a2 = new ArrayList<Block>();

        Block rewardBlock1 = createReward(a1);
        store.resetStore();
        Block rewardBlock3 = createReward2(a2);
        store.resetStore();
        blocksAddedAll.addAll(a1);
        blocksAddedAll.addAll(a2);
        // Check add in random order
        Collections.shuffle(blocksAddedAll);

        store.resetStore();
        // add many times to get chain out of order
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true); 
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).getMilestone() == -1);

        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).getMilestone() == 2);
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // mcmc can not change the status of chain
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

    }

    // test wrong chain with fixed graph and required blocks
    @Test
    public void testReorgMiningRewardWrong() throws Exception {
        // reset to start on node 2
        store.resetStore();
        List<Block> blocksAddedAll = new ArrayList<Block>();
        Block rewardBlock1 = createReward(blocksAddedAll);

        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).getMilestone() == 1);

       // Block rollingBlock1 = addFixedBlocks(5, networkParameters.getGenesisBlock(), blocksAddedAll);

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createReward(rewardBlock1.getHash(),
                blocksAddedAll.get(0).getHash(),  blocksAddedAll.get(0).getHash());
        blocksAddedAll.add(rewardBlock2);
 
       // assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).getMilestone() == 2
             );
 
    }

    // test cutoff chains, reward should not take blocks behind the cutoff chain
    @Test
    public void testReorgMiningRewardCutoff() throws Exception {

        List<Block> blocksAddedAll = new ArrayList<Block>();
        Block rollingBlock1 = addFixedBlocks(5, networkParameters.getGenesisBlock(), blocksAddedAll);

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        blocksAddedAll.add(rewardBlock2);
        for (int i = 0; i < NetworkParameters.MILESTONE_CUTOFF + 5; i++) {
            rewardBlock2 = rewardService.createReward(rewardBlock2.getHash());
        }

        // create a long block graph
        Block rollingBlock2 = addFixedBlocks(200, networkParameters.getGenesisBlock(), blocksAddedAll);
        // rewardBlock3 takes the long block graph behind cutoff
        try {
            Block rewardBlock3 = rewardService.createReward(rewardBlock2.getHash(),
                    rollingBlock2.getHash(), rollingBlock2.getHash());
            fail();
        } catch (VerificationException e) {
            // TODO: handle exception
        }

        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).getMilestone() >= 0);

    }

    // generate a list of block using mcmc and return the last block
    private Block addBlocks(int num, List<Block> blocksAddedAll)
            throws BlockStoreException, JsonProcessingException, IOException {
        // add more blocks using mcmc
        Block rollingBlock1 = null;
        for (int i = 0; i < num; i++) {
            // rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            HashMap<String, String> requestParam = new HashMap<String, String>();
            byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            rollingBlock1 = networkParameters.getDefaultSerializer().makeBlock(data);
            rollingBlock1.solve();
            blockGraph.add(rollingBlock1, true);
            blocksAddedAll.add(rollingBlock1);
        }
        return rollingBlock1;
    }

    @Test
    public void blocksFromChainlength() throws Exception {
        // create some blocks
        // testReorgMiningReward();
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

}