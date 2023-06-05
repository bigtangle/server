/*******************************************************************************
 *  
 *  Copyright   2018  Inasset GmbH. 
 *******************************************************************************/
package net.bigtangle.tradeservice;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.Deposit;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.Withdraw;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Stopwatch;
import com.huobi.client.AccountClient;
import com.huobi.client.MarketClient;
import com.huobi.client.TradeClient;
import com.huobi.client.req.account.AccountBalanceRequest;
import com.huobi.client.req.market.CandlestickRequest;
import com.huobi.client.req.market.MarketDepthRequest;
import com.huobi.client.req.market.MarketDetailMergedRequest;
import com.huobi.client.req.trade.CreateOrderRequest;
import com.huobi.client.req.wallet.CreateWithdrawRequest;
import com.huobi.client.req.wallet.DepositWithdrawRequest;
import com.huobi.client.req.wallet.WithdrawAddressRequest;
import com.huobi.constant.HuobiOptions;
import com.huobi.constant.enums.CandlestickIntervalEnum;
import com.huobi.constant.enums.DepositWithdrawTypeEnum;
import com.huobi.constant.enums.DepthSizeEnum;
import com.huobi.constant.enums.DepthStepEnum;
import com.huobi.model.account.Account;
import com.huobi.model.account.AccountBalance;
import com.huobi.model.account.Balance;
import com.huobi.model.market.Candlestick;
import com.huobi.model.market.MarketDepth;
import com.huobi.model.market.MarketDetailMerged;
import com.huobi.model.market.PriceLevel;
import com.huobi.model.trade.MatchResult;
import com.huobi.model.trade.Order;
import com.huobi.model.wallet.DepositWithdraw;
import com.huobi.model.wallet.WithdrawAddress;
import com.huobi.model.wallet.WithdrawAddressResult;
import com.huobi.service.huobi.HuobiWalletService;

import net.bigtangle.core.Utils;
import net.bigtangle.core.ordermatch.AskBid;
import net.bigtangle.core.response.AbstractResponse;
import net.bigtangle.core.response.BooleanResponse;
import net.bigtangle.core.response.ErrorResponse;
import net.bigtangle.core.response.GetAskBidListResponse;
import net.bigtangle.core.response.GetAskBidsResponse;
import net.bigtangle.core.response.GetStringResponse;
import net.bigtangle.core.response.LongResponse;
import net.bigtangle.tradeservice.binance.DepthCache;
import net.bigtangle.utils.Gzip;
import net.bigtangle.utils.Json;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
 
@RestController
@RequestMapping("/")
public class DispatcherController {

    private static final Logger logger = LoggerFactory.getLogger(DispatcherController.class);

    private static final String BIDS = "BIDS";
    private static final String ASKS = "ASKS";

