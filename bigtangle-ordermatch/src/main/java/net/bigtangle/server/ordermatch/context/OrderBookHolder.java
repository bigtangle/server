/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.context;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Tokens;
import net.bigtangle.server.ordermatch.bean.OrderBook;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents;
import net.bigtangle.server.ordermatch.bean.Side;
import net.bigtangle.server.service.OrderPublishService;
import net.bigtangle.server.service.TokensService;
import net.bigtangle.server.service.response.GetTokensResponse;

@Component
public class OrderBookHolder {

    public void init() {
        try {
            GetTokensResponse getTokensResponse = (GetTokensResponse) tokensService.getTokensList();
            ConcurrentHashMap<String, OrderBook> dataMap = new ConcurrentHashMap<String, OrderBook>();
            for (Tokens tokens : getTokensResponse.getTokens()) {
                this.putOrderBook(dataMap, tokens.getTokenid(), this.createOrderBook());
                logger.info("add order book tokenHex : {}, success", tokens.getTokenid());
            }
            this.dataMap = dataMap;
            List<OrderPublish> orderPublishs = this.orderPublishService.getOrderPublishListWithNotMatch();
            for (OrderPublish order : orderPublishs) {
                OrderBook orderBook = dataMap.get(order.getTokenid());
                if (orderBook == null) {
                    orderBook = this.createOrderBook();
                    this.addOrderBook(order.getTokenid(), orderBook);
                }
                orderBook.enter(order.getOrderid(), order.getType() == 1 ? Side.SELL : Side.BUY, order.getPrice(),
                        order.getAmount());
            }
        }
        catch (Exception e) {
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(OrderBookHolder.class);
    
    @Autowired
    private OrderPublishService orderPublishService;
    
    public void putOrderBook(ConcurrentHashMap<String, OrderBook> dataMap, String tokenHex, OrderBook orderBook) {
        dataMap.put(tokenHex, orderBook);
    }

    public OrderBook createOrderBook() {
        OrderBookEvents events = new OrderBookEvents();
        return new OrderBook(events);
    }

    public OrderBook getOrderBookWithTokenId(String tokenSTR) {
        OrderBook orderBook = this.dataMap.get(tokenSTR);
        return orderBook;
    }
    
    private ConcurrentHashMap<String, OrderBook> dataMap = new ConcurrentHashMap<String, OrderBook>();
    
    @Autowired
    private TokensService tokensService;

    public void addOrderBook(String tokenSTR, OrderBook orderBook) {
        this.dataMap.put(tokenSTR, orderBook);
    }
}
