/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.service;

import org.springframework.stereotype.Service;

import net.bigtangle.core.Sha256Hash;

/**
 * ask other miner to get some missing blocks 1) the block can be imported
 * complete or initial start 2) block is missing in the database 3) The block is
 * published via Kafka stream.
 * 
 */
@Service
public class BlockRequester {

    public void requestBlock(Sha256Hash hash) {
        //TODO default request block from network 
    }
}
