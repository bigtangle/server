/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

/*
 * help to set meo string as key value list
 */
public class MemoInfo implements java.io.Serializable {
    private static final String MEMO = "memo";
    private static final long serialVersionUID = 6992138619113601243L;

    public MemoInfo() {
    }
    public MemoInfo(String memo) {
         keyvalues = new ArrayList<KeyValue>();
        KeyValue kv= new KeyValue();
        kv.setKey(MEMO);
        kv.setKey(memo);
        keyvalues.add(kv);
    }

    private List<KeyValue> keyvalues;

    public String toJson() throws JsonProcessingException {
        return Json.jsonmapper().writeValueAsString(this);

    }

    public static MemoInfo parse(String jsonStr) throws JsonParseException, JsonMappingException, IOException {

        return Json.jsonmapper().readValue(jsonStr, MemoInfo.class);
    }

    public List<KeyValue> getKeyvalues() {
        return keyvalues;
    }

    public void setKeyvalues(List<KeyValue> keyvalues) {
        this.keyvalues = keyvalues;
    }

}
