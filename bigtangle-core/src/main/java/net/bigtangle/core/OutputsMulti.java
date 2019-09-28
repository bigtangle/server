/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class OutputsMulti {

    private Sha256Hash hash;

    private String toAddress;
    
    private long outputIndex;
    
 
    
    public OutputsMulti() {
    }

    public OutputsMulti(Sha256Hash hash, String toaddress, long outputIndex) {
        this.hash = hash;
        this.toAddress = toaddress;
        this.outputIndex = outputIndex;
      
    }

    public Sha256Hash getHash() {
        return hash;
    }

    public void setHash(Sha256Hash hash) {
        this.hash = hash;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public long getOutputIndex() {
        return outputIndex;
    }

    public void setOutputIndex(long outputIndex) {
        this.outputIndex = outputIndex;
    }

     
}
