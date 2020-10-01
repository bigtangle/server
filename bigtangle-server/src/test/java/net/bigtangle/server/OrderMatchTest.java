package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.core.response.OrderTickerResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.OrderTickerService;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.OrderUtil;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderMatchTest extends AbstractIntegrationTest {

    @Autowired
    OrderTickerService tickerService;

    @Test
    public void orderTickerPrice() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
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

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);
        mcmcServiceUpdate();
        // Verify the order ticker has the correct price
        HashSet<String> a = new HashSet<String>();
        a.add(testTokenId);
        assertEquals(1000l, tickerService.getLastMatchingEvents(a, store).getTickers().get(0).getPrice());

        // Verify deterministic overall execution

        // check the method of client service

    }

    @Test
    public void orderTickerSearchAPI() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 99, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 22, addedBlocks);

        makeAndConfirmSellOrder(testKey, testTokenId, 1002, 100, addedBlocks);
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // get the datat
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        List<String> tokenids = new ArrayList<String>();

        requestParam.put("tokenids", tokenids);
        String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrdersTicker.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderTickerResponse orderTickerResponse = Json.jsonmapper().readValue(response0, OrderTickerResponse.class);

        assertTrue(orderTickerResponse.getTickers().size() > 0);
        for (MatchResult m : orderTickerResponse.getTickers()) {
            assertTrue(m.getTokenid().equals(testTokenId));
            // assertTrue(m.getExecutedQuantity() == 78||
            // m.getExecutedQuantity() == 22);
            // TODO check the execute ordering. price is 1000 or 1001
            assertTrue(m.getPrice() == 1000 || m.getPrice() == 1001);
        }
    }

    @Test
    public void orderTickerSearch() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        Block s1 = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        Block s2 = makeAndConfirmSellOrder(testKey, testTokenId, 2000, 100, addedBlocks);

        // Open buy order for test tokens
        Block b1 = makeAndConfirmBuyOrder(genesisKey, testTokenId, 100, 100, addedBlocks);
        Block b2 = makeAndConfirmBuyOrder(genesisKey, testTokenId, 10, 100, addedBlocks);
        mcmcServiceUpdate();
        blockGraph.updateChain();
        makeAndConfirmOrderMatching(addedBlocks);
        blockGraph.updateChain();
        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify the best orders are correct

        makeAndConfirmOrderMatching(addedBlocks);
        rewardService.createReward(store);
        blockGraph.updateChain();
        List<OrderRecord> bestOpenSellOrders = tickerService.getBestOpenSellOrders(testTokenId, 2, store);
        assertEquals(2, bestOpenSellOrders.size());
        assertEquals(s1.getHash(), bestOpenSellOrders.get(0).getBlockHash());
        assertEquals(s2.getHash(), bestOpenSellOrders.get(1).getBlockHash());
        List<OrderRecord> bestOpenBuyOrders = tickerService.getBestOpenBuyOrders(testTokenId, 2, store);

        assertEquals(2, bestOpenBuyOrders.size());
        assertEquals(b1.getHash(), bestOpenBuyOrders.get(0).getBlockHash());
        assertEquals(b2.getHash(), bestOpenBuyOrders.get(1).getBlockHash());

        List<OrderRecord> bestOpenSellOrders2 = tickerService.getBestOpenSellOrders(testTokenId, 1, store);
        assertEquals(1, bestOpenSellOrders2.size());
        assertEquals(s1.getHash(), bestOpenSellOrders2.get(0).getBlockHash());
        List<OrderRecord> bestOpenBuyOrders2 = tickerService.getBestOpenBuyOrders(testTokenId, 1, store);
        assertEquals(1, bestOpenBuyOrders2.size());
        assertEquals(b1.getHash(), bestOpenBuyOrders2.get(0).getBlockHash());

        // Verify deterministic overall execution

    }

    @Test
    public void buy() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        checkOrders(1);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        checkOrders(2);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void buyBaseToken() throws Exception {
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));

        long tokennumber = 888888 * 1000;
        makeTestToken(yuan, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
        int  priceshift=1000000;
        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);
        makeAndConfirmOrderMatching(addedBlocks);
        // Open buy order for test tokens
        makeAndConfirmBuyOrder(yuan, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(2);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
 
     
      
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), 2l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }
    @Test
    public void buyBaseTokenSmall() throws Exception {
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));
        int  priceshift=1000000;
        long tokennumber = priceshift * 1000;
        makeTestToken(yuan, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        // Make test token 
        makeTestToken(testKey, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
     
        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 200, priceshift, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);
        makeAndConfirmOrderMatching(addedBlocks);
        // Open buy order for test tokens
        makeAndConfirmBuyOrder(yuan, testTokenId, 200, priceshift, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(2);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
 
     
      
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), priceshift*1l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void buyBaseTokenSmallRemainder() throws Exception {
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));
        int  priceshift=1000000;
        long tokennumber = priceshift * 1000;
        makeTestToken(yuan, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        // Make test token 
        makeTestToken(testKey, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
     
        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 200, priceshift, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);
        makeAndConfirmOrderMatching(addedBlocks);
        // Open buy order for test tokens, the  100 can not be executed
        makeAndConfirmBuyOrder(yuan, testTokenId, 200, priceshift + 100, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(2);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        checkOrders(1);
     
      
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), priceshift*1l);

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 200, priceshift, yuan.getPublicKeyAsHex(), addedBlocks);
        
        makeAndConfirmOrderMatching(addedBlocks);
        checkOrders(1);
      
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), priceshift*1l);

        
        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        
        
        // Verify deterministic overall execution

    }
    @Test
    public void buyBaseTokenMixed() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();
      int  priceshift=1000000;
        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));
        resetAndMakeTestToken(yuan, addedBlocks);
        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens, orderbase yuan
        makeAndConfirmSellOrder(testKey, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);
        makeAndConfirmOrderMatching(addedBlocks);
        // Open buy order for test tokens,orderbase yuan
        makeAndConfirmBuyOrder(yuan, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);

        // Open sell order for test tokens orderbase BIG
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens, orderbase BIG
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        List<Map<String, Object>> orderData = new ArrayList<Map<String, Object>>();
        OrderUtil.orderMap(orderdataResponse, orderData, Locale.getDefault(),networkParameters);
        assertTrue(orderData.size() == 4);
        for (Map<String, Object> map : orderData) {
            assertTrue(map.get("price").equals("0.001") || map.get("price").equals("1"));
        }

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the order ticker has the correct price
        HashSet<String> a = new HashSet<String>();
        a.add(testTokenId);
        List<MatchResult> tickers = tickerService.getLastMatchingEvents(a, store).getTickers();
        assertEquals(tickers.size(), 2);
        assertTrue(1000l == tickers.get(0).getPrice() 
                || priceshift == tickers.get(0).getPrice());
        
        // Verify the tokens changed possession
 
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), 2l);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);



    }

    @Test
    public void sell() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
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

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void multiLevelBuy() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

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

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void multiLevelSell() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 999, 100, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 50, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        mcmcServiceUpdate();

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100050l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void partialBuy() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
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

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void partialSell() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
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
        sendEmpty(10);
        mcmcServiceUpdate();
        blockGraph.updateChain();
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 50000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 50l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void partialBidFill() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
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
        sendEmpty(10);
        mcmcServiceUpdate();
        blockGraph.updateChain();

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void partialAskFill() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
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

        mcmcServiceUpdate();
        sendEmpty(10);
        mcmcServiceUpdate();
        blockGraph.updateChain();
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify deterministic overall execution

    }

    @Test
    public void cancel() throws Exception {

        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
        // Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
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

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void cancelTwoStep() throws Exception {

        // ECKey genesisKey =
        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
        // Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
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

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Cancel
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify deterministic overall execution

    }

    @Test
    public void effectiveCancel() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        Block buy = makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Cancel all
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);
        makeAndConfirmCancelOp(buy, genesisKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify all tokens did not change possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 0l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 0l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void testValidFromTime() throws Exception {
        final int waitTime = 5000;

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();
        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens with timeout
        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        long sellAmount = (long) 100;
        Block block = null;
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo((long) 1000 * sellAmount, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                testKey.getPubKey(), null, System.currentTimeMillis() + waitTime, Side.SELL,
                testKey.toAddress(networkParameters).toBase58(), NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l, sellAmount,
                testTokenId);
        tx.setData(info.toByteArray());
        tx.setDataClassName("OrderOpen");

        // Burn tokens to sell
        Coin amount = Coin.valueOf(sellAmount, testTokenId);
        List<UTXO> outputs = getBalance(false, testKey).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(testTokenId))
                .filter(out -> out.getValue().getValue().compareTo(amount.getValue()) > 0).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with order
        block = predecessor.createNextBlock(predecessor);
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block = adjustSolve(block);
        this.blockGraph.add(block, true, store);
        addedBlocks.add(block);
        mcmcServiceUpdate();
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>(), (long) -1, store);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        Block matcherBlock1 = makeAndConfirmOrderMatching(addedBlocks);

        // Verify the order is still open
        // NOTE: Can fail if the test takes longer than 5 seconds. In that case,
        // increase the wait time variable
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));
        assertFalse(store.getOrderSpent(block.getHash(), matcherBlock1.getHash()));

        // Wait until valid
        Thread.sleep(waitTime);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the order is now closed
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));
        assertTrue(store.getOrderSpent(block.getHash(), matcherBlock1.getHash()));

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void testValidToTime() throws Exception {
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens with timeout
        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        long sellAmount = (long) 100;
        Block block = null;
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo((long) 1000 * sellAmount, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                testKey.getPubKey(), System.currentTimeMillis() - 10000, null, Side.SELL,
                testKey.toAddress(networkParameters).toBase58(), NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l, sellAmount,
                testTokenId);
        tx.setData(info.toByteArray());
        tx.setDataClassName("OrderOpen");

        // Burn tokens to sell
        Coin amount = Coin.valueOf(sellAmount, testTokenId);
        List<UTXO> outputs = getBalance(false, testKey).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(testTokenId))
                .filter(out -> out.getValue().getValue().compareTo(amount.getValue()) > 0).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with order
        block = predecessor.createNextBlock(predecessor);
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block = adjustSolve(block);
        this.blockGraph.add(block, true, store);
        addedBlocks.add(block);
        mcmcServiceUpdate();
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>(), (long) -1, store);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify there is no open order left
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void testAllOrdersSpent() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        Block b1 = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        Block b2 = makeAndConfirmBuyOrder(genesisKey, testTokenId, 999, 50, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        makeAndConfirmOrderMatching(addedBlocks);
        makeAndConfirmOrderMatching(addedBlocks);

        // Cancel orders
        makeAndConfirmCancelOp(b1, testKey, addedBlocks);
        makeAndConfirmCancelOp(b2, genesisKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void testReorgMatching() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 225, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 75, addedBlocks);

        // Execute order matching and then unexecute it
        Block orderMatching = makeAndConfirmOrderMatching(addedBlocks);
        blockGraph.unconfirm(orderMatching.getHash(), new HashSet<>(), store);

        // Verify the tokens did not change possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 0l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 0l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);
    }

    @Test
    public void testMultiMatching1() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 225, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 75, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 450000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 450l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

    }

    @Test
    public void testMultiMatching3() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 456, 20, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 789, 3, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 987, 10, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 654, 8, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 321, 5, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 159, 2, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 951, 25, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 753, 12, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 357, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution

        // Bonus: check open and closed orders
        List<String> a = new ArrayList<String>();
        a.add(genesisKey.toAddress(networkParameters).toBase58());
        List<OrderRecord> closedOrders = store.getMyClosedOrders(a);
        List<OrderRecord> openOrders = store
                .getMyRemainingOpenOrders(genesisKey.toAddress(networkParameters).toBase58());
        List<OrderRecord> initialOrders = store
                .getMyInitialOpenOrders(genesisKey.toAddress(networkParameters).toBase58());

        System.out.println(closedOrders.toString());
        System.out.println(openOrders.toString());
        System.out.println(initialOrders.toString());
    }

    @Test
    public void testMultiMatching4() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 456, 20, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 789, 3, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 987, 10, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 654, 8, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 456, 20, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 789, 3, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 987, 10, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 654, 8, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        mcmcServiceUpdate();

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        blockGraph.updateChain();
        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    // test buy order with multiple inputs
    public void testBuy() throws Exception {
        File f3 = new File("./logs/", "bigtangle3.wallet");
        if (f3.exists()) {
            f3.delete();
        }

        File f4 = new File("./logs/", "bigtangle4.wallet");
        if (f4.exists()) {
            f4.delete();
        }

        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle3");
        walletAppKit1.wallet().setServerURL(contextRoot);
        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);

        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle4");
        walletAppKit2.wallet().setServerURL(contextRoot);
        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);

        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        long amountToken = 2 * 88l;
        // split token
        payTestToken(testKey, amountToken);
        mcmcServiceUpdate();

        long tradeAmount = 100l;
        long price = 1;
        Block block = walletAppKit2.wallet().sellOrder(null, testTokenId, price, tradeAmount, null, null,  
                NetworkParameters.BIGTANGLE_TOKENID_STRING);
        addedBlocks.add(block);
        mcmcServiceUpdate();
        // blockGraph.confirm(block.getHash(), new HashSet<>(), (long) -1,
        // store); // mcmcServiceUpdate();

        long amount = 77l;
        // split BIG
        payBig(amount);
        payBig(amount);
        checkBalanceSum(Coin.valueOf(2 * amount, NetworkParameters.BIGTANGLE_TOKENID), wallet1Keys);
        // Open buy order for test tokens
        block = walletAppKit1.wallet().buyOrder(null, testTokenId, price, tradeAmount, null, null);
        addedBlocks.add(block);
        mcmcServiceUpdate();
        blockGraph.confirm(block.getHash(), new HashSet<>(), (long) -1, store);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        showOrders();

        // Verify the tokens changed position
        checkBalanceSum(Coin.valueOf(tradeAmount * price, NetworkParameters.BIGTANGLE_TOKENID), wallet2Keys);

        checkBalanceSum(Coin.valueOf(amountToken - tradeAmount, testKey.getPubKey()), wallet2Keys);

        checkBalanceSum(Coin.valueOf(tradeAmount, testKey.getPubKey()), wallet1Keys);
        checkBalanceSum(Coin.valueOf(2 * amount - tradeAmount * price, NetworkParameters.BIGTANGLE_TOKENID),
                wallet1Keys);
    }

    private void payBig(long amount) throws JsonProcessingException, IOException, InsufficientMoneyException,
            InterruptedException, ExecutionException, BlockStoreException, UTXOProviderException {
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        giveMoneyResult.put(wallet1Keys.get(0).toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, "payBig");
        // log.debug("block " + (b == null ? "block is null" : b.toString()));
        mcmcServiceUpdate();

    }

    private void payTestToken(ECKey testKey, long amount)
            throws JsonProcessingException, IOException, InsufficientMoneyException, InterruptedException,
            ExecutionException, BlockStoreException, UTXOProviderException {

        HashMap<String, Long> giveMoneyTestToken = new HashMap<String, Long>();

        giveMoneyTestToken.put(wallet2Keys.get(0).toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyTestToken, testKey.getPubKey(), "", 3, 1000);
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        mcmcServiceUpdate();

        // Open sell order for test tokens
    }

    @Test
    public void chechDecimalFormat() throws Exception {

        ECKey dollarKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();
        int priceshift = 1000000;
        // base token yuan with decimal 2
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));
        makeTestToken(yuan, BigInteger.valueOf(10000000), addedBlocks, 2);

        // Make test token with decimal 2
        makeTestToken(dollarKey, BigInteger.valueOf(20000000), addedBlocks, 2);
        String dollar = dollarKey.getPublicKeyAsHex();

        // Open sell order for dollar, price 0.1 yuan for 200 dollar Block
        // Transaction=20 in dollar
        makeAndConfirmSellOrder(dollarKey, dollar, 700*priceshift, 20000, yuan.getPublicKeyAsHex(), addedBlocks);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        List<Map<String, Object>> orderData = new ArrayList<Map<String, Object>>();
        OrderUtil.orderMap(orderdataResponse, orderData, Locale.getDefault(),networkParameters);
        for (Map<String, Object> map : orderData) {
            assertTrue(map.get("price").equals("7"));
            assertTrue(map.get("total").equals("1400"));
        
        }

        // targeValue=20 (0.2 yuan)
        checkOrders(1);

        // Open buy order for dollar, target value=2 dollar Block Transaction=
        // 20 in Yuan
        makeAndConfirmBuyOrder(yuan, dollar, 700*priceshift, 20000, yuan.getPublicKeyAsHex(), addedBlocks);

        requestParam = new HashMap<String, Object>();

        response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        orderData = new ArrayList<Map<String, Object>>();
        OrderUtil.orderMap(orderdataResponse, orderData, Locale.getDefault(),networkParameters);
        assertTrue(orderData.size() == 2);
        for (Map<String, Object> map : orderData) {
            assertTrue(map.get("price").equals("7"));
            assertTrue(map.get("total").equals("1400"));
        }

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        showOrders();

    }
}
