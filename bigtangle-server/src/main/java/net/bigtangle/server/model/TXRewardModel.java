package net.bigtangle.server.model;

import net.bigtangle.core.Sha256Hash;

public class TXRewardModel extends SpentBlockModel implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private String prevblockhash;

    private long difficulty;
    private long chainLength;

    // this is for json
    public TXRewardModel() {

    }

    public TXRewardModel(Sha256Hash hash, boolean confirmed, boolean spent, Sha256Hash prevBlockHash,
            Sha256Hash spenderblockhash, long difficulty, long chainLength) {
        super();
        this.setBlockhash(hash.toString());
        this.setConfirmed(confirmed);
        this.setSpent(spent);
        this.prevblockhash = prevBlockHash.toString();
        this.setSpenderblockhash(spenderblockhash.toString());
        this.difficulty = difficulty;
        this.chainLength = chainLength;
    }

    public String getPrevblockhash() {
        return prevblockhash;
    }

    public void setPrevblockhash(String prevblockhash) {
        this.prevblockhash = prevblockhash;
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
