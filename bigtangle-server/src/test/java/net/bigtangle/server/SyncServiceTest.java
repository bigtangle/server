/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.math.LongMath;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.MissingNumberCheckService;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

@RunWith(SpringRunner.class)
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