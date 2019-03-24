/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class OrderMatchingInfo extends DataClass implements java.io.Serializable {

    private static final long serialVersionUID = 6516115233185538213L;

    private long fromHeight; 
    private long toHeight; 
    private Sha256Hash prevHash;

    public OrderMatchingInfo() {
    }
    
    public OrderMatchingInfo(long fromHeight, long toHeight, Sha256Hash prevHash) {
        super();
        this.fromHeight = fromHeight;
        this.toHeight = toHeight;
        this.prevHash = prevHash;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public long getFromHeight() {
        return fromHeight;
    }

    public void setFromHeight(long fromHeight) {
        this.fromHeight = fromHeight;
    }

    public long getToHeight() {
        return toHeight;
    }

    public void setToHeight(long toHeight) {
        this.toHeight = toHeight;
    }

    public void setPrevHash(Sha256Hash prevHash) {
        this.prevHash = prevHash;
    }

    public Sha256Hash getPrevHash() {
        return prevHash;
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

    public static OrderMatchingInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderMatchingInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderMatchingInfo.class);
        return tokenInfo;
    }
    
}
