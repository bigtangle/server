/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools;

import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.SettingResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class TokenAndPayTests extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TokenAndPayTests.class);

    public void testMultiSignByJson() throws Exception {
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid("111111");
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress("222222");
        multiSignBy0.setPublickey("33333");
        multiSignBy0.setSignature("44444");
        multiSignBies.add(multiSignBy0);

        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);

        String jsonStr = Json.jsonmapper().writeValueAsString(multiSignByRequest);
        log.info(jsonStr);

        multiSignByRequest = Json.jsonmapper().readValue(jsonStr, MultiSignByRequest.class);
        for (MultiSignBy multiSignBy : multiSignByRequest.getMultiSignBies()) {
            log.info(Json.jsonmapper().writeValueAsString(multiSignBy));
        }
    }

    @Test
    public void testClientVersion() throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String resp = OkHttp3Util.postString(contextRoot + ReqCmd.version.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        SettingResponse settingResponse = Json.jsonmapper().readValue(resp, SettingResponse.class);
        String version = settingResponse.getVersion();
        assertTrue(version.equals("0.3.5.0"));
    }

    
  

    public void testRequestBlock(Block block) throws Exception {

        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("hashHex", Utils.HEX.encode(block.getHash().getBytes()));

        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block re = networkParameters.getDefaultSerializer().makeBlock(data);
        log.info("resp : " + re);

    }

    public Block nextBlockSerializer(ByteBuffer byteBuffer) {
        int len = byteBuffer.getInt();
        byte[] data = new byte[len];
        byteBuffer.get(data);
        Block r1 = networkParameters.getDefaultSerializer().makeBlock(data);
        log.debug("block len : " + len + " conv : " + r1.getHashAsString());
        return r1;
    }

    public byte[] getAskTransactionBlock() throws JsonProcessingException, Exception {
        final Map<String, Object> request = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(request));
        return data;
    }

    public void reqCmdSaveBlock(Block block) throws Exception, UnsupportedEncodingException {
        String data = OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        log.info("testSaveBlock resp : " + data);
        checkResponse(data);
    }

}
