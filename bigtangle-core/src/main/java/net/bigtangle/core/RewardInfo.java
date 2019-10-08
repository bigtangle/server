/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
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
