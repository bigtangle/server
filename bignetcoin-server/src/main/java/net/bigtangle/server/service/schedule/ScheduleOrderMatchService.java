/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

import java.util.Iterator;

import net.bigtangle.core.Tokens;
import net.bigtangle.order.match.OrderBook;
import net.bigtangle.order.match.OrderBookEvents;
import net.bigtangle.server.response.GetTokensResponse;
import net.bigtangle.server.service.OrderBookHolder;
import net.bigtangle.server.service.TokensService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduleOrderMatchService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleOrderMatchService.class);

    @Autowired
    private OrderBookHolder orderBookHolder;

    @Autowired
    private TokensService tokensService;

    @Scheduled(fixedRateString = "10000")
    public void updateMatch() {
        try {
            logger.debug("cal order match start");
            GetTokensResponse getTokensResponse = (GetTokensResponse) tokensService.getTokensList();
            for (Tokens tokens : getTokensResponse.getTokens()) {
                String tokenSTR = tokens.getTokenHex();
                OrderBook orderBook = orderBookHolder.getOrderBookWithTokenId(tokenSTR);
                if (orderBook == null) {
                    orderBookHolder.addOrderBook(tokenSTR, orderBookHolder.createOrderBook());
                    continue;
                }
                try {
                    orderBook.lock();
                    OrderBookEvents orderBookEvents = (OrderBookEvents) orderBook.listener();
                    Iterator<OrderBookEvents.Event> iterator = orderBookEvents.collect().iterator();
                    while (iterator.hasNext()) {
                        OrderBookEvents.Event event = iterator.next();
                        if (event instanceof OrderBookEvents.Match) {
                            OrderBookEvents.Match match = (OrderBookEvents.Match) event;
                            logger.debug("order match hit : " + match);
                            iterator.remove();
                        }
                    }
                } finally {
                    orderBook.unlock();
                }
            }
            logger.debug("cal order match end");
        } catch (Exception e) {
            logger.warn("cal order match error", e);
        }
    }

}
