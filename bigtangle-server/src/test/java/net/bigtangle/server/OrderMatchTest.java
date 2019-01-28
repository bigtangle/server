package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderMatchTest extends AbstractIntegrationTest {

	private Block setupStartEnvironment(ECKey testKey, List<Block> addedBlocks) throws JsonProcessingException, Exception, BlockStoreException {
    	store.resetStore();
		
		// Make the "test" token
		Block block = null;
		{
	        TokenInfo tokenInfo = new TokenInfo();
	        
	        Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
	        long amount = coinbase.getValue();
	        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test", 1, 0,
	                amount, false, true);
	
	        tokenInfo.setTokens(tokens);
	        tokenInfo.getMultiSignAddresses()
	                .add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));
	
	        // This (saveBlock) calls milestoneUpdate currently
	        block = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
	        addedBlocks.add(block);
	        blockGraph.confirm(block.getHash(), new HashSet<>());
		}
		return block;
	}

	private Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount, List<Block> addedBlocks) throws Exception {
		Block block = null;
		Transaction tx = new Transaction(networkParameters);
		OrderOpenInfo info = new OrderOpenInfo(sellPrice * sellAmount, NetworkParameters.BIGTANGLE_TOKENID_STRING, beneficiary.getPubKey());
		tx.setData(info.toByteArray());
		
        // Create burning 2 "test"
		Coin amount = Coin.valueOf(sellAmount, tokenId);
		List<UTXO> outputs = getBalance(false, beneficiary).stream()
				.filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(tokenId))
				.filter(out -> out.getValue().getValue() >= amount.getValue())
				.collect(Collectors.toList());
		TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
		        0);
		// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx, amount, testKey));
		tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), beneficiary));
		TransactionInput input = tx.addInput(spendableOutput);
		Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
		
		TransactionSignature sig = new TransactionSignature(beneficiary.sign(sighash), Transaction.SigHash.ALL,
		        false);
		Script inputScript = ScriptBuilder.createInputScript(sig);
		input.setScriptSig(inputScript);

        // Create block with order
		Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
		block = predecessor.createNextBlock();
		block.addTransaction(tx);
		block.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
		block.solve();
		this.blockGraph.add(block, true);
        addedBlocks.add(block);
		this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());
		return block;
	}

	private Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount, List<Block> addedBlocks) throws Exception {
		Block block = null;
		Transaction tx = new Transaction(networkParameters);
		OrderOpenInfo info = new OrderOpenInfo(buyAmount, tokenId, beneficiary.getPubKey());
		tx.setData(info.toByteArray());
        
        // Create burning BIG
		Coin amount = Coin.valueOf(buyAmount * buyPrice, NetworkParameters.BIGTANGLE_TOKENID);
		List<UTXO> outputs = getBalance(false, beneficiary).stream()
				.filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID)))
				.filter(out -> out.getValue().getValue() >= amount.getValue())
				.collect(Collectors.toList());
		TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
		        0);
		// BURN: tx.addOutput(new TransactionOutput(networkParameters, tx, amount, testKey));
		tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), beneficiary));
		TransactionInput input = tx.addInput(spendableOutput);
		Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
		
		TransactionSignature sig = new TransactionSignature(beneficiary.sign(sighash), Transaction.SigHash.ALL,
		        false);
		Script inputScript = ScriptBuilder.createInputScript(sig);
		input.setScriptSig(inputScript);

        // Create block with order
		Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
		block = predecessor.createNextBlock();
		block.addTransaction(tx);
		block.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
		block.solve();
		this.blockGraph.add(block, true);
        addedBlocks.add(block);
		this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());
		return block;
	}	

	private Block makeAndConfirmOrderMatching(List<Block> addedBlocks) throws Exception, BlockStoreException {
		// Generate blocks until passing first reward interval
		Block tip = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
	    Block rollingBlock = tip;
	    for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
	        rollingBlock = rollingBlock.createNextBlock(rollingBlock);
	        blockGraph.add(rollingBlock, true);
	        addedBlocks.add(rollingBlock);
	    }
	    
	    // Generate mining reward block
	    Block rewardBlock = transactionService.createAndAddMiningRewardBlock(store.getMaxConfirmedRewardBlockHash(),
	            rollingBlock.getHash(), rollingBlock.getHash());
        addedBlocks.add(rewardBlock);
	    
	    // Confirm
	    blockGraph.confirm(rewardBlock.getHash(), new HashSet<>());
		return rewardBlock;
	}
	
	private void assertCurrentTokenAmountEquals(HashMap<String, Long> origTokenAmounts) throws BlockStoreException {
		HashMap<String, Long> currTokenAmounts = getCurrentTokenAmounts();
		for (Entry<String, Long> origTokenAmount : origTokenAmounts.entrySet()) {
			assertTrue(currTokenAmounts.containsKey(origTokenAmount.getKey()));
			assertEquals(currTokenAmounts.get(origTokenAmount.getKey()), origTokenAmount.getValue());
		}
		for (Entry<String, Long> currTokenAmount : currTokenAmounts.entrySet()) {
			assertTrue(origTokenAmounts.containsKey(currTokenAmount.getKey()));
			assertEquals(origTokenAmounts.get(currTokenAmount.getKey()), currTokenAmount.getValue());
		}
	}

	private void assertHasAvailableToken(ECKey testKey, String tokenId_, Long amount) throws Exception {
		List<UTXO> balance = getBalance(false, testKey);
		HashMap<String, Long> hashMap = new HashMap<>();
		for (UTXO o : balance) {
			String tokenId = Utils.HEX.encode(o.getValue().tokenid);
			if (!hashMap.containsKey(tokenId))
				hashMap.put(tokenId, 0L);
			hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().value);
		}
		
		assertEquals(amount, hashMap.get(tokenId_));
	}

	private HashMap<String, Long> getCurrentTokenAmounts() throws BlockStoreException {
		HashMap<String, Long> hashMap = new HashMap<>();
		addCurrentUTXOTokens(hashMap);
		addCurrentOrderTokens(hashMap);
		return hashMap;
	}

	private void addCurrentOrderTokens(HashMap<String, Long> hashMap) throws BlockStoreException {
		List<OrderRecord> orders = store.getAllAvailableOrdersSorted();
		for (OrderRecord o : orders) {
			String tokenId = o.getOfferTokenid();
			if (!hashMap.containsKey(tokenId))
				hashMap.put(tokenId, 0L);
			hashMap.put(tokenId, hashMap.get(tokenId) + o.getOfferValue());
		}
	}

	private void addCurrentUTXOTokens(HashMap<String, Long> hashMap) throws BlockStoreException {
		List<UTXO> utxos = store.getAllAvailableUTXOsSorted();
		for (UTXO o : utxos) {
			String tokenId = Utils.HEX.encode(o.getValue().tokenid);
			if (!hashMap.containsKey(tokenId))
				hashMap.put(tokenId, 0L);
			hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().value);
		}
	}

	private void redoAndAssertDeterministicExecution(List<Block> addedBlocks) throws BlockStoreException {
		List<OrderRecord> allOrdersSorted = store.getAllOrdersSorted();
		List<UTXO> allUTXOsSorted = store.getAllUTXOsSorted();
		store.resetStore();
		for (Block b : addedBlocks) {
			blockGraph.add(b, false);
		    blockGraph.confirm(b.getHash(), new HashSet<>());
		}
		List<OrderRecord> allOrdersSorted2 = store.getAllOrdersSorted();
		List<UTXO> allUTXOsSorted2 = store.getAllUTXOsSorted();
		assertEquals(allOrdersSorted2.toString(), allOrdersSorted.toString()); // Works for now
		assertEquals(allUTXOsSorted2.toString(), allUTXOsSorted.toString());
	}
	
	// TODO prev order determinism

    @Test
    public void buy() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = outKey;
		List<Block> addedBlocks = new ArrayList<>();
		
		// Make test token
		setupStartEnvironment(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();
		
		// Get current existing token amount
		HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
	
		// Open sell order for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
	
		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
	
		// Execute order matching
		makeAndConfirmOrderMatching(addedBlocks);
		
		// Verify the tokens changed possession
		assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
		assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);
		
		// Verify token amount invariance (adding the mining reward)
		origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING, 
				origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) 
				+ NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
		assertCurrentTokenAmountEquals(origTokenAmounts);
	
		// Verify deterministic overall execution
		redoAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void sell() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = outKey;
		List<Block> addedBlocks = new ArrayList<>();
		
		// Make test token
		setupStartEnvironment(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();
		
		// Get current existing token amount
		HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
	
		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
	
		// Open sell order for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
	
		// Execute order matching
		makeAndConfirmOrderMatching(addedBlocks);
		
		// Verify the tokens changed possession
		assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
		assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);
		
		// Verify token amount invariance (adding the mining reward)
		origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING, 
				origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) 
				+ NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
		assertCurrentTokenAmountEquals(origTokenAmounts);
	
		// Verify deterministic overall execution
		redoAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void multiLevelBuy() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = outKey;
		List<Block> addedBlocks = new ArrayList<>();
		
		// Make test token
		setupStartEnvironment(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();
	
		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
		makeAndConfirmSellOrder(testKey, testTokenId, 1001, 100, addedBlocks);
		makeAndConfirmSellOrder(testKey, testTokenId, 999, 50, addedBlocks);
	
		// Execute order matching
		makeAndConfirmOrderMatching(addedBlocks);
	
		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
	
		// Execute order matching
		makeAndConfirmOrderMatching(addedBlocks);
		
		// Verify the tokens changed possession
		assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 99950l);
		assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);
	
		// Verify deterministic overall execution
		redoAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void multiLevelSell() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = outKey;
		List<Block> addedBlocks = new ArrayList<>();
		
		// Make test token
		setupStartEnvironment(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();
	
		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 999, 100, addedBlocks);
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 50, addedBlocks);
	
		// Execute order matching
		makeAndConfirmOrderMatching(addedBlocks);
	
		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
	
		// Execute order matching
		makeAndConfirmOrderMatching(addedBlocks);
		
		// Verify the tokens changed possession
		assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100050l);
		assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);
	
		// Verify deterministic overall execution
		redoAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialBuy() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = outKey;
		List<Block> addedBlocks = new ArrayList<>();
		
		// Make test token
		setupStartEnvironment(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();
		
		// Get current existing token amount
		HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
	
		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
	
		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
	
		// Execute order matching
		makeAndConfirmOrderMatching(addedBlocks);
		
		// Verify the tokens changed possession
		assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 50000l);
		assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 50l);
		
		// Verify token amount invariance (adding the mining reward)
		origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING, 
				origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) 
				+ NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
		assertCurrentTokenAmountEquals(origTokenAmounts);
	
		// Verify deterministic overall execution
		redoAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialSell() throws Exception {
		@SuppressWarnings("deprecation")
		ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
		ECKey testKey = outKey;
		List<Block> addedBlocks = new ArrayList<>();
		
		// Make test token
		setupStartEnvironment(testKey, addedBlocks);
		String testTokenId = testKey.getPublicKeyAsHex();
		
		// Get current existing token amount
		HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
	
		// Open buy order for test tokens
		makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
	
		// Open sell orders for test tokens
		makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
	
		// Execute order matching
		makeAndConfirmOrderMatching(addedBlocks);
		
		// Verify the tokens changed possession
		assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 50000l);
		assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 50l);
		
		// Verify token amount invariance (adding the mining reward)
		origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING, 
				origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING) 
				+ NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
		assertCurrentTokenAmountEquals(origTokenAmounts);
	
		// Verify deterministic overall execution
		redoAndAssertDeterministicExecution(addedBlocks);
    }