    @SuppressWarnings("unchecked")
    @RequestMapping(value = "{reqCmd}", method = { RequestMethod.POST, RequestMethod.GET })
    public void process(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] contentBytes,
            HttpServletResponse httpServletResponse, HttpServletRequest httprequest) throws Exception {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        @SuppressWarnings("rawtypes")
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {
                processDo(reqCmd, contentBytes, httpServletResponse, httprequest);
                return "";
            }
        });
        try {
            handler.get(30, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            logger.debug(" process  Timeout  ");
            handler.cancel(true);
            AbstractResponse resp = ErrorResponse.create(100);
            StringWriter sw = new StringWriter();
            resp.setMessage(sw.toString());

        } finally {
            executor.shutdownNow();
        }

    }

    @SuppressWarnings("unchecked")
    public void processDo(@PathVariable("reqCmd") String reqCmd, @RequestBody byte[] contentBytes,
            HttpServletResponse httpServletResponse, HttpServletRequest httprequest) throws Exception {
        Stopwatch watch = Stopwatch.createStarted();

        String backMessage = "";
        byte[] bodyByte = new byte[0];
        try {

            logger.trace("reqCmd : {} from {}, size : {}, started.", reqCmd, httprequest.getRemoteAddr(),
                    contentBytes.length);

            bodyByte = Gzip.decompressOut(contentBytes);
            ReqCmd reqCmd0000 = ReqCmd.valueOf(reqCmd);

            switch (reqCmd0000) {
            case payHuobi: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String toaddress = (String) request.get("toaddress");
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String symbol = (String) request.get("symbol");
                String chain = (String) request.get("chain");
                if (chain == null || "".endsWith(chain)) {
                    chain = "hrc20" + symbol;
                }
                String amountString = (String) request.get("amountString");
                Long a = payHuobi(apikey, secret, toaddress, amountString, symbol, chain, backMessage);
                this.outPrintJSONString(httpServletResponse, LongResponse.create(a), watch);
            }
                break;
            case checkHuobiWithdrawAddress: {

                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String address = (String) request.get("address");
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                boolean a = checkHuobiWithdrawAddress(apikey, secret, address);
                this.outPrintJSONString(httpServletResponse, BooleanResponse.create(a), watch);
            }
                break;
            case checkHoubiWithdraw: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String withdrawid = (String) request.get("withdrawid");
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                boolean a = checkHoubiWithdraw(Long.valueOf(withdrawid), apikey, secret);
                this.outPrintJSONString(httpServletResponse, BooleanResponse.create(a), watch);
            }
                break;

            case payBinance: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String toaddress = (String) request.get("toaddress");
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String coin = (String) request.get("coin");
                String amountString = (String) request.get("amountString");
                String a = payBinance(apikey, secret, coin, toaddress, amountString);
                this.outPrintJSONString(httpServletResponse, GetStringResponse.create(a), watch);
            }
                break;
            case checkBinanceWithdraw: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String withdrawid = (String) request.get("withdrawid");
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String a = checkBinanceWithdraw(apikey, secret, withdrawid);
                this.outPrintJSONString(httpServletResponse, GetStringResponse.create(a), watch);
            }
                break;

            case askBidBest: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String symbol = (String) request.get("symbol");
                MarketClient marketClient = MarketClient.create(new HuobiOptions());
                MarketDetailMerged marketDetailMerged = marketClient
                        .getMarketDetailMerged(MarketDetailMergedRequest.builder().symbol(symbol).build());
                List<AskBid> askbidlist = new ArrayList<AskBid>();
                AskBid a = new AskBid();
                a.setTradesystem("huobi");
                a.setSymbol(symbol);
                a.setAskBestPrice(marketDetailMerged.getAsk().getPrice().stripTrailingZeros());
                a.setAskBestAmount(marketDetailMerged.getAsk().getAmount().stripTrailingZeros());
                a.setBidBestPrice(marketDetailMerged.getBid().getPrice().stripTrailingZeros());
                a.setBidBestAmount(marketDetailMerged.getBid().getAmount().stripTrailingZeros());
                askbidlist.add(a);

                DepthCache d = new DepthCache(symbol);
                a = new AskBid();
                a.setTradesystem("binance");
                a.setSymbol(symbol);
                a.setAskBestPrice(d.getBestAsk().getKey().stripTrailingZeros());
                a.setAskBestAmount(d.getBestAsk().getValue().stripTrailingZeros());
                a.setBidBestPrice(d.getBestBid().getKey().stripTrailingZeros());
                a.setBidBestAmount(d.getBestBid().getKey().stripTrailingZeros());
                askbidlist.add(a);
                this.outPrintJSONString(httpServletResponse, GetAskBidListResponse.create(askbidlist), watch);
            }
                break;
            case huobiSpotLimit: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String side = (String) request.get("side");

                String clientOrderId = (String) request.get("clientOrderId");
                String symbol = (String) request.get("symbol");
                String price = (String) request.get("price");
                String amount = (String) request.get("amount");

                String spotAccountId = getHuobiSpotAccountid(apikey, secret);

                Long a = huobiSpotLimit(apikey, secret, side, new Long(spotAccountId), clientOrderId, symbol,
                        new BigDecimal(price), new BigDecimal(amount));
                this.outPrintJSONString(httpServletResponse, LongResponse.create(a), watch);
            }
                break;
            case getHuobiOrder: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String orderId = (String) request.get("orderId");

                Order a = getHuobiOrder(apikey, secret, orderId);
                this.gzipBinary(httpServletResponse, a);
            }
            case getHuobiOrdermatch: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String orderId = (String) request.get("orderId");
                TradeClient tradeService = TradeClient
                        .create(HuobiOptions.builder().apiKey(apikey).secretKey(secret).build());

                List<MatchResult> m = tradeService.getMatchResult(new Long(orderId));

                this.gzipBinary(httpServletResponse, m);
            }
                break;
            case cancelHuobiOrder: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");

                String orderId = (String) request.get("orderId");

                Long a = cancelHuobiOrder(apikey, secret, orderId);
                this.outPrintJSONString(httpServletResponse, GetStringResponse.create(a.toString()), watch);
            }
                break;
            case getHuobiAccount: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");

                AccountClient accountService = AccountClient
                        .create(HuobiOptions.builder().apiKey(apikey).secretKey(secret).build());
                List<Balance> list = new ArrayList<Balance>();

                accountService.getAccounts().forEach(account -> {
                    System.out.println(account.toString());
                    Long accountId = account.getId();
                    AccountBalance accountBalance = accountService
                            .getAccountBalance(AccountBalanceRequest.builder().accountId(accountId).build());
                    list.addAll(accountBalance.getList());
                });

                this.outPutDataMap(httpServletResponse, list);
            }
                break;
            case binanceSpotLimit: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String symbol = (String) request.get("symbol");
                String side = (String) request.get("side");
                String price = (String) request.get("price");
                String amount = (String) request.get("amount");
                String clientOrderId = (String) request.get("clientOrderId").toString();
                this.gzipBinary(httpServletResponse,
                        binanceSpotLimit(apikey, secret, side, clientOrderId, symbol.toUpperCase(), price, amount));
            }
                break;
            case getBinanceOrder: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");

                String orderId = (String) request.get("orderId");
                String symbol = (String) request.get("symbol");
                this.gzipBinary(httpServletResponse, getBinanceOrder(apikey, secret, symbol.toUpperCase(), orderId));
            }
            case getBinanceTrade: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String symbol = (String) request.get("symbol");
                String orderId = (String) request.get("orderId");

                BinanceApiRestClient client = BinanceApiClientFactory.newInstance(apikey, secret).newRestClient();
                List<Trade> ts = client.getMyTrades(symbol);
                if (orderId != null && !"".equals(orderId)) {
                    ts = ts.stream().filter(a -> a.getOrderId().equals(orderId)).collect(Collectors.toList());
                }
                this.gzipBinaryBinance(httpServletResponse, ts);
            }

                break;
            case cancelBinanceOrder: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String symbol = (String) request.get("symbol");
                String orderId = (String) request.get("orderId");

                CancelOrderResponse a = cancelBinanceOrder(apikey, secret, symbol, orderId);
                this.outPrintJSONString(httpServletResponse, GetStringResponse.create(a.getClientOrderId()), watch);
            }
                break;
            case getBinanceAccount: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");

                BinanceApiRestClient client = BinanceApiClientFactory.newInstance(apikey, secret).newRestClient();
                com.binance.api.client.domain.account.Account account = client.getAccount(60_000L,
                        System.currentTimeMillis());

                this.outPutDataMap(httpServletResponse, account.getBalances());
            }
                break;
            case getBinanceDeposit: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String apikey = (String) request.get("apikey");
                String secret = (String) request.get("secret");
                String token = (String) request.get("token");
                this.outPutDataMap(httpServletResponse, getBinanceDeposit(apikey, secret, token));
            }
                break;

            case askBidHuobi: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String symbol = (String) request.get("symbol");
                MarketClient marketClient = MarketClient.create(new HuobiOptions());
                MarketDepth marketDepth = marketClient.getMarketDepth(MarketDepthRequest.builder().symbol(symbol)
                        .depth(DepthSizeEnum.SIZE_5).step(DepthStepEnum.STEP0).build());
                Map<String, NavigableMap<BigDecimal, BigDecimal>> askbids = new HashMap<>();
                NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>(Comparator.reverseOrder());

                for (PriceLevel ask : marketDepth.getAsks()) {
                    asks.put(ask.getPrice().stripTrailingZeros(), ask.getAmount().stripTrailingZeros());
                }
                askbids.put(ASKS, asks);

                NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
                for (PriceLevel bid : marketDepth.getBids()) {
                    bids.put(bid.getPrice().stripTrailingZeros(), bid.getAmount().stripTrailingZeros());
                }
                askbids.put(BIDS, bids);

                this.outPrintJSONString(httpServletResponse, GetAskBidsResponse.create(askbids), watch);

            }
                break;

            case askBidBinance: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String symbol = (String) request.get("symbol");
                DepthCache d = new DepthCache(symbol);
                this.outPrintJSONString(httpServletResponse, GetAskBidsResponse.create(d.getDepthCache()), watch);
            }
                break;
            case getCandlestickHoubi: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String symbol = (String) request.get("symbol");
                String interval = (String) request.get("interval");
                String quantity = (String) request.get("quantity");
                MarketClient marketClient = MarketClient.create(new HuobiOptions());
                List<Candlestick> list = marketClient.getCandlestick(CandlestickRequest.builder().symbol(symbol)
                        .interval(CandlestickIntervalEnum.valueOf(interval)).size(Integer.valueOf(quantity)).build());
                outPutDataMap(httpServletResponse, list);
            }
                break;

            case urlTobyte: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String url = (String) request.get("url");
                this.outPrintJSONString(httpServletResponse, GetStringResponse.create(urlTobyte(url)), watch);

            }
                break;
            case relay: {
                String reqStr = new String(bodyByte, "UTF-8");
                Map<String, Object> request = Json.jsonmapper().readValue(reqStr, Map.class);
                String prompt = (String) request.get("prompt");
                this.outPrintJSONString(httpServletResponse, GetStringResponse.create(relay(prompt)), watch);

            }
                break;
            default:
                break;
            }
        } catch (Throwable exception) {
            logger.error("reqCmd : {}, reqHex : {}, {},error.", reqCmd, bodyByte.length, remoteAddr(httprequest),
                    exception);
            AbstractResponse resp = ErrorResponse.create(100);
            StringWriter sw = new StringWriter();
            sw.append(backMessage);
            exception.printStackTrace(new PrintWriter(sw));
            resp.setMessage(sw.toString());
            this.outPrintJSONString(httpServletResponse, resp, watch);
        } finally {
            if (watch.elapsed(TimeUnit.MILLISECONDS) > 1000)
                logger.info(reqCmd + " takes {} from {}", watch.elapsed(TimeUnit.MILLISECONDS),
                        remoteAddr(httprequest));
            watch.stop();
        }
    }

    public com.binance.api.client.domain.account.Order getBinanceOrder(String userapikey, String secret, String symbol,
            String orderId) throws Exception {
        BinanceApiRestClient client = BinanceApiClientFactory.newInstance(userapikey, secret).newRestClient();
        OrderStatusRequest s = new OrderStatusRequest(symbol.toUpperCase(), new Long(orderId));
        return client.getOrderStatus(s);

    }

    public CancelOrderResponse cancelBinanceOrder(String userapikey, String secret, String symbol, String orderId)
            throws Exception {
        BinanceApiRestClient client = BinanceApiClientFactory.newInstance(userapikey, secret).newRestClient();
        CancelOrderRequest s = new CancelOrderRequest(symbol, new Long(orderId));
        return client.cancelOrder(s);

    }

    public NewOrderResponse binanceSpotLimit(String userapikey, String secret, String buysell, String clientOrderId,
            String symbol, String price, String amount) throws Exception {
        BinanceApiRestClient client = BinanceApiClientFactory.newInstance(userapikey, secret).newRestClient();

        if ("buy".equals(buysell)) {

            NewOrder order = new NewOrder(symbol, OrderSide.BUY, OrderType.LIMIT, TimeInForce.GTC, amount, price);
            if (clientOrderId != null)
                order.newClientOrderId(clientOrderId);
            return client.newOrder(order);
        }
        if ("sell".equals(buysell)) {

            NewOrder order = new NewOrder(symbol, OrderSide.SELL, OrderType.LIMIT, TimeInForce.GTC, amount, price);

            return client.newOrder(order);
        }
        return null;
    }

    public Order getHuobiOrder(String userapikey, String secret, String orderId) throws Exception {
        TradeClient tradeService = TradeClient
                .create(HuobiOptions.builder().apiKey(userapikey).secretKey(secret).build());

        Order clientOrder = tradeService.getOrder(new Long(orderId));

        return clientOrder;
    }

    public String getHuobiSpotAccountid(String userapikey, String secret) throws Exception {
        AccountClient accountService = AccountClient
                .create(HuobiOptions.builder().apiKey(userapikey).secretKey(secret).build());

        List<Account> accountList = accountService.getAccounts();
        for (Account account : accountList) {
            if (account.getType().equals("spot")) {
                return account.getId().toString();
            }
        }
        return null;
    }

    public Long cancelHuobiOrder(String userapikey, String secret, String orderId) throws Exception {
        TradeClient tradeService = TradeClient
                .create(HuobiOptions.builder().apiKey(userapikey).secretKey(secret).build());

        return tradeService.cancelOrder(new Long(orderId));

    }

    public Long huobiSpotLimit(String userapikey, String secret, String buysell, Long spotAccountId,
            String clientOrderId, String symbol, BigDecimal price, BigDecimal amount) throws Exception {
        TradeClient tradeService = TradeClient
                .create(HuobiOptions.builder().apiKey(userapikey).secretKey(secret).build());
        if ("buy".equals(buysell)) {
            CreateOrderRequest buyLimitRequest = CreateOrderRequest.spotBuyLimit(spotAccountId, clientOrderId, symbol,
                    price, amount);
            Long id = tradeService.createOrder(buyLimitRequest);
            return id;
        }
        if ("sell".equals(buysell)) {
            CreateOrderRequest buyLimitRequest = CreateOrderRequest.spotSellLimit(spotAccountId, clientOrderId, symbol,
                    price, amount);
            Long id = tradeService.createOrder(buyLimitRequest);
            return id;
        }
        return null;
    }

    private boolean checkHoubiWithdraw(Long withdrawid, String userapikey, String secret)
            throws JsonProcessingException, IOException, InterruptedException {
        boolean rating = false;
        while (!rating) {
            HuobiWalletService walletService = new HuobiWalletService(
                    HuobiOptions.builder().apiKey(userapikey).secretKey(secret).build());
            List<DepositWithdraw> depositWithdrawList = walletService.getDepositWithdraw(
                    DepositWithdrawRequest.builder().type(DepositWithdrawTypeEnum.WITHDRAW).build());

            for (DepositWithdraw depositWithdraw : depositWithdrawList) {
                if (depositWithdraw.getId().equals(withdrawid) && "confirmed".equals(depositWithdraw.getState())) {
                    return true;
                }
            }
            Thread.sleep(5000);

        }
        return rating;
    }

    public boolean checkHuobiWithdrawAddress(String userapikey, String secret, String address) {
        HuobiWalletService walletService = new HuobiWalletService(
                HuobiOptions.builder().apiKey(userapikey).secretKey(secret).build());

        WithdrawAddressResult withdrawAddressResult = walletService
                .getWithdrawAddress(WithdrawAddressRequest.builder().currency("usdt").limit(500).build());

        for (WithdrawAddress w : withdrawAddressResult.getWithdrawAddressList()) {
            if (w.getAddress().equals(address)) {
                return true;
            }
        }

        return false;

    }

    @RequestMapping("/")
    public String index() {
        return "tradeservice";
    }

    public Long payHuobi(String userapikey, String secret, String toaddress, String amountString, String symbol,
            String chain, String backMessage) throws Exception {
        HuobiWalletService walletService = new HuobiWalletService(
                HuobiOptions.builder().apiKey(userapikey).secretKey(secret).build());

        BigDecimal amount = new BigDecimal(amountString);
        BigDecimal fee = fee(symbol);
        CreateWithdrawRequest hpay = CreateWithdrawRequest.builder().address(toaddress).addrTag("").currency(symbol)
                .chain(chain).amount(amount.subtract(fee)).fee(fee).build();
        backMessage = " payHuobi to " + toaddress + " " + hpay.toString();
        logger.debug(backMessage);
        return walletService.createWithdraw(hpay);
    }

    private BigDecimal fee(String symbol) throws Exception {
        return feeHuobi(symbol);
    }

    public BigDecimal feeHuobi(String symbol) throws Exception {
        BigDecimal fee = new BigDecimal(0.1);
        if (!"usdt".equals(symbol)) {
            MarketClient marketClient = MarketClient.create(new HuobiOptions());
            MarketDetailMerged marketDetailMerged = marketClient
                    .getMarketDetailMerged(MarketDetailMergedRequest.builder().symbol(symbol + "usdt").build());
            fee = fee.divide(marketDetailMerged.getClose(), 8, RoundingMode.HALF_UP);
        }
        return fee;
    }

    int cacheTime = 15000;
    Long lastUpdate = 0l;
    Map<String, String> stockPrice = new HashMap<String, String>();

    public synchronized void refreshCache() {

        if (lastUpdate < System.currentTimeMillis() - cacheTime) {
            stockPrice = new HashMap<String, String>();
            lastUpdate = System.currentTimeMillis();
        }
    }

    public String relay(String prompt) throws IOException {
    	  // Your API Key
        String apiKey = "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";

        // The prompt to complete
      //  String prompt = "二氧化碳排放交易系统";

        // The URL of the API endpoint
        String endpoint = "https://api.openai.com/v1/completions";

        // The request headers
        Headers headers = new Headers.Builder().add("Content-Type", "application/json")
                .add("Authorization", "Bearer " + apiKey).build();

        // The request payload
        JSONObject payload = new JSONObject();
        payload.put("prompt", prompt);
        payload.put("model", "text-davinci-003");
        payload.put("max_tokens", 2000);
        payload.put("temperature", 0.5);
       payload.put("top_p", 1);

        // Create the request body
       okhttp3.RequestBody body = okhttp3.RequestBody.create(MediaType.parse("application/json"), payload.toString());

        // Create the request
        Request request = new Request.Builder().url(endpoint).headers(headers).post(body).build();

        // Make the request
        OkHttpClient client = new OkHttpClient().newBuilder().connectTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES).build();
        Response response = client.newCall(request).execute();

        // Parse the response
        String responseString = response.body().string();
        return responseString;
    }

    
    public String urlTobyte(String url) throws MalformedURLException {
        refreshCache();
        String p = stockPrice.get(url);
        if (p == null) {
            p = urlTobyteNoCache(url);
            stockPrice.put(url, p);
        }
        return p;
    }

    public String urlTobyteNoCache(String url) throws MalformedURLException {
        URL ur = new URL(url);
        BufferedInputStream in = null;
        ByteArrayOutputStream out = null;
        try {
            in = new BufferedInputStream(ur.openStream());
            out = new ByteArrayOutputStream(1024);
            byte[] temp = new byte[1024];
            int size = 0;
            while ((size = in.read(temp)) != -1) {
                out.write(temp, 0, size);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // e.printStackTrace();
            }
        }
        byte[] content = out.toByteArray();
        return new String(content);
    }

    public String payBinance(String userapikey, String secret, String coin, String toaddress, String amountString)
            throws Exception {

        BinanceApiRestClient client = BinanceApiClientFactory.newInstance(userapikey, secret).newRestClient();

        return client.withdraw(coin, toaddress, amountString, null, null).getId();
    }

    public String checkBinanceWithdraw(String userapikey, String secret, String withdrawid) throws Exception {

        BinanceApiRestClient client = BinanceApiClientFactory.newInstance(userapikey, secret).newRestClient();
        for (Withdraw d : client.getWithdrawHistory("")) {
            if (d.getId().equals(withdrawid)) {
                return "" + d.getStatus();
            }
        }
        return "";
    }

    public String getBinanceWithdrawAddress(String userapikey, String secret) throws Exception {

        BinanceApiRestClient client = BinanceApiClientFactory.newInstance(userapikey, secret).newRestClient();
        return client.getDepositAddress("ETH").getAddress();
    }

    public Deposit[] getBinanceDeposit(String userapikey, String secret, String token) throws Exception {

        BinanceApiRestClient client = BinanceApiClientFactory.newInstance(userapikey, secret).newRestClient();
        return client.getDepositHistory(token);
    }

    private void errorLimit(HttpServletResponse httpServletResponse, Stopwatch watch) throws Exception {
        AbstractResponse resp = ErrorResponse.create(101);
        resp.setErrorcode(403);
        resp.setMessage(" limit reached. ");
        this.outPrintJSONString(httpServletResponse, resp, watch);
    }

    public void outPutDataMap(HttpServletResponse httpServletResponse, Object data) throws Exception {
        httpServletResponse.setCharacterEncoding("UTF-8");
        HashMap<String, Object> result = new HashMap<String, Object>();
        result.put("data", data);
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(result));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void outPointBinaryArray(HttpServletResponse httpServletResponse, byte[] data) throws Exception {
        httpServletResponse.setCharacterEncoding("UTF-8");

        HashMap<String, Object> result = new HashMap<String, Object>();
        result.put("dataHex", Utils.HEX.encode(data));
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(result));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void outPrintJSONString(HttpServletResponse httpServletResponse, AbstractResponse response, Stopwatch watch)
            throws Exception {
        long duration = watch.elapsed(TimeUnit.MILLISECONDS);
        response.setDuration(duration);
        gzipBinary(httpServletResponse, response);
    }

    public void gzipBinary(HttpServletResponse httpServletResponse, AbstractResponse response) throws Exception {
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void gzipBinary(HttpServletResponse httpServletResponse, List<MatchResult> response) throws Exception {
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void gzipBinary(HttpServletResponse httpServletResponse, Order response) throws Exception {
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void gzipBinaryBinance(HttpServletResponse httpServletResponse, List<Trade> response) throws Exception {
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void gzipBinary(HttpServletResponse httpServletResponse, NewOrderResponse response) throws Exception {
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public void gzipBinary(HttpServletResponse httpServletResponse,
            com.binance.api.client.domain.account.Order response) throws Exception {
        GZIPOutputStream servletOutputStream = new GZIPOutputStream(httpServletResponse.getOutputStream());

        servletOutputStream.write(Json.jsonmapper().writeValueAsBytes(response));
        servletOutputStream.flush();
        servletOutputStream.close();
    }

    public String remoteAddr(HttpServletRequest request) {
        String remoteAddr = "";
        remoteAddr = request.getHeader("X-FORWARDED-FOR");
        if (remoteAddr == null || "".equals(remoteAddr)) {
            remoteAddr = request.getRemoteAddr();
        } else {
            StringTokenizer tokenizer = new StringTokenizer(remoteAddr, ",");
            while (tokenizer.hasMoreTokens()) {
                remoteAddr = tokenizer.nextToken();
                break;
            }
        }
        return remoteAddr;
    }

    private static final String Huobi15Fee = "15";
}
