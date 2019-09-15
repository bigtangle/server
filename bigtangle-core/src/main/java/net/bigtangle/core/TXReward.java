package net.bigtangle.core;

public class TXReward  extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private byte[] hash;
    private boolean confirmed;
    private boolean spent;
    private long toHeight;
    private byte[] prevBlockHash;
    private byte[] spenderblockhash;
    private long difficulty;
    private long chainLength;
    
    //this is for json 
    public TXReward() {
        
    }
    public Sha256Hash getSha256Hash() {
        return Sha256Hash.wrap(hash);
    }
    
    public TXReward(byte[] hash, boolean confirmed, boolean spent, long toHeight, byte[] prevBlockHash,
            byte[] spenderblockhash, long difficulty, long chainLength) {
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
    public byte[] getHash() {
        return hash;
    }
    public void setHash(byte[] hash) {
        this.hash = hash;
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
    public long getToHeight() {
        return toHeight;
    }
    public void setToHeight(long toHeight) {
        this.toHeight = toHeight;
    }
    public byte[] getPrevBlockHash() {
        return prevBlockHash;
    }
    public void setPrevBlockHash(byte[] prevBlockHash) {
        this.prevBlockHash = prevBlockHash;
    }
    public byte[] getSpenderblockhash() {
        return spenderblockhash;
    }
    public void setSpenderblockhash(byte[] spenderblockhash) {
        this.spenderblockhash = spenderblockhash;
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
    
 
  
  

}