//	public void payToken(ECKey outKey) throws Exception {
//        HashMap<String, String> requestParam = new HashMap<String, String>();
//        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
//                Json.jsonmapper().writeValueAsString(requestParam));
//        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
//        LOGGER.info("resp block, hex : " + Utils.HEX.encode(data));
//        UTXO utxo = null;
//        List<UTXO> ulist = testTransactionAndGetBalances();
//        for (UTXO u : ulist) {
//            if (!Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
//                utxo = u;
//            }
//        }
//        System.out.println(utxo.getValue());
//        Address destination = outKey.toAddress(networkParameters);
//        SendRequest request = SendRequest.to(destination, utxo.getValue());
//        walletAppKit.wallet().completeTx(request);
//        rollingBlock.addTransaction(request.tx);
//        rollingBlock.solve();
//        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
//        LOGGER.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
//    }
//
//	@SuppressWarnings("unchecked")
//   // need ordermatch  @Test
//    public void exchangeOrder() throws Exception {
//        String marketURL = "http://localhost:8089/";
//        
//        // get token from wallet to spent
//        ECKey yourKey = walletAppKit1.wallet().walletKeys(null).get(0);
//
//        payToken(yourKey);
//        List<ECKey> keys = new ArrayList<ECKey>();
//        keys.add(yourKey);
//        List<UTXO> utxos = testTransactionAndGetBalances(false, keys);
//        UTXO yourutxo = utxos.get(0);
//        List<UTXO> ulist = testTransactionAndGetBalances();
//        UTXO myutxo = null;
//        for (UTXO u : ulist) {
//            if (Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
//                myutxo = u;
//            }
//        }
//
//        HashMap<String, Object> request = new HashMap<String, Object>();
//        request.put("address", yourutxo.getAddress());
//        request.put("tokenid", yourutxo.getTokenId());
//        request.put("type", 1);
//        request.put("price", 1000);
//        request.put("amount", 1000);
//        System.out.println("req : " + request);
//        // sell token order
//        String response = OkHttp3Util.post(marketURL + OrdermatchReqCmd.saveOrder.name(),
//                Json.jsonmapper().writeValueAsString(request).getBytes());
//        
//        request.put("address", myutxo.getAddress());
//        request.put("tokenid", yourutxo.getTokenId());
//        request.put("type", 2);
//        request.put("price", 1000);
//        request.put("amount", 1000);
//        System.out.println("req : " + request);
//        // buy token order
//          response = OkHttp3Util.post(marketURL + OrdermatchReqCmd.saveOrder.name(),
//                Json.jsonmapper().writeValueAsString(request).getBytes());
//
//        Thread.sleep(10000);
//
//        HashMap<String, Object> requestParam = new HashMap<String, Object>();
//        requestParam.put("address", myutxo.getAddress());
//          response = OkHttp3Util.post(marketURL + OrdermatchReqCmd.getExchange.name(),
//                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
//        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
//        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("exchanges");
//        assertTrue(list.size() >= 1);
//        Map<String, Object> exchangemap = list.get(0);
//        
//        String serverURL = contextRoot;
//        String orderid = (String) exchangemap.get("orderid");
//        
//        PayOrder payOrder1 = new PayOrder(walletAppKit.wallet(), orderid, serverURL, marketURL);
//        payOrder1.sign();
//        
//        PayOrder payOrder2 = new PayOrder(walletAppKit1.wallet(), orderid, serverURL, marketURL);
//        payOrder2.sign();
//    }
}

