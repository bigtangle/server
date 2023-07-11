/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.OkResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class FromAddressTests extends AbstractIntegrationTest {
	@Autowired
	private NetworkParameters networkParameters;
	public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
	public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";

	private ECKey accountKey;
	Wallet yuanWallet;
	protected static final Logger log = LoggerFactory.getLogger(FromAddressTests.class);

	@Test
	public void testUserpay() throws Exception {
		yuanWallet = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)),
				contextRoot);
		//first delete all table 
		payBigTo(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)), Coin.FEE_DEFAULT.getValue().multiply(BigInteger.valueOf(1000)), null);
		accountKey = new ECKey();
		testTokens();
		getBalanceAccount(false, yuanWallet.walletKeys());

		makeRewardBlock();
		createUserPay(accountKey);
		getBalanceAccount(false, yuanWallet.walletKeys());
	}

	private void checkResult(ECKey userkey, String fromaddress, String memo) throws Exception {

		List<UTXO> users = getBalance(userkey.toAddress(networkParameters).toBase58());

		for (UTXO u : users) {
			assertTrue(u.getFromaddress().equals(fromaddress));
			assertTrue(u.getMemoInfo().getKv().get(0).getValue().equals(memo));
		}
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
		Wallet w = Wallet.fromKeys(networkParameters, key, contextRoot);
		w.setServerURL(contextRoot);
		try {

			w.pay(null, accountKey.toAddress(networkParameters).toString(),
					Coin.valueOf(100, Utils.HEX.decode(yuanTokenPub)), new MemoInfo(" buy ticket"));
		} catch (InsufficientMoneyException e) {
			// TODO: handle exception
		}
		mcmc();
		List<ECKey> userkeys = new ArrayList<ECKey>();
		userkeys.add(key);
		userkeys.add(accountKey);
		getBalanceAccount(false, userkeys);
		// checkResult(accountKey, key.toAddress(networkParameters).toBase58());
	}

	public List<ECKey> payKeys() throws Exception {
		List<ECKey> userkeys = new ArrayList<ECKey>();
		HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();

		ECKey key = new ECKey();
		giveMoneyResult.put(key.toAddress(networkParameters).toString(), BigInteger.valueOf(100));
		userkeys.add(key);
		ECKey key2 = new ECKey();
		giveMoneyResult.put(key2.toAddress(networkParameters).toString(), BigInteger.valueOf(100));
		userkeys.add(key2);

		String memo = "pay to user";
		Block b = yuanWallet.payToList(null, giveMoneyResult, Utils.HEX.decode(yuanTokenPub), memo);
		log.debug("block " + (b == null ? "block is null" : b.toString()));
		makeRewardBlock();

		getBalanceAccount(false, userkeys);
		checkResult(key, yuanWallet.walletKeys().get(0).toAddress(networkParameters).toBase58(), memo);
		return userkeys;
	}

	@Test
	public void fixBalanceAmount() throws Exception {
		List<String> keyStrHex000 = new ArrayList<String>();
		byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.fixAccountBalance.name(),
				Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());
		OkResponse okResponse = Json.jsonmapper().readValue(response, OkResponse.class);
		assertTrue(okResponse.getErrorcode()==0);

		
	}
	
	public void testTokens() throws JsonProcessingException, Exception {
		String domain = "";
		ECKey fromPrivate = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));

		testCreateMultiSigToken(fromPrivate, "人民币", 2, domain, "人民币 CNY", BigInteger.valueOf(10000000l));
		makeRewardBlock();
	}

	public Address getAddress() {
		return ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)).toAddress(networkParameters);
	}

	// create a token with multi sign
	protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname,
			String description, BigInteger amount) throws JsonProcessingException, Exception {
		try {

			createToken(key, tokename, decimals, domainname, description, amount, true, null,
					TokenType.currency.ordinal(), key.getPublicKeyAsHex(),
					Wallet.fromKeys(networkParameters, key, contextRoot));
			ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

			wallet.multiSign(key.getPublicKeyAsHex(), signkey, null);

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
