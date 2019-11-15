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
 * help to set memo string as key value list
 */
public class MemoInfo implements java.io.Serializable {
    private static final String MEMO = "memo";
    private static final long serialVersionUID = 6992138619113601243L;

    public MemoInfo() {
    }

    public MemoInfo(String memo) {
        kv = new ArrayList<KeyValue>();
        KeyValue keyValue = new KeyValue();
        keyValue.setKey(MEMO);
        keyValue.setValue(memo);
        kv.add(keyValue);
    }

    private List<KeyValue> kv;

    public String toJson() throws JsonProcessingException {
        return Json.jsonmapper().writeValueAsString(this);

    }

    public static MemoInfo parse(String jsonStr) throws JsonParseException, JsonMappingException, IOException {

        return Json.jsonmapper().readValue(jsonStr, MemoInfo.class);
    }

    public static String parseToString(String jsonStr) {
        try {
            MemoInfo m = Json.jsonmapper().readValue(jsonStr, MemoInfo.class);
            String s = "";
            for (KeyValue keyvalue : m.getKv()) {
                if (keyvalue.getValue() != null && keyvalue.getKey() != null && !keyvalue.getKey().equals("null")) {
                    s += keyvalue.getKey() + ": " + keyvalue.getValue() + " \n";
                }
            }
            return s;
        } catch (Exception e) {
            return jsonStr;
        }
    }

    public List<KeyValue> getKv() {
        return kv;
    }

    public void setKv(List<KeyValue> kv) {
        this.kv = kv;
    }

}
