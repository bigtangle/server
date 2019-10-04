/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

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
  //  private   final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;
  
    @Autowired
    private OrderReclaimService orderReclaimService;
    @Autowired
    ServerConfiguration serverConfiguration;
    
    @Scheduled(fixedRate = 180000)
    public void updatemcmcService() {
        if (scheduleConfiguration.isMilestone_active()&& serverConfiguration.checkService()) {
            orderReclaimService.startSingleProcess();
        }
    }

}
