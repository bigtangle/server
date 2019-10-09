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
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RewardServiceTest extends AbstractIntegrationTest implements addBlocks {

    public Block testReorgMiningReward(List<Block> blocksAddedAll) throws Exception {
        // test reward chain, longest chanin win
        // mcmc can not change the reward chain
        store.resetStore();

        Block rollingBlock1 = addBlocks(5, networkParameters.getGenesisBlock(), blocksAddedAll);
        // start on node 1
        // Generate mining reward block
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        blocksAddedAll.add(rewardBlock1);

        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).getMilestone() > 0);

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        // blocksAddedAll.add(rewardBlock2);
        // second is false , as first win
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).getMilestone() > 0);
        return rewardBlock1;
    }

    public Block testReorgMiningReward2(List<Block> blocksAddedAll) throws Exception {
        // reset to start on node 2
        store.resetStore();

        Block rollingBlock1 = addBlocks(5, networkParameters.getGenesisBlock(), blocksAddedAll);

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        blocksAddedAll.add(rewardBlock2);
        // add more reward to reward2
        // rewardBlock3 takes only referenced blocks not in reward2
        Block b3 = addBlocks(10, rewardBlock2, blocksAddedAll);
        Block rewardBlock3 = rewardService.createAndAddMiningRewardBlock(rewardBlock2.getHash(), b3.getHash(),
                b3.getHash());
        blocksAddedAll.add(rewardBlock3);
        return rewardBlock3;
    }

    @Test
    public void testReorgMiningReward3() throws Exception {
        List<Block> blocksAddedAll = new ArrayList<Block>();
        List<Block> a1 = new ArrayList<Block>();
        List<Block> a2 = new ArrayList<Block>();

        Block rewardBlock1 = testReorgMiningReward(a1);
        Block rewardBlock3 = testReorgMiningReward2(a2);

        store.resetStore();
        for (Block b : a1)
            blockGraph.add(b, true);

        for (Block b : a2)
            blockGraph.add(b, true);
        for (Block b : a2)
            blockGraph.add(b, true);
        for (Block b : a2)
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        store.resetStore();

        // Check add in reverse order
        Collections.reverse(blocksAddedAll);

        store.resetStore();
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // Check add in random order
        Collections.shuffle(blocksAddedAll);

        store.resetStore();
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

    }

    private Block addBlocks(int num, Block startBlock, List<Block> blocksAddedAll) throws BlockStoreException {
        // add more blocks follow this startBlock
        Block rollingBlock1 = startBlock;
        for (int i = 0; i < num; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
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