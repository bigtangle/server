package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

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
import net.bigtangle.core.ordermatch.MatchLastdayResult;
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
import net.bigtangle.utils.MarketOrderItem;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.WalletUtil;
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
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify the order ticker has the correct price
        HashSet<String> a = new HashSet<String>();
        a.add(testTokenId);
        assertEquals(1000l, tickerService.getLastMatchingEvents(a, NetworkParameters.BIGTANGLE_TOKENID_STRING, store)
                .getTickers().get(0).getPrice());

        assertEquals(1000l,
                tickerService
                        .getTimeBetweenMatchingEvents(a, NetworkParameters.BIGTANGLE_TOKENID_STRING, null, null, store)
                        .getTickers().get(0).getPrice());

        assertEquals(1000l,
                tickerService
                        .getTimeBetweenMatchingEvents(a, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                                (System.currentTimeMillis() - 10000000) / 1000, null, store)
                        .getTickers().get(0).getPrice());

        assertEquals(1000l,
                tickerService.getTimeBetweenMatchingEvents(a, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                        (System.currentTimeMillis() - 10000000) / 1000, (System.currentTimeMillis()) / 1000, store)
                        .getTickers().get(0).getPrice());

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);

        // check the method of client service

    }

    @Test
    public void orderTickerSearchAPI() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 99, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 22, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1002, 100, addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // get the data
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        List<String> tokenids = new ArrayList<String>();
        requestParam.put("tokenids", tokenids);
        requestParam.put("count", 1);
        requestParam.put("basetoken", NetworkParameters.BIGTANGLE_TOKENID_STRING);
        byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrdersTicker.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderTickerResponse orderTickerResponse = Json.jsonmapper().readValue(response0, OrderTickerResponse.class);

        assertTrue(orderTickerResponse.getTickers().size() > 0);
        for (MatchLastdayResult m : orderTickerResponse.getTickers()) {
            if (m.getTokenid().equals(testTokenId)) {
                // assertTrue(m.getExecutedQuantity() == 78||
                // m.getExecutedQuantity() == 22);
                // TODO check the execute ordering. price is 1000 or 1001
                assertTrue(m.getPrice() == 1000 || m.getPrice() == 1001);
            }
        }

        // check wallet

        BigDecimal a = walletAppKit.wallet().getLastPrice(testTokenId, NetworkParameters.BIGTANGLE_TOKENID_STRING);
        assertTrue(a.compareTo(new BigDecimal("0.001")) == 0);

    }

    @Test
    public void orderTickerSearchAVGAPI() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 99, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 22, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1002, 100, addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // 200, 300 avg daily 200+300/2
        store.batchAddAvgPrice();
        // get the data
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        List<String> tokenids = new ArrayList<String>();
        tokenids.add(testTokenId);
        requestParam.put("tokenids", tokenids);
        requestParam.put("interval", "43200");
        requestParam.put("basetoken", NetworkParameters.BIGTANGLE_TOKENID_STRING);

        byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrdersTicker.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderTickerResponse orderTickerResponse = Json.jsonmapper().readValue(response0, OrderTickerResponse.class);

        assertTrue(orderTickerResponse.getTickers().size() > 0);
        for (MatchLastdayResult m : orderTickerResponse.getTickers()) {
            if (m.getTokenid().equals(testTokenId)) {
                // assertTrue(m.getExecutedQuantity() == 78||
                // m.getExecutedQuantity() == 22);
                // TODO check the execute ordering. price is 1000 or 1001
                assertTrue(m.getPrice() == 1000 || m.getPrice() == 1001);
            }
        }

        // check wallet

        BigDecimal b = walletAppKit.wallet().getLastPrice(testTokenId, NetworkParameters.BIGTANGLE_TOKENID_STRING);
        assertTrue(b.compareTo(new BigDecimal("0.001")) == 0);

    }

    @Test
    public void orderTickerSearchWithLastdayPriceAPI() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 99, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 22, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1002, 100, addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // 200, 300 avg daily 200+300/2
        store.batchAddAvgPrice();
        // get the data
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        List<String> tokenids = new ArrayList<String>();
        tokenids.add(testTokenId);
        requestParam.put("tokenids", tokenids);
        requestParam.put("count", 1);
        requestParam.put("basetoken", NetworkParameters.BIGTANGLE_TOKENID_STRING);

        byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrdersTicker.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderTickerResponse orderTickerResponse = Json.jsonmapper().readValue(response0, OrderTickerResponse.class);

        assertTrue(orderTickerResponse.getTickers().size() > 0);
        for (MatchLastdayResult m : orderTickerResponse.getTickers()) {
            if (m.getTokenid().equals(testTokenId)) {
                // assertTrue(m.getExecutedQuantity() == 78||
                // m.getExecutedQuantity() == 22);
                // TODO check the execute ordering. price is 1000 or 1001
                log.info("price:" + m.getPrice() + ";ExecutedQuantity:" + m.getExecutedQuantity() + ";getLastdayprice:"
                        + m.getLastdayprice());
                assertTrue(m.getPrice() == 1000 || m.getPrice() == 1001);
                assertTrue(m.getLastdayprice() == 1001);
            }
        }

        // check wallet

        BigDecimal b = walletAppKit.wallet().getLastPrice(testTokenId, NetworkParameters.BIGTANGLE_TOKENID_STRING);
        assertTrue(b.compareTo(new BigDecimal("0.001")) == 0);

    }

    @Test
    public void buy() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        checkOrders(1);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        checkOrders(0);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
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
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
        int priceshift = 1000000;

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(yuan, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(0);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), 2l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        List<String> tokenids = new ArrayList<String>();
        requestParam.put("count", 1);
        requestParam.put("tokenids", tokenids);
        requestParam.put("basetoken", yuan.getPublicKeyAsHex());
        byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrdersTicker.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderTickerResponse orderTickerResponse = Json.jsonmapper().readValue(response0, OrderTickerResponse.class);

        assertTrue(orderTickerResponse.getTickers().size() > 0);
        for (MatchLastdayResult m : orderTickerResponse.getTickers()) {
            if (m.getTokenid().equals(testTokenId)) {
                assertTrue(m.getPrice() == priceshift);
            }
        }

    }

    @Test
    public void buyBase2Token() throws Exception {

        List<Block> addedBlocks = new ArrayList<>();
        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));

        long tokennumber = 888888 * 1000;
        makeTestToken(yuan, BigInteger.valueOf(tokennumber), addedBlocks, 2);
        // Make test token
        ECKey testKey = walletKeys.get(0);
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(walletKeys.get(1), addedBlocks);

        // Make test token 2
        ECKey testKey2 = walletKeys.get(1);
        resetAndMakeTestTokenWithSpare(testKey2, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();
        int priceshift = 1000000;

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(yuan, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(0);

        String testTokenId2 = testKey2.getPublicKeyAsHex();

        // Open buy order for test token 2
        makeBuyOrder(yuan, testTokenId2, priceshift, 3, yuan.getPublicKeyAsHex(), addedBlocks);
        // Open sell order for test token 2
        makeSellOrder(testKey2, testTokenId2, priceshift, 3, yuan.getPublicKeyAsHex(), addedBlocks);
        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(testKey2, yuan.getPublicKeyAsHex(), 3l);
        assertHasAvailableToken(yuan, testKey2.getPublicKeyAsHex(), 3l);
        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void buyBaseTokenSmall() throws Exception {
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));
        int priceshift = 1000000;
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

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(yuan, testTokenId, 200, priceshift, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(0);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), priceshift * 1l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void buyBaseTokenSmallRemainder() throws Exception {
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));
        int priceshift = 1000000;
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

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(yuan, testTokenId, 200, priceshift + 100, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), priceshift * 1l);

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 200, priceshift, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, yuan.getPublicKeyAsHex(), 2l);
        assertHasAvailableToken(yuan, testKey.getPublicKeyAsHex(), priceshift * 1l + 100);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void buyBaseTokenMixed() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();
        int priceshift = 1000000;

        // base token
        ECKey yuan = ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv));
        resetAndMakeTestToken(yuan, addedBlocks);

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens, orderbase yuan
        makeAndConfirmSellOrder(testKey, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);
        checkOrders(1);
        makeRewardBlock(addedBlocks);
        // Open buy order for test tokens,orderbase yuan
        makeAndConfirmBuyOrder(yuan, testTokenId, priceshift, 2, yuan.getPublicKeyAsHex(), addedBlocks);

        // Open sell order for test tokens orderbase BIG
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens, orderbase BIG
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        List<MarketOrderItem> orderData = new ArrayList<MarketOrderItem>();
        WalletUtil.orderMap(orderdataResponse, orderData,   networkParameters, "buy", "sell");
        // assertTrue(orderData.size() == 4);
        for (MarketOrderItem map : orderData) {
            assertTrue(map.getPrice().equals("0.001") || map.getPrice().equals("1"));
        }

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the order ticker has the correct price
        HashSet<String> a = new HashSet<String>();
        a.add(testTokenId);
        List<MatchLastdayResult> tickers = tickerService
                .getLastMatchingEvents(a, NetworkParameters.BIGTANGLE_TOKENID_STRING, store).getTickers();
        assertEquals(tickers.size(), 1);
        assertTrue(1000l == tickers.get(0).getPrice() || priceshift == tickers.get(0).getPrice());

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
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Open sell order for test tokens
        makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void multiLevelBuy() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        makeSellOrder(testKey, testTokenId, 1001, 100, addedBlocks);
        makeSellOrder(testKey, testTokenId, 999, 50, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Open buy order for test tokens
        makeBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 99950l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void multiLevelSell() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 999, 100, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1001, 50, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Open sell orders for test tokens
        makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100050l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialBuy() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        makeSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);

        // Open buy order for test tokens
        makeBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 50000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 50l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialSell() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);

        // Open sell orders for test tokens
        makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 50000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 50l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialBidFill() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Open sell orders for test tokens
        makeSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
        makeSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
        makeSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialAskFill() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void cancel() throws Exception {

        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
        // Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Cancel
        makeCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void cancelTwoStep() throws Exception {

        // ECKey genesisKey =
        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
        // Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Cancel
        makeCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void effectiveCancel() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        Block buy = makeBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Cancel all
        makeCancelOp(sell, testKey, addedBlocks);
        makeCancelOp(buy, genesisKey, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify all tokens did not change possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 0l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 0l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testValidFromTime() throws Exception {
        final int waitTime = 5000;

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();
        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens with timeout
        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        long sellAmount = (long) 100;
        Block block = null;
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo((long) 1000 * sellAmount, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                testKey.getPubKey(), null, System.currentTimeMillis() + waitTime, Side.SELL,
                testKey.toAddress(networkParameters).toBase58(), NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l,
                sellAmount, testTokenId);
        tx.setData(info.toByteArray());
        tx.setDataClassName("OrderOpen");

        // Burn tokens to sell
        Coin amount = Coin.valueOf(sellAmount, testTokenId);
        List<UTXO> outputs = getBalance(false, testKey).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(testTokenId))
                .filter(out -> out.getValue().getValue().compareTo(amount.getValue()) > 0)
                .filter(out -> out.getScript().isSentToRawPubKey()).collect(Collectors.toList());
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

        // Open buy order for test tokens
        makeBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        Block matcherBlock1 = makeRewardBlock(addedBlocks);

        // Verify the order is still open
        // NOTE: Can fail if the test takes longer than 5 seconds. In that case,
        // increase the wait time variable
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));
        assertFalse(store.getOrderSpent(block.getHash(), matcherBlock1.getHash()));

        // Wait until valid
        Thread.sleep(waitTime);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the order is now closed
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));
        assertTrue(store.getOrderSpent(block.getHash(), matcherBlock1.getHash()));

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testValidToTime() throws Exception {
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
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
                testKey.toAddress(networkParameters).toBase58(), NetworkParameters.BIGTANGLE_TOKENID_STRING, 1l,
                sellAmount, testTokenId);
        tx.setData(info.toByteArray());
        tx.setDataClassName("OrderOpen");

        // Burn tokens to sell
        Coin amount = Coin.valueOf(sellAmount, testTokenId);
        List<UTXO> outputs = getBalance(false, testKey).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(testTokenId))
                .filter(out -> out.getValue().getValue().compareTo(amount.getValue()) > 0)
                .filter(out -> out.getScript().isSentToRawPubKey()).collect(Collectors.toList());
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

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify there is no open order left
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testAllOrdersSpent() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        Block b1 = makeSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        Block b2 = makeBuyOrder(genesisKey, testTokenId, 999, 50, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Cancel orders
        makeCancelOp(b1, testKey, addedBlocks);
        makeCancelOp(b2, genesisKey, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testReorgMatching() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1000, 225, addedBlocks);
        makeSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1000, 150, addedBlocks);
        makeSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1000, 75, addedBlocks);

        // Execute order matching and then unexecute it
        Block orderMatching = makeRewardBlock(addedBlocks);
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
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1000, 225, addedBlocks);
        makeSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1000, 150, addedBlocks);
        makeSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 1000, 75, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 450000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 450l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testMultiMatching3() throws Exception {

        ECKey genesisKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();
        generateSpareChange(genesisKey, addedBlocks);

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeSellOrder(testKey, testTokenId, 123, 150, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 456, 20, addedBlocks);
        makeSellOrder(testKey, testTokenId, 789, 3, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 987, 10, addedBlocks);
        makeSellOrder(testKey, testTokenId, 654, 8, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 321, 5, addedBlocks);
        makeSellOrder(testKey, testTokenId, 159, 2, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 951, 25, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Open orders
        makeSellOrder(testKey, testTokenId, 753, 12, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 357, 23, addedBlocks);
        makeBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);

        // Execute order matching
        makeRewardBlock(addedBlocks);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);

        // Bonus: check open and closed orders
        List<String> a = new ArrayList<String>();
        a.add(genesisKey.toAddress(networkParameters).toBase58());
        List<OrderRecord> closedOrders = store.getMyClosedOrders(a);

        System.out.println(closedOrders.toString());

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
        resetAndMakeTestTokenWithSpare(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        long amountToken = 2 * 88l;
        // split token
        payTestToken(testKey, amountToken);

        long tradeAmount = 100l;
        long price = 1;
        Block block = walletAppKit2.wallet().sellOrder(null, testTokenId, price, tradeAmount, null, null,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, true);
        addedBlocks.add(block);
        makeRewardBlock(addedBlocks);
        // blockGraph.confirm(block.getHash(), new HashSet<>(), (long) -1,
        // store); // mcmcServiceUpdate();

        long amount = 77l;
        // split BIG
        payBig(amount);
        payBig(amount);
        checkBalanceSum(Coin.valueOf(2 * amount, NetworkParameters.BIGTANGLE_TOKENID), wallet1Keys);
        // Open buy order for test tokens
        block = walletAppKit1.wallet().buyOrder(null, testTokenId, price, tradeAmount, null, null,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, true);
        addedBlocks.add(block);

        // Execute order matching
        makeRewardBlock(addedBlocks);
        showOrders();

        // Verify the tokens changed position
        checkBalanceSum(Coin.valueOf(tradeAmount * price, NetworkParameters.BIGTANGLE_TOKENID), wallet2Keys);

        checkBalanceSum(Coin.valueOf(amountToken - tradeAmount, testKey.getPubKey()), wallet2Keys);

        checkBalanceSum(Coin.valueOf(tradeAmount, testKey.getPubKey()), wallet1Keys);
        checkBalanceSum(Coin.valueOf(2 * amount - tradeAmount * price, NetworkParameters.BIGTANGLE_TOKENID),
                wallet1Keys);
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
        makeAndConfirmSellOrder(dollarKey, dollar, 700 * priceshift, 20000, yuan.getPublicKeyAsHex(), addedBlocks);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        byte[] response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        List<MarketOrderItem> orderData = new ArrayList<MarketOrderItem>();
        WalletUtil.orderMap(orderdataResponse, orderData , networkParameters, "buy", "sell");
        for (MarketOrderItem map : orderData) {
            assertTrue(map.getPrice().equals("7"));

        }

        // targeValue=20 (0.2 yuan)
        checkOrders(1);

        // Open buy order for dollar, target value=2 dollar Block Transaction=
        // 20 in Yuan
        makeBuyOrder(yuan, dollar, 700 * priceshift, 20000, yuan.getPublicKeyAsHex(), addedBlocks);

        requestParam = new HashMap<String, Object>();

        response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        orderData = new ArrayList<MarketOrderItem>();
        WalletUtil.orderMap(orderdataResponse, orderData,  networkParameters, "buy", "sell");
        assertTrue(orderData.size() == 1);
        for (MarketOrderItem map : orderData) {
            assertTrue(map.getPrice().equals("7"));

        }

        // Execute order matching
        makeRewardBlock(addedBlocks);
        showOrders();

    }
}
