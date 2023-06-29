package net.bigtangle.server.performance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.ContractResult;
import net.bigtangle.server.service.ServiceBase;
import net.bigtangle.server.service.ServiceContract;
import net.bigtangle.wallet.Wallet;

public class LotteryRemoteTest extends LotteryTests {

	protected static final Logger log = LoggerFactory.getLogger(LotteryRemoteTest.class);
	ECKey contractKey;

	public void lotteryM() throws Exception {

		usernumber = 10;
		winnerAmount = new BigInteger(usernumber * 100 + "");
		contractKey = new ECKey();
		lotteryDo();

	}

	public void lotteryDo() throws Exception {
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
		lotteryDo();
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
		blockService.saveBlock(resultBlock, store);
		// check one of user get the winnerAmount
		HashMap endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString()).equals(winnerAmount.multiply(BigInteger.valueOf(10))));
		// unconfirm enven and will lead to unconfirm result

		new ServiceBase(serverConfiguration, networkParameters).unconfirm(events.get(0).getHash(), new HashSet<>(),
				store);
		endMap = new HashMap<>();
		check(ulist, endMap);
		assertTrue(endMap.get(winnerAddress.toString())==null);

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
		payContract(ulist,new ArrayList<>());
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

		createToken(contractKey, "contractlottery", 0, domain, "contractlottery", BigInteger.valueOf(1), true,
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

}
