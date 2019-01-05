/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.context;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bigtangle.core.Json;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.server.ordermatch.bean.OrderBook;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents;
import net.bigtangle.server.ordermatch.bean.Side;
import net.bigtangle.server.ordermatch.service.OrderPublishService;

@Component
public class OrderBookHolder {

    public void init() {
        try {
            ConcurrentHashMap<String, OrderBook> dataMap = new ConcurrentHashMap<String, OrderBook>();
            List<OrderPublish> orderPublishs = this.orderPublishService.getOrderPublishListWithNotMatch();
            for (OrderPublish order : orderPublishs) {
                OrderBook orderBook = dataMap.get(order.getTokenId());
                if (orderBook == null) {
                    orderBook = this.createOrderBook();
                    this.addOrderBook(order.getTokenId(), orderBook);
                }
                orderBook.enter(order.getOrderId(), order.getType() == 1 ? Side.SELL : Side.BUY, order.getPrice(),
                        order.getAmount());
                LOGGER.info("init orderbook : " + Json.jsonmapper().writeValueAsString(order));
            }
            this.dataMap = dataMap;
        }
        catch (Exception e) {
        }
    }
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderBookHolder.class);
    
    @Autowired
    private OrderPublishService orderPublishService;
    
    public void putOrderBook(ConcurrentHashMap<String, OrderBook> dataMap, String tokenHex, OrderBook orderBook) {
        dataMap.put(tokenHex, orderBook);
    }

    public OrderBook createOrderBook() {
        OrderBookEvents events = new OrderBookEvents();
        return new OrderBook(events);
    }
    
    public Collection<OrderBook> values() {
        return this.dataMap.values();
    }

    public OrderBook getOrderBookWithTokenId(String tokenSTR) {
        OrderBook orderBook = this.dataMap.get(tokenSTR);
        return orderBook;
    }
    
    private ConcurrentHashMap<String, OrderBook> dataMap = new ConcurrentHashMap<String, OrderBook>();
    
    public void addOrderBook(String tokenSTR, OrderBook orderBook) {
        this.dataMap.put(tokenSTR, orderBook);
    }
}
