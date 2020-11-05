/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.performance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.server.AbstractIntegrationTest;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformanceTest extends AbstractIntegrationTest { 

    // Test limit of blocks in reward chain 
//    @Test
    public void testMiningRewardTooLarge() throws Exception {

        List<Block> blocksAddedAll = new ArrayList<Block>();
        Block rollingBlock1 = addFixedBlocks(NetworkParameters.TARGET_MAX_BLOCKS_IN_REWARD + 10,
                networkParameters.getGenesisBlock(), blocksAddedAll);

        // Generate more mining reward blocks
        final Pair<Sha256Hash, Sha256Hash> validatedRewardBlockPair = tipsService.getValidatedRewardBlockPair(networkParameters.getGenesisBlock().getHash(),store);
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                validatedRewardBlockPair.getLeft(), validatedRewardBlockPair.getRight(),store);
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash(),store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash(),store).getMilestone() == 1);
        assertTrue(blockService.getBlockEvaluation(rollingBlock1.getHash(),store).getMilestone() == -1);
    }

    // Test cutoff to limit  
    // TODO @Test
    public void testMiningRewardCutoff() throws Exception {

        List<Block> blocksAddedAll = new ArrayList<Block>();
        Block rollingBlock1 = addFixedBlocks(NetworkParameters.TARGET_MAX_BLOCKS_IN_REWARD + 10,
                networkParameters.getGenesisBlock(), blocksAddedAll);

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash(),store);
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash(),store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash(),store).getMilestone() == 1);
        assertTrue(blockService.getBlockEvaluation(rollingBlock1.getHash(),store).getMilestone() == -1);
    }
    
    @Test
    public void testReorgMiningRewardLong() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true,store);

        Block rollingBlock1 = rollingBlock;
        long blocksPerChain = 2000;
        for (int i = 0; i < blocksPerChain; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true,store);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < blocksPerChain; i++) {
            rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
            blockGraph.add(rollingBlock2, true,store);
        }

        Block fusingBlock = rollingBlock1.createNextBlock(rollingBlock2);
        blockGraph.add(fusingBlock, true,store);

        // Generate mining reward block
        Block rewardBlock1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash(),store);
        mcmcServiceUpdate();
        
        // Mining reward block should go through
        mcmcServiceUpdate();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash(),store).isConfirmed());

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash(),store);
        Block rewardBlock3 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash(),store);
        mcmcServiceUpdate();
        
        // No change
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash(),store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash(),store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash(),store).isConfirmed());
        mcmcServiceUpdate();
        
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash(),store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash(),store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash(),store).isConfirmed());

        // Third mining reward block should now instead go through since longer
        rollingBlock = rewardBlock3;
        for (int i = 1; i < blocksPerChain; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true,store);
        }
       // syncBlockService. reCheckUnsolidBlock();
        rewardService.createReward(rewardBlock3.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash(),store);
        mcmcServiceUpdate();
        
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash(),store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash(),store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash(),store).isConfirmed());

        // Check that not both mining blocks get approved
        for (int i = 1; i < 10; i++) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair( store);
            Block r1 = blockService.getBlock(tipsToApprove.getLeft(),store);
            Block r2 = blockService.getBlock(tipsToApprove.getRight(),store);
            Block b = r2.createNextBlock(r1);
            blockGraph.add(b, true,store);
        }
        mcmcServiceUpdate();
        
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash(),store).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash(),store).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash(),store).isConfirmed());
    }
    @Test
    @Ignore
    // must fix for testnet and mainnet
    public void testGenesisBlockHash() throws Exception {
        assertTrue(networkParameters.getGenesisBlock().getHash().toString()
                .equals("f3f9fbb12f3a24e82f04ed3f8afe1dac7136830cd953bd96b25b1371cd11215c"));

    }
    
}