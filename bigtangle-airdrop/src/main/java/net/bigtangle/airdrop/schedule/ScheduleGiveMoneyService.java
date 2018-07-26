/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop.schedule;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.airdrop.config.ScheduleConfiguration;
import net.bigtangle.airdrop.utils.GiveMoneyUtils;
import net.bigtangle.core.ECKey;

@Component
@EnableAsync
public class ScheduleGiveMoneyService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleGiveMoneyService.class);

    @Autowired
    private ScheduleConfiguration scheduleConfiguration;

    @Autowired
    private GiveMoneyUtils giveMoneyUtils;

    @Scheduled(fixedRateString = "${service.giveMoneyService.rate:10000}")
    public void updateMilestoneService() {
        if (scheduleConfiguration.isGiveMoneyServiceActive()) {
            try {
                logger.debug(" Start ScheduleGiveMoneyService");
                List<ECKey> ecKeys = new ArrayList<ECKey>();
                if (ecKeys.isEmpty()) {
                    return;
                }
                giveMoneyUtils.batchGiveMoneyToECKeyList(ecKeys);
            } catch (Exception e) {
                logger.warn("ScheduleGiveMoneyService", e);
            }
        }
    }
}
