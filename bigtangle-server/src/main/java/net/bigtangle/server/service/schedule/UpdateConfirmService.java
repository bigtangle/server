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
import net.bigtangle.server.service.MCMCService;
import net.bigtangle.store.FullBlockGraph;

@Component
@EnableAsync
public class UpdateConfirmService {
    private static final Logger logger = LoggerFactory.getLogger(UpdateConfirmService.class);
    
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;
    @Autowired
    ServerConfiguration serverConfiguration;
    @Autowired
    protected FullBlockGraph blockGraph;
    @Async
    @Scheduled(fixedDelayString = "${service.schedule.mcmcrate:1500}")
    public void updateConfirmService() {
        if (scheduleConfiguration.isMilestone_active() && serverConfiguration.checkService()) {
            try { 
                blockGraph.updateConfirmed(); 
            } catch (Exception e) {
                logger.warn("updateConfirmService ", e);
            }
        }
    }
 
}
