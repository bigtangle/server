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
import net.bigtangle.server.service.TransactionService;

@Component
@EnableAsync
public class ScheduleRewardService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleRewardService.class);

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private  TransactionService transactionService;

    @Scheduled(fixedDelay = 90000)
    public void updateReward() {
        if (scheduleConfiguration.isMilestone_active()) {
            try {
                logger.debug(" Start updateReward: ");
                //TODO check first
                //TODO separate ordermatch
                transactionService.createAndAddMiningRewardBlock();
    
            } catch (Exception e) {
                logger.warn("updateReward ", e);
            }
        }
    }

}
