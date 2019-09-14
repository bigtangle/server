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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.core.http.server.resp.GetBlockListResponse;
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
                    transactionService.addConnected(data, false, false);
                    break;
                } catch (Exception e) {
                    log.debug(s, e);

                    badserver.add(s);
                }
            }
        }
        return data;
    }

    public void requestBlocks(long chainlength) {
        String[] re = serverConfiguration.getRequester().split(",");
        List<String> badserver = new ArrayList<String>();

        for (String s : re) {
            if (s != null && !"".equals(s.trim()) && !badserver(badserver, s)) {
                HashMap<String, String> requestParam = new HashMap<String, String>();
                requestParam.put("start", chainlength + "");
                requestParam.put("end", chainlength + "");
                try {
                    String response = OkHttp3Util.postString(s.trim() + "/" + ReqCmd.blocksFromChainLength,
                            Json.jsonmapper().writeValueAsString(requestParam));
                    GetBlockListResponse blockbytelist = Json.jsonmapper().readValue(response,
                            GetBlockListResponse.class);
                    for (byte[] data : blockbytelist.getBlockbytelist()) {
                        transactionService.addConnected(data, false, false);
                    }
                    break;
                } catch (Exception e) {
                    log.debug(s, e);
                    badserver.add(s);
                }
            }
        }

    }

    public boolean badserver(List<String> badserver, String s) {
        for (String d : badserver) {
            if (d.equals(s))
                return true;
        }
        return false;
    }

    

    /*
     * switch chain select * from txreward where confirmed=1 chainlength with my
     * blockhash with remote ;
     */
    public void diff() throws Exception {
        String[] re = serverConfiguration.getRequester().split(",");
        for (String s : re) {
            if (s != null && !"".equals(s))
                diff(s.trim());
        }
    }

    /*
     * check difference to remote server2 and get it.
     * ask the remote getMaxConfirmedReward to compare the my getMaxConfirmedReward
     * if the remote has length > my length, then find the get the list of confirmed chains data.
     * match the block hash to find the sync chain length, then sync the chain data from 
     */
    public void diff(String server2) throws Exception {
        log.debug(" start difference check with " + server2);

        List<BlockEvaluationDisplay> remoteBlocks = getBlockInfos(server2);

        List<BlockEvaluationDisplay> localblocks = getBlockInfos();
        for (BlockEvaluationDisplay b : remoteBlocks) {
            BlockEvaluationDisplay s = find(localblocks, b);
            if (s == null) {
                // not found request
                try {
                    HashMap<String, String> requestParam = new HashMap<String, String>();
                    requestParam.put("hashHex", b.getBlockHexStr());
                    byte[] data = OkHttp3Util.post(server2 + "/" + ReqCmd.getBlock,
                            Json.jsonmapper().writeValueAsString(requestParam));
                    // Optional<Block> block =
                    transactionService.addConnected(data, true, false);
                    // first can not be added and the stop do the rest
                    // if(block.equals(Optional.empty())) {
                    // break;
                    // }

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

        requestParam.put("lastestAmount", "" + NetworkParameters.ALLOWED_SEARCH_BLOCKS);
        return ((GetBlockEvaluationsResponse) blockService.searchBlock(requestParam)).getEvaluations();

    }

    private List<BlockEvaluationDisplay> getBlockInfos(String server) throws Exception {
        String CONTEXT_ROOT = server;
        String lastestAmount = "" + NetworkParameters.ALLOWED_SEARCH_BLOCKS;
        Map<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("lastestAmount", lastestAmount);
        String response = OkHttp3Util.postString(CONTEXT_ROOT + "/" + ReqCmd.searchBlock.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return getBlockEvaluationsResponse.getEvaluations();
    }

}
