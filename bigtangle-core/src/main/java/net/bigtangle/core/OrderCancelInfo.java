/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

// This object being part of a signed transaction's data legitimates it
public class OrderCancelInfo implements java.io.Serializable {

    private static final long serialVersionUID = 5955604810374397496L;

    private Sha256Hash blockHash;

    public OrderCancelInfo() {
        super();
    }

    public OrderCancelInfo(Sha256Hash initialBlockHash) {
        super();

        this.blockHash = initialBlockHash;
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(Sha256Hash blockHash) {
        this.blockHash = blockHash;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static OrderCancelInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        OrderCancelInfo tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderCancelInfo.class);
        return tokenInfo;
    }

    public static OrderCancelInfo parseChecked(byte[] buf) {
        String jsonStr = new String(buf);
        OrderCancelInfo tokenInfo;
        try {
            tokenInfo = Json.jsonmapper().readValue(jsonStr, OrderCancelInfo.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tokenInfo;
    }
}
