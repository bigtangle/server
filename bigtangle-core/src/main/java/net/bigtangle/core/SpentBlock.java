/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import org.apache.commons.lang3.StringUtils;

/*
  * Block output dynamic evaluation data
  */
public class SpentBlock extends DataClass {
    private Sha256Hash blockHash;
    //confirmed=true, MCMC rating > 75 or block is on a milestone
    private boolean confirmed;
    private boolean spent;
    private Sha256Hash spenderBlockHash;
    //create time of the block output
    private long time;
    
    
    public void setDefault() {
        spent= false;
        confirmed= false;
        spenderBlockHash=null;
        time=System.currentTimeMillis() / 1000;
        
    }
    public void setBlockHashHex(String blockHashHex) {
        if (StringUtils.isNotBlank(blockHashHex))
            this.blockHash = Sha256Hash.wrap(blockHashHex);
    }
    public String getBlockHashHex() {
        return this.blockHash != null ? Utils.HEX.encode(this.blockHash.getBytes()) : "";
    }
     
    public Sha256Hash getBlockHash() {
        return blockHash;
    }
    public void setBlockHash(Sha256Hash blockHash) {
        this.blockHash = blockHash;
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
    public Sha256Hash getSpenderBlockHash() {
        return spenderBlockHash;
    }
    public void setSpenderBlockHash(Sha256Hash spenderBlockHash) {
        this.spenderBlockHash = spenderBlockHash;
    }


    public long getTime() {
        return time;
    }


    public void setTime(long time) {
        this.time = time;
    }

 
}
