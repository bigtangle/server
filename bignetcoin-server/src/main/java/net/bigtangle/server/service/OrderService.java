package net.bigtangle.server.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.order.match.OrderBook;
import net.bigtangle.order.match.Side;
import net.bigtangle.server.response.AbstractResponse;
import net.bigtangle.server.response.GetOrderResponse;
import net.bigtangle.store.FullPrunedBlockStore;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    public AbstractResponse saveOrder(Map<String, Object> request) throws Exception {
        String address = (String) request.get("address");
        String tokenid = (String) request.get("tokenid");
        int type = (Integer) request.get("type");
        String validateto = (String) request.get("validateto");
        String validatefrom = (String) request.get("validatefrom");
        int price = (Integer) request.get("price");
        int amount = (Integer) request.get("amount");
        
        Date toDate = null;
        Date fromDate = null;
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (!StringUtils.isBlank(validateto)) {
            toDate = simpleDateFormat.parse(validateto);
        }
        if (!StringUtils.isBlank(validatefrom)) {
            fromDate = simpleDateFormat.parse(validatefrom);
        }
        OrderPublish order = OrderPublish.create(address, tokenid, type, toDate, fromDate, price, amount);
        store.saveOrderPublish(order);
        
        OrderBook orderBook = orderBookHolder.getOrderBookWithTokenId(tokenid);
        if (orderBook != null) {
            orderBook.enter(order.getOrderid(), type == 1 ? Side.SELL : Side.BUY, price, amount);
        }
        return AbstractResponse.createEmptyResponse();
    }
    
    @Autowired
    private OrderBookHolder orderBookHolder;
    
    @Autowired
    protected FullPrunedBlockStore store;

    public AbstractResponse getOrderList(Map<String, Object> request) throws BlockStoreException {
        List<OrderPublish> orders = this.store.getOrderPublishList(request);
        return GetOrderResponse.create(orders);
    }
}
