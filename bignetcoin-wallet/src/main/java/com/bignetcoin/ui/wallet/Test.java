/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Json;
import net.bigtangle.core.UTXO;

public class Test {

    public static void main(String[] args) {
        String string = "{" + "\"duration\" : 0," + "\"outputs\" : [{\"address\":\"a1\"},{\"address\":\"a2\"} ],"
                + "\"tokens\" : {" + "\"1\" : {" + "\"value\" : 0," + "\"tokenid\" : 1" + "}" + "}" + "}";
        System.out.println(string);
        try {
            final Map<String, Object> data = Json.jsonmapper().readValue(string, Map.class);
            List list = (List) data.get("outputs");
            for (Object utxo : list) {
                if (utxo instanceof Map) {
                    for (Object object : ((Map) utxo).keySet()) {
                        System.out.println(object);
                    }

                }
            }
            Map map=(Map) data.get("tokens");
            if (map!=null&&!map.isEmpty()) {
                for (Object object : map.keySet()) {
                    System.out.println(object);
                }
            }
        } catch (JsonParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

}
