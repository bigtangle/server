/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.core.http.server.resp.TokenIndexResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

public abstract class AbstractIntegrationTest {

	// private static final String CONTEXT_ROOT_TEMPLATE =
	// "http://localhost:%s/";
	protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
	public String contextRoot = "https://bigtangle.org/";
	        //"http://localhost:8088";
	        //
	        //"https://bigtangle.org/";
	public List<ECKey> walletKeys;
	public List<ECKey> wallet1Keys;
	public List<ECKey> wallet2Keys;

	WalletAppKit walletAppKit;
	WalletAppKit walletAppKit1;
	WalletAppKit walletAppKit2;

	protected static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
	protected static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
	protected static ObjectMapper objectMapper = new ObjectMapper();

	NetworkParameters networkParameters = MainNetParams.get();

	boolean deleteWlalletFile =false;
	@Before
	public void setUp() throws Exception {
		  //  System.setProperty("https.proxyHost", "anwproxy.anwendungen.localnet.de");
		  //  System.setProperty("https.proxyPort", "3128");
		walletKeys();
		wallet1();
		wallet2();
		// emptyBlocks(10);
	}

	protected Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
			List<Block> addedBlocks) throws Exception {
		Thread.sleep(30000);
		Block block = walletAppKit.wallet().sellOrder(null,  tokenId, sellPrice, sellAmount,
				null, null);
		addedBlocks.add(block);
		return block;

	}

	protected Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
			List<Block> addedBlocks) throws Exception {
		//Thread.sleep(100000);
		Block block = walletAppKit.wallet().buyOrder(null,  tokenId, buyPrice, buyAmount,
				null, null);
		addedBlocks.add(block);
		return block;

	}

	protected Block makeAndConfirmCancelOp(Block order, ECKey legitimatingKey, List<Block> addedBlocks)
			throws Exception {
		Block block = walletAppKit.wallet().cancelOrder(order.getHash(), legitimatingKey);
		addedBlocks.add(block);
		return block;
	}

	protected void assertHasAvailableToken(ECKey testKey, String tokenId_, Long amount) throws Exception {
		// Asserts that the given ECKey possesses the given amount of tokens
		List<UTXO> balance = getBalance(false, testKey);
		HashMap<String, Long> hashMap = new HashMap<>();
		for (UTXO o : balance) {
			String tokenId = Utils.HEX.encode(o.getValue().getTokenid());
			if (!hashMap.containsKey(tokenId))
				hashMap.put(tokenId, 0L);
			hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().getValue());
		}

		assertEquals(amount, hashMap.get(tokenId_));
	}

	protected Sha256Hash getRandomSha256Hash() {
		byte[] rawHashBytes = new byte[32];
		new Random().nextBytes(rawHashBytes);
		Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
		return sha256Hash;
	}

	protected Transaction createTestGenesisTransaction() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		List<UTXO> outputs = getBalance(false, genesiskey);
		TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
				0);
		Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
		Transaction tx = new Transaction(networkParameters);
		tx.addOutput(new TransactionOutput(networkParameters, tx, amount, genesiskey));
		if (spendableOutput.getValue().subtract(amount).getValue() != 0)
			tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount),
					genesiskey));
		TransactionInput input = tx.addInput(spendableOutput);
		Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

		TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
				false);
		Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
		input.setScriptSig(inputScript);
		return tx;
	}

	protected void walletKeys() throws Exception {
		KeyParameter aesKey = null;
		File f = new File("./logs/", "bigtangle");
		if (f.exists() & deleteWlalletFile)
			f.delete();
		walletAppKit = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle");
		walletAppKit.wallet().importKey(new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub)));
		// add ge
		walletAppKit.wallet().setServerURL(contextRoot);
		walletKeys = walletAppKit.wallet().walletKeys(aesKey);
	}

	protected void wallet1() throws Exception {
		KeyParameter aesKey = null;
		// delete first
		File f = new File("./logs/", "bigtangle1");
		if (f.exists() & deleteWlalletFile)
			f.delete();
		walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle1");
		walletAppKit1.wallet().setServerURL(contextRoot);

		wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);
	}

	protected void wallet2() throws Exception {
		KeyParameter aesKey = null;
		// delete first
		File f = new File("./logs/", "bigtangle2");
		if (f.exists() & deleteWlalletFile)
			f.delete();
		walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle2");
		walletAppKit2.wallet().setServerURL(contextRoot);

		wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);
	}

	protected List<UTXO> getBalance() throws Exception {
		return getBalance(false);
	}

	// get balance for the walletKeys
	protected List<UTXO> getBalance(boolean withZero) throws Exception {
		return getBalance(withZero, walletKeys);
	}

	protected UTXO getBalance(String tokenid, boolean withZero, List<ECKey> keys) throws Exception {
		List<UTXO> ulist = getBalance(withZero, keys);

		for (UTXO u : ulist) {
			if (tokenid.equals(u.getTokenId())) {
				return u;
			}
		}

		throw new RuntimeException();
	}

	// get balance for the walletKeys
	protected List<UTXO> getBalance(boolean withZero, List<ECKey> keys) throws Exception {
		List<UTXO> listUTXO = new ArrayList<UTXO>();
		List<String> keyStrHex000 = new ArrayList<String>();

		for (ECKey ecKey : keys) {
			// keyStrHex000.add(ecKey.toAddress(networkParameters).toString());
			keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
		}
		String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
				Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

		GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

		// String response = mvcResult.getResponse().getContentAsString();
		for (UTXO utxo : getBalancesResponse.getOutputs()) {
			if (withZero) {
				listUTXO.add(utxo);
			} else if (utxo.getValue().getValue() > 0) {
				listUTXO.add(utxo);
			}
		}

		return listUTXO;
	}

	protected List<UTXO> getBalance(boolean withZero, ECKey ecKey) throws Exception {
		List<ECKey> keys = new ArrayList<ECKey>();
		keys.add(ecKey);
		return getBalance(withZero, keys);
	}

	protected void testInitWallet() throws Exception {

		// testCreateMultiSig();
		testCreateMarket();
		testInitTransferWallet();
		// testInitTransferWalletPayToTestPub();
		List<UTXO> ux = getBalance();
		// assertTrue(!ux.isEmpty());
		for (UTXO u : ux) {
			log.debug(u.toString());
		}

	}

	// transfer the coin from protected testPub to address in wallet
	@SuppressWarnings("deprecation")
	protected void testInitTransferWallet() throws Exception {
		ECKey fromkey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
		giveMoneyResult.put(walletKeys.get(1).toAddress(networkParameters).toString(), 3333333l);
		walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
	}

	   protected void payToWallet( Wallet wallet) throws Exception {
	        ECKey fromkey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
	        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
	        giveMoneyResult.put(wallet.walletKeys().get(1).toAddress(networkParameters).toString(), 3333333l);
	        wallet.payMoneyToECKeyList(null, giveMoneyResult, fromkey);
	    }

	   
	protected void testCreateToken() throws JsonProcessingException, Exception {
		ECKey outKey = walletKeys.get(0);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();

		String tokenid = Utils.HEX.encode(pubKey);

		Coin basecoin = Coin.valueOf(77777L, pubKey);
		long amount = basecoin.getValue();

		Token tokens = Token.buildSimpleTokenInfo(true, "", tokenid, "test", "", 1, 0, amount, true);
		tokenInfo.setToken(tokens);

		// add MultiSignAddress item
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
	}

	protected void testCreateMarket() throws JsonProcessingException, Exception {
		ECKey outKey = walletKeys.get(1);
		byte[] pubKey = outKey.getPubKey();
		TokenInfo tokenInfo = new TokenInfo();

		String tokenid = Utils.HEX.encode(pubKey);
		Token tokens = Token.buildMarketTokenInfo(true, "", tokenid, "p2p", "", "https://market,bigtangle.net");
		tokenInfo.setToken(tokens);

		// add MultiSignAddress item
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

		Coin basecoin = Coin.valueOf(0, pubKey);
		walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
	}

	protected void checkResponse(String resp) throws JsonParseException, JsonMappingException, IOException {
		checkResponse(resp, 0);
	}

	protected void checkResponse(String resp, int code) throws JsonParseException, JsonMappingException, IOException {
		@SuppressWarnings("unchecked")
		HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
		int error = (Integer) result2.get("errorcode");
		assertTrue(error == code);
	}

	protected void checkBalance(Coin coin, ECKey ecKey) throws Exception {
		ArrayList<ECKey> a = new ArrayList<ECKey>();
		a.add(ecKey);
		checkBalance(coin, a);
	}

	protected void checkBalance(Coin coin, List<ECKey> a) throws Exception {

		List<UTXO> ulist = getBalance(false, a);
		UTXO myutxo = null;
		for (UTXO u : ulist) {
			if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue() == u.getValue().getValue()) {
				myutxo = u;
				break;
			}
		}
		assertTrue(myutxo != null);
		assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
		log.debug(myutxo.toString());
	}

	// create a token with multi sign
	protected void testCreateMultiSigToken(ECKey key, String tokename)
			throws JsonProcessingException, Exception {
		try {
		createMultisignToken(key, new TokenInfo(), tokename,678900000);
		}catch (Exception e) {
			// TODO: handle exception
			log.warn("",e);
		}

	}

	protected void createMultisignToken(ECKey key, TokenInfo tokenInfo, String tokename, int amount)
			throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
		String tokenid = key.getPublicKeyAsHex();

		Coin basecoin = Coin.valueOf(amount, tokenid);

		HashMap<String, String> requestParam00 = new HashMap<String, String>();
		requestParam00.put("tokenid", tokenid);
		String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
				Json.jsonmapper().writeValueAsString(requestParam00));

		TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
		long tokenindex_ = tokenIndexResponse.getTokenindex();
		String prevblockhash = tokenIndexResponse.getBlockhash();

		Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, tokename, tokename, 1, tokenindex_,
				amount,  false);
		tokenInfo.setToken(tokens);

		tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));

		walletAppKit.wallet().saveToken(tokenInfo, basecoin, key, null);

	}

	public Block resetAndMakeTestToken(ECKey testKey, List<Block> addedBlocks)
			throws JsonProcessingException, Exception, BlockStoreException {

		Block block = null;
		TokenInfo tokenInfo = new TokenInfo();

		Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
		long amount = coinbase.getValue();
		Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test", 1, 0,
				amount, true);

		tokenInfo.setToken(tokens);
		tokenInfo.getMultiSignAddresses()
				.add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

		// TODO This (saveBlock) calls milestoneUpdate currently
		block = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
		//wait to confirm of token
		//Thread.sleep(100000);
		return block;
	}

	public void emptyBlocks(int number) throws JsonProcessingException, Exception {
		for (int i = 0; i < number; i++) {
			HashMap<String, String> requestParam = new HashMap<String, String>();
			byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
					Json.jsonmapper().writeValueAsString(requestParam));

			Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
			rollingBlock.solve();

			OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
		}
	}
	
	 
    
}
