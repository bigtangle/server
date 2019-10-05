/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UnsolidBlock;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.Threading;

/**
 * <p>
 * Provides services for blocks.
 * </p>
 */
@Service
public class UnsolidBlockService {

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    FullPrunedBlockGraph blockgraph;

    @Autowired
    private BlockRequester blockRequester;

    @Autowired
    private BlockService blockService;

    @Autowired
    private MCMCService mcmcService;

    private static final Logger log = LoggerFactory.getLogger(UnsolidBlockService.class);

    protected final ReentrantLock lock = Threading.lock("UnsolidBlockService");

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            log.debug(this.getClass().getName() + " UnsolidBlockService running. Returning...");
            return;
        }

        try {
            log.debug(" Start updateUnsolideServiceSingle: ");
            Context context = new Context(networkParameters);
            Context.propagate(context);
            blockRequester.diff();
            // deleteOldUnsolidBlock();
            // updateSolidity();
            log.debug(" end  updateUnsolideServiceSingle: ");
        } catch (Exception e) {
            log.warn("updateUnsolideService ", e);
        } finally {
            lock.unlock();
            ;
        }

    }

    public void requestPrev(Block block) {
        try {
            if (block.getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
                return;
            }

            Block storedBlock0 = null;
            try {
                storedBlock0 = blockService.getBlock(block.getPrevBlockHash());
            } catch (NoBlockException e) {
                // Ok, no prev
            }

            if (storedBlock0 == null) {
                byte[] re = blockRequester.requestBlock(block.getPrevBlockHash());
                if (re != null) {
                    Block req = (Block) networkParameters.getDefaultSerializer().makeBlock(re);
                    blockgraph.add(req, true);
                }
            }
            Block storedBlock1 = null;

            try {
                storedBlock1 = blockService.getBlock(block.getPrevBranchBlockHash());
            } catch (NoBlockException e) {
                // Ok, no prev
            }

            if (storedBlock1 == null) {
                byte[] re = blockRequester.requestBlock(block.getPrevBranchBlockHash());
                if (re != null) {
                    Block req = (Block) networkParameters.getDefaultSerializer().makeBlock(re);
                    blockgraph.add(req, true);
                }
            }
        } catch (Exception e) {
            log.debug("", e);
        }
    }

    /*
     * all very old unsolid blocks are deleted
     */
    public void deleteOldUnsolidBlock() throws Exception {

        this.store.deleteOldUnsolid(getTimeSeconds(1));
    }

    public long getTimeSeconds(int days) throws Exception {
        return System.currentTimeMillis() / 1000 - days * 60 * 24 * 60;
    }

    public void updateSolidity()
            throws BlockStoreException, NoBlockException, InterruptedException, ExecutionException {

        /*
         * Cutoff window around current chain.
         */
        long cutoffHeight = blockService.getCutoffHeight();
        List<UnsolidBlock> storedBlocklist = store.getNonSolidMissingBlocks(cutoffHeight);
        log.debug(
                "getNonSolidMissingBlocks size = " + storedBlocklist.size() + " from cutoff Height: " + cutoffHeight);
        for (UnsolidBlock storedBlock : storedBlocklist) {
            if (storedBlock != null) {
                Block req = blockService.getBlock(storedBlock.missingdependencyHash());

                if (req != null) {
                    store.updateMissingBlock(storedBlock.missingdependencyHash(), false);
                    // if the block is there, now scan the rest unsolid
                    // blocks
                    if (store.getBlockEvaluation(req.getHash()).getSolid() >= 1) {
                        mcmcService.scanWaitingBlocks(req);
                    }
                } else {
                    blockRequester.requestBlock(storedBlock.missingdependencyHash());
                }
            }
        }

    }

    public void testCheckToken() throws JsonProcessingException, Exception {
        String server = "http://localhost:8088/";
        Map<String, BigInteger> tokensums = tokensum(server);
        Set<String> tokenids = tokensums.keySet();

        for (String tokenid : tokenids) {
            Coin tokensum = new Coin(tokensums.get(tokenid) == null ? BigInteger.ZERO : tokensums.get(tokenid),
                    tokenid);

            testCheckToken(server, tokenid, tokensum);
        }
    }

    public void testCheckToken(String server, String tokenid, Coin tokensum) throws JsonProcessingException, Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(server + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        // log.info("getOutputsResponse : " + getOutputsResponse);
        Coin sumUnspent = Coin.valueOf(0l, tokenid);
        // Coin sumCoinbase = Coin.valueOf(0l, tokenid);
        for (UTXO u : getOutputsResponse.getOutputs()) {

            if (u.isConfirmed() && !u.isSpent())
                sumUnspent = sumUnspent.add(u.getValue());
        }

        Coin ordersum = ordersum(tokenid, server);

        log.info("sumUnspent : " + sumUnspent);
        log.info("ordersum : " + ordersum);
        // log.info("sumCoinbase : " + sumCoinbase);

        log.info("tokensum : " + tokensum);

        log.info("  ordersum + : sumUnspent = " + sumUnspent.add(ordersum));

        if (!tokenid.equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
            if(!tokensum.equals(sumUnspent.add(ordersum))) {
                log.warn("tokensum.equals(sumUnspent.add(ordersum)" );
            } 
    }else {
            if(tokensum.compareTo(sumUnspent.add(ordersum)) <= 0) {
                log.warn("tokensum.compareTo(sumUnspent.add(ordersum)) <= 0" ); 
            }
        }
     
    }

    public Coin ordersum(String tokenid, String server) throws JsonProcessingException, Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response0 = OkHttp3Util.post(server + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        Coin sumUnspent = Coin.valueOf(0l, tokenid);
        for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
            if (orderRecord.getOfferTokenid().equals(tokenid)) {
                sumUnspent = sumUnspent.add(Coin.valueOf(orderRecord.getOfferValue(), tokenid));
            }
        }
        return sumUnspent;
    }

    public Map<String, BigInteger> tokensum(String server) throws JsonProcessingException, Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", null);
        String response = OkHttp3Util.post(server + ReqCmd.searchTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());

        GetTokensResponse orderdataResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);

        return orderdataResponse.getAmountMap();
    }
}
