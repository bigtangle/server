package net.bigtangle.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.core.http.server.resp.GetOutputsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
public class CompareServerTest extends AbstractIntegrationTest {

    private static final String CHECHNUMBER = "2000";

    @Test
    public void diffThread() throws Exception {

        while (true) {
            diff(HTTPS_BIGTANGLE_ORG, HTTPS_BIGTANGLE_DE);

            Thread.sleep(30000);
        }

    }

    private void diff(String server, String server2) throws Exception {
        System.out.println(" start difference check " + server + "  :   " + server2 + "  ");

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
                    if (block == null)
                        System.out.println(" block from " + server + " not found in  " + server2 + "  " + b.toString());
                } catch (Exception e) {
                    System.out.println(" " + b.toString());  e.printStackTrace();
                }

            }
        }

        for (BlockEvaluationDisplay b : l2) {
            BlockEvaluationDisplay s = find(l1, b);
            if (s == null) {
                // compare is not complete
                try {
                    Block block = getBlock(server, b.getBlockHexStr());
                    if (block == null)
                        System.out.println(" block from " + server2 + " not found in  " + server + "  " + b.toString());
                } catch (Exception e) {

                    System.out.println(" block from " + server2 + " not found in  " + server + "  " + b.toString());
                }

            }
        }

        System.out.println(" finish difference check " + server + "  :   " + server2 + "  ");
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
        byte[] data = OkHttp3Util.postAndGetBlock(server + ReqCmd.getBlock,
                Json.jsonmapper().writeValueAsString(requestParam));
        if (data != null) {
            return networkParameters.getDefaultSerializer().makeBlock(data);
        } else {
            return null;
        }

    }
    @Test 
    public void testCheckToken() throws JsonProcessingException, Exception {
        Wallet w = Wallet.fromKeys(networkParameters, ECKey.fromPrivate(Utils.HEX.decode(testPriv)));

        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(ETHTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(BTCTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(EURTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)));
        w.importKey(ECKey.fromPrivate(Utils.HEX.decode(JPYTokenPriv)));
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        for (ECKey k : w.walletKeys()) {
            requestParam.put("tokenid", k.getPublicKeyAsHex());
            String resp = OkHttp3Util.postString(contextRoot + ReqCmd.outputsbyToken.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
            log.info("getOutputsResponse : " + getOutputsResponse);
            Coin sumUnspent= Coin.valueOf(0l, k.getPubKey());
            Coin sumCoinbase= Coin.valueOf(0l, k.getPubKey());
            for(UTXO u:getOutputsResponse.getOutputs()) {
                if(!u.isCoinbase())
                sumCoinbase=  sumCoinbase. add(u.getValue());
                
                if(!u.isSpent())
                    sumUnspent=  sumUnspent. add(u.getValue());
            }
            log.info("sumUnspent : " + sumUnspent);
            log.info("sumCoinbase : " + sumCoinbase);
           assertTrue( sumUnspent.equals(sumCoinbase));
            // assertTrue(getOutputsResponse.getOutputs().get(0).getValue()
            // .equals(Coin.valueOf(77777L, walletKeys.get(0).getPubKey())));

        }

    }
}
