/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.concurrent.ConcurrentHashMap;

import net.bigtangle.core.Tokens;
import net.bigtangle.order.match.OrderBook;
import net.bigtangle.order.match.OrderBookEvents;
import net.bigtangle.server.response.GetTokensResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderBookHolder {

    public void init() {
        try {
            GetTokensResponse getTokensResponse = (GetTokensResponse) tokensService.getTokensList();
            ConcurrentHashMap<String, OrderBook> dataMap = new ConcurrentHashMap<String, OrderBook>();
            for (Tokens tokens : getTokensResponse.getTokens()) {
                this.putOrderBook(dataMap, tokens.getTokenHex(), this.createOrderBook());
                logger.info("add order book tokenHex : {}, success", tokens.getTokenHex());
            }
            this.dataMap = dataMap;
        }
        catch (Exception e) {
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(OrderBookHolder.class);
    
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
