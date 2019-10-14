/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UnsolidBlock;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.response.GetBlockListResponse;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
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
public class SyncBlockService {

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    FullPrunedBlockGraph blockgraph;

    @Autowired
    private BlockService blockService;

    @Autowired
    private RewardService rewardService;
    @Autowired
    protected ServerConfiguration serverConfiguration;
    private static final Logger log = LoggerFactory.getLogger(SyncBlockService.class);

    protected final ReentrantLock lock = Threading.lock("syncBlockService");

    public void startSingleProcess() {
        if (!lock.tryLock()) {
            log.debug(this.getClass().getName() + " syncBlockService running. Returning...");
            return;
        }

        try {
            log.debug(" Start  SyncBlockService Single: ");
            Context context = new Context(networkParameters);
            Context.propagate(context);
            diff();
            // deleteOldUnsolidBlock();
            // updateSolidity();
            log.debug(" end SyncBlockService Single: ");
        } catch (Exception e) {
            log.warn("SyncBlockService ", e);
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
                byte[] re = requestBlock(block.getPrevBlockHash());
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
                byte[] re = requestBlock(block.getPrevBranchBlockHash());
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
        log.debug("getNonSolidMissingBlocks size = " + storedBlocklist.size() + " from cutoff Height: " + cutoffHeight);
        for (UnsolidBlock storedBlock : storedBlocklist) {
            if (storedBlock != null) {
                Block req = blockService.getBlock(storedBlock.missingdependencyHash());

                if (req != null) {
                    store.updateMissingBlock(storedBlock.missingdependencyHash(), false);
                    // if the block is there, now scan the rest unsolid
                    // blocks
                    if (store.getBlockEvaluation(req.getHash()).getSolid() >= 1) {
                        rewardService.scanWaitingBlocks(req);
                    }
                } else {
                    requestBlock(storedBlock.missingdependencyHash());
                }
            }
        }

    }

    public class Tokensums {
        String tokenid;
        BigInteger initial;
        BigInteger unspent;
        BigInteger order;
        @Override
        public String toString() {
            return "Tokensums [tokenid=" + tokenid + ", initial=" + initial + ", unspent=" + unspent + ", order="
                    + order + "]";
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getEnclosingInstance().hashCode();
            result = prime * result + ((initial == null) ? 0 : initial.hashCode());
            result = prime * result + ((order == null) ? 0 : order.hashCode());
            result = prime * result + ((tokenid == null) ? 0 : tokenid.hashCode());
            result = prime * result + ((unspent == null) ? 0 : unspent.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Tokensums other = (Tokensums) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
                return false;
            if (initial == null) {
                if (other.initial != null)
                    return false;
            } else if (!initial.equals(other.initial))
                return false;
            if (order == null) {
                if (other.order != null)
                    return false;
            } else if (!order.equals(other.order))
                return false;
            if (tokenid == null) {
                if (other.tokenid != null)
                    return false;
            } else if (!tokenid.equals(other.tokenid))
                return false;
            if (unspent == null) {
                if (other.unspent != null)
                    return false;
            } else if (!unspent.equals(other.unspent))
                return false;
            return true;
        }
        private SyncBlockService getEnclosingInstance() {
            return SyncBlockService.this;
        }
        
    }

    public void checkToken(String server, Map<String, Set<Tokensums>> result)
            throws JsonProcessingException, Exception {
        // String server = "http://localhost:8088/";
        Set<Tokensums> tokensumset = new HashSet<Tokensums>();

        result.put(server, tokensumset);

        Map<String, BigInteger> tokensums = tokensum(server);

        Set<String> tokenids = tokensums.keySet();

        for (String tokenid : tokenids) {
            Coin tokensum = new Coin(tokensums.get(tokenid) == null ? BigInteger.ZERO : tokensums.get(tokenid),
                    tokenid);

            checkToken(server, tokenid, tokensum, result.get(server));
        }
    }

    public void checkToken(String server, String tokenid, Coin tokensum, Set<Tokensums> tokensums)
            throws JsonProcessingException, Exception {

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(server + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);

        Coin sumUnspent = Coin.valueOf(0l, tokenid);

        for (UTXO u : getOutputsResponse.getOutputs()) {
            if (u.isConfirmed() && !u.isSpent())
                sumUnspent = sumUnspent.add(u.getValue());
        }
        Tokensums t = new Tokensums();

        Coin ordersum = ordersum(tokenid, server);
        t.tokenid = tokenid;
        t.unspent = sumUnspent.getValue();
        t.order = ordersum.getValue();
        t.initial = tokensum.getValue();

        // if (!tokenid.equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)) {
        // if (!tokensum.equals(sumUnspent.add(ordersum))) {
        // log.warn("tokensum.equals(sumUnspent.add(ordersum)");
        // }
        // } else {
        // if (tokensum.compareTo(sumUnspent.add(ordersum)) <= 0) {
        // log.warn("tokensum.compareTo(sumUnspent.add(ordersum)) <= 0");
        // }
        // }
        tokensums.add(t);
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
                    data = OkHttp3Util.postAndGetBlock(s.trim() + "/" + ReqCmd.getBlockByHash,
                            Json.jsonmapper().writeValueAsString(requestParam));
                    blockService.addConnected(data, true);
                    break;
                } catch (Exception e) {
                    log.debug(s, e);

                    badserver.add(s);
                }
            }
        }
        return data;
    }

    public void requestBlocks(Block rewardBlock) {
        RewardInfo rewardInfo = RewardInfo.parseChecked(rewardBlock.getTransactions().get(0).getData());

        String[] re = serverConfiguration.getRequester().split(",");
        List<String> badserver = new ArrayList<String>();
        for (String s : re) {
            if (s != null && !"".equals(s.trim()) && !badserver(badserver, s)) {
                try {
                    requestBlocks(rewardInfo.getChainlength(), s);
                    requestBlock(rewardInfo.getPrevRewardHash());
                } catch (Exception e) {
                    log.debug(s, e);
                    badserver.add(s);
                }
            }
        }
    }

    public void requestBlocks(long chainlength, String s)
            throws JsonProcessingException, IOException, ProtocolException, BlockStoreException, NoBlockException {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("start", chainlength + "");
        requestParam.put("end", chainlength + "");

        String response = OkHttp3Util.postString(s.trim() + "/" + ReqCmd.blocksFromChainLength,
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockListResponse blockbytelist = Json.jsonmapper().readValue(response, GetBlockListResponse.class);
        log.debug("blocks: " + blockbytelist.getBlockbytelist().size() + " remote chain requestBlocks  at:  "
                + chainlength + " at server: " + s);
        List<Block> sortedBlocks = new ArrayList<Block>();
        for (byte[] data : blockbytelist.getBlockbytelist()) {
            sortedBlocks.add(networkParameters.getDefaultSerializer().makeBlock(data));

        }
        Collections.sort(sortedBlocks, new SortbyBlock());
        for (Block block : sortedBlocks) {
            blockService.addConnectedBlock(block, true);
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
        // mcmcService.cleanupNonSolidMissingBlocks();
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
        if (my == null || aMaxConfirmedReward.aTXReward == null)
            return;
        log.debug("  remote chain lenght  " + aMaxConfirmedReward.aTXReward.getChainLength() + " server: "
                + aMaxConfirmedReward.server + " my chain lenght " + my.getChainLength());
        // sync all chain data d

        // Comparator<TXReward>chainlengthID=(TXReward o1,TXReward
        // o2)->o1.getChainLength()>o2.getChainLength()?1:0));

        if (aMaxConfirmedReward.aTXReward.getChainLength() > my.getChainLength()) {

            List<TXReward> remotes = getAllConfirmedReward(aMaxConfirmedReward.server);
            Collections.sort(remotes, new SortbyChain());
            List<TXReward> mylist = store.getAllConfirmedReward();
            Collections.sort(mylist, new SortbyChain());
            TXReward re = findSync(remotes, mylist);
            log.debug(" start sync remote chain   " + re.getChainLength() + " to "
                    + aMaxConfirmedReward.aTXReward.getChainLength());
            for (long i = re.getChainLength() + 1; i <= aMaxConfirmedReward.aTXReward.getChainLength(); i++) {
                requestBlocks(i, aMaxConfirmedReward.server);
                // mcmcService.update();
            }
            // mcmcService.updateMilestone();
        }
        log.debug(" finish difference check " + aMaxConfirmedReward.server + "  ");
    }

    public class SortbyBlock implements Comparator<Block> {

        public int compare(Block a, Block b) {
            return a.getHeight() > b.getHeight() ? 1 : -1;
        }
    }

    public class SortbyChain implements Comparator<TXReward> {
        // Used for sorting in ascending order of
        // roll number
        public int compare(TXReward a, TXReward b) {
            return a.getChainLength() < b.getChainLength() ? 1 : -1;
        }
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
            if (b1.getBlockHash().equals(my.getBlockHash())) {
                return b1;
            }
        }
        return null;
    }
}
