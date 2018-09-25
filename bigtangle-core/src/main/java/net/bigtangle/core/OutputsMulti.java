/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class OutputsMulti {

    private Sha256Hash hash;

    private String toAddress;
    
    private long outputIndex;
    
    private long minimumSignCount;
    
    public OutputsMulti() {
    }

    public OutputsMulti(Sha256Hash hash, String toaddress, long outputIndex, long minimumSign) {
        this.hash = hash;
        this.toAddress = toaddress;
        this.outputIndex = outputIndex;
        this.minimumSignCount = minimumSign;
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

    public long getMinimumSignCount() {
        return minimumSignCount;
    }

    public void setMinimumSignCount(long minimumSign) {
        this.minimumSignCount = minimumSign;
    }
}
