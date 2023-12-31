/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service.schedule;

import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.bitcoin.Secp256k1Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.kafka.BlockStreamHandler;
import net.bigtangle.server.config.ScheduleConfiguration;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.SyncBlockService;
import net.bigtangle.store.MySQLFullBlockStore;

@Component
@EnableAsync
public class ScheduleInitService {

	@Autowired
	private ScheduleConfiguration scheduleConfiguration;

	@Autowired
	private ServerConfiguration serverConfiguration;
	@Autowired
	NetworkParameters networkParameters;
	@Autowired
	protected transient DataSource dataSource;

	@Autowired
	private SyncBlockService syncBlockService;
	@Autowired
	BlockStreamHandler blockStreamHandler;
	private static final Logger logger = LoggerFactory.getLogger(ScheduleInitService.class);

	@Async
	@Scheduled(initialDelay = 1000, fixedDelay = Long.MAX_VALUE, timeUnit = TimeUnit.NANOSECONDS)
	public void syncService() throws BlockStoreException {

		try {
			logger.debug("server config: " + serverConfiguration.toString());
			logger.debug("Schedule: " + scheduleConfiguration.toString()); 
	 

			Secp256k1Context.getContext();
			if (scheduleConfiguration.isMilestone_active()) {
				try {
					logger.debug("syncBlockService startInit");
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

		} catch (Exception e) {
			logger.error("", e);
			// TODO sync checkpoint System.exit(-1);
		}
	}

}
