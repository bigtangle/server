/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.order.match.OrderBook;
import net.bigtangle.order.match.OrderBookEvents;
import net.bigtangle.order.match.OrderBookEvents.Event;
import net.bigtangle.order.match.Side;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderBookTokenTest extends AbstractIntegrationTest {

    private HashMap<String, OrderBook> orderBookResult;

    @Before
    public void setUp() {
        orderBookResult = new HashMap<String, OrderBook>();
        orderBookResult.put(Utils.HEX.encode(NetworkParameters.BIGNETCOIN_TOKENID), this.createOrderBook());
    }
    
    public OrderBook createOrderBook() {
        OrderBookEvents events = new OrderBookEvents();
        return new OrderBook(events);
    }

    private OrderBook getOrderBookWithTokenId(String tokenSTR) {
        OrderBook orderBook = this.orderBookResult.get(tokenSTR);
        if (orderBook == null) {
            orderBook = this.createOrderBook();
            this.orderBookResult.put(tokenSTR, orderBook);
        }
        return orderBook;
    }
    
    @Test
    public void testOrderMatch() {
        String tokenSTR = Utils.HEX.encode(NetworkParameters.BIGNETCOIN_TOKENID);
        OrderBook book = this.getOrderBookWithTokenId(tokenSTR);
        book.enter(1, Side.SELL, 1001, 100);
        book.enter(2, Side.BUY,  1004,  50);
        book.enter(3, Side.BUY,  1003,  80);
        book.enter(4, Side.BUY,  1002,  50);
        book.enter(5, Side.SELL, 1001, 100);
        book.enter(6, Side.SELL, 1001, 100);
        book.enter(7, Side.SELL, 1001, 100);
        book.enter(8, Side.SELL, 1001, 100);
        book.enter(9, Side.SELL, 1001, 100);
        List<OrderBookEvents.Match> orderMatchs = new ArrayList<OrderBookEvents.Match>();
        OrderBookEvents orderBookEvents = (OrderBookEvents) book.listener();
        for (Event event : orderBookEvents.collect()) {
            if (event instanceof OrderBookEvents.Match) {
                OrderBookEvents.Match match = (OrderBookEvents.Match) event;
                System.out.println(match);
                orderMatchs.add(match);
            }
            else if (event instanceof OrderBookEvents.Add) {
                OrderBookEvents.Add add = (OrderBookEvents.Add) event;
                System.out.println(add);
            }
        }
        
    }
}
