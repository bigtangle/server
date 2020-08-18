/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component

public class ScheduleConfiguration {

    @Value("${service.schedule.mcmc:false}")
    boolean milestone_active;
    @Value("${service.schedule.mcmcrate:500}")
    Long mcmcrate;
    @Value("${service.schedule.mining:false}")
    boolean mining;
    
    @Value("${service.schedule.blockbatch:false}")
    boolean blockBatchService_active;

    @Value("${service.schedule.miningrate:50000}")
    Long miningrate;
    @Value("${service.schedule.blockbatchrate:50000}")
    Long blockbatchrate;
    
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

    public Long getBlockbatchrate() {
        return blockbatchrate;
    }

    public void setBlockbatchrate(Long blockbatchrate) {
        this.blockbatchrate = blockbatchrate;
    }

    public Long getMcmcrate() {
        return mcmcrate;
    }

    public void setMcmcrate(Long mcmcrate) {
        this.mcmcrate = mcmcrate;
    }

  

}