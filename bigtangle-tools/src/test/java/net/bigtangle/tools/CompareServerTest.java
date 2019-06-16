package net.bigtangle.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class CompareServerTest extends AbstractIntegrationTest {

    // buy everthing in test

    private static final String CHECHNUMBER = "2000";

    @Test
    public void diffThread() throws Exception {

        while (true) {
            diff(HTTPS_BIGTANGLE_INFO, HTTPS_BIGTANGLE_DE);
        
            Thread.sleep(30000);
        }

    }

    private void diff(String server, String server2) throws Exception {
        System.out.println(" start difference check " + server + "  :   " + server2 + "  "  );
        
        List<BlockEvaluationDisplay> l1 = getBlockInfos(server);

        List<BlockEvaluationDisplay> l2 = getBlockInfos(server2);
        for (BlockEvaluationDisplay b : l1) {
            BlockEvaluationDisplay s = find(l2, b);
            if (s != null) {
                if (s.getRating() != b.getRating() && Math.abs(s.getRating() - b.getRating()) > 30) {
                    System.out.println(server2 + "  " + s.toString());
                    System.out.println(server + "  " + b.toString());
                    // log.debug(server2 + " "+ s.toString());
                    // log.debug(server + " " + b.toString());
                }
            } else {
                
                try {
                    Block block = getBlock(server2, b.getBlockHexStr());
                    if(block==null)      System.out.println(" block from " + server + " not found in  " + server2 + "  " + b.toString());
                      }catch (Exception e) {
                          System.out.println(" block from " + server + " not found in  " + server2 + "  " + b.toString());
                      }
               
            }
        }

        for (BlockEvaluationDisplay b : l2) {
            BlockEvaluationDisplay s = find(l1, b);
            if (s == null) {
                //compare is not complete
                try {
              Block block = getBlock(server, b.getBlockHexStr());
              if(block==null)      System.out.println(" block from " + server2 + " not found in  " + server + "  " + b.toString());
                }catch (Exception e) {
 
                    System.out.println(" block from " + server2 + " not found in  " + server + "  " + b.toString());
                }
           
            }
        }

        System.out.println(" finish difference check " + server + "  :   " + server2 + "  "  );
    }

    private BlockEvaluationDisplay find(List<BlockEvaluationDisplay> l, BlockEvaluationDisplay b) throws Exception {

        for (BlockEvaluationDisplay b1 : l) {
            if (b1.getBlockHash().equals(b.getBlockHash())) {
                return b1;
            }
        }
        return null;
    }

    private List<BlockEvaluationDisplay> getBlockInfos(String server) throws Exception {
        String CONTEXT_ROOT = server;
        String lastestAmount = CHECHNUMBER;
        Map<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("lastestAmount", lastestAmount);
        String response = OkHttp3Util.postString(CONTEXT_ROOT + "/" + ReqCmd.searchBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return getBlockEvaluationsResponse.getEvaluations();
    }

    private Block getBlock(String server, String blockhash) throws Exception {
       
    
        Map<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("hashHex", blockhash);
        String response = OkHttp3Util.postString(server + "/" + ReqCmd.getBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return null;
    }

    
}
