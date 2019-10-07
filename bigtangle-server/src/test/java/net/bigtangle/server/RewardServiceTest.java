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
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RewardServiceTest extends AbstractIntegrationTest {

    @Test
    public void testReorgMiningReward() throws Exception {
        store.resetStore();
        List<Block> blocksAddedAll = new ArrayList<>();
        List<Block> blocksAdded1 = new ArrayList<>();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);
        blocksAddedAll.add(rollingBlock);
        blocksAdded1.add(rollingBlock);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < 1 + 1 + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
            blocksAddedAll.add(rollingBlock1);
            blocksAdded1.add(rollingBlock1);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < 1 + 1 + 1; i++) {
            rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
            blockGraph.add(rollingBlock2, true);
            blocksAddedAll.add(rollingBlock2);
            blocksAdded1.add(rollingBlock2);
        }

        Block fusingBlock = rollingBlock1.createNextBlock(rollingBlock2);
        blockGraph.add(fusingBlock, true);
        blocksAddedAll.add(fusingBlock);
        blocksAdded1.add(fusingBlock);

        // Generate mining reward block
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        blocksAddedAll.add(rewardBlock1);
        mcmcService.update();

        // Mining reward block should go through
        mcmcService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        blocksAddedAll.add(rewardBlock2);

        store.resetStore();
        for (Block b : blocksAdded1)
            blockGraph.add(b, true);

        Block rewardBlock3 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        blocksAddedAll.add(rewardBlock3);
        mcmcService.update();

        // No change
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        mcmcService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // Third mining reward block should now instead go through since longer
        rollingBlock = rewardBlock3;
        for (int i = 1; i < 1 + 1 + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
            blocksAddedAll.add(rollingBlock);
        }

        Block rewardBlock4 = rewardService.createAndAddMiningRewardBlock(rewardBlock3.getHash(), rollingBlock.getHash(),
                rollingBlock.getHash());
        blocksAddedAll.add(rewardBlock4);
        mcmcService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());

        store.resetStore();
        for (Block b : blocksAddedAll)
            blockGraph.add(b, true);

        // Check that not both mining blocks get approved
        for (int i = 1; i < 10; i++) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            Block b = r2.createNextBlock(r1);
            blockGraph.add(b, true);
            blocksAddedAll.add(b);
        }
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());

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
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());

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
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        mcmcService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
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