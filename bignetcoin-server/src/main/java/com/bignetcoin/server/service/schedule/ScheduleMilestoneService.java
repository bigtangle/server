/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.service.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.bignetcoin.server.service.MilestoneService;

@Service
public class ScheduleMilestoneService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleMilestoneService.class);
    @Autowired
    private MilestoneService milestoneService;

    @Scheduled(fixedRateString = "${service.milestoneschedule.rate:200000}")
    public void updateMilestoneService() {
        try {
          //  logger.debug("updateMilestoneService" );
            milestoneService.update();
        } catch (Exception e) {
        //    logger.warn("updateMilestoneService ", e);
        } 
        }
     
}
