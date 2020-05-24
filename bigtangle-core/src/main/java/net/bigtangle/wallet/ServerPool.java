/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Json;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

/*
 * keep the potential list of servers and check the servers.
 * A List of server, which can provide block service
 * 1) check the server chain length 
 * 2) check the response speed of the server
 * 3) check the health of the server
 * 4) balance of the server select for random 
 */
public class ServerPool {

    private List<ServerState> servers = new ArrayList<ServerState>();
    private static final Logger log = LoggerFactory.getLogger(ServerPool.class);
    // get a best server to be used and balance with random
    public ServerState getServer() {
        return servers.get(0);
    }

    public void addServer(String s) {
        Long time = System.currentTimeMillis();
        TXReward chain;
        try {
            chain = getChainNumber(s);
            ServerState serverState = new ServerState();
            serverState.setServerurl(s);
            serverState.setResponseTime(System.currentTimeMillis() - time);
            serverState.setChainlength(chain.getChainLength());
            servers.add(serverState);
        } catch (Exception e) {
            log.debug("", e);
        }
        Collections.sort(servers, new SortbyChain());
    }

    public void removeServer(ServerState serverState) {
        for (Iterator<ServerState> iter = servers.listIterator(); iter.hasNext();) {
            ServerState a = iter.next();
            if (serverState.getServerurl().equals(a.getServerurl())) {
                iter.remove();
            }
        }
    }

    public void addServers(List<String> serverCandidates) {
        servers = new ArrayList<ServerState>(); 
        for (String s : serverCandidates) {
            addServer(s); 
        } 
    }

    public class SortbyChain implements Comparator<ServerState> {
        // Used for sorting in ascending order of
        // roll number
        public int compare(ServerState a, ServerState b) {
            if (a.getChainlength() == b.getChainlength()) {
                return a.getResponseTime() > b.getResponseTime() ? 1 : -1;
            }

            return a.getChainlength() < b.getChainlength() ? -1 : 1;
        }
    }

    public TXReward getChainNumber(String s) throws JsonProcessingException, IOException {

        HashMap<String, String> requestParam = new HashMap<String, String>();

        String response = OkHttp3Util.postString(s.trim() + "/" + ReqCmd.getChainNumber,
                Json.jsonmapper().writeValueAsString(requestParam));
        GetTXRewardResponse aTXRewardResponse = Json.jsonmapper().readValue(response, GetTXRewardResponse.class);

        return aTXRewardResponse.getTxReward();

    }
    
    

}
