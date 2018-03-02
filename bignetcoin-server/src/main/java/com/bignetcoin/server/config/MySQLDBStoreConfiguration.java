/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.config;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.MySQLFullPrunedBlockStore;
import org.iq80.leveldb.DBException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MySQLDBStoreConfiguration {
    
    @Bean
    public FullPrunedBlockStore store() {
        NetworkParameters params = UnitTestParams.get();
        String hostname = "localhost";
        String dbName = "bitcoinj_test";
        String username = "root";
        String password = "adminroot";
        int fullStoreDepth = 10;
        try {
            return new MySQLFullPrunedBlockStore(
                    params, fullStoreDepth, hostname, dbName, username, password);
        } catch (BlockStoreException e) {
            throw new DBException();
        }
    }
}
