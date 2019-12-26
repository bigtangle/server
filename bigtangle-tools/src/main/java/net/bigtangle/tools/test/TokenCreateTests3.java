/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.response.GetBlockEvaluationsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class TokenCreateTests3 extends HelpTest {

    // 18TiXgUW913VFs3nqak6QAadTS7EYL6mGg

    @Test
    public void testTokens() throws JsonProcessingException, Exception {

        walletAppKit1.wallet().setServerURL("https://p.bigtangle.de:8088/");
        walletAppKit1.wallet().getDomainNameBlockHash("test.shop");

    }

    @Test
    public void testRating() throws JsonProcessingException, Exception {

        Map<String, Object> requestParam = new HashMap<String, Object>();

        List<String> blockhashs = new ArrayList<String>();
        blockhashs.add("000019a19012fb15f6694f1807c377facbf1e3c75041dbb9a68276c605aaacdb");
        requestParam.put("blockhashs", blockhashs);

        contextRoot = "https://p.bigtangle.org:8088/";

        String response = OkHttp3Util.postString(contextRoot + ReqCmd.searchBlockByBlockHashs.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        List<BlockEvaluationDisplay> blockEvaluations = getBlockEvaluationsResponse.getEvaluations();

    }
}
