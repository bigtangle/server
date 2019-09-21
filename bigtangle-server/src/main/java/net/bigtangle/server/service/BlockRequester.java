/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.http.server.resp.GetBlockEvaluationsResponse;
import net.bigtangle.core.http.server.resp.GetBlockListResponse;
import net.bigtangle.core.http.server.resp.GetTXRewardListResponse;
import net.bigtangle.core.http.server.resp.GetTXRewardResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
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
    protected BlockService blockService;
    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected FullPrunedBlockGraph blockgraph;
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
                    data = OkHttp3Util.postAndGetBlock(s.trim() + "/" + ReqCmd.getBlock,
                            Json.jsonmapper().writeValueAsString(requestParam));
                    transactionService.addConnected(data, true);
                    break;
                } catch (Exception e) {
                    log.debug(s, e);

                    badserver.add(s);
                }
            }
        }
        return data;
    }

    public void requestBlocks(long chainlength, String s)
            throws JsonProcessingException, IOException, ProtocolException, BlockStoreException, NoBlockException {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("start", chainlength + "");
        requestParam.put("end", chainlength + "");

        String response = OkHttp3Util.postString(s.trim() + "/" + ReqCmd.blocksFromChainLength,
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockListResponse blockbytelist = Json.jsonmapper().readValue(response, GetBlockListResponse.class);
        for (byte[] data : blockbytelist.getBlockbytelist()) {
            transactionService.addConnected(data, true);
        }
    }

    public TXReward getMaxConfirmedReward(String s) throws JsonProcessingException, IOException {

        HashMap<String, String> requestParam = new HashMap<String, String>();

        String response = OkHttp3Util.postString(s.trim() + "/" + ReqCmd.getMaxConfirmedReward,
                Json.jsonmapper().writeValueAsString(requestParam));
        GetTXRewardResponse aTXRewardResponse = Json.jsonmapper().readValue(response, GetTXRewardResponse.class);

        return aTXRewardResponse.getTxReward();

    }

    public List<TXReward> getAllConfirmedReward(String s) throws JsonProcessingException, IOException {

        HashMap<String, String> requestParam = new HashMap<String, String>();

        String response = OkHttp3Util.postString(s.trim() + "/" + ReqCmd.getAllConfirmedReward,
                Json.jsonmapper().writeValueAsString(requestParam));
        GetTXRewardListResponse aTXRewardResponse = Json.jsonmapper().readValue(response,
                GetTXRewardListResponse.class);

        return aTXRewardResponse.getTxReward();

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
    public class MaxConfirmedReward {
        String server;
        TXReward aTXReward;
    }

    public void diff() throws Exception {
        String[] re = serverConfiguration.getRequester().split(",");
        MaxConfirmedReward aMaxConfirmedReward = new MaxConfirmedReward();
        for (String s : re) {
            if (s != null && !"".equals(s)) {
                TXReward aTXReward = getMaxConfirmedReward(s.trim());
                if (aMaxConfirmedReward.aTXReward == null) {
                    aMaxConfirmedReward.server = s.trim();
                    aMaxConfirmedReward.aTXReward = aTXReward;
                } else {
                    if (aTXReward.getChainLength() > aMaxConfirmedReward.aTXReward.getChainLength()) {
                        aMaxConfirmedReward.server = s.trim();
                        aMaxConfirmedReward.aTXReward = aTXReward;
                    }
                }
            }
        }
        diffMaxConfirmedReward(aMaxConfirmedReward);
    }

    /*
     * check difference to remote server2 and get it. ask the remote
     * getMaxConfirmedReward to compare the my getMaxConfirmedReward if the
     * remote has length > my length, then find the get the list of confirmed
     * chains data. match the block hash to find the sync chain length, then
     * sync the chain data from
     */
    public void diffMaxConfirmedReward(MaxConfirmedReward aMaxConfirmedReward) throws Exception {
        TXReward my = store.getMaxConfirmedReward();
        if (my == null || aMaxConfirmedReward.aTXReward==null)
            return;
        log.debug("  remote chain lenght  " + aMaxConfirmedReward.aTXReward.getChainLength() + " server: "
                + aMaxConfirmedReward.server + " my chain lenght " + my.getChainLength());
        // sync all chain data d
        if (aMaxConfirmedReward.aTXReward.getChainLength() > my.getChainLength() + 2) {

            List<TXReward> remotes = getAllConfirmedReward(aMaxConfirmedReward.server);
            List<TXReward> mylist = store.getAllConfirmedReward();
            TXReward re = findSync(remotes, mylist);
            log.debug(" start sync remote chain   " + re.getChainLength() + " to "
                    + aMaxConfirmedReward.aTXReward.getChainLength());
            for (long i = re.getChainLength(); i <= aMaxConfirmedReward.aTXReward.getChainLength(); i++) {
                log.debug(
                        "   sync remote chain requestBlocks  at:  " + i + " at server: " + aMaxConfirmedReward.server);
                requestBlocks(i, aMaxConfirmedReward.server);
            }
        }
        log.debug(" finish difference check " + aMaxConfirmedReward.server + "  ");
    }

    private TXReward findSync(List<TXReward> remotes, List<TXReward> mylist) throws Exception {

        for (TXReward my : mylist) {
            TXReward f = findSync(remotes, my);
            if (f != null)
                return f;

        }
        return null;
    }

    private TXReward findSync(List<TXReward> remotes, TXReward my) throws Exception {

        for (TXReward b1 : remotes) {
            if (b1.getSha256Hash().equals(my.getSha256Hash())) {
                return b1;
            }
        }
        return null;
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
