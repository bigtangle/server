/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.server.ReqCmd;
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
    protected ServerConfiguration serverConfiguration;

    public byte[] requestBlock(Sha256Hash hash) {
        // block from network peers
        log.debug("requestBlock" + hash.toString()); 
        String[] re = serverConfiguration.getRequester().split(",");
        byte[] data = null;
        for (String s : re) { 
            if(s!=null && !"".equals(  s.trim()))
                    {
            HashMap<String, String> requestParam = new HashMap<String, String>();
            requestParam.put("hashHex", Utils.HEX.encode(hash.getBytes())); 
            try {
                data = OkHttp3Util.post(s +"/" + ReqCmd.getBlock, Json.jsonmapper().writeValueAsString(requestParam));
                transactionService.addConnected(data, false);
                break;
            } catch (Exception e) {
                log.debug(s, e);
            }
                    }
        }
        return data;
    }

    public void broadcastBlocks(long startheight, String kafkaserver) {

    }

}
