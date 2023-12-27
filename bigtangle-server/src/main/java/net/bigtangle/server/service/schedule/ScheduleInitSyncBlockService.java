/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.BeforeStartup;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.SyncBlockService;

@Component
@EnableAsync
public class ScheduleInitSyncBlockService {
    
    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private SyncBlockService syncBlockService;
    @Autowired
    ServerConfiguration serverConfiguration;
    private static final Logger logger = LoggerFactory.getLogger(BeforeStartup.class);
    @Async
    @Scheduled(initialDelay = 1000 , fixedDelay=Long.MAX_VALUE, timeUnit = TimeUnit.NANOSECONDS )
    public void syncService() throws BlockStoreException { 
            if (scheduleConfiguration.isInitSync()) {
                try {
                    logger.debug("syncBlockService startInit" );
                    syncBlockService.startInit();
                } catch (Exception e) {
                    logger.error("", e);
                    // TODO sync checkpoint System.exit(-1);
                }
            }
      

    }

}
