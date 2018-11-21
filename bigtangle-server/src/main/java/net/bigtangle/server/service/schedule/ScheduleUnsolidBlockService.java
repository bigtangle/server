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

@Component
@EnableAsync
public class ScheduleUnsolidBlockService {
    private static final Logger logger = LoggerFactory.getLogger(ScheduleUnsolidBlockService.class);

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private  BlockService blockService;

    /*
     * unsolid blocks can be solid, if previous can be found  in  network etc.
     * read data from table oder by insert time,  use add Block to check again, 
     * if missing previous,  it may request network for the blocks 
     */
    @Scheduled(fixedRateString = "${service.updateUnsolideService.rate:5000}")
    public void updateUnsolideService() {
        if (scheduleConfiguration.isMilestone_active()) {
            try {
                logger.debug(" Start ScheduleMilestoneService: ");
                blockService. deleteOldUnsolidBlock();
                blockService.reCheckUnsolidBlock(); 
           
            } catch (Exception e) {
                logger.warn("updateUnsolideService ", e);
            }
        }
    }

}
