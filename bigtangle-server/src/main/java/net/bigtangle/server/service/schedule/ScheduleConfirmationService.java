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
import net.bigtangle.server.service.ConfirmationService;

@Component
@EnableAsync
public class ScheduleConfirmationService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleConfirmationService.class);
    @Autowired
    private ConfirmationService confirmationService;

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;
    @Autowired
    ServerConfiguration serverConfiguration;

    @Async
    @Scheduled(fixedRate = 2000)
    public void updatemcmcService() {
        if (scheduleConfiguration.isMilestone_active() && serverConfiguration.checkService()) {
            try {
                confirmationService.startSingleProcess();
            } catch (Exception e) {
                logger.warn("confirmationService ", e);
            }
        }
    }

}
