package net.bigtangle.core;

import java.math.BigInteger;

import net.bigtangle.core.exception.VerificationException;

public class TXReward extends SpentBlock implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private Sha256Hash prevBlockHash;

    private long difficulty;
    private long chainLength;

    // this is for json
    public TXReward() {

    }

    public TXReward(Sha256Hash hash, boolean confirmed, boolean spent, Sha256Hash prevBlockHash,
            Sha256Hash spenderblockhash, long difficulty, long chainLength) {
        super();
        this.setBlockHash(hash);
        this.setConfirmed(confirmed);
        this.setSpent(spent);
        this.prevBlockHash = prevBlockHash;
        this.setSpenderBlockHash(spenderblockhash);
        this.difficulty = difficulty;
        this.chainLength = chainLength;
    }

    

    /**
     * The number that is one greater than the largest representable SHA-256
     * hash.
     */
    private static BigInteger LARGEST_HASH = BigInteger.ONE.shiftLeft(256);

    /**
     * Returns the work represented by this block.<p>
     *
     * Work is defined as the number of tries needed to solve a block in the
     * average case. Consider a difficulty target that covers 5% of all possible
     * hash values. Then the work of the block will be 20. As the target gets
     * lower, the amount of work goes up.
     */
    public BigInteger getWork() throws VerificationException {
        BigInteger target = getDifficultyTargetAsInteger();
        return LARGEST_HASH.divide(target.add(BigInteger.ONE));
    }
    
    /**
     * Returns the difficulty target as a 256 bit value that can be compared to a SHA-256 hash. Inside a block the
     * target is represented using a compact form. If this form decodes to a value that is out of bounds, an exception
     * is thrown.
     */
    public BigInteger getDifficultyTargetAsInteger() throws VerificationException {
        BigInteger target = Utils.decodeCompactBits(difficulty);
        return target;
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

    @Override
    public String toString() {
        return "TXReward [prevBlockHash=" + prevBlockHash + ", \n difficulty="
                + difficulty + ", \n chainLength=" + chainLength + "]";
    }

}
