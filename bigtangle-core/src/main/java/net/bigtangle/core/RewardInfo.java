/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class RewardInfo implements java.io.Serializable {

    private static final long serialVersionUID = 6516115233185538213L;

    private long fromHeight;
    private long toHeight;
    private String prevRewardHash;

    public RewardInfo() {
    }
    
    public RewardInfo(long fromHeight, long toHeight, String prevRewardHash) {
        super();
        this.fromHeight = fromHeight;
        this.toHeight = toHeight;
        this.prevRewardHash = prevRewardHash;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public long getFromHeight() {
        return fromHeight;
    }

    public long getToHeight() {
        return toHeight;
    }

    public String getPrevRewardHash() {
        return prevRewardHash;
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
        return "RewardInfo [fromHeight=" + fromHeight + ", toHeight=" + toHeight + ", prevRewardHash=" + prevRewardHash + "]";
    }
    
    
}
