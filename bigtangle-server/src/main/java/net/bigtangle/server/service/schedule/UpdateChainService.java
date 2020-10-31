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
import net.bigtangle.store.FullBlockGraph;

@Component
@EnableAsync
public class UpdateChainService {
    private static final Logger logger = LoggerFactory.getLogger(UpdateChainService.class);
    
     
    @Autowired
    ServerConfiguration serverConfiguration;
    @Autowired
    protected FullBlockGraph blockGraph;
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;
    @Async
    @Scheduled(fixedDelayString = "100")
    public void updateChain() {
        if (scheduleConfiguration.isMilestone_active() &&  serverConfiguration.checkService()) {
            try { 
                blockGraph.updateChain(); 
            } catch (Exception e) {
                logger.warn("updateConfirmService ", e);
            }
        }
    }
 
}
