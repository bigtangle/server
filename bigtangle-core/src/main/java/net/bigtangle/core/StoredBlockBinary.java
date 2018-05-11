/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.Serializable;

public class StoredBlockBinary implements Serializable{

    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private byte[] blockBytes;
    private long height;
     
     
    public StoredBlockBinary(byte[] blockBytes, long height) {
        super();
        this.blockBytes = blockBytes;
        this.height = height;
    }
    public byte[] getBlockBytes() {
        return blockBytes;
    }
    public void setBlockBytes(byte[] blockBytes) {
        this.blockBytes = blockBytes;
    }
    public long getHeight() {
        return height;
    }
    public void setHeight(long height) {
        this.height = height;
    }
 
}
