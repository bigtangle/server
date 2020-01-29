package net.bigtangle.server.performance;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.apps.lottery.Lottery;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.params.TestParams;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class LotteryRemoteTest {

	private NetworkParameters networkParameters =TestParams.get();

	// 1GDsvV5Vgwa7VYULyDmW9Unj9v1hWFxBJ5
	public static String USDTokenPub = "02fbef0f3e1344f548abb7d4b6a799e372d2310ff13fe023b3ba0446f2e3f58e04";
	public static String USDTokenPriv = "6fb4cb30d593536e3c54ac17bfaa311cb0e2bdf219c89483aa8d7185f6c5c3b7";

	int usernumber = Math.abs(new Random().nextInt()) % 88;
	BigInteger winnerAmount = new BigInteger(Math.abs(new Random().nextInt()) % 9999 + "");
	Wallet wallet;
	public static String contextRoot = "https://test.bigtangle.de/";
	private ECKey accountKey;

	protected static final Logger log = LoggerFactory.getLogger(LotteryRemoteTest.class);

	@Test
	public void lottery() throws Exception {

		proxy();

		for (int i = 0; i < 1; i++) {
			usernumber = Math.abs(new Random().nextInt()) % 8;
			winnerAmount = new BigInteger(Math.abs(new Random().nextInt()) % 99 + "");

			lotteryDo();
			log.debug("done iteration " + i + "usernumber=" + usernumber + " winnerAmount=" + winnerAmount);
		}
	}

	private void proxy() {
		System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
		System.setProperty("https.proxyPort", "3128");
	}

	public void lotteryDo() throws Exception {
		wallet = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)));
		accountKey = new ECKey();
		wallet.importKey(accountKey);
		wallet.setServerURL(contextRoot);
	 
		// testTokens();
		createUserPay(accountKey);

		Lottery startLottery = startLottery();
		while (!startLottery.isMacthed()) {
			createUserPay(accountKey);
			startLottery = startLottery();
		}
		checkResult(startLottery);
	}

	private Lottery startLottery()
			throws Exception, JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {
		Lottery startLottery = new Lottery();
		startLottery.setTokenid(USDTokenPub);
		startLottery.setCONTEXT_ROOT(contextRoot);
		startLottery.setParams(networkParameters);
		startLottery.setWalletAdmin(wallet);
		startLottery.setWinnerAmount(winnerAmount);
		startLottery.setAccountKey(accountKey);
		startLottery.start();

		return startLottery;
	}

	private void checkResult(Lottery startLottery) throws Exception {
		Coin coin = new Coin(startLottery.sum(), USDTokenPub);

		List<UTXO> users = getBalance(startLottery.getWinner());
		UTXO myutxo = null;
		for (UTXO u : users) {
			if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue().equals(u.getValue().getValue())) {
				myutxo = u;
				break;
			}
		}
		assertTrue(myutxo != null);
		assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
		log.debug(myutxo.toString());
	}

	// get balance for the walletKeys
	protected List<UTXO> getBalance(String address) throws Exception {
		List<UTXO> listUTXO = new ArrayList<UTXO>();
		List<String> keyStrHex000 = new ArrayList<String>();

		keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(networkParameters, address).getHash160()));
		String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
				Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

		GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

		for (UTXO utxo : getBalancesResponse.getOutputs()) {
			listUTXO.add(utxo);
		}

		return listUTXO;
	}

	private void createUserPay(ECKey accountKey) throws Exception {
		List<ECKey> ulist = payKeys();
		for (ECKey key : ulist) {
			buyTicket(key, accountKey);
		}
	}

	/*
	 * pay money to the key and use the key to buy lottery
	 */
	public void buyTicket(ECKey key, ECKey accountKey) throws Exception {
		Wallet w = Wallet.fromKeys(networkParameters, key);
		w.setServerURL(contextRoot);
		try {
			int satoshis = Math.abs(new Random().nextInt()) % 1000;
			w.pay(null, accountKey.toAddress(networkParameters), Coin.valueOf(satoshis, Utils.HEX.decode(USDTokenPub)),
					" buy ticket");
		} catch (InsufficientMoneyException e) {
			// TODO: handle exception
		}
	}

	public List<ECKey> payKeys() throws Exception {
		List<ECKey> userkeys = new ArrayList<ECKey>();
		HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

		for (int i = 0; i < usernumber; i++) {
			ECKey key = new ECKey();
			giveMoneyResult.put(key.toAddress(networkParameters).toString(), winnerAmount.longValue() );
			userkeys.add(key);
		}

		Block b = wallet.payMoneyToECKeyList(null, giveMoneyResult, Utils.HEX.decode(USDTokenPub), "", 3, 20000);
		log.debug("block " + (b == null ? "block is null" : b.toString()));

		return userkeys;
	}

}
