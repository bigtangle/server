/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.core.response.MultiSignByRequest;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.data.BatchBlock;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DirectExchangeTest extends AbstractIntegrationTest {

	private static final Logger log = LoggerFactory.getLogger(DirectExchangeTest.class);

	@Test
	public void testBatchBlock() throws Exception {
		byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
				Json.jsonmapper().writeValueAsString(new HashMap<String, String>()));
		Block block = networkParameters.getDefaultSerializer().makeBlock(data);
		store.insertBatchBlock(block);

		List<BatchBlock> batchBlocks = store.getBatchBlockList();
		assertTrue(batchBlocks.size() == 1);

		BatchBlock batchBlock = batchBlocks.get(0);

		// String hex1 = Utils.HEX.encode(block.bitcoinSerialize());
		// String hex2 = Utils.HEX.encode(batchBlock.getBlock());
		// assertEquals(hex1, hex2);

		assertArrayEquals(block.bitcoinSerialize(), batchBlock.getBlock());

		store.deleteBatchBlock(batchBlock.getHash());
		batchBlocks = store.getBatchBlockList();
		assertTrue(batchBlocks.size() == 0);
	}

	@Test
	public void testTransactionResolveSubtangleID() throws Exception {
		Transaction transaction = new Transaction(this.networkParameters);

		byte[] subtangleID = new byte[32];
		new Random().nextBytes(subtangleID);

		transaction.setToAddressInSubtangle(subtangleID);

		byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
				Json.jsonmapper().writeValueAsString(new HashMap<String, String>()));
		Block block = networkParameters.getDefaultSerializer().makeBlock(data);
		block.setBlockType(Block.Type.BLOCKTYPE_CROSSTANGLE);
		block.addTransaction(transaction);
		block.solve();
		OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());

		HashMap<String, Object> requestParam = new HashMap<String, Object>();
		requestParam.put("hashHex", Utils.HEX.encode(block.getHash().getBytes()));
		data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getBlockByHash.name(),
				Json.jsonmapper().writeValueAsString(requestParam));
		block = networkParameters.getDefaultSerializer().makeBlock(data);

		Transaction transaction2 = block.getTransactions().get(0);
		assertNotNull(subtangleID);
		assertTrue(Arrays.equals(subtangleID, transaction.getToAddressInSubtangle()));
		assertTrue(Arrays.equals(subtangleID, transaction2.getToAddressInSubtangle()));
	}

	public void createTokenSubtangle() throws Exception {
		ECKey ecKey = new ECKey();
		byte[] pubKey = ecKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();

		Token tokens = Token.buildSubtangleTokenInfo(false, null, Utils.HEX.encode(pubKey), "subtangle", "", "");
		tokenInfo.setToken(tokens);

		tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokens.getTokenid(), "", ecKey.getPublicKeyAsHex()));

		Coin basecoin = Coin.valueOf(0L, pubKey);

		HashMap<String, String> requestParam = new HashMap<String, String>();
		byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
				Json.jsonmapper().writeValueAsString(requestParam));
		Block block = networkParameters.getDefaultSerializer().makeBlock(data);
		block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
		block.addCoinbaseTransaction(ecKey.getPubKey(), basecoin, tokenInfo, new MemoInfo("coinbase"));

		Transaction transaction = block.getTransactions().get(0);

		Sha256Hash sighash = transaction.getHash();
		ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
		byte[] buf1 = party1Signature.encodeToDER();

		List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
		MultiSignBy multiSignBy0 = new MultiSignBy();
		multiSignBy0.setTokenid(Utils.HEX.encode(pubKey));
		multiSignBy0.setTokenindex(0);
		multiSignBy0.setAddress(ecKey.toAddress(networkParameters).toBase58());
		multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
		multiSignBy0.setSignature(Utils.HEX.encode(buf1));
		multiSignBies.add(multiSignBy0);
		MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
		transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

		// save block
		block = adjustSolve(block);
		OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block.bitcoinSerialize());
	}

	@Test
	public void testGiveMoney() throws Exception {

		ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
				Utils.HEX.decode(testPub));
		List<UTXO> balance1 = getBalance(false, genesiskey);
		log.info("balance1 : " + balance1);
		// two utxo to spent
		HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();
		for (int i = 0; i < 3; i++) {
			ECKey outKey = new ECKey();
			giveMoneyResult.put(outKey.toAddress(networkParameters).toBase58(), Coin.COIN.getValue() );
		}
		wallet.payMoneyToECKeyList(null, giveMoneyResult, "testGiveMoney");
		makeRewardBlock();

		List<UTXO> balance = getBalance(false, genesiskey);
		log.info("balance : " + balance);
		for (UTXO utxo : balance) {

			assertTrue(utxo.getValue().getValue().equals(NetworkParameters.BigtangleCoinTotal
					.subtract(Coin.COIN.getValue().multiply(BigInteger.valueOf(3)))));

		}
	}

	@Test
	public void testRatingRead() throws Exception {

		ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
				Utils.HEX.decode(testPub));
		List<UTXO> balance1 = getBalance(false, genesiskey);
		log.info("balance1 : " + balance1);
		// two utxo to spent
		HashMap<String, BigInteger> giveMoneyResult = new HashMap<>();
		for (int i = 0; i < 3; i++) {
			ECKey outKey = new ECKey();
			giveMoneyResult.put(outKey.toAddress(networkParameters).toBase58(), Coin.COIN.getValue() );
		}
		Block b = wallet.payMoneyToECKeyList(null, giveMoneyResult, "testGiveMoney");
		makeRewardBlock();

		Map<String, Object> requestParam = new HashMap<String, Object>();

		List<String> blockhashs = new ArrayList<String>();
		blockhashs.add(b.getHashAsString());
		requestParam.put("blockhashs", blockhashs);

		byte[] response = OkHttp3Util.postString(contextRoot + ReqCmd.searchBlockByBlockHashs.name(),
				Json.jsonmapper().writeValueAsString(requestParam));

		GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
				GetBlockEvaluationsResponse.class);
		List<BlockEvaluationDisplay> blockEvaluations = getBlockEvaluationsResponse.getEvaluations();

		assertTrue(!blockEvaluations.isEmpty());

	}

	@Test
	public void searchBlock() throws Exception {
		List<ECKey> keys = wallet.walletKeys(null);
		List<String> address = new ArrayList<String>();
		for (ECKey ecKey : keys) {
			address.add(ecKey.toAddress(networkParameters).toBase58());
		}
		HashMap<String, Object> request = new HashMap<String, Object>();
		request.put("address", address);

		byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.findBlockEvaluation.name(),
				Json.jsonmapper().writeValueAsString(request).getBytes());

		log.info("searchBlock resp : " + response);

	}

	// TODO @Test
	public void testExchangeTokenMulti() throws Exception {
		 

		List<ECKey> keys =  wallet.walletKeys(null);
		TokenInfo tokenInfo = new TokenInfo();
		testCreateMultiSigToken(keys, tokenInfo);
		UTXO multitemp = null;
		UTXO systemcoin = null;
		List<UTXO> utxos = getBalance(false, keys);
		for (UTXO utxo : utxos) {
			if (multitemp == null && !Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
				multitemp = utxo;
			}
			if (systemcoin == null && Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
				systemcoin = utxo;
			}
			log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
		}
		UTXO yourutxo = utxos.get(0);
		List<UTXO> ulist = getBalance();
		UTXO mymultitemp = null;
		UTXO mysystemcoin = null;
		for (UTXO utxo : ulist) {
			if (mymultitemp == null && !Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
				mymultitemp = utxo;
			}
			if (mysystemcoin == null && Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
				mysystemcoin = utxo;
			}
			log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
		}
		UTXO myutxo = null;
		for (UTXO u : ulist) {
			if (Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
				myutxo = u;
			}
		}
		log.debug("outKey : " + myutxo.getAddress());

		Coin amount = Coin.valueOf(10000, yourutxo.getValue().getTokenid());

		SendRequest req = null;

		// ulist.addAll(utxos);
		Transaction transaction = new Transaction(networkParameters);

		List<ECKey> signKeys = new ArrayList<>();
		signKeys.add(keys.get(0));
		signKeys.add(keys.get(1));
		signKeys.add(keys.get(2));

		TransactionOutput multisigOutput = new FreeStandingTransactionOutput(this.networkParameters, yourutxo);

		transaction.addOutput(amount, Address.fromBase58(networkParameters, myutxo.getAddress()));

		Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(3, signKeys);
		Coin amount2 = multisigOutput.getValue().subtract(amount);
		transaction.addOutput(amount2, scriptPubKey);

		transaction.addInput(yourutxo.getBlockHash(), multisigOutput);

		List<byte[]> sigs = new ArrayList<byte[]>();
		for (ECKey ecKey : signKeys) {
			TransactionOutput multisigOutput_ = new FreeStandingTransactionOutput(networkParameters, yourutxo);
			Script multisigScript_ = multisigOutput_.getScriptPubKey();

			Sha256Hash sighash = transaction.hashForSignature(0, multisigScript_, Transaction.SigHash.ALL, false);
			TransactionSignature transactionSignature = new TransactionSignature(ecKey.sign(sighash, null),
					Transaction.SigHash.ALL, false);

			ECKey.ECDSASignature party1Signature = ecKey.sign(transaction.getHash(), null);
			byte[] signature = party1Signature.encodeToDER();
			boolean success = ECKey.verify(transaction.getHash().getBytes(), signature, ecKey.getPubKey());
			if (!success) {
				throw new BlockStoreException("key multisign signature error");
			}
			sigs.add(transactionSignature.encodeToBitcoin());
		}
		Script inputScript = ScriptBuilder.createMultiSigInputScriptBytes(sigs);
		transaction.getInput(0).setScriptSig(inputScript);
		req = SendRequest.forTx(transaction);

		exchangeTokenComplete(req.tx);

		for (UTXO utxo : getBalance(false, keys)) {
			log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
		}
		for (UTXO utxo : getBalance()) {
			log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
		}
		Address destination = Address.fromBase58(networkParameters, yourutxo.getAddress());
		amount = Coin.valueOf(1000, myutxo.getValue().getTokenid());
		req = SendRequest.to(destination, amount);
		//wallet.completeTx(req, null);
		wallet.signTransaction(req);

		exchangeTokenComplete(req.tx);
		UTXO multitemp1 = null;
		UTXO systemcoin1 = null;
		for (UTXO utxo : getBalance(false, keys)) {
			if (multitemp1 == null && !Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
				multitemp1 = utxo;
			}
			if (systemcoin1 == null && Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
				systemcoin1 = utxo;
			}
			log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
		}
		UTXO mymultitemp1 = null;
		UTXO mysystemcoin1 = null;
		for (UTXO utxo : getBalance()) {
			if (mymultitemp1 == null && Arrays.equals(utxo.getTokenidBuf(), multitemp.getTokenidBuf())) {
				mymultitemp1 = utxo;
			}
			if (mysystemcoin1 == null && Arrays.equals(utxo.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
				mysystemcoin1 = utxo;
			}
			log.debug(utxo.getValue().getValue() + "," + utxo.getTokenId() + "," + utxo.getAddress());
		}
		assertEquals(multitemp.getValue().getValue().longValue() - 10000, multitemp1.getValue().getValue());
		assertEquals(1000, systemcoin1.getValue().getValue());
		assertEquals(10000, mymultitemp1.getValue().getValue());
		assertEquals(mysystemcoin.getValue().getValue().longValue() - 1000, mysystemcoin1.getValue().getValue());
	}

	public void exchangeTokenComplete(Transaction tx) throws Exception {
		// get new Block to be used from server
		HashMap<String, String> requestParam = new HashMap<String, String>();
		byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
				Json.jsonmapper().writeValueAsString(requestParam));
		Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
		rollingBlock.addTransaction(tx);
		rollingBlock.solve();

		byte[] res = OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
		log.debug(res.toString());
	}

	// Pay BIG
	public void payToken(ECKey outKey, Wallet wallet) throws Exception {
		payToken(100, outKey, NetworkParameters.BIGTANGLE_TOKENID, wallet);
	}

	public void payToken(int amount, ECKey outKey, byte[] tokenbuf, Wallet wallet) throws Exception {
		HashMap<String, String> requestParam = new HashMap<String, String>();
		byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
				Json.jsonmapper().writeValueAsString(requestParam));
		Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
		log.info("resp block, hex : " + Utils.HEX.encode(data));
		// get other tokenid from wallet
		UTXO utxo = null;
		List<UTXO> ulist = getBalance();

		for (UTXO u : ulist) {
			if (Arrays.equals(u.getTokenidBuf(), tokenbuf)) {
				utxo = u;
			}
		}
		log.debug(utxo.getValue().toString());
		// Coin baseCoin = utxo.getValue().subtract(Coin.parseCoin("10000",
		// utxo.getValue().getTokenid()));
		// log.debug(baseCoin);
		Address destination = outKey.toAddress(networkParameters);

		Coin coinbase = Coin.valueOf(amount, utxo.getValue().getTokenid());
		wallet.pay(null, destination.toString(), coinbase, "");

		log.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
		makeRewardBlock();

		checkBalance(coinbase, wallet.walletKeys(null));
	}

	@Test
	public void createTransaction() throws Exception {
		 

		Address destination = Address.fromBase58(networkParameters, "1NWN57peHapmeNq1ndDeJnjwPmC56Z6x8j");

		Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);

		List<Block> rollingBlock= wallet.pay(null, destination.toString(), amount, "");

		log.info("req block, hex : " + rollingBlock.get(0));

		getBalance();

		// log.info("transaction, tokens : " +
		// Json.jsonmapper().writeValueAsString(transaction.getTokenInfo()));

	}

}
