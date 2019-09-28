package net.bigtangle.core;

public class TXReward extends SpentBlock implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private long toHeight;
    private Sha256Hash prevBlockHash;

    private long difficulty;
    private long chainLength;

    // this is for json
    public TXReward() {

    }

    public TXReward(Sha256Hash hash, boolean confirmed, boolean spent, long toHeight, Sha256Hash prevBlockHash,
            Sha256Hash spenderblockhash, long difficulty, long chainLength) {
        super();
        this.setBlockHash(hash);
        this.setConfirmed(confirmed);
        this.setSpent(spent);
        this.toHeight = toHeight;
        this.prevBlockHash = prevBlockHash;
        this.setSpenderBlockHash(spenderblockhash);
        this.difficulty = difficulty;
        this.chainLength = chainLength;
    }

     
    public long getToHeight() {
        return toHeight;
    }

    public void setToHeight(long toHeight) {
        this.toHeight = toHeight;
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

    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }

    public void setPrevBlockHash(Sha256Hash prevBlockHash) {
        this.prevBlockHash = prevBlockHash;
    }

    

}
