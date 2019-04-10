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
import net.bigtangle.server.service.UnsolidBlockService;

@Component
@EnableAsync
public class ScheduleUnsolidBlockService {
    
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private UnsolidBlockService unsolidBlockService;
    @Autowired
    ServerConfiguration serverConfiguration;
    /*
     * unsolid blocks can be solid, if previous can be found in network etc.
     * read data from table oder by insert time, use add Block to check again,
     * if missing previous, it may request network for the blocks
     */
    @Scheduled(fixedRate = 5000)
    public void updateUnsolideService() {
        if (scheduleConfiguration.isMilestone_active() && serverConfiguration.checkService()) {
            unsolidBlockService.updateUnsolideServiceSingle();
        }

    }

}
