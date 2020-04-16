/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Coin;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.response.GetOutputsResponse;
import net.bigtangle.core.response.GetTokensResponse;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.data.Tokensums;
import net.bigtangle.utils.OkHttp3Util;

/**
 * <p>
 * For a given confirmed reward block as checkpoint, this value from the
 * database must be the same for all servers. This checkpoint is saved as
 * checkpoint block and can be verified and enable fast setup and pruned history
 * data. The checkpoint is used as the new genesis state.
 * </p>
 */
@Service
public class CheckpointService {

    @Autowired
    protected FullPrunedBlockStore store;

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    FullPrunedBlockGraph blockgraph;

    @Autowired
    protected ServerConfiguration serverConfiguration;
    private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);

    private List<UTXO> getOutputs(String server, String tokenid)
            throws IOException, JsonProcessingException, JsonMappingException {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("tokenid", tokenid);
        String resp = OkHttp3Util.postString(server + ReqCmd.outputsOfTokenid.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetOutputsResponse getOutputsResponse = Json.jsonmapper().readValue(resp, GetOutputsResponse.class);
        return getOutputsResponse.getOutputs();
    }

    public Coin ordersum(String tokenid, String server, List<OrderRecord> orders)
            throws JsonProcessingException, Exception {
        Coin sumUnspent = Coin.valueOf(0l, tokenid);
        for (OrderRecord orderRecord : orders) {
            if (orderRecord.getOfferTokenid().equals(tokenid)) {
                sumUnspent = sumUnspent.add(Coin.valueOf(orderRecord.getOfferValue(), tokenid));
            }
        }
        return sumUnspent;
    }

    private List<OrderRecord> orders(String server) throws IOException, JsonProcessingException, JsonMappingException {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        String response0 = OkHttp3Util.post(server + ReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes(StandardCharsets.UTF_8));

        OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
        return orderdataResponse.getAllOrdersSorted();
    }

    public Map<String, BigInteger> tokensumInitial(String server) throws IOException {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("name", null);
        String response = OkHttp3Util.post(server + ReqCmd.searchTokens.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes(StandardCharsets.UTF_8));
        GetTokensResponse orderdataResponse = Json.jsonmapper().readValue(response, GetTokensResponse.class);
        return orderdataResponse.getAmountMap();
    }

    public void checkToken(String server, Map<String, Map<String, Tokensums>> result)
            throws JsonMappingException, JsonProcessingException, IOException {

        Map<String, Tokensums> tokensumset = new HashMap<String, Tokensums>();
        result.put(server, tokensumset);
        Map<String, BigInteger> tokensumsInitial = tokensumInitial(server);
        Set<String> tokenids = tokensumsInitial.keySet();
        for (String tokenid : tokenids) {
            Tokensums tokensums = new Tokensums();
            tokensums.setTokenid(tokenid);
            tokensums.setUtxos(getOutputs(server, tokenid));
            tokensums.setOrders(orders(server));
            tokensums.setInitial(tokensumsInitial.get(tokenid));
            tokensums.calculate();
            result.get(server).put(tokenid, tokensums);
        }

    }
}
