/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.tools.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.Token;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.response.GetBalancesResponse;
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
        blockhashs.add( 
                "00004c2b55b4cb4d53c89a5524546354a15e85965b9827e330265d4264e31d14");
        requestParam.put("blockhashs", blockhashs);

        contextRoot = "https://p.bigtangle.org:8088/";

        String response = OkHttp3Util.postString(contextRoot + ReqCmd.searchBlockByBlockHashs.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        List<BlockEvaluationDisplay> blockEvaluations = getBlockEvaluationsResponse.getEvaluations();
        System.out.print(blockEvaluations.toString());
    }
    
    

    @Test
    public void historyUTXOList() throws Exception {
        String addressString="15tzBMvBPobE4Jg5TeUCPtRFxLcJJF5YDY";
        Map<String, String> param = new HashMap<String, String>();
       
        param.put("toaddress", addressString);
        
        String response = OkHttp3Util.postString("https://p.bigtangle.de:8088/" + ReqCmd.getOutputsHistory.name(),
                Json.jsonmapper().writeValueAsString(param));
        
       
        GetBalancesResponse balancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        Map<String, Token> tokennames = balancesResponse.getTokennames();
        int h=0;
        int my=0;
        for (UTXO utxo : balancesResponse.getOutputs()) {
            if(utxo.isSpent()) {h++;}
            
   
        }
      //  assertTrue(h>0);
        assertTrue(my==1);
    }
}
