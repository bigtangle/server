/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.server.ordermatch.bean.OrderBook;
import net.bigtangle.server.ordermatch.bean.Side;
import net.bigtangle.server.ordermatch.context.OrderBookHolder;
import net.bigtangle.server.ordermatch.service.response.AbstractResponse;
import net.bigtangle.server.ordermatch.service.response.GetOrderResponse;
import net.bigtangle.server.ordermatch.store.FullPrunedBlockStore;

@Service
public class OrderPublishService {

    public AbstractResponse saveOrderPublish(Map<String, Object> request) throws Exception {
        String address = (String) request.get("address");
        String tokenid = (String) request.get("tokenid");
        int type = (Integer) request.get("type");
        String validateto = (String) request.get("validateto");
        String validatefrom = (String) request.get("validatefrom");
        long price =  Long.parseLong( request.get("price").toString());
        long amount =   Long.parseLong( request.get("amount").toString());
        
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
        if (market == null) market = "";
        // add market
        OrderPublish order = OrderPublish.create(address, tokenid, type, toDate, fromDate, price, amount, market);
        store.saveOrderPublish(order);
        
        OrderBook orderBook = orderBookHolder.getOrderBookWithTokenId(tokenid);
        synchronized (this) {
            if (orderBook == null) {
                orderBook = orderBookHolder.createOrderBook();
                orderBookHolder.addOrderBook(tokenid, orderBook);
            }
            orderBook.enter(order.getOrderid(), type == 1 ? Side.SELL : Side.BUY, price, amount);
        }
        return AbstractResponse.createEmptyResponse();
    }
    
    @SuppressWarnings("unchecked")
    public void deleteOrder(Block block) throws Exception {
        Transaction transaction = block.getTransactions().get(0);
        
        byte[] buf = transaction.getData();
        String orderid = new String(buf);
        
        List<HashMap<String, Object>> multiSignBies = Json.jsonmapper().readValue(transaction.getDatasignature(),
                List.class);
        Map<String, Object> multiSignBy = multiSignBies.get(0);
        byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
        byte[] data = transaction.getHash().getBytes();
        byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
        
        boolean success = ECKey.verify(data, signature, pubKey);
        if (!success) {
            throw new BlockStoreException("multisign signature error");
        }
        
        OrderPublish orderPublish = this.store.getOrderPublishByOrderid(orderid);
        if (orderPublish == null) {
            throw new BlockStoreException("order publish not found");
        }
        
        String tokenid = orderPublish.getTokenid();
        OrderBook orderBook = orderBookHolder.getOrderBookWithTokenId(tokenid);
        synchronized (this) {
            if (orderBook == null) {
                orderBook = orderBookHolder.createOrderBook();
                orderBookHolder.addOrderBook(tokenid, orderBook);
            }
            orderBook.cancel(orderPublish.getOrderid(), 0);
        }
        
        this.store.deleteOrderPublish(orderPublish.getOrderid());
        this.store.deleteExchangeInfo(orderPublish.getOrderid());
        this.store.deleteOrderMatch(orderPublish.getOrderid());
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
