package net.bigtangle.server.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import net.bigtangle.server.data.ContractResult;
import net.bigtangle.server.service.base.ServiceBaseConnect;
import net.bigtangle.server.service.base.ServiceContract;
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

	private void prepare() throws JsonProcessingException, Exception {
		wallet.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
		testTokens();
		testContractTokens();
		ulist = createUserkey();
		payUserKeys(ulist);
		payBigUserKeys(ulist);

	}

	private void executionAndCheck()
			throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException, Exception {

		Block resultBlock = contractExecutionService.createContractExecution(contractKey.getPublicKeyAsHex(), store);

		if (resultBlock != null) {
			ContractResult result = new ContractResult().parse(resultBlock.getTransactions().get(0).getData());

			ContractResult check = new ServiceContract(serverConfiguration, networkParameters, cacheBlockService)
					.executeContract(resultBlock, store, result.getContracttokenid(), result.getPrevblockhash(),
							result.getReferencedBlocks());
			blockService.saveBlock(resultBlock, store);
			makeRewardBlock(resultBlock);
			assertTrue(resultBlock != null);
			if (!check.getOutputTx().getOutputs().isEmpty()) {
				Address winnerAddress = check.getOutputTx().getOutput(0).getScriptPubKey()
						.getToAddress(networkParameters);

				makeRewardBlock(resultBlock);
				// check one of user get the winnerAmount
				Map<String, BigInteger> endMap = new HashMap<>();
				check(ulist, endMap);
				// List<UTXO> utxos = getBalance(false, ulist);
				assertTrue(endMap.get(winnerAddress.toString()) != null);

				assertTrue(endMap.get(winnerAddress.toString()).equals(new BigInteger(winnerAmount)));
			}
		}
	}

	public void check(List<ECKey> ulist, Map<String, BigInteger> map) throws Exception {

		List<UTXO> utxos = getBalance(false, ulist);
		List<UTXO> ylist = utxos.stream().filter(u -> u.getTokenId().equals(yuanTokenPub)).collect(Collectors.toList());
		for (UTXO u : ylist) {
		//	log.debug(u.toString());
			BigInteger p = map.get(u.getAddress());
			if (p != null) {
				map.put(u.getAddress(), p.add(u.getValue().getValue()));
			} else {
				map.put(u.getAddress(), u.getValue().getValue());
			}

		}

	}

	@Test
	public void testlotteryRepeat() throws Exception {

		prepare();
		List<Block> blocks = new ArrayList<>();
		payContract(ulist, blocks, true);
	}

	@Test
	public void lotteryConflict() throws Exception {
		// create two blocks for the ContractExecution and only one is taken

		prepare();
		Sha256Hash prevbeforeConflict = Sha256Hash.ZERO_HASH;
		boolean win = false;
		for (ECKey key : ulist) {
			Wallet w = Wallet.fromKeys(networkParameters, key, contextRoot);
			w.payContract(null, yuanTokenPub, payContractAmount, null, null, contractKey.getPublicKeyAsHex());

			if (win) {
				// create conflict
				Block conflictBlock = contractExecutionService.createContractExecution(
						blockService.getBlockPrototype(store), contractKey.getPublicKeyAsHex(), prevbeforeConflict,
						store);
				if (conflictBlock != null) {
					blockService.saveBlock(conflictBlock, store);
					makeRewardBlock(conflictBlock);
				}
			}
			prevbeforeConflict = store.getLastContractResultBlockHash(contractKey.getPublicKeyAsHex());

			Block resultBlock = contractExecutionService.createContractExecution(contractKey.getPublicKeyAsHex(),
					store);

			if (resultBlock != null) {
				ContractResult result = new ContractResult().parse(resultBlock.getTransactions().get(0).getData());

				ContractResult check = new ServiceContract(serverConfiguration, networkParameters, cacheBlockService)
						.executeContract(resultBlock, store, result.getContracttokenid(), result.getPrevblockhash(),
								result.getReferencedBlocks());
				blockService.saveBlock(resultBlock, store);
				makeRewardBlock(resultBlock);
				assertTrue(resultBlock != null);
				if (!check.getOutputTx().getOutputs().isEmpty()) {
					Address winnerAddress = check.getOutputTx().getOutput(0).getScriptPubKey()
							.getToAddress(networkParameters);
					// check one of user get the winnerAmount
					Map<String, BigInteger> endMap = new HashMap<>();
					check(ulist, endMap);
					// List<UTXO> utxos = getBalance(false, ulist);
					assertTrue(endMap.get(winnerAddress.toString()) != null);
					assertTrue(endMap.get(winnerAddress.toString()).equals(new BigInteger(winnerAmount)));
					win = true;

				}
			}
		}

	}

	@Test
	public void unconfirmEvent() throws Exception {

		prepare();

		for (ECKey key : ulist) {
			Wallet w = Wallet.fromKeys(networkParameters, key, contextRoot);
			Block event = w.payContract(null, yuanTokenPub, payContractAmount, null, null,
					contractKey.getPublicKeyAsHex());
			Sha256Hash prev = store.getLastContractResultBlockHash(contractKey.getPublicKeyAsHex());
			if (prev == null) {
				prev = Sha256Hash.ZERO_HASH;
			}
			Block resultBlock = contractExecutionService.createContractExecution(contractKey.getPublicKeyAsHex(),
					store);

			if (resultBlock != null) {
				ContractResult result = new ContractResult().parse(resultBlock.getTransactions().get(0).getData());

				ContractResult check = new ServiceContract(serverConfiguration, networkParameters, cacheBlockService)
						.executeContract(resultBlock, store, result.getContracttokenid(), result.getPrevblockhash(),
								result.getReferencedBlocks());
				blockService.saveBlock(resultBlock, store);
				Block reward=	makeRewardBlock(resultBlock);
				assertTrue(resultBlock != null);
				if (!check.getOutputTx().getOutputs().isEmpty()) {
					Address winnerAddress = check.getOutputTx().getOutput(0).getScriptPubKey()
							.getToAddress(networkParameters);
					// check one of user get the winnerAmount
					Map<String, BigInteger> endMap = new HashMap<>();
					check(ulist, endMap);
					// List<UTXO> utxos = getBalance(false, ulist);
					assertTrue(endMap.get(winnerAddress.toString()) != null);
					assertTrue(endMap.get(winnerAddress.toString()).equals(new BigInteger(winnerAmount)));


					// unconfirm an event will not lead to unconfirm execution result 

					new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
							.unconfirmRecursive(event.getHash(), new HashSet<>(), store);
					endMap = new HashMap<>();
					check(ulist, endMap);
			 
					assertTrue(endMap.get(winnerAddress.toString() ) ==null);

					

				}
			}
		}

	}

	@Test
	public void testUnconfirmChain() throws Exception {

		prepare();

		Block resultBlock = contractExecutionService.createContractExecution(contractKey.getPublicKeyAsHex(), store);

		if (resultBlock != null) {
			ContractResult result = new ContractResult().parse(resultBlock.getTransactions().get(0).getData());

			ContractResult check = new ServiceContract(serverConfiguration, networkParameters, cacheBlockService)
					.executeContract(resultBlock, store, result.getContracttokenid(), result.getPrevblockhash(),
							result.getReferencedBlocks());
			blockService.saveBlock(resultBlock, store);
			makeRewardBlock(resultBlock);
			assertTrue(resultBlock != null);
			if (!check.getOutputTx().getOutputs().isEmpty()) {
				Address winnerAddress = check.getOutputTx().getOutput(0).getScriptPubKey()
						.getToAddress(networkParameters);

				makeRewardBlock(resultBlock);
				// check one of user get the winnerAmount
				Map<String, BigInteger> endMap = new HashMap<>();
				check(ulist, endMap);
				// List<UTXO> utxos = getBalance(false, ulist);
				assertTrue(endMap.get(winnerAddress.toString()) != null);

				assertTrue(endMap.get(winnerAddress.toString()).equals(new BigInteger(winnerAmount)));

				// Unconfirm
				new ServiceBaseConnect(serverConfiguration, networkParameters, cacheBlockService)
						.unconfirmRecursive(resultBlock.getHash(), new HashSet<>(), store);
				// Winner Should be unconfirmed now
				endMap = new HashMap<>();
				check(ulist, endMap);
				assertTrue(endMap.get(winnerAddress.toString()) == null);
			}
		}

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
	public Block payContract(List<ECKey> userkeys, List<Block> blocks, boolean executecontract) throws Exception {
		Block payContract = null;
		for (ECKey key : userkeys) {
			Wallet w = Wallet.fromKeys(networkParameters, key, contextRoot);
			payContract = w.payContract(null, yuanTokenPub, payContractAmount, null, null,
					contractKey.getPublicKeyAsHex());
			blocks.add(payContract);
			if (executecontract)
				executionAndCheck();
			else
				mcmcServiceUpdate();

		}
		return payContract;
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
			Block b = wallet.payToList(null, giveMoneyResult, NetworkParameters.BIGTANGLE_TOKENID, "pay big to user");
			// log.debug("block " + (b == null ? "block is null" : b.toString()));
			makeRewardBlock(b);
		}

	}

	// create a token with multi sign
	protected void testCreateMultiSigToken(ECKey key, String tokename, int decimals, String domainname,
			String description, BigInteger amount) throws JsonProcessingException, Exception {
		try {
			wallet.setServerURL(contextRoot);
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
			Block b = wallet.payToList(null, giveMoneyResult, Utils.HEX.decode(yuanTokenPub), "pay yuan to user");
			// log.debug("block " + (b == null ? "block is null" : b.toString()));
			makeRewardBlock(b);
		}
		log.debug("pay user " + usernumber + "  duration minutes " + watch.elapsed(TimeUnit.MINUTES));
		log.debug("rate  " + usernumber * 1.0 / watch.elapsed(TimeUnit.SECONDS));

	}
}
