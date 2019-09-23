/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.util.Arrays;

public class UnsolidBlock extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private byte[] hash;

    private long inserttime;
    private long reason;
    private byte[] missingdependency;
    private long height;
    private boolean directlymissing;
   
    public UnsolidBlock() {
        
    }
    public UnsolidBlock(byte[] hash, long inserttime, long reason, byte[] missingdependency, long height,
            boolean directlymissing) {
        super();
        this.hash = hash;
        this.inserttime = inserttime;
        this.reason = reason;
        this.missingdependency = missingdependency;
        this.height = height;
        this.directlymissing = directlymissing;
    }

    public Sha256Hash missingdependencyHash() {
        return Sha256Hash.wrap(missingdependency);
    }

    public byte[] getHash() {
        return hash;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public long getInserttime() {
        return inserttime;
    }

    public void setInserttime(long inserttime) {
        this.inserttime = inserttime;
    }

    public long getReason() {
        return reason;
    }

    public void setReason(long reason) {
        this.reason = reason;
    }

    public byte[] getMissingdependency() {
        return missingdependency;
    }

    public void setMissingdependency(byte[] missingdependency) {
        this.missingdependency = missingdependency;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public boolean isDirectlymissing() {
        return directlymissing;
    }

    public void setDirectlymissing(boolean directlymissing) {
        this.directlymissing = directlymissing;
    }

    @Override
    public String toString() {
        return "UnsolidBlock [hash=" + Arrays.toString(hash) + ", inserttime=" + inserttime + ", reason=" + reason
                + ", missingdependency=" + Arrays.toString(missingdependency) + ", height=" + height
                + ", directlymissing=" + directlymissing + "]";
    }

}
