/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import net.bigtangle.server.service.OrderBookHolder;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

public class BeforeStartup implements ApplicationListener<ContextRefreshedEvent> {

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        OrderBookHolder service = contextRefreshedEvent.getApplicationContext().getBean(OrderBookHolder.class);
        service.init();
    }
}
