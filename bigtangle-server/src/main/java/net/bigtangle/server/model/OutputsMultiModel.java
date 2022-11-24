/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import net.bigtangle.core.Sha256Hash;

public class OutputsMultiModel implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private String hash;

    private String toaddress;
    
    private long outputindex;
    
 
    
    public OutputsMultiModel() {
    }

    public OutputsMultiModel(Sha256Hash hash, String toaddress, long outputIndex) {
        this.hash = hash.toString();
        this.toaddress = toaddress;
        this.outputindex = outputIndex;
      
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getToaddress() {
        return toaddress;
    }

    public void setToaddress(String toaddress) {
        this.toaddress = toaddress;
    }

    public long getOutputindex() {
        return outputindex;
    }

    public void setOutputindex(long outputindex) {
        this.outputindex = outputindex;
    }

    
 
}
