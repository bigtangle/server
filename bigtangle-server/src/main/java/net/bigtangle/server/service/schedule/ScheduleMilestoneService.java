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
import net.bigtangle.server.service.MilestoneService;

@Component
@EnableAsync
public class ScheduleMilestoneService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleMilestoneService.class);
    @Autowired
    private MilestoneService milestoneService;
  
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;
    @Autowired
    ServerConfiguration serverConfiguration;
    @Scheduled(fixedRate = 3000)
    public void updateMilestoneService() {
        if (scheduleConfiguration.isMilestone_active() && serverConfiguration.checkService()) {
            try {
                logger.debug(" Start ScheduleMilestoneService: ");
                milestoneService.update();
  
            } catch (Exception e) {
                logger.warn("updateMilestoneService ", e);
            }
        }
    }
 
}
