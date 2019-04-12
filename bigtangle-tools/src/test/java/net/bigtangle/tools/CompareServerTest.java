package net.bigtangle.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class CompareServerTest extends AbstractIntegrationTest {

    // buy everthing in test

    @Test
    public void diffThread() throws Exception {

        while (true) {
            diff("https://bigtangle.org", "https://bigtangle.info");
            Thread.sleep(100000);
        }

    }

    private void diff(String server, String server2) throws Exception {
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
                System.out.println(" block from " + server + " not found in  " + server2 + "  " + b.toString());
            }
        }

        for (BlockEvaluationDisplay b : l2) {
            BlockEvaluationDisplay s = find(l1, b);
            if (s == null)
                System.out.println(" block from " + server2 + " not found in  " + server + "  " + b.toString());
        }

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
        String lastestAmount = "200";
        Map<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("lastestAmount", lastestAmount);
        String response = OkHttp3Util.postString(CONTEXT_ROOT + "/" + ReqCmd.searchBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return getBlockEvaluationsResponse.getEvaluations();
    }

}
