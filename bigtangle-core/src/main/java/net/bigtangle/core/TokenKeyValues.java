/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class TokenKeyValues implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private List<KeyValue> keyvalues;
    
    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public static TokenKeyValues parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);
        return Json.jsonmapper().readValue(jsonStr, TokenKeyValues.class);
    }

    public List<KeyValue> getKeyvalues() {
        return keyvalues;
    }

    public void setKeyvalues(List<KeyValue> keyvalues) {
        this.keyvalues = keyvalues;
    }

}
