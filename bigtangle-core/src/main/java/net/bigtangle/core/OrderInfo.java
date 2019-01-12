/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class OrderInfo implements java.io.Serializable {

    // TODO drop string from everywhere, stop using sha256hash.wrap, stop using jsonserialization!

    private long fromHeight; // TODO slated for removal, can be inferred
    private long toHeight; // TODO remove or allow variable toHeights
    private Sha256Hash prevRewardHash;

    public OrderInfo() {
    }
    
    public OrderInfo(long fromHeight, long toHeight, Sha256Hash prevRewardHash) {
        super();
        this.fromHeight = fromHeight;
        this.toHeight = toHeight;
        this.prevRewardHash = prevRewardHash;
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

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static OrderInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderInfo.class);
        return tokenInfo;
    }
    
}
