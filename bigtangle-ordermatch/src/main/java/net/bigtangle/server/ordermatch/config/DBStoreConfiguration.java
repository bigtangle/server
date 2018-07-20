/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.server.ordermatch.store.FullPrunedBlockStore;
import net.bigtangle.server.ordermatch.store.MySQLFullPrunedBlockStore;

@Configuration
public class DBStoreConfiguration {

    @Value("${db.dbtype:mysql}")
    private String dbtype;

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
    @Autowired
    NetworkParameters networkParameters;

    @Bean
    public FullPrunedBlockStore store() throws BlockStoreException {
        return createMysqlBlockStore();
    }

    public FullPrunedBlockStore createMysqlBlockStore() throws BlockStoreException {
        MySQLFullPrunedBlockStore store = new MySQLFullPrunedBlockStore(networkParameters, fullStoreDepth,
                hostname + ":" + port, "ordermatch", username, password);

        return store;
    }
}
