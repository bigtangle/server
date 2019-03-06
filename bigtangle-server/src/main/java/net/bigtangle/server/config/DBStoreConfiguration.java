/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.MySQLFullPrunedBlockStore;
import net.bigtangle.store.PhoenixBlockStore;

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
        if ("phoenix".equalsIgnoreCase(dbtype))
            return createPhoenixBlockStore();
        if ("cassandra".equalsIgnoreCase(dbtype))
            return createCassandraBlockStore();
        else
            return createMysqlBlockStore();
    }

    private FullPrunedBlockStore createCassandraBlockStore() {
        return null;
    }

    public FullPrunedBlockStore createMysqlBlockStore() throws BlockStoreException {
        MySQLFullPrunedBlockStore store = new MySQLFullPrunedBlockStore(networkParameters, fullStoreDepth,
                hostname + ":" + port, dbName, username, password);

        return store;
    }

    public FullPrunedBlockStore createPhoenixBlockStore() throws BlockStoreException {
        PhoenixBlockStore store = new PhoenixBlockStore(networkParameters, fullStoreDepth, hostname + ":" + port, "",
                null, null);

        return store;
    }
}
