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
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.OrderReclaimService;

@Component
@EnableAsync
public class ScheduleOrderReclaimService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleOrderReclaimService.class);

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private OrderReclaimService orderReclaimService;
    @Autowired
    ServerConfiguration serverConfiguration;
    
    @Scheduled(fixedRate = 600000)
    public void updateMilestoneService() {
        if (scheduleConfiguration.isMilestone_active()&& serverConfiguration.checkService()) {
            orderReclaimService.updateOrderReclaim();
        }
    }

}
