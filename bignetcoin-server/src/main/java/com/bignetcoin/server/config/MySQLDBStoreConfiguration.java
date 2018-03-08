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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MySQLDBStoreConfiguration {

    @Value("${db.hostname:localhost}")
    private String hostname;
    @Value("${db.dbName:bitcoinj_test}")
    private String dbName = "bitcoinj_test";
    @Value("${db.username:root}")
    private String username = "root";
    @Value("${db.password:adminroot}")
    private String password;
    @Value("${db.port:3306}")
    private String port;
    
    private int fullStoreDepth = 10;

    @Bean
    public FullPrunedBlockStore store() {
        NetworkParameters params = UnitTestParams.get();

        try {
            MySQLFullPrunedBlockStore store = new MySQLFullPrunedBlockStore(params, fullStoreDepth, hostname+ ":"+ port, dbName, username, password);
            try {
            store.initFromDatabase();
            }catch (Exception e) {
                // TODO: handle exception
            }
            return store;
                     
        } catch (Exception e) {
            throw new DBException(e);
        }
    }
}
