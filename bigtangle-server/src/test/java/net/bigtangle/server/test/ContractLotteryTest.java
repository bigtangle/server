package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
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
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
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
	 
	@Autowired
	public NetworkParameters networkParameters;
	public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
	public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";

	public static String lotteryTokenPub = "039aee4f0291991dd71ea0dd3c0e91ef680e769eca0326f1e36b74107aec4ac1f4";
	public static String lotteryTokenPriv = "6cecae9a820844dac41521ddad4f1b5068fdcac59ce28a6dd1ed01a12f782362";
	ECKey contractKey = ECKey.fromPrivate(Utils.HEX.decode(lotteryTokenPriv));
	public int usernumber = 10;

	List<ECKey> ulist;
	String winnerAmount = "10000";
	String contractAmount = "2500";
	public BigInteger payContractAmount = new BigInteger(contractAmount);

	public void lotteryM() throws Exception {

	
		lotteryDo();

	}

	public void lotteryDo() throws Exception {
		prepare();
	 
		executionAndCheck();

	}

	private void prepare() throws JsonProcessingException, Exception {
		wallet.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
		testTokens();
		testContractTokens();
		ulist = createUserkey();
		payUserKeys(ulist);
		payBigUserKeys(ulist);
		Map<String, BigInteger> startMap = new HashMap<>();
		check(ulist, startMap);
		// createUserPay(accountKey, ulist);
		ArrayList<Block> blocks = new ArrayList<>();
		payContract(ulist, blocks);
		mcmc();
		
		makeRewardBlock(blocks.get(blocks.size()-1));
	}

	private void executionAndCheck()
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException, Exception {
		Block tip = blockService.getBlockPrototype(store);
		Sha256Hash prev = store.getLastContractResultBlockHash(contractKey.getPublicKeyAsHex());
		if (prev == null) {
			prev = Sha256Hash.ZERO_HASH;
		}
		ContractResult check = new ServiceContract(serverConfiguration, networkParameters,cacheBlockService).executeContract(tip, store,
				contractKey.getPublicKeyAsHex(), prev);

		Block resultBlock = contractExecutionService.createContractExecution(tip, contractKey.getPublicKeyAsHex(),
				store);
 
		blockService.saveBlock(resultBlock, store);

		assertTrue(resultBlock != null);
		Address winnerAddress = check.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);

		makeRewardBlock(resultBlock);
		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);

		assertTrue(endMap.get(winnerAddress.toString()) != null);

		assertTrue(endMap.get(winnerAddress.toString()).equals(new BigInteger(winnerAmount)));
	}

	public void check(List<ECKey> ulist, Map<String, BigInteger> map) throws Exception {

		List<UTXO> utxos = getBalance(false, ulist);
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

		for (String address : map.keySet()) {
			log.debug(" check address==" + address + "  ;  balance==" + map.get(address));
		}
	}

	@Test
	public void testlotteryRepeat() throws Exception {
 
		lotteryDo();
		// repeat of execution
		executionAndCheck();

	}

	@Test
	public void lotteryConflict() throws Exception {
		// create two blocks for the ContractExecution and only one is taken

		prepare();
		// check all payContract ok?
		Map<String, BigInteger> payContractMap = new HashMap<>();
		check(ulist, payContractMap);

		Block resultBlock = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
		assertTrue(resultBlock != null);

		blockService.saveBlock(resultBlock, store);

		Block resultBlock2 = contractExecutionService.createContractExecution(store, contractKey.getPublicKeyAsHex());
		assertTrue(resultBlock2 != null);
		blockService.saveBlock(resultBlock2, store);

		ContractResult result = new ServiceContract(serverConfiguration, networkParameters,cacheBlockService).executeContract(resultBlock,
				store, contractKey.getPublicKeyAsHex(), resultBlock.getHash());
		Address winnerAddress = result.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);

		makeRewardBlock();
		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()) != null);

		assertTrue(endMap.get(winnerAddress.toString()).equals(new BigInteger(winnerAmount)));

	}

	@Test
	public void unconfirmEvent() throws Exception {
		// unconfirm the a event, -> unconfirm result
		// create two blocks for the ContractExecution and only one is taken 
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
		Block tip = blockService.getBlockPrototype(store);
		Sha256Hash prev = store.getLastContractResultBlockHash(contractKey.getPublicKeyAsHex());
		if (prev == null) {
			prev = Sha256Hash.ZERO_HASH;
		}
		ContractResult result = new ServiceContract(serverConfiguration, networkParameters,cacheBlockService).executeContract(tip, store,
				contractKey.getPublicKeyAsHex(), prev);

		Block resultBlock = contractExecutionService.createContractExecution(tip, contractKey.getPublicKeyAsHex(),
				store);
		blockService.saveBlock(resultBlock, store);

		mcmc();
		Address winnerAddress = result.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);

		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()).equals(new BigInteger(winnerAmount)));
		// unconfirm enven and will lead to unconfirm result

		new ServiceBase(serverConfiguration, networkParameters,cacheBlockService).unconfirm(events.get(0).getHash(), new HashSet<>(),
				store);
		endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()) == null);

	}

	@Test
	public void testUnconfirmChain() throws Exception { 
 
		prepare();
		Block tip = blockService.getBlockPrototype(store);
		Sha256Hash prev = store.getLastContractResultBlockHash(contractKey.getPublicKeyAsHex());
		if (prev == null) {
			prev = Sha256Hash.ZERO_HASH;
		}
		ContractResult check = new ServiceContract(serverConfiguration, networkParameters,cacheBlockService).executeContract(tip, store,
				contractKey.getPublicKeyAsHex(), prev);

		Block resultBlock = contractExecutionService.createContractExecution(tip, contractKey.getPublicKeyAsHex(),
				store);
		blockService.saveBlock(resultBlock, store);

		// check one of user get the winnerAmount
		Map<String, BigInteger> endMap = new HashMap<>();
		check(ulist, endMap);

		// Unconfirm
		new ServiceBase(serverConfiguration, networkParameters,cacheBlockService).unconfirmRecursive(resultBlock.getHash(),
				new HashSet<>(), store);
		Address winnerAddress = check.getOutputTx().getOutput(0).getScriptPubKey().getToAddress(networkParameters);

		// Winner Should be unconfirmed now
		endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()) == null);
		// new chain will ok again
		makeRewardBlock();
		endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()).equals(new BigInteger(winnerAmount)));

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

		kv.setValue(winnerAmount);
		tokenKeyValues.addKeyvalue(kv);
		kv = new KeyValue();
		kv.setKey("amount");
		kv.setValue(contractAmount);
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
			blocks.add(
					w.payContract(null, yuanTokenPub, payContractAmount, null, null, contractKey.getPublicKeyAsHex()));
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
				payContractAmount.multiply(BigInteger.valueOf(usernumber * 10000l)));
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
			makeRewardBlock(b);
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

	public void payUserKeys(List<ECKey> userkeys) throws Exception {

		Stopwatch watch = Stopwatch.createStarted();
		List<List<ECKey>> parts = Wallet.chopped(userkeys, 1000);

		for (List<ECKey> list : parts) {
			HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();
			for (ECKey key : list) {
				giveMoneyResult.put(key.toAddress(networkParameters).toString(), payContractAmount);
			}
			Block b = wallet.payToList(null, giveMoneyResult, Utils.HEX.decode(yuanTokenPub), "pay to user");
			// log.debug("block " + (b == null ? "block is null" : b.toString()));
			makeRewardBlock();
		}
		log.debug("pay user " + usernumber + "  duration minutes " + watch.elapsed(TimeUnit.MINUTES));
		log.debug("rate  " + usernumber * 1.0 / watch.elapsed(TimeUnit.SECONDS));

	}
}
