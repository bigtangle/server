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
import net.bigtangle.server.service.BlockService;
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
    private  BlockService blockService;
    
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

    /*
     * unsolid blocks can be solid, if previous can be found  in  network etc.
     * read data from table oder by insert time,  use add Block to check again, 
     * if missing previous,  it may request network for the blocks 
     */
    @Scheduled(fixedRateString = "${service.updateUnsolideService.rate:30000}")
    public void updateUnsolideService() {
        
            try {
                logger.debug(" Start ScheduleMilestoneService: ");
                blockService. deleteOldUnsolidBlock();
                blockService.reCheckUnsolidBlock(); 
           
            } catch (Exception e) {
                logger.warn("updateUnsolideService ", e);
            }
         
    }

}
