/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Sha256Hash;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Ignore
public class PerformanceTest extends AbstractIntegrationTest { 
    
    @Test
    public void testReorgMiningRewardLong() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        long blocksPerChain = 2000;
        for (int i = 0; i < blocksPerChain; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < blocksPerChain; i++) {
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
        for (int i = 1; i < blocksPerChain; i++) {
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

    
}