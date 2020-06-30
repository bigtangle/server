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

    @Value("${schedule.mcmc:false}")
    boolean milestone_active;
    @Value("${schedule.mining:false}")
    boolean mining;
    
    @Value("${schedule.blockbatch:false}")
    boolean blockBatchService_active;

    @Value("${schedule.miningrate:50000}")
    Long miningrate;
    
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

    public boolean isMining() {
        return mining;
    }

    public void setMining(boolean mining) {
        this.mining = mining;
    }

    public Long getMiningrate() {
        return miningrate;
    }

    public void setMiningrate(Long miningrate) {
        this.miningrate = miningrate;
    }

  

}