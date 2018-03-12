package com.bignetcoin.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected NetworkParameters networkParameters;
    
    private static final Logger logger = LoggerFactory.getLogger(TipsServiceTest.class);

    @Test
    public void testCreateTransaction() throws Exception {
        ECKey outKey = new ECKey();
        final Map<String, Object> reqParam0 = new HashMap<>();
        reqParam0.put("command", "askTransaction");
        reqParam0.put("pubkey", Utils.HEX.encode(outKey.getPubKey()));
        reqParam0.put("toaddressPubkey", Utils.HEX.encode(outKey.getPubKey()));
        reqParam0.put("amount", String.valueOf(100));
        reqParam0.put("tokenid", -1);
        
        MockHttpServletRequestBuilder httpRequest0 = post(contextRoot).content(toJson(reqParam0));
        MvcResult result0 = getMockMvc().perform(httpRequest0).andExpect(status().isOk()).andReturn();
        String jsonString0 = result0.getResponse().getContentAsString();
        logger.debug("接收askTransaction数据 : " + jsonString0);
        
        JSONObject jsonObject = new JSONObject(jsonString0);
        String blockHex = jsonObject.getString("blockHex");
        logger.debug("blockHex : " + blockHex);
        
        byte[] bytes = Utils.HEX.decode(blockHex);
        Block block = (Block) networkParameters.getDefaultSerializer().deserialize(ByteBuffer.wrap(bytes));
        // 交易签名
        for (Transaction t : block.getTransactions()) {
            t.addSigned(outKey);
        }
        // 解谜
        block.solve();
        
        final Map<String, Object> reqParam1 = new HashMap<>();
        reqParam1.put("command", "saveBlock");
        reqParam1.put("blockString", Utils.HEX.encode(block.bitcoinSerialize()));
        MockHttpServletRequestBuilder httpRequest1 = post(contextRoot).content(toJson(reqParam1));
        MvcResult result1 = getMockMvc().perform(httpRequest1).andExpect(status().isOk()).andReturn();
        String jsonString1 = result1.getResponse().getContentAsString();
        logger.debug("接收saveBlock数据 : " + jsonString1);
    }
}
