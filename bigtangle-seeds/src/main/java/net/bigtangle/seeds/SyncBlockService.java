/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.seeds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.response.GetTXRewardResponse;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.OkHttp3Util;

/**
 * <p>
 * Provides services for sync blocks from remote servers via p2p.
 * 
 * sync remote chain data from chainlength,
 * if chainlength = null, then sync the chain data from the total rating with
 * chain 100%
 * For the sync from given checkpoint, the server must be restarted.
 * 
 * 
 * </p>
 */
@Service
public class SyncBlockService {

    @Autowired
    protected NetworkParameters networkParameters;
 
 
    private static final Logger log = LoggerFactory.getLogger(SyncBlockService.class);
  

    // default start sync of chain and non chain data
    public void startSingleProcess() throws BlockStoreException, JsonProcessingException, IOException {
     
            log.debug(" Start syncServerInfo  : ");
            syncServerInfo( );
             
            log.debug(" end syncServerInfo: ");
       

    }

 
    public void  syncServerInfo( ) throws JsonProcessingException, IOException {
 
  
        List<String> badserver = new ArrayList<String>();
        byte[] data = null;
        for (String s : MainNetParams.get().serverSeeds()) {
          
        	 HashMap<String, String> requestParam = new HashMap<String, String>();
        
                    data = OkHttp3Util.postAndGetBlock(s.trim() + "/" + ReqCmd.serverinfolist,
                            Json.jsonmapper().writeValueAsString(requestParam));
                 
                  //update the list    DispatcherController.serverinfo;
                   
  
            }
    //check each server data
 
    }
 
    

    
    /*
     * last chain max 
     */

    public TXReward getMaxConfirmedReward(String server) throws JsonProcessingException, IOException {

        HashMap<String, String> requestParam = new HashMap<String, String>();

        byte[] response = OkHttp3Util.postString(server.trim() + "/" + ReqCmd.getChainNumber,
                Json.jsonmapper().writeValueAsString(requestParam));
        GetTXRewardResponse aTXRewardResponse = Json.jsonmapper().readValue(response, GetTXRewardResponse.class);

        return aTXRewardResponse.getTxReward();

    }
  
}
