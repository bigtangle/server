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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.server.AbstractIntegrationTest;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RangeAttackTest extends AbstractIntegrationTest { 

    

    @Before
    public void setUp() throws Exception {
        Utils.unsetMockClock();
        
        this.walletKeys();
        this.initWalletKeysMapper();
      
    }
    
    
    // Test limit of blocks in reward chain and build very long chain on local server and publish to network
    @Test
    public void testMiningRewardTooLarge() throws Exception {

        List<Block> blocksAddedAll = new ArrayList<Block>();
        Block rollingBlock1 = addFixedBlocks(NetworkParameters.TARGET_MAX_BLOCKS_IN_REWARD + 10,
                networkParameters.getGenesisBlock(), blocksAddedAll);

        // Generate more mining reward blocks
        final Pair<Sha256Hash, Sha256Hash> validatedRewardBlockPair = tipsService.getValidatedRewardBlockPair(networkParameters.getGenesisBlock().getHash());
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                validatedRewardBlockPair.getLeft(), validatedRewardBlockPair.getRight());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).getMilestone() == 1);
        assertTrue(blockService.getBlockEvaluation(rollingBlock1.getHash()).getMilestone() == -1);
    }

    // Test cutoff to limit  
    // TODO @Test
    public void testMiningRewardCutoff() throws Exception {

        List<Block> blocksAddedAll = new ArrayList<Block>();
        Block rollingBlock1 = addFixedBlocks(NetworkParameters.TARGET_MAX_BLOCKS_IN_REWARD + 10,
                networkParameters.getGenesisBlock(), blocksAddedAll);

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).getMilestone() == 1);
        assertTrue(blockService.getBlockEvaluation(rollingBlock1.getHash()).getMilestone() == -1);
    }
    
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
        Block rewardBlock1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        mcmcService.update();
        confirmationService.update();
        // Mining reward block should go through
        mcmcService.update();
        confirmationService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        Block rewardBlock3 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        mcmcService.update();
        confirmationService.update();

        // No change
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        mcmcService.update();
        confirmationService.update();
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
        rewardService.createReward(rewardBlock3.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        mcmcService.update();
        confirmationService.update();
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
        confirmationService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
    }
    @Test
    @Ignore
    // must fix for testnet and mainnet
    public void testGenesisBlockHash() throws Exception {
        assertTrue(networkParameters.getGenesisBlock().getHash().toString()
                .equals("f3f9fbb12f3a24e82f04ed3f8afe1dac7136830cd953bd96b25b1371cd11215c"));

    }
    
}