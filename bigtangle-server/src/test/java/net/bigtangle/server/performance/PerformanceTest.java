/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.performance;

import java.io.StringWriter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.math.LongMath;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.ErrorResponse;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.test.ContractTest;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformanceTest extends ContractTest {

	@BeforeEach
	public void setUp() throws Exception {
		contextRoot = "http://localhost:8088/";
		wallet = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(testPriv)), contextRoot);
		store = storeService.getStore();
	}

	@Test
	public void testStartProcess() throws Exception {
		List<Block> a1 = new ArrayList<Block>();

		testToken(a1);
		wallet.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
		testTokens();
		testContractTokens();
		wallet = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(testPriv)), contextRoot);
	}

	@Test
	public void testProcess() throws Exception {
		List<Block> a1 = new ArrayList<Block>();
		for (int i = 0; i < 3000; i++) {
			process(a1);
		}

	}

	public void process(List<Block> blocksAddedAll) throws Exception {

		ExecutorService executor = Executors.newSingleThreadExecutor();
		@SuppressWarnings("rawtypes")
		final Future<String> handler = executor.submit(new Callable() {
			@Override
			public String call() throws Exception {
				payMoneyToWallet1(blocksAddedAll);
				sell(blocksAddedAll);
				buy(blocksAddedAll);
				ulist = createUserkey();
				payUserKeys(ulist);
				payBigUserKeys(ulist);
				payContract();
				return "";
			}
		});
		try {
			handler.get(30, TimeUnit.MINUTES);
		} catch (Exception e) {
			// logger.debug(" process Timeout ");
			handler.cancel(true);
			AbstractResponse resp = ErrorResponse.create(100);
			StringWriter sw = new StringWriter();
			resp.setMessage(sw.toString());
		} finally {
			executor.shutdownNow();
		}

	}

	public void testToken(List<Block> blocksAddedAll) throws Exception {

		testCreateToken(wallet.walletKeys().get(0), "test", blocksAddedAll);
		makeRewardBlock(blocksAddedAll);

	}

	public void payContract() throws Exception {

		for (ECKey key : ulist) {
			Wallet w = Wallet.fromKeys(networkParameters, key, contextRoot);
			w.payContract(null, yuanTokenPub, payContractAmount, null, null, contractKey.getPublicKeyAsHex());
		}

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
		// long q = 8;
		for (UTXO utxo : utxos) {
			if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(utxo.getTokenId())) {
				wallet.setServerURL(contextRoot);
				try {
					Block sellOrder = wallet.sellOrder(null, utxo.getTokenId(), 10000000,
							utxo.getValue().getValue().longValue(), null, null,
							NetworkParameters.BIGTANGLE_TOKENID_STRING, true);
					blocksAddedAll.add(sellOrder);
				//	makeOrderExecutionAndReward(blocksAddedAll);
				} catch (InsufficientMoneyException e) {
					// ignore: handle exception
				}
			}
		}
	}

	public void payMoneyToWallet1(List<Block> blocksAddedAll) throws Exception {

		HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();

		for (int i = 0; i < 10; i++) {
			giveMoneyResult.put(new ECKey().toAddress(networkParameters).toString(),
					BigInteger.valueOf(3333000000l / LongMath.pow(2, 1)));
		}

		Block b = wallet.payMoneyToECKeyList(null, giveMoneyResult, "payMoneyToWallet1");
		blocksAddedAll.add(b);
	//	makeRewardBlock(blocksAddedAll);
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

			Block buyOrder = wallet.buyOrder(null, orderRecord.getOfferTokenid(), price, orderRecord.getOfferValue(),
					null, null, NetworkParameters.BIGTANGLE_TOKENID_STRING, false);
			blocksAddedAll.add(buyOrder);
		//	makeOrderExecutionAndReward(blocksAddedAll);

		}

	}

}