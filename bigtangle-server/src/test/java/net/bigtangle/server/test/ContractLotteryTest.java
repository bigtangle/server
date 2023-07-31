package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Stopwatch;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.data.ContractResult;
import net.bigtangle.server.service.ServiceBase;
import net.bigtangle.server.service.ServiceContract;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class ContractLotteryTest extends AbstractIntegrationTest {

	protected static final Logger log = LoggerFactory.getLogger(ContractLotteryTest.class);
	ECKey contractKey;
	@Autowired
	public NetworkParameters networkParameters;
	public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
	public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";

	public static String lotteryTokenPub = "039aee4f0291991dd71ea0dd3c0e91ef680e769eca0326f1e36b74107aec4ac1f4";
	public static String lotteryTokenPriv = "6cecae9a820844dac41521ddad4f1b5068fdcac59ce28a6dd1ed01a12f782362";

	public int usernumber = Math.abs(new Random().nextInt()) % 88;
	public BigInteger winnerAmount = new BigInteger(Math.abs(new Random().nextInt()) % 9999 + "");
	List<ECKey> ulist;

	public void lotteryM() throws Exception {

		usernumber = 10;
		winnerAmount = new BigInteger(usernumber * 100 + "");
		contractKey = ECKey.fromPrivate(Utils.HEX.decode(lotteryTokenPriv));
		lotteryDo();

	}

	public void lotteryDo() throws Exception {
		wallet.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
		testTokens();
		testContractTokens();
		ulist = createUserkey();
		payUserKeys(ulist);
		payBigUserKeys(ulist);
		Map<String, BigInteger> startMap = new HashMap<>();
		check(ulist, startMap);
		// createUserPay(accountKey, ulist);
		payContract(ulist, new ArrayList<>());
		makeRewardBlock();
		Block resultBlock = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
		assertTrue(resultBlock != null);
		ContractResult result = new ServiceContract(serverConfiguration, networkParameters).executeContract(resultBlock,
				store, contractKey.getPublicKeyAsHex());
		Address winnerAddress = result.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);
		blockService.saveBlock(resultBlock, store);

		makeRewardBlock();
		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);

		assertTrue(endMap.get(winnerAddress.toString()) != null);

		assertTrue(endMap.get(winnerAddress.toString()).equals(winnerAmount.multiply(BigInteger.valueOf(10))));

	}

	public void check(List<ECKey> ulist, Map<String, BigInteger> map) throws Exception {

		for (ECKey ecKey : ulist) {
			List<UTXO> utxos = getBalance(ecKey.toAddress(networkParameters).toString());
			for (UTXO u : utxos) {
				if (u.getTokenId().equals(yuanTokenPub)) {
					BigInteger p = map.get(u.getAddress());
					if (p != null) {
						map.put(u.getAddress(), p.add(u.getValue().getValue()));
					} else {
						map.put(u.getAddress(), u.getValue().getValue());
					}
				}
			}
		}
		for (String address : map.keySet()) {
			log.debug("start address==" + address + "  ;  balance==" + map.get(address));
		}
	}

	@Test
	public void testlotteryRepeat() throws Exception {
		usernumber = 10;
		winnerAmount = new BigInteger(usernumber * 100 + "");
		contractKey = new ECKey();
		lotteryDo();
		payUserKeys(ulist);
		payBigUserKeys(ulist);
		Map<String, BigInteger> startMap = new HashMap<>();
		check(ulist, startMap);
		// createUserPay(accountKey, ulist);
		payContract(ulist, new ArrayList<>());
		makeRewardBlock();
		Block resultBlock = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
		assertTrue(resultBlock != null);
		ContractResult result = new ServiceContract(serverConfiguration, networkParameters).executeContract(resultBlock,
				store, contractKey.getPublicKeyAsHex());
		Address winnerAddress = result.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);
		blockService.saveBlock(resultBlock, store);

		makeRewardBlock();
		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);

		assertTrue(endMap.get(winnerAddress.toString()) != null);

		assertTrue(endMap.get(winnerAddress.toString()).equals(winnerAmount.multiply(BigInteger.valueOf(10))));

	}

	@Test
	public void lotteryConflict() throws Exception {
		// create two blocks for the ContractExecution and only one is taken
		usernumber = 10;
		winnerAmount = new BigInteger(usernumber * 100 + "");
		contractKey = new ECKey();
		wallet.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
		testTokens();
		testContractTokens();
		List<ECKey> ulist = createUserkey();
		payUserKeys(ulist);
		payBigUserKeys(ulist);
		Map<String, BigInteger> startMap = new HashMap<>();
		check(ulist, startMap);
		// createUserPay(accountKey, ulist);
		payContract(ulist, new ArrayList<>());
		makeRewardBlock();
		// check all payContract ok?
		Map<String, BigInteger> payContractMap = new HashMap<>();
		check(ulist, payContractMap);

		Block resultBlock = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
		assertTrue(resultBlock != null);

		blockService.saveBlock(resultBlock, store);

		Block resultBlock2 = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
		assertTrue(resultBlock2 != null);
		blockService.saveBlock(resultBlock2, store);

		ContractResult result = new ServiceContract(serverConfiguration, networkParameters).executeContract(resultBlock,
				store, contractKey.getPublicKeyAsHex());
		Address winnerAddress = result.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);

		makeRewardBlock();
		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()) != null);

		assertTrue(endMap.get(winnerAddress.toString()).equals(winnerAmount.multiply(BigInteger.valueOf(10))));

	}

	@Test
	public void unconfirmEvent() throws Exception {
		// unconfirm the a event, -> unconfirm result
		// create two blocks for the ContractExecution and only one is taken
		usernumber = 10;
		winnerAmount = new BigInteger(usernumber * 100 + "");
		contractKey = new ECKey();
		wallet.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
		testTokens();
		testContractTokens();
		List<ECKey> ulist = createUserkey();
		payUserKeys(ulist);
		payBigUserKeys(ulist);
		Map<String, BigInteger> startMap = new HashMap<>();
		check(ulist, startMap);
		// createUserPay(accountKey, ulist);
		List<Block> events = new ArrayList<>();
		payContract(ulist, events);
		mcmc();

		Block resultBlock = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
		ContractResult result = new ServiceContract(serverConfiguration, networkParameters).executeContract(resultBlock,
				store, contractKey.getPublicKeyAsHex());
		assertTrue(resultBlock != null);

		blockService.saveBlock(resultBlock, store);

		mcmc();
		Address winnerAddress = result.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);

		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()).equals(winnerAmount.multiply(BigInteger.valueOf(10))));
		// unconfirm enven and will lead to unconfirm result

		new ServiceBase(serverConfiguration, networkParameters).unconfirm(events.get(0).getHash(), new HashSet<>(),
				store);
		endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()) == null);

	}

	@Test
	public void testUnconfirmChain() throws Exception {
		usernumber = 10;
		winnerAmount = new BigInteger(usernumber * 100 + "");
		contractKey = new ECKey();
		wallet.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));

		testTokens();
		testContractTokens();
		List<ECKey> ulist = createUserkey();
		payUserKeys(ulist);
		payBigUserKeys(ulist);
		Map<String, BigInteger> startMap = new HashMap<>();
		check(ulist, startMap);
		// createUserPay(accountKey, ulist);
		payContract(ulist, new ArrayList<>());
		makeRewardBlock();
		Block resultBlock = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
		assertTrue(resultBlock != null);
		ContractResult result = new ServiceContract(serverConfiguration, networkParameters).executeContract(resultBlock,
				store, contractKey.getPublicKeyAsHex());
		Address winnerAddress = result.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);
		blockService.saveBlock(resultBlock, store);

		Block rewardBlock1 = makeRewardBlock();
		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);

		// Unconfirm
		new ServiceBase(serverConfiguration, networkParameters).unconfirmRecursive(resultBlock.getHash(),
				new HashSet<>(), store);

		// Winner Should be unconfirmed now
		endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()) == null);
		// new chain will ok again
		Block rewardBlock2 = makeRewardBlock();
		endMap = new HashMap<>();
		check(ulist, endMap);

		assertTrue(endMap.get(winnerAddress.toString()).equals(winnerAmount.multiply(BigInteger.valueOf(10))));

	}

	public void testContractTokens() throws JsonProcessingException, Exception {

		String domain = "";

		TokenKeyValues tokenKeyValues = new TokenKeyValues();
		KeyValue kv = new KeyValue();
		kv.setKey("system");
		kv.setValue("java");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("classname");
		kv.setValue("net.bigtangle.server.service.LotteryContract");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("winnerAmount");
		kv.setValue("10000");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("amount");
		kv.setValue("50");
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("token");
		kv.setValue(yuanTokenPub);
		tokenKeyValues.addKeyvalue(kv);

		createToken(contractKey, "contractlottery", 0, domain, "contractlottery", BigInteger.valueOf(1), false,
				tokenKeyValues, TokenType.contract.ordinal(), contractKey.getPublicKeyAsHex(), wallet);

		ECKey signkey = ECKey.fromPrivate(Utils.HEX.decode(testPriv));

		wallet.multiSign(contractKey.getPublicKeyAsHex(), signkey, null);

		makeRewardBlock();
	}

	/*
	 * pay money to the contract
	 */
	public void payContract(List<ECKey> userkeys, List<Block> blocks) throws Exception {
		for (ECKey key : userkeys) {
			Wallet w = Wallet.fromKeys(networkParameters, key, contextRoot);
			blocks.add(w.payContract(null, yuanTokenPub, winnerAmount, null, null, contractKey.getPublicKeyAsHex()));
		}
	}

	public List<ECKey> createUserkey() {
		List<ECKey> userkeys = new ArrayList<ECKey>();
		for (int i = 0; i < usernumber; i++) {
			ECKey key = new ECKey();
			userkeys.add(key);
		}
		return userkeys;
	}

	public void testTokens() throws JsonProcessingException, Exception {

		String domain = "";

		ECKey fromPrivate = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));

		testCreateMultiSigToken(fromPrivate, "人民币", 2, domain, "人民币 CNY",
				winnerAmount.multiply(BigInteger.valueOf(usernumber * 10000l)));
		makeRewardBlock();
	}

	public Address getAddress() {
		return ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)).toAddress(networkParameters);
	}

	public void payBigUserKeys(List<ECKey> userkeys) throws Exception {

		List<List<ECKey>> parts = Wallet.chopped(userkeys, 1000);

		for (List<ECKey> list : parts) {
			HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();
			for (ECKey key : list) {
				giveMoneyResult.put(key.toAddress(networkParameters).toString(), BigInteger.valueOf(10000));
			}
			Block b = wallet.payToList(null, giveMoneyResult, NetworkParameters.BIGTANGLE_TOKENID, "pay to user");
			// log.debug("block " + (b == null ? "block is null" : b.toString()));
			makeRewardBlock();
		}

	}

	// create a token with multi sign
	protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname,
			String description, BigInteger amount) throws JsonProcessingException, Exception {
		try {
			wallet.setServerURL(contextRoot);

			// pay fee to ECKey key

			createToken(key, tokename, decimals, domainname, description, amount, true, null,
					TokenType.identity.ordinal(), key.getPublicKeyAsHex(), wallet);

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

	public void payUserKeys(List<ECKey> userkeys) throws Exception {

		Stopwatch watch = Stopwatch.createStarted();
		List<List<ECKey>> parts = Wallet.chopped(userkeys, 1000);

		for (List<ECKey> list : parts) {
			HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();
			for (ECKey key : list) {
				giveMoneyResult.put(key.toAddress(networkParameters).toString(), winnerAmount);
			}
			Block b = wallet.payToList(null, giveMoneyResult, Utils.HEX.decode(yuanTokenPub), "pay to user");
			// log.debug("block " + (b == null ? "block is null" : b.toString()));
			makeRewardBlock();
		}
		log.debug("pay user " + usernumber + "  duration minutes " + watch.elapsed(TimeUnit.MINUTES));
		log.debug("rate  " + usernumber * 1.0 / watch.elapsed(TimeUnit.SECONDS));

	}
}
