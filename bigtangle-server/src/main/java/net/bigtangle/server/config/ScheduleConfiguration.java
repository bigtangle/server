/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "schedule")
public class ScheduleConfiguration {

    @Value("${milestone.active:false}")
    boolean milestone_active;

    @Value("${ordermatch.active:false}")
    boolean ordermatch_active;

    public boolean isMilestone_active() {
        return milestone_active;
    }

    public void setMilestone_active(boolean milestone_active) {
        this.milestone_active = milestone_active;
    }

    public boolean isOrdermatch_active() {
        return ordermatch_active;
    }

    public void setOrdermatch_active(boolean ordermatch_active) {
        this.ordermatch_active = ordermatch_active;
    }

}