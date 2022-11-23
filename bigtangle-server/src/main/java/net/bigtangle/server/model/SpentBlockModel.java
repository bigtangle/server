/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import org.apache.commons.lang3.StringUtils;

/*
  * Block output dynamic evaluation data
  */
public class SpentBlockModel   {
    private String blockhash;
    //confirmed=true, MCMC rating > 75 or block is on a milestone
    private boolean confirmed;
    private boolean spent;
    private String spenderblockhash;
    //create time of the block output
    private long time;
    
    
    public String getBlockhash() {
        return blockhash;
    }
    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
    }
    public boolean isConfirmed() {
        return confirmed;
    }
    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
    public boolean isSpent() {
        return spent;
    }
    public void setSpent(boolean spent) {
        this.spent = spent;
    }
    public String getSpenderblockhash() {
        return spenderblockhash;
    }
    public void setSpenderblockhash(String spenderblockhash) {
        this.spenderblockhash = spenderblockhash;
    }
    public long getTime() {
        return time;
    }
    public void setTime(long time) {
        this.time = time;
    }
    
    
 
}
