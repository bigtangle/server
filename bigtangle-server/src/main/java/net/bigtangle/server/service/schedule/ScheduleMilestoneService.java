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
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.server.service.TransactionService;

@Component
@EnableAsync
public class ScheduleMilestoneService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleMilestoneService.class);
    @Autowired
    private MilestoneService milestoneService;
    @Autowired
    private  TransactionService transactionService;
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Scheduled(fixedRateString = "${service.milestoneschedule.rate:10000}")
    public void updateMilestoneService() {
        if (scheduleConfiguration.isMilestone_active()) {
            try {
                logger.debug(" Start ScheduleMilestoneService: ");
                milestoneService.update();
  
            } catch (Exception e) {
                logger.warn("updateMilestoneService ", e);
            }
        }
    }
 
}
