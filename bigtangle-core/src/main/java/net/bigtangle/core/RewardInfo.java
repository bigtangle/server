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
    private long fromHeight; 
    private long toHeight; 
    private Sha256Hash prevRewardHash;
    private Set<Sha256Hash> blocks;

    
    public RewardInfo() {
    }
    
    public RewardInfo(long fromHeight, long toHeight, Sha256Hash prevRewardHash, Set<Sha256Hash> blocks, long chainlength) {
        super();
        this.fromHeight = fromHeight;
        this.toHeight = toHeight;
        this.prevRewardHash = prevRewardHash;
        this.blocks = blocks;
        this.chainlength = chainlength;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public void setFromHeight(long fromHeight) {
        this.fromHeight = fromHeight;
    }

    public void setToHeight(long toHeight) {
        this.toHeight = toHeight;
    }

    public void setPrevRewardHash(Sha256Hash prevRewardHash) {
        this.prevRewardHash = prevRewardHash;
    }

    public long getFromHeight() {
        return fromHeight;
    }

    public long getToHeight() {
        return toHeight;
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

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static RewardInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        RewardInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, RewardInfo.class);
        return tokenInfo;
    }

    @Override
    public String toString() {
        return "RewardInfo [chainlength=" + chainlength + ", \n fromHeight=" + fromHeight + ", \n toHeight=" + toHeight
                + ", \n prevRewardHash=" + prevRewardHash + ", \n referenced block size =" + blocks.size() + "]";
    }

    
    
}
