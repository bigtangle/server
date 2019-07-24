/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.blockconfirm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "service")
public class ScheduleConfiguration {

    @Value("${giveMoneyService.active:true}")
    boolean giveMoneyServiceActive;
    @Value("${server.serverURL:http://loalhost:8088}")
    String serverURL;

    public boolean isGiveMoneyServiceActive() {
        return giveMoneyServiceActive;
    }

    public void setGiveMoneyServiceActive(boolean giveMoneyServiceActive) {
        this.giveMoneyServiceActive = giveMoneyServiceActive;
    }

    public String getServerURL() {
        return serverURL;
    }

    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }
}