/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.AVGPriceService;

@Component
@EnableAsync
public class ScheduleAVGPriceService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleAVGPriceService.class);
    @Autowired
    private AVGPriceService aVGPriceService;

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;
    @Autowired
    ServerConfiguration serverConfiguration;

    @Async
    @Scheduled(cron = "0 0 1 * * ?")
    public void updatemcmcService() {
        if (scheduleConfiguration.isMilestone_active() && serverConfiguration.checkService()) {
            try {
                // logger.debug(" Start SchedulemcmcService: ");
                aVGPriceService.startSingleProcessCalAdd();

            } catch (Exception e) {
                logger.warn("updatemcmcService ", e);
            }
        }
    }

}
