/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import net.bigtangle.server.ordermatch.context.OrderBookHolder;

public class BeforeStartup implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        OrderBookHolder service = contextRefreshedEvent.getApplicationContext().getBean(OrderBookHolder.class);
        service.init();
    }
}
