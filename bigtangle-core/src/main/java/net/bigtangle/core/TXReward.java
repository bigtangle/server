package net.bigtangle.core;

public class TXReward  extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private Sha256Hash hash;
    private boolean confirmed;
    private boolean spent;
    private long toHeight;
    private Sha256Hash prevBlockHash;
    private Sha256Hash spenderblockhash;
    private long difficulty;
    private long chainLength;
    
 
  
    public TXReward(Sha256Hash hash, boolean confirmed, boolean spent, long toHeight, Sha256Hash prevBlockHash,
            Sha256Hash spenderblockhash, long difficulty, long chainLength) {
        super();
        this.hash = hash;
        this.confirmed = confirmed;
        this.spent = spent;
        this.toHeight = toHeight;
        this.prevBlockHash = prevBlockHash;
        this.spenderblockhash = spenderblockhash;
        this.difficulty = difficulty;
        this.chainLength = chainLength;
    }
    public Sha256Hash getSpenderblockhash() {
        return spenderblockhash;
    }
    public void setSpenderblockhash(Sha256Hash spenderblockhash) {
        this.spenderblockhash = spenderblockhash;
    }
    public boolean isSpent() {
        return spent;
    }
    public void setSpent(boolean spent) {
        this.spent = spent;
    }
    public Sha256Hash getHash() {
        return hash;
    }
    public void setHash(Sha256Hash hash) {
        this.hash = hash;
    }
    public long getToHeight() {
        return toHeight;
    }
    public void setToHeight(long toHeight) {
        this.toHeight = toHeight;
    }
    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }
    public void setPrevBlockHash(Sha256Hash prevBlockHash) {
        this.prevBlockHash = prevBlockHash;
    }
    public long getDifficulty() {
        return difficulty;
    }
    public void setDifficulty(long difficulty) {
        this.difficulty = difficulty;
    }
    public long getChainLength() {
        return chainLength;
    }
    public void setChainLength(long chainLength) {
        this.chainLength = chainLength;
    }
    public boolean isConfirmed() {
        return confirmed;
    }
    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

}
