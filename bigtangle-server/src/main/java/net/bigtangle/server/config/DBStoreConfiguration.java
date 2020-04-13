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
import net.bigtangle.store.cassandra.CassandraBlockStore;

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
    @Autowired
    ServerConfiguration serverConfiguration;

    @Bean
    public FullPrunedBlockStore store() throws BlockStoreException {
 
        if ("cassandra".equalsIgnoreCase(dbtype))
            return createCassandraBlockStore();
        else
            return createMysqlBlockStore();

    }

    private FullPrunedBlockStore createCassandraBlockStore() throws BlockStoreException {
        CassandraBlockStore store = new CassandraBlockStore(networkParameters, fullStoreDepth, hostname + ":" + port,
                dbName, username, password);
        return store;
    }

    public FullPrunedBlockStore createMysqlBlockStore() throws BlockStoreException {

        MySQLFullPrunedBlockStore store = new MySQLFullPrunedBlockStore(networkParameters, fullStoreDepth,
                hostname + ":" + port, dbName, username, password);

        return store;
    }

    public String getDbtype() {
        return dbtype;
    }

    public void setDbtype(String dbtype) {
        this.dbtype = dbtype;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

}
