/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.server.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

/**
 * ask other miner to get some missing blocks 1) the block can be imported
 * complete or initial start 2) block is missing in the database 3) The block is
 * published via Kafka stream.
 * 
 */
@Service
public class BlockRequester {
    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    TransactionService transactionService;

    public byte[] requestBlock(Sha256Hash hash) {
        // TODO block from network peers

        List<String> serverList = new ArrayList<String>();
        serverList.add("http://de.server.bigtangle.net:8088/");
        serverList.add("http://cn.server.bigtangle.net:8088/");
        byte[] data=null;
        for (String s : serverList) {

            HashMap<String, String> requestParam = new HashMap<String, String>();
            requestParam.put("hashHex", Utils.HEX.encode(hash.getBytes()));

            try {
                data = OkHttp3Util.post(s + ReqCmd.getBlock, Json.jsonmapper().writeValueAsString(requestParam));
                transactionService.addConnected(data);
                break;
            } catch (JsonProcessingException e) {
                // TODO Auto-generated catch block
            } catch (Exception e) {
                // TODO Auto-generated catch block
            }

        }
        return data;
    }

}
