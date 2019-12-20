/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.server.service.OrderTickerService;

@Component
@EnableAsync
public class OrdertickerService {

    private static final Logger logger = LoggerFactory.getLogger(OrdertickerService.class);

    @Autowired
    protected OrderTickerService orderTickerService;

   
    @Scheduled(fixedRate = 10000) 
    public void cleanup() {
        orderTickerService.evictAllCacheValues();

    }
 
}
