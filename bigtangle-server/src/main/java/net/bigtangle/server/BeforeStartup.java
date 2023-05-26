/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import javax.sql.DataSource;

import org.bitcoin.Secp256k1Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.kafka.BlockStreamHandler;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.SyncBlockService;
import net.bigtangle.store.MySQLFullBlockStore;

@Component
public class BeforeStartup {

    private static final Logger logger = LoggerFactory.getLogger(BeforeStartup.class);

    @PostConstruct
    public void run() throws Exception {

        logger.debug("server config: " + serverConfiguration.toString());
        logger.debug("Schedule: " + scheduleConfiguration.toString());
        
        // set false in test
        if (serverConfiguration.getCreatetable()) {
            MySQLFullBlockStore store = new MySQLFullBlockStore(networkParameters,
                    dataSource.getConnection());
            try {
                store.create();
                // update tables to new version after initial setup
                store.updateDatabse();
            } finally {
                store.close();
            }
        }
   
        Secp256k1Context.getContext();
        if (scheduleConfiguration.isMilestone_active()) {
            try {
                logger.debug("syncBlockService startInit" );
                syncBlockService.startInit();
            } catch (Exception e) {
                logger.error("", e);
                // TODO sync checkpoint System.exit(-1);
            }
        }
        serverConfiguration.setServiceReady(true);
        if (serverConfiguration.getRunKafkaStream()) {
            blockStreamHandler.runStream();
        }

    }

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private SyncBlockService syncBlockService;

    @Autowired
    private ServerConfiguration serverConfiguration;
    @Autowired
    NetworkParameters networkParameters;
    @Autowired
    protected transient DataSource dataSource;

    @Autowired
    BlockStreamHandler blockStreamHandler;
  
}
