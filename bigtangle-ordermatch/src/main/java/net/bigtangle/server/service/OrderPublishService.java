/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.server.ordermatch.bean.OrderBook;
import net.bigtangle.server.ordermatch.bean.Side;
import net.bigtangle.server.ordermatch.context.OrderBookHolder;
import net.bigtangle.server.service.response.AbstractResponse;
import net.bigtangle.server.service.response.GetOrderResponse;
import net.bigtangle.server.store.FullPrunedBlockStore;

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
        if (!validateto.trim().equals("00:00:00") && !StringUtils.isBlank(validateto)) {
            toDate = simpleDateFormat.parse(validateto);
        }
        if (!validatefrom.trim().equals("00:00:00") && !StringUtils.isBlank(validatefrom)) {
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
