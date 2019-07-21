/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.airdrop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "service")
public class ScheduleConfiguration {

    @Value("${giveMoneyService.active:true}")
    boolean giveMoneyServiceActive;

    public boolean isGiveMoneyServiceActive() {
        return giveMoneyServiceActive;
    }

    public void setGiveMoneyServiceActive(boolean giveMoneyServiceActive) {
        this.giveMoneyServiceActive = giveMoneyServiceActive;
    }
}