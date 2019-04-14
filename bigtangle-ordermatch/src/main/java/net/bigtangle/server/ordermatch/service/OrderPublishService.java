/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.ExchangeMulti;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Side;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.http.AbstractResponse;
import net.bigtangle.core.http.ordermatch.resp.GetOrderResponse;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.server.ordermatch.context.OrderBookHolder;
import net.bigtangle.server.ordermatch.store.FullPrunedBlockStore;
import net.bigtangle.server.utils.OrderBook;

@Service
public class OrderPublishService {

    public AbstractResponse saveOrderPublish(Map<String, Object> request) throws Exception {
        String address = (String) request.get("address");
        String tokenid = (String) request.get("tokenid");
        @SuppressWarnings("unchecked")
        List<String> signaddress = (List<String>) request.get("signaddress");
        int type = (Integer) request.get("type");
        String validateto = (String) request.get("validateto");
        String validatefrom = (String) request.get("validatefrom");
        long price = Long.parseLong(request.get("price").toString());
        long amount = Long.parseLong(request.get("amount").toString());

        Date toDate = null;
        Date fromDate = null;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (validateto != null && !validateto.trim().equals("00:00:00") && !StringUtils.isBlank(validateto)) {
            toDate = simpleDateFormat.parse(validateto);
        }
        if (validatefrom != null && !validatefrom.trim().equals("00:00:00") && !StringUtils.isBlank(validatefrom)) {
            fromDate = simpleDateFormat.parse(validatefrom);
        }
        String market = (String) request.get("market");
        if (market == null)
            market = "";
        // add market
        OrderPublish order = OrderPublish.create(address, tokenid, type, toDate, fromDate, price, amount, market);
        store.saveOrderPublish(order);
        if (type == 1) {
            if (signaddress != null && !signaddress.isEmpty()) {
                for (String addr : signaddress) {
                    ExchangeMulti exchangeMulti = new ExchangeMulti();
                    exchangeMulti.setOrderid(order.getOrderId());
                    exchangeMulti.setPubkey(addr);
                    exchangeMulti.setSign(0);
                    store.saveExchangeMulti(exchangeMulti);
                }
            }

        }

        OrderBook orderBook = orderBookHolder.getOrderBookWithTokenId(tokenid);
        synchronized (this) {
            if (orderBook == null) {
                orderBook = orderBookHolder.createOrderBook();
                orderBookHolder.addOrderBook(tokenid, orderBook);
            }
            orderBook.enter(order.getOrderId(), type == 1 ? Side.SELL : Side.BUY, price, amount);
        }
        return AbstractResponse.createEmptyResponse();
    }

    public void deleteOrder(Block block) throws Exception {
        Transaction transaction = block.getTransactions().get(0);

        byte[] buf = transaction.getData();
        String orderid = new String(buf);

        MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                MultiSignByRequest.class);

        MultiSignBy multiSignBy = multiSignByRequest.getMultiSignBies().get(0);
        byte[] pubKey = Utils.HEX.decode(multiSignBy.getPublickey());
        byte[] data = transaction.getHash().getBytes();
        byte[] signature = Utils.HEX.decode(multiSignBy.getSignature());

        boolean success = ECKey.verify(data, signature, pubKey);
        if (!success) {
            throw new BlockStoreException("multisign signature error");
        }

        OrderPublish orderPublish = this.store.getOrderPublishByOrderid(orderid);
        if (orderPublish == null) {
            throw new BlockStoreException("order publish not found");
        }

        String tokenid = orderPublish.getTokenId();
        OrderBook orderBook = orderBookHolder.getOrderBookWithTokenId(tokenid);
        synchronized (this) {
            if (orderBook == null) {
                orderBook = orderBookHolder.createOrderBook();
                orderBookHolder.addOrderBook(tokenid, orderBook);
            }
            orderBook.cancel(orderPublish.getOrderId(), 0);
        }

        this.store.deleteOrderPublish(orderPublish.getOrderId());
        this.store.deleteExchangeInfo(orderPublish.getOrderId());
        this.store.deleteOrderMatch(orderPublish.getOrderId());
    }

    @Autowired
    private OrderBookHolder orderBookHolder;

    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getOrderPublishListWithCondition(Map<String, Object> request) throws BlockStoreException {
        List<OrderPublish> orders = this.store.getOrderPublishListWithCondition(request);
        return GetOrderResponse.create(orders);
    }

    public List<OrderPublish> getOrderPublishListWithNotMatch() throws BlockStoreException {
        List<OrderPublish> orders = this.store.getOrderPublishListWithNotMatch();
        return orders;
    }
}
