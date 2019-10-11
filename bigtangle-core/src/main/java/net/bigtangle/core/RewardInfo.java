/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Set;

 

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class RewardInfo extends DataClass implements java.io.Serializable {

    private static final long serialVersionUID = 6516115233185538213L;
    private long chainlength;
    private Sha256Hash prevRewardHash;
    private Set<Sha256Hash> blocks;
    private long difficultyTargetReward;

    public RewardInfo() {
    }

    public RewardInfo(Sha256Hash prevRewardHash,long difficultyTargetReward, Set<Sha256Hash> blocks, long chainlength) {
        super();
        this.prevRewardHash = prevRewardHash;
        this.difficultyTargetReward=difficultyTargetReward;
        this.blocks = blocks;
        this.chainlength = chainlength;
    }


    /** Returns true if this objects getWork  is higher than the others. */
    public boolean moreWorkThan( RewardInfo other) {
        return getWork().compareTo(other.getWork()) > 0;
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
    public BigInteger getWork()  {
        BigInteger target = getDifficultyTargetAsInteger();
        return LARGEST_HASH.divide(target.add(BigInteger.ONE));
    }

    /**
     * Returns the difficulty target as a 256 bit value that can be compared to a SHA-256 hash. Inside a block the
     * target is represented using a compact form. If this form decodes to a value that is out of bounds, an exception
     * is thrown.
     */
    public BigInteger getDifficultyTargetAsInteger()  {
        BigInteger target = Utils.decodeCompactBits(difficultyTargetReward);
        return target;
    }
    
    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public void setPrevRewardHash(Sha256Hash prevRewardHash) {
        this.prevRewardHash = prevRewardHash;
    }

    public Sha256Hash getPrevRewardHash() {
        return prevRewardHash;
    }

    public Set<Sha256Hash> getBlocks() {
        return blocks;
    }

    public void setBlocks(Set<Sha256Hash> blocks) {
        this.blocks = blocks;
    }

    public long getChainlength() {
        return chainlength;
    }

    public void setChainlength(long chainlength) {
        this.chainlength = chainlength;
    }

    
    public long getDifficultyTargetReward() {
        return difficultyTargetReward;
    }

    public void setDifficultyTargetReward(long difficultyTargetReward) {
        this.difficultyTargetReward = difficultyTargetReward;
    }

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static RewardInfo parseChecked(byte[] buf) {
        try {
            return RewardInfo.parse(buf);
        } catch (IOException e) {
            // Cannot happen since checked before
            throw new RuntimeException(e);
        }
    }

    public static RewardInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        RewardInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, RewardInfo.class);
        return tokenInfo;
    }

    @Override
    public String toString() {
        return "RewardInfo [chainlength=" + chainlength + ", \n prevRewardHash=" + prevRewardHash
                + ", \n prevRewardDifficulty=" + difficultyTargetReward
                + ", \n referenced block size =" + blocks.size() + "]";
    }

}
