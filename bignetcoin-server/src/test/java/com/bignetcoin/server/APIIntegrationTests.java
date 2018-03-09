/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.bitcoinj.core.ECKey;
import org.junit.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class APIIntegrationTests extends AbstractIntegrationTest {

    /**
     * curl http://localhost:14265 \ -X POST \ -H 'Content-Type:
     * application/json' \ -d '{"command": "getBalances", "addresses":
     * ["HBBYKAKTILIPVUKFOTSLHGENPTXYBNKXZFQFR9VQFWNBMTQNRVOUKPVPRNBSZVVILMAFBKOTBLGLWLOHQ"],
     * "threshold": 100}'
     * 
     * @throws Exception
     */
    @Test
    public void testGetBalances() throws Exception {
        ECKey key = new ECKey();
        String addr = "030d8952f6c079f60cd26eb3ba83cf16a81c51fc8e47b767721fa38b5e20092a75";
        final Map<String, Object> request = new HashMap<>();
        request.put("command", "getBalances");
        request.put("addresses", new String[] { addr });
        request.put("threshold", 100);

        MockHttpServletRequestBuilder httprequest = post(contextRoot).content(toJson(request));
        MvcResult s = getMockMvc().perform(httprequest).andExpect(status().isOk())
                // .andExpect(jsonPath("$._links.self.href",
                // CoreMatchers.is(expectedLink)))
                .andReturn();
        System.out.println(s.getResponse().getContentAsString());
    }

}
