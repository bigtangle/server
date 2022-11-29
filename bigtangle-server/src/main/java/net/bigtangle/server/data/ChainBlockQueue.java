/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.data;

import java.util.Arrays;

import net.bigtangle.core.Utils;

public class ChainBlockQueue {

    private String hash;
    private byte[] block;
    private long inserttime;
    private long chainlength;
    private boolean orphan;

    public ChainBlockQueue() {

    }

    public ChainBlockQueue(String hash, byte[] block, long chainlength, boolean orphan, long inserttime) {
        super();
        this.hash = hash;
        this.block = block;
        this.inserttime = inserttime;
        this.chainlength = chainlength;
        this.orphan = orphan;
    }

 
    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public byte[] getBlock() {
        return block;
    }

    public void setBlock(byte[] block) {
        this.block = block;
    }

    public long getInserttime() {
        return inserttime;
    }

    public void setInserttime(long inserttime) {
        this.inserttime = inserttime;
    }

    public long getChainlength() {
        return chainlength;
    }

    public void setChainlength(long chainlength) {
        this.chainlength = chainlength;
    }

    public boolean isOrphan() {
        return orphan;
    }

    public void setOrphan(boolean orphan) {
        this.orphan = orphan;
    }

    
}
