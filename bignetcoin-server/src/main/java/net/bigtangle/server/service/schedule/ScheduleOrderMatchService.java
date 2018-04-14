/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

import java.util.Iterator;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderMatch;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Utils;
import net.bigtangle.order.match.OrderBook;
import net.bigtangle.order.match.OrderBookEvents;
import net.bigtangle.order.match.Side;
import net.bigtangle.server.response.GetTokensResponse;
import net.bigtangle.server.service.OrderBookHolder;
import net.bigtangle.server.service.TokensService;
import net.bigtangle.store.FullPrunedBlockStore;

@Service
public class ScheduleOrderMatchService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleOrderMatchService.class);

    @Autowired
    private OrderBookHolder orderBookHolder;

    @Autowired
    private TokensService tokensService;

    @Autowired
    protected FullPrunedBlockStore store;

    @Scheduled(fixedRateString = "10000")
    public void updateMatch() {
        try {
            // logger.debug("cal order match start");
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
                            saveOrderMatch(match);

                            OrderPublish incomingOrder = this.store.getOrderPublishByOrderid(match.incomingOrderId);
                            OrderPublish restingOrder = this.store.getOrderPublishByOrderid(match.restingOrderId);
                            // TODO Here's the change orders Jiang
                            // sell side will get the system coin as token
                            if (match.incomingSide == Side.SELL) {
                                Exchange exchange = new Exchange(incomingOrder.getAddress(), incomingOrder.getTokenid(),
                                        String.valueOf(match.executedQuantity), restingOrder.getAddress(),
                                        Utils.HEX.encode(NetworkParameters.BIGNETCOIN_TOKENID),
                                        String.valueOf(match.executedQuantity * match.price), new byte[0]);
                                this.store.saveExchange(exchange);
                            } else {
                                Exchange exchange = new Exchange(restingOrder.getAddress(), restingOrder.getTokenid(),
                                        String.valueOf(match.executedQuantity), incomingOrder.getAddress(),
                                        Utils.HEX.encode(NetworkParameters.BIGNETCOIN_TOKENID),
                                        String.valueOf(match.executedQuantity * match.price), new byte[0]);
                                //add exchange to store
                                this.store.saveExchange(exchange);
                            }

                            iterator.remove();
                        }
                    }
                } finally {
                    orderBook.unlock();
                }
            }
            // logger.debug("cal order match end");
        } catch (Exception e) {
            logger.warn("cal order match error", e);
        }
    }

    public void saveOrderMatch(OrderBookEvents.Match match) throws BlockStoreException {
        OrderMatch orderMatch = new OrderMatch();
        orderMatch.setMatchid(UUID.randomUUID().toString().replaceAll("-", ""));
        orderMatch.setRestingOrderId(match.restingOrderId);
        orderMatch.setIncomingOrderId(match.incomingOrderId);
        orderMatch.setType(match.incomingSide == Side.SELL ? 1 : 0);
        orderMatch.setPrice(match.price);
        orderMatch.setExecutedQuantity(match.executedQuantity);
        orderMatch.setRemainingQuantity(match.remainingQuantity);
        this.store.saveOrderMatch(orderMatch);
    }

}
