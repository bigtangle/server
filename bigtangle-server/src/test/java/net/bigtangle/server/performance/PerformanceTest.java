/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.bigtangle.core.Block;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.test.AbstractIntegrationTest;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformanceTest extends AbstractIntegrationTest {

	// Test limit of blocks in reward chain
//    @Test
	public void testMiningRewardTooLarge() throws Exception {

		List<Block> blocksAddedAll = new ArrayList<Block>();
		Block rollingBlock1 = addFixedBlocks(NetworkParameters.TARGET_MAX_BLOCKS_IN_REWARD + 10,
				networkParameters.getGenesisBlock(), blocksAddedAll);

		// Generate more mining reward blocks
		final Pair<BlockWrap, BlockWrap> validatedRewardBlockPair = tipsService
				.getValidatedRewardBlockPair(networkParameters.getGenesisBlock().getHash(), store);
		Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
				validatedRewardBlockPair.getLeft(), validatedRewardBlockPair.getRight(), store);
		assertTrue(getBlockEvaluation(rewardBlock2.getHash(), store).isConfirmed());
		assertTrue(getBlockEvaluation(rewardBlock2.getHash(), store).getMilestone() == 1);
		assertTrue(getBlockEvaluation(rollingBlock1.getHash(), store).getMilestone() == -1);
	}

	@Test
	public void testReorgMiningRewardLong() throws Exception {
		 resetStore();

		// Generate blocks until passing first reward interval
		Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
		blockGraph.add(rollingBlock, true, store);

		Block rollingBlock1 = rollingBlock;
		long blocksPerChain = 2000;
		for (int i = 0; i < blocksPerChain; i++) {
			rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
			blockGraph.add(rollingBlock1, true, store);
		}

		Block rollingBlock2 = rollingBlock;
		for (int i = 0; i < blocksPerChain; i++) {
			rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
			blockGraph.add(rollingBlock2, true, store);
		}

		Block fusingBlock = rollingBlock1.createNextBlock(rollingBlock2);
		blockGraph.add(fusingBlock, true, store);

		// Generate mining reward block
		Block rewardBlock1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(rollingBlock1), defaultBlockWrap(rollingBlock1), store);
		mcmcServiceUpdate();

		// Mining reward block should go through
		mcmcServiceUpdate();
		assertTrue(getBlockEvaluation(rewardBlock1.getHash(), store).isConfirmed());

		// Generate more mining reward blocks
		Block rewardBlock2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(fusingBlock), defaultBlockWrap(rollingBlock1), store);
		Block rewardBlock3 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
				defaultBlockWrap(fusingBlock), defaultBlockWrap(rollingBlock1), store);
		mcmcServiceUpdate();

		// No change
		assertTrue(getBlockEvaluation(rewardBlock1.getHash(), store).isConfirmed());
		assertFalse(getBlockEvaluation(rewardBlock2.getHash(), store).isConfirmed());
		assertFalse(getBlockEvaluation(rewardBlock3.getHash(), store).isConfirmed());
		mcmcServiceUpdate();

		assertTrue(getBlockEvaluation(rewardBlock1.getHash(), store).isConfirmed());
		assertFalse(getBlockEvaluation(rewardBlock2.getHash(), store).isConfirmed());
		assertFalse(getBlockEvaluation(rewardBlock3.getHash(), store).isConfirmed());

		// Third mining reward block should now instead go through since longer
		rollingBlock = rewardBlock3;
		for (int i = 1; i < blocksPerChain; i++) {
			rollingBlock = rollingBlock.createNextBlock(rollingBlock);
			blockGraph.add(rollingBlock, true, store);
		}
		// syncBlockService. reCheckUnsolidBlock();
		rewardService.createReward(rewardBlock3.getHash(), defaultBlockWrap(rollingBlock),
				defaultBlockWrap(rollingBlock), store);
		mcmcServiceUpdate();

		assertFalse(getBlockEvaluation(rewardBlock1.getHash(), store).isConfirmed());
		assertFalse(getBlockEvaluation(rewardBlock2.getHash(), store).isConfirmed());
		assertTrue(getBlockEvaluation(rewardBlock3.getHash(), store).isConfirmed());

		// Check that not both mining blocks get approved
		for (int i = 1; i < 10; i++) {
			Pair<BlockWrap, BlockWrap> tipsToApprove = tipsService.getValidatedBlockPair(store);
			Block r1 = tipsToApprove.getLeft().getBlock();
			Block r2 = tipsToApprove.getRight().getBlock();
			Block b = r2.createNextBlock(r1);
			blockGraph.add(b, true, store);
		}
		mcmcServiceUpdate();

		assertFalse(getBlockEvaluation(rewardBlock1.getHash(), store).isConfirmed());
		assertFalse(getBlockEvaluation(rewardBlock2.getHash(), store).isConfirmed());
		assertTrue(getBlockEvaluation(rewardBlock3.getHash(), store).isConfirmed());
	}

	@Test
	@Disabled
	// must fix for testnet and mainnet
	public void testGenesisBlockHash() throws Exception {
		assertTrue(networkParameters.getGenesisBlock().getHash().toString()
				.equals("f3f9fbb12f3a24e82f04ed3f8afe1dac7136830cd953bd96b25b1371cd11215c"));

	}

}