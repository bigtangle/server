/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.service.schedule;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.core.OrderPublish;
import net.bigtangle.server.ordermatch.bean.OrderBook;
import net.bigtangle.server.ordermatch.context.OrderBookHolder;
import net.bigtangle.server.ordermatch.store.FullPrunedBlockStore;

@Component
@EnableAsync
public class ScheduleOrderRemoveService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleOrderRemoveService.class);

    @Autowired
    private OrderBookHolder orderBookHolder;

    @Autowired
    protected FullPrunedBlockStore store;
    
    @Scheduled(cron = "0 0 0 * * ?")
    public void updateRemoveSchedule() {
        try {
            logger.info("cal order match start");
            List<OrderPublish> list = this.store.getOrderPublishListRemoveDaily(2);
            for (OrderPublish orderPublish : list) {
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
        } catch (Exception e) {
            logger.error("cal order match error", e);
        }
    }
}
