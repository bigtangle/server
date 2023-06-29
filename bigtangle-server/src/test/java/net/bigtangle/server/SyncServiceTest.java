/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import net.bigtangle.core.Block;
import net.bigtangle.core.TXReward;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.MissingNumberCheckService;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SyncServiceTest extends RewardService2Test {

	@Autowired
	ServerConfiguration serverConfiguration;

	public void createNonChain(Block rewardBlock1, List<Block> blocksAddedAll) throws Exception {
		for (int j = 1; j < 2; j++) {
			payMoneyToWallet1(j, blocksAddedAll);
			makeRewardBlock(blocksAddedAll);

			sell(blocksAddedAll);
			buy(blocksAddedAll);
		}
	}

	//@Test
	public void testSyncCheckChain() throws Exception {
		List<Block> a1 = new ArrayList<Block>();
		testToken(a1);
		makeRewardBlock(a1);
		Block r1 = networkParameters.getGenesisBlock();
		for (int i = 0; i < 3; i++) {
			createNonChain(r1, a1);
		}
		serverConfiguration.setRequester(contextRoot);
		syncBlockService.startSingleProcess();
		for (int i = 0; i < 130; i++) {
			createReward(r1, a1);
		}
		List<TXReward> allConfirmedReward = store.getAllConfirmedReward();
		MissingNumberCheckService missingNumberCheckService = new MissingNumberCheckService();
		missingNumberCheckService.check(allConfirmedReward);
	}

}