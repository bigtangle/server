package net.bigtangle.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderMatchTest extends AbstractIntegrationTest {

    @Test
    public void buy() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        showOrders();
        
        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        showOrders();
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        showOrders();
        
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance (adding the mining reward)
        origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                        + NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void sell() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
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
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void multiLevelBuy() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
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
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void multiLevelSell() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
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
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialBuy() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
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
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialSell() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
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
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialBidFill() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);

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
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialAskFill() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);

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
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void cancel() throws Exception {
        @SuppressWarnings({ "deprecation", "unused" })
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Cancel
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance (adding the mining reward)
        origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                        + NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void cancelTwoStep() throws Exception {
        @SuppressWarnings({ "deprecation", "unused" })
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance (adding the mining reward)
        origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                        + NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Cancel
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        // TODO check result
        
        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialCancel() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);

        // Cancel sell
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify some tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 50000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 50l);

        // Verify token amount invariance (adding the mining reward)
        origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                        + NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void ineffectiveCancel() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Cancel sell
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify all tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance (adding the mining reward)
        origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                        + NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testValidToTime() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
     //   addedBlocks.add(walletAppKit.wallet().makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000l, 100l, null));
        addedBlocks.add(walletAppKit.wallet().makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000l, 100l, System.currentTimeMillis() - 10000,null));
     //   addedBlocks.add(walletAppKit.wallet().makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000l, 100l, System.currentTimeMillis() + 10000));
        
        // Cancel sell
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify all tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance (adding the mining reward)
        origTokenAmounts.put(NetworkParameters.BIGTANGLE_TOKENID_STRING,
                origTokenAmounts.get(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                        + NetworkParameters.REWARD_INITIAL_TX_REWARD * NetworkParameters.REWARD_HEIGHT_INTERVAL);
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    // public void payToken(ECKey outKey) throws Exception {
    // HashMap<String, String> requestParam = new HashMap<String, String>();
    // byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
    // Json.jsonmapper().writeValueAsString(requestParam));
    // Block rollingBlock =
    // networkParameters.getDefaultSerializer().makeBlock(data);
    // LOGGER.info("resp block, hex : " + Utils.HEX.encode(data));
    // UTXO utxo = null;
    // List<UTXO> ulist = testTransactionAndGetBalances();
    // for (UTXO u : ulist) {
    // if (!Arrays.equals(u.getTokenidBuf(),
    // NetworkParameters.BIGTANGLE_TOKENID)) {
    // utxo = u;
    // }
    // }
    // System.out.println(utxo.getValue());
    // Address destination = outKey.toAddress(networkParameters);
    // SendRequest request = SendRequest.to(destination, utxo.getValue());
    // walletAppKit.wallet().completeTx(request);
    // rollingBlock.addTransaction(request.tx);
    // rollingBlock.solve();
    // OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(),
    // rollingBlock.bitcoinSerialize());
    // LOGGER.info("req block, hex : " +
    // Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
    // }
    //
    // @SuppressWarnings("unchecked")
    // // need ordermatch @Test
    // public void exchangeOrder() throws Exception {
    // String marketURL = "http://localhost:8089/";
    //
    // // get token from wallet to spent
    // ECKey yourKey = walletAppKit1.wallet().walletKeys(null).get(0);
    //
    // payToken(yourKey);
    // List<ECKey> keys = new ArrayList<ECKey>();
    // keys.add(yourKey);
    // List<UTXO> utxos = testTransactionAndGetBalances(false, keys);
    // UTXO yourutxo = utxos.get(0);
    // List<UTXO> ulist = testTransactionAndGetBalances();
    // UTXO myutxo = null;
    // for (UTXO u : ulist) {
    // if (Arrays.equals(u.getTokenidBuf(),
    // NetworkParameters.BIGTANGLE_TOKENID)) {
    // myutxo = u;
    // }
    // }
    //
    // HashMap<String, Object> request = new HashMap<String, Object>();
    // request.put("address", yourutxo.getAddress());
    // request.put("tokenid", yourutxo.getTokenId());
    // request.put("type", 1);
    // request.put("price", 1000);
    // request.put("amount", 1000);
    // System.out.println("req : " + request);
    // // sell token order
    // String response = OkHttp3Util.post(marketURL +
    // OrdermatchReqCmd.saveOrder.name(),
    // Json.jsonmapper().writeValueAsString(request).getBytes());
    //
    // request.put("address", myutxo.getAddress());
    // request.put("tokenid", yourutxo.getTokenId());
    // request.put("type", 2);
    // request.put("price", 1000);
    // request.put("amount", 1000);
    // System.out.println("req : " + request);
    // // buy token order
    // response = OkHttp3Util.post(marketURL +
    // OrdermatchReqCmd.saveOrder.name(),
    // Json.jsonmapper().writeValueAsString(request).getBytes());
    //
    // Thread.sleep(10000);
    //
    // HashMap<String, Object> requestParam = new HashMap<String, Object>();
    // requestParam.put("address", myutxo.getAddress());
    // response = OkHttp3Util.post(marketURL +
    // OrdermatchReqCmd.getExchange.name(),
    // Json.jsonmapper().writeValueAsString(requestParam).getBytes());
    // final Map<String, Object> data = Json.jsonmapper().readValue(response,
    // Map.class);
    // List<Map<String, Object>> list = (List<Map<String, Object>>)
    // data.get("exchanges");
    // assertTrue(list.size() >= 1);
    // Map<String, Object> exchangemap = list.get(0);
    //
    // String serverURL = contextRoot;
    // String orderid = (String) exchangemap.get("orderid");
    //
    // PayOrder payOrder1 = new PayOrder(walletAppKit.wallet(), orderid,
    // serverURL, marketURL);
    // payOrder1.sign();
    //
    // PayOrder payOrder2 = new PayOrder(walletAppKit1.wallet(), orderid,
    // serverURL, marketURL);
    // payOrder2.sign();
    // }
}
