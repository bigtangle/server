package net.bigtangle.server.data;

import net.bigtangle.core.Sha256Hash;

public class BlockPrototype {

    private Sha256Hash prevBlockHash;
    private Sha256Hash prevBranchBlockHash; // second predecessor
 
    private long time;

    
    public BlockPrototype(Sha256Hash prevBlockHash, Sha256Hash prevBranchBlockHash, long time) {
        super();
        this.prevBlockHash = prevBlockHash;
        this.prevBranchBlockHash = prevBranchBlockHash;
        this.time = time;
    }

    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }

    public void setPrevBlockHash(Sha256Hash prevBlockHash) {
        this.prevBlockHash = prevBlockHash;
    }

    public Sha256Hash getPrevBranchBlockHash() {
        return prevBranchBlockHash;
    }

    public void setPrevBranchBlockHash(Sha256Hash prevBranchBlockHash) {
        this.prevBranchBlockHash = prevBranchBlockHash;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
    
}
