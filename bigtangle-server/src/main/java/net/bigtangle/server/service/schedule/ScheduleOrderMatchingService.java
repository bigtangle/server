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

import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.service.OrdermatchService;

@Component
@EnableAsync
public class ScheduleOrderMatchingService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleOrderMatchingService.class);

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private OrdermatchService ordermatchService;
    
    @Scheduled(fixedRate = 30000)
    public void updateOrderMatching() {
        if (scheduleConfiguration.isMilestone_active()) {
            ordermatchService. updateOrderMatchingDo();
        }
    }


}
