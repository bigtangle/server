/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.utils.OkHttp3Util;

/**
 * ask other miner to get some missing blocks 1) the block can be imported
 * complete or initial start 2) block is missing in the database 3) The block is
 * published via Kafka stream.
 * 
 */
@Service
public class BlockRequester {
    private static final Logger log = LoggerFactory.getLogger(BlockRequester.class);

    private static final String CHECHNUMBER = "2000";

    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    TransactionService transactionService;

    @Autowired
    BlockService blockService;

    @Autowired
    protected ServerConfiguration serverConfiguration;

    public byte[] requestBlock(Sha256Hash hash) {
        // block from network peers
        // log.debug("requestBlock" + hash.toString());
        String[] re = serverConfiguration.getRequester().split(",");
        List<String> badserver = new ArrayList<String>();
        byte[] data = null;
        for (String s : re) {
            if (s != null && !"".equals(s.trim()) && !badserver(badserver, s)) {
                HashMap<String, String> requestParam = new HashMap<String, String>();
                requestParam.put("hashHex", Utils.HEX.encode(hash.getBytes()));
                try {
                    data = OkHttp3Util.post(s.trim() + "/" + ReqCmd.getBlock,
                            Json.jsonmapper().writeValueAsString(requestParam));
                    transactionService.addConnected(data, false);
                    break;
                } catch (Exception e) {
                    log.debug(s, e);

                    badserver.add(s);
                }
            }
        }
        return data;
    }

    public boolean badserver(List<String> badserver, String s) {
        for (String d : badserver) {
            if (d.equals(s))
                return true;
        }
        return false;
    }

    public void broadcastBlocks(long startheight, String kafkaserver) {

    }

    public void diff() throws Exception {
        String[] re = serverConfiguration.getRequester().split(",");
        for (String s : re) {
            diff(s);
        }
    }

    /*
     * check difference to remote server2 and get it.
     */
    public void diff(String server2) throws Exception {
        log.debug(" start difference check with " + server2);

        List<BlockEvaluationDisplay> l1 = getBlockInfos(server2);
            //sort increasing of height for add to connected 
        Collections.sort(l1, new Comparator<BlockEvaluationDisplay>() {
            public int compare(BlockEvaluationDisplay p1, BlockEvaluationDisplay p2) {
                return p1.getHeight() < p2.getHeight() ? -1 : 1;
            }
        });

        List<BlockEvaluationDisplay> l2 = getBlockInfos();
        for (BlockEvaluationDisplay b : l1) {
            BlockEvaluationDisplay s = find(l2, b);
            if (s == null) {
                // not found request
                try {
                    HashMap<String, String> requestParam = new HashMap<String, String>();
                    requestParam.put("hashHex", b.getBlockHexStr());
                    byte[] data = OkHttp3Util.post(server2 + "/" + ReqCmd.getBlock,
                            Json.jsonmapper().writeValueAsString(requestParam));
                   Optional<Block> block = transactionService.addConnected(data, true);
                } catch (Exception e) {
                    // TODO: handle exception
                }

            }
        }

        log.debug(" finish difference check " + server2 + "  ");
    }

    private BlockEvaluationDisplay find(List<BlockEvaluationDisplay> l, BlockEvaluationDisplay b) throws Exception {

        for (BlockEvaluationDisplay b1 : l) {
            if (b1.getBlockHash().equals(b.getBlockHash())) {
                return b1;
            }
        }
        return null;
    }

    private List<BlockEvaluationDisplay> getBlockInfos() throws Exception {

        Map<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("lastestAmount", CHECHNUMBER);
        return ((GetBlockEvaluationsResponse) blockService.searchBlock(requestParam)).getEvaluations();

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

}
