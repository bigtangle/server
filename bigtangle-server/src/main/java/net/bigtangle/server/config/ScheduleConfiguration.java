/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "service")
public class ScheduleConfiguration {

    @Value("${milestoneschedule.active:false}")
    boolean milestone_active;

    @Value("${blockBatchService.active:false}")
    boolean blockBatchService_active;

    public boolean isMilestone_active() {
        return milestone_active;
    }

    public void setMilestone_active(boolean milestone_active) {
        this.milestone_active = milestone_active;
    }

    public boolean isBlockBatchService_active() {
        return blockBatchService_active;
    }

    public void setBlockBatchService_active(boolean blockBatchService_active) {
        this.blockBatchService_active = blockBatchService_active;
    }

  

}