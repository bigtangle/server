/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.math.LongMath;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Tokensums;
import net.bigtangle.core.TokensumsMap;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.checkpoint.CheckpointService;
import net.bigtangle.server.service.MissingNumberCheckService;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

/*
 * test payment, buy and sell and create the roll back of reward  by switch to longest chain
 */
public class RewardService2Test extends AbstractIntegrationTest {

	@Autowired
	CheckpointService checkpointService;

	//test payment, buy and sell
	public Block createReward(Block rewardBlock1, List<Block> blocksAddedAll) throws Exception {
		for (int j = 1; j < 2; j++) {
			payMoneyToWallet1(j, blocksAddedAll);
			makeRewardBlock(blocksAddedAll);

			sell(blocksAddedAll);
			buy(blocksAddedAll);
		}

		// Generate mining reward block
		Block next = makeRewardBlock(blocksAddedAll);
		blocksAddedAll.add(next);

		return next;
	}

	@Test
	// the switch to longest chain
	public void testReorgMiningReward() throws Exception {
		List<Block> a1 = new ArrayList<Block>();
		List<Block> a2 = new ArrayList<Block>();
		// first chains
		testToken(a1);
		Block r1 = networkParameters.getGenesisBlock();
		for (int i = 0; i < 3; i++) {
			r1 = createReward(r1, a1);
		}
		log.debug(r1.toString());
		checkSum();
		resetStore();
		testToken(a2);
		// second chain
		Block r2 = networkParameters.getGenesisBlock();
		for (int i = 0; i < 10; i++) {
			r2 = createReward(r2, a2);
		}
		checkSum();
		log.debug(r2.toString());
		assertTrue(
				r2.getRewardInfo().getChainlength() == cacheBlockService.getMaxConfirmedReward(store).getChainLength());

		Sha256Hash hash = checkpointService.checkToken(store).hash();
		// replay
		resetStore();

		// replay first chain
		for (Block b : a1) {
			if (b != null)
				blockGraph.add(b, true, true, store);
		}
		// check
		assertTrue(
				r1.getRewardInfo().getChainlength() == cacheBlockService.getMaxConfirmedReward(store).getChainLength());
		// replay second chain
		for (Block b : a2) {
			if (b != null)
				blockGraph.add(b, true, true, store);

		}
		assertTrue(
				r2.getRewardInfo().getChainlength() == cacheBlockService.getMaxConfirmedReward(store).getChainLength());

		Sha256Hash hash1 = checkSum();
		assertTrue(hash.equals(checkpointService.checkToken(store).hash()));
		// replay second and then replay first
		resetStore();
		for (Block b : a2) {
			if (b != null)
				blockGraph.add(b, true, true, store);

		}
		for (Block b : a1) {
			if (b != null)
				blockGraph.add(b, true, true, store);
		}
		assertTrue(r2.getRewardInfo().getChainlength() == store.getMaxConfirmedReward().getChainLength());
		assertTrue(hash.equals(checkpointService.checkToken(store).hash()));
		Sha256Hash hash2 = checkSum();
		assertTrue(hash1.equals(hash2));
	}

	private Sha256Hash checkSum() throws JsonProcessingException, Exception {
		TokensumsMap map = checkpointService.checkToken(store);
		Map<String, Tokensums> r11 = map.getTokensumsMap();
		for (Entry<String, Tokensums> a : r11.entrySet()) {
			assertTrue(a.getValue().check(), " " + a.toString());
		}
		return map.hash();
	}

	public void testToken(List<Block> blocksAddedAll) throws Exception {

		testCreateToken(wallet.walletKeys().get(0), "test", blocksAddedAll);
		makeRewardBlock(blocksAddedAll);

	}

	public void sell(List<Block> blocksAddedAll) throws Exception {

		List<String> keyStrHex000 = new ArrayList<String>();

		for (ECKey ecKey : wallet.walletKeys()) {
			keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
		}

		byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
				Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

		GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
		List<UTXO> utxos = getBalancesResponse.getOutputs();
		Collections.shuffle(utxos);
		long q = 8;
		for (UTXO utxo : utxos) {
			if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(utxo.getTokenId())) {
				wallet.setServerURL(contextRoot);
				try {
					Block sellOrder = wallet.sellOrder(null, utxo.getTokenId(), 10000000, utxo.getValue().getValue().longValue(),
							null, null, NetworkParameters.BIGTANGLE_TOKENID_STRING, true);
					blocksAddedAll.add(sellOrder);
					makeOrderExecutionAndReward(blocksAddedAll);
				} catch (InsufficientMoneyException e) {
					// ignore: handle exception
				}
			}
		}
	}

	public void payMoneyToWallet1(int j, List<Block> blocksAddedAll) throws Exception {

		HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();

		for (int i = 0; i < 10; i++) {
			giveMoneyResult.put(new ECKey().toAddress(networkParameters).toString(),
					BigInteger.valueOf(3333000000l / LongMath.pow(2, j)));
		}

		Block b = wallet.payMoneyToECKeyList(null, giveMoneyResult, "payMoneyToWallet1");
		blocksAddedAll.add(b);
		makeRewardBlock(blocksAddedAll);
	}

	public void buy(List<Block> blocksAddedAll) throws Exception {

		HashMap<String, Object> requestParam = new HashMap<String, Object>();
		byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
				Json.jsonmapper().writeValueAsString(requestParam).getBytes());

		OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);

		for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
			try {
				buy(orderRecord, blocksAddedAll);
			} catch (InsufficientMoneyException e) {
				Thread.sleep(4000);
			} catch (Exception e) {
				log.debug("", e);
			}
		}
	}

	public void buy(OrderRecord orderRecord, List<Block> blocksAddedAll) throws Exception {

		if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(orderRecord.getOfferTokenid())) {
			// sell order and make buy
			long price = orderRecord.getTargetValue() / orderRecord.getOfferValue();

			blocksAddedAll.add(wallet.buyOrder(null, orderRecord.getOfferTokenid(), price, orderRecord.getOfferValue(),
					null, null, NetworkParameters.BIGTANGLE_TOKENID_STRING, false));
			makeOrderExecutionAndReward(blocksAddedAll);

		}

	}

	public void createNonChain(Block rewardBlock1, List<Block> blocksAddedAll) throws Exception {
		for (int j = 1; j < 2; j++) {
			payMoneyToWallet1(j, blocksAddedAll);
			makeRewardBlock(blocksAddedAll);

			sell(blocksAddedAll);
			buy(blocksAddedAll);
		}
	}

	// @Test
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