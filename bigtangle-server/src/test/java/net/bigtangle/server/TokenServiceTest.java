package net.bigtangle.server;

import java.util.HashMap;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.utils.OkHttp3Util;

public class TokenServiceTest {

    @Test
    public void create() throws JsonProcessingException, Exception {
        ECKey outKey = new ECKey();
        byte[] pubKey = outKey.getPubKey();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
        requestParam.put("amount", 164385643856L);
        requestParam.put("tokenname", "Test");
        requestParam.put("description", "Test");
        requestParam.put("blocktype", false);
        requestParam.put("tokenHex", Utils.HEX.encode(pubKey));

        String contextRoot = "http://localhost:8088/";
        OkHttp3Util.post(contextRoot + ReqCmd.createGenesisBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        
        System.out.println("Utils.HEX.encode(pubKey) = " + Utils.HEX.encode(pubKey));
    }
}
