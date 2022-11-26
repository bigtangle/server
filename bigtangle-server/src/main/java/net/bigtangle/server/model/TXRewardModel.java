package net.bigtangle.server.model;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;

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

    public TXReward toTXReward() {
        TXReward t= new TXReward();
        t.setBlockHash(Sha256Hash.wrap(getBlockhash()));
        t.setConfirmed(isConfirmed());
        t.setSpent(isSpent());
        t.setPrevBlockHash(Sha256Hash.wrap(getPrevblockhash()) );
        t.setSpenderBlockHash(Sha256Hash.wrap(getPrevblockhash()) );
        t.setDifficulty( getDifficulty());
        t.setChainLength(getChainLength());
        toSpentBlock(t);
        return t;
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
