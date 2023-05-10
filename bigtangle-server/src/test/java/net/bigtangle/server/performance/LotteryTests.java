/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.performance;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.apps.lottery.Lottery;
import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.AbstractIntegrationTest;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class LotteryTests extends AbstractIntegrationTest {
	@Autowired
	private NetworkParameters networkParameters;
	public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
	public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";
	int usernumber = Math.abs(new Random().nextInt()) % 88;
	BigInteger winnerAmount = new BigInteger(Math.abs(new Random().nextInt()) % 9999 + "");

	private ECKey accountKey;

	protected static final Logger log = LoggerFactory.getLogger(LotteryTests.class);

	@Test
	public void lottery() throws Exception {
		for (int i = 0; i < 18; i++) {
			usernumber = Math.abs(new Random().nextInt()) % 88;
			winnerAmount = new BigInteger(Math.abs(new Random().nextInt()) % 9999 + "");

			lotteryDo();
			log.debug("done iteration " + i + "usernumber=" + usernumber + " winnerAmount=" + winnerAmount);
		}
	}

	public void lotteryDo() throws Exception {
		walletAppKit.wallet().importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
		accountKey = new ECKey();
		walletAppKit.wallet().importKey(accountKey);

//		testTokens();
		List<Block> addedBlocks = new ArrayList<>();

		// base token
		ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));

		long tokennumber = 100000000;
		makeTestToken(yuan, BigInteger.valueOf(tokennumber), addedBlocks, 2);
		for (ECKey ecKey : walletKeys) {
			System.out.println("+++++++++++++");
			System.out.println(ecKey.toAddress(networkParameters).toString());
			List<UTXO> list = this.getBalance(ecKey.toAddress(networkParameters).toString());
			if (list == null) {
				System.out.println("list==null");
			} else {
				for (UTXO utxo : list) {
					System.out.println("================");
					System.out.println(utxo.toString());
				}
			}

		}

		mcmc();
		createUserPay(accountKey);
		mcmc();
		Lottery startLottery = startLottery();
		while (!startLottery.isMacthed()) {
			createUserPay(accountKey);
			startLottery = startLottery();
		}
		mcmc();
		mcmc();
		mcmc();
		checkResult(startLottery);
	}

	private Lottery startLottery()
			throws Exception, JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {
		Lottery startLottery = new Lottery();
		startLottery.setTokenid(yuanTokenPub);
		startLottery.setContextRoot(contextRoot);
		startLottery.setParams(networkParameters);

		startLottery.setWinnerAmount(winnerAmount);
		startLottery.setAccountKey(accountKey);
		startLottery.start();

		mcmc();
		return startLottery;
	}

	private void checkResult(Lottery startLottery) throws Exception {
		Coin coin = new Coin(startLottery.sum(), yuanTokenPub);

		List<UTXO> users = getBalance(startLottery.getWinner());

		Coin sum = Coin.valueOf(0, Utils.HEX.decode(yuanTokenPub));
		for (UTXO u : users) {
			if (coin.getTokenHex().equals(u.getTokenId())
					&& u.getFromaddress().equals(accountKey.toAddress(networkParameters).toBase58())) {
				sum = sum.add(u.getValue());

			}
		}

		assertTrue(sum != null);
		if (startLottery.getWinnerAmount().compareTo(sum.getValue()) > 0) {
			log.debug(sum.toString());
		}
		assertTrue(" " + startLottery.getWinnerAmount() + " sum=" + sum.getValue(),
				startLottery.getWinnerAmount().compareTo(sum.getValue()) <= 0);

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
			w.pay(null, accountKey.toAddress(networkParameters), Coin.valueOf(satoshis, Utils.HEX.decode(yuanTokenPub)),
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
			giveMoneyResult.put(key.toAddress(networkParameters).toString(), winnerAmount.longValue());
			userkeys.add(key);
		}

		Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, Utils.HEX.decode(yuanTokenPub),
				"net.bigtangle.server.performance.LotteryTests.payKeys() pay to user");
		log.debug("block " + (b == null ? "block is null" : b.toString()));
		mcmc();
		return userkeys;
	}

	public void testTokens() throws JsonProcessingException, Exception {

		String domain = "";

		testCreateMultiSigToken(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)), "人民币", 2, domain, "人民币 CNY",
				winnerAmount.multiply(BigInteger.valueOf(usernumber * 10000000l)));
		mcmc();
	}

	public Address getAddress() {
		return ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)).toAddress(networkParameters);
	}

	// create a token with multi sign
	protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname,
			String description, BigInteger amount) throws JsonProcessingException, Exception {
		try {
			walletAppKit1.wallet().setServerURL(contextRoot);
			createToken(key, tokename, decimals, domainname, description, amount, true, null,
					TokenType.identity.ordinal(), key.getPublicKeyAsHex(), walletAppKit1.wallet());

			ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

			walletAppKit1.wallet().multiSign(key.getPublicKeyAsHex(), signkey, null);

		} catch (Exception e) {
			// TODO: handle exception
			log.warn("", e);
		}

	}

	// get balance for the walletKeys
	protected List<UTXO> getBalance(String address) throws Exception {
		List<UTXO> listUTXO = new ArrayList<UTXO>();
		List<String> keyStrHex000 = new ArrayList<String>();

		keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(networkParameters, address).getHash160()));
		byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
				Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

		GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

		for (UTXO utxo : getBalancesResponse.getOutputs()) {
			listUTXO.add(utxo);
		}

		return listUTXO;
	}

}
