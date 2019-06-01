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
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.core.http.server.resp.SettingResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class RequesterTests extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(RequesterTests.class);

 
    @Test
    public void testRequestBlock( ) throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        List<String> address = new ArrayList<String>();
        for (ECKey ecKey : keys) {
            address.add(ecKey.toAddress(networkParameters).toBase58());
        }
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("address", address);

        String response = OkHttp3Util.post(contextRoot + ReqCmd.searchBlock.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
       
        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("hashHex", Utils.HEX.encode(getBlockEvaluationsResponse.getEvaluations().get(0).getBlockHash().getBytes()));

        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block re = networkParameters.getDefaultSerializer().makeBlock(data);
        log.info("resp : " + re);

    }
 

}
