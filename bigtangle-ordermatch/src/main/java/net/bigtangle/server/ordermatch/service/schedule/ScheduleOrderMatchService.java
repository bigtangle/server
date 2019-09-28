/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.service.schedule;

import java.util.Iterator;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.core.Coin;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OTCOrder;
import net.bigtangle.core.Side;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.ordermatch.OrderBookEvents;
import net.bigtangle.server.ordermatch.bean.OrderMatch;
import net.bigtangle.server.ordermatch.config.ScheduleConfiguration;
import net.bigtangle.server.ordermatch.context.OrderBookHolder;
import net.bigtangle.server.ordermatch.store.FullPrunedBlockStore;
import net.bigtangle.server.utils.OrderBook;
import net.bigtangle.utils.OrderState;

@Component
@EnableAsync
public class ScheduleOrderMatchService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleOrderMatchService.class);

    @Autowired
    private OrderBookHolder orderBookHolder;

    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Scheduled(fixedRateString = "${service.orderMatchService.rate:5000}")
    public void updateMatchSchedule() {
        if (scheduleConfiguration.isOrdermatch_active()) {
            updateMatch();
        }
    }

    public void updateMatch() {

        try {
            logger.info("cal order match start");
            for (OrderBook orderBook : orderBookHolder.values()) {
//                String tokenSTR = tokens.getTokenid();
//                OrderBook orderBook = orderBookHolder.getOrderBookWithTokenId(tokenSTR);
//                if (orderBook == null) {
//                    orderBookHolder.addOrderBook(tokenSTR, orderBookHolder.createOrderBook());
//                    continue;
//                }
                try {
                    orderBook.lock();
                    OrderBookEvents orderBookEvents = (OrderBookEvents) orderBook.listener();
                    Iterator<OrderBookEvents.Event> iterator = orderBookEvents.collect().iterator();
                    while (iterator.hasNext()) {
                        OrderBookEvents.Event event = iterator.next();
                        if (event instanceof OrderBookEvents.Match) {
                            OrderBookEvents.Match match = (OrderBookEvents.Match) event;
                            logger.info("order match hit : " + match);
                            saveOrderMatch(match);

                            OTCOrder incomingOrder = this.store.getOrderPublishByOrderid(match.incomingOrderId);
                            OTCOrder restingOrder = this.store.getOrderPublishByOrderid(match.restingOrderId);
                            // TODO Here's the change orders Jiang
                            // sell side will get the system coin as token
                            if (match.incomingSide == Side.BUY) {
                                Exchange exchange = new Exchange(incomingOrder.getOrderId(), incomingOrder.getAddress(),
                                        incomingOrder.getTokenId(), String.valueOf(match.executedQuantity),
                                        restingOrder.getOrderId(), restingOrder.getAddress(),
                                        Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID),
                                        String.valueOf(match.executedQuantity * match.price / Coin.COIN.getValue().longValue()),
                                        new byte[0], incomingOrder.getMarket());
                                this.store.saveExchange(exchange);
                            } else {
                                Exchange exchange = new Exchange(restingOrder.getOrderId(), restingOrder.getAddress(),
                                        restingOrder.getTokenId(), String.valueOf(match.executedQuantity),
                                        incomingOrder.getOrderId(), incomingOrder.getAddress(),
                                        Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID),
                                        String.valueOf(match.executedQuantity * match.price /  Coin.COIN.getValue().longValue()),
                                        new byte[0], restingOrder.getMarket());
                                // add exchange to store
                                this.store.saveExchange(exchange);
                            }
                            this.store.updateOrderPublishState(incomingOrder.getOrderId(), OrderState.match.ordinal());
                            this.store.updateOrderPublishState(restingOrder.getOrderId(), OrderState.match.ordinal());
                            iterator.remove();
                        } else {
                            // Add add = (Add) event;
                            // System.out.println(tokenSTR + "," + add);
                        }
                    }
                } finally {
                    orderBook.unlock();
                }
            }
            // logger.debug("cal order match end");
        } catch (Exception e) {
            logger.error("cal order match error", e);
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
