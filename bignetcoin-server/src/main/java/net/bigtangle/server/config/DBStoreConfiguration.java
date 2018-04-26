/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.store.FullPrunedBlockGraph;
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
    
    private static final Logger logger = LoggerFactory.getLogger(DBStoreConfiguration.class);
    
    @Bean
    public FullPrunedBlockStore store() throws BlockStoreException {
//            if("phoenix".equalsIgnoreCase(dbtype))
//                    return phoenixStore() ;
//            if("cassandra".equalsIgnoreCase(dbtype))
//                return mysqlstore() ;
        
            return phoenixStore() ;
            
            
    }
    
    public FullPrunedBlockStore mysqlstore() throws BlockStoreException {
        MySQLFullPrunedBlockStore store = new MySQLFullPrunedBlockStore(networkParameters, fullStoreDepth,
                hostname + ":" + port, dbName, username, password);
        try {
            store.initFromDatabase();
            FullPrunedBlockGraph blockgraph = new FullPrunedBlockGraph(networkParameters, store); 
            // Add genesis block
            blockgraph.add(networkParameters.getGenesisBlock());
         
            BlockEvaluation genesisEvaluation = store.getBlockEvaluation(networkParameters.getGenesisBlock().getHash());
            store.updateBlockEvaluationMilestone(genesisEvaluation.getBlockhash(), true);
            store.updateBlockEvaluationSolid(genesisEvaluation.getBlockhash(), true);
        } catch (Exception e) {
            logger.warn("create bean FullPrunedBlockStore store", e);
        }
        return store;
    }
    
    public FullPrunedBlockStore phoenixStore() throws BlockStoreException {
        hostname= "cn.phoenix.bigtangle.net";
        port = "8765";
        PhoenixBlockStore store = new PhoenixBlockStore(networkParameters, fullStoreDepth,
                hostname+ ":" + port, "", null, null);
        try {
            store.initFromDatabase();
            FullPrunedBlockGraph blockgraph = new FullPrunedBlockGraph(networkParameters, store); 
            // Add genesis block
            blockgraph.add(networkParameters.getGenesisBlock());
         
            BlockEvaluation genesisEvaluation = store.getBlockEvaluation(networkParameters.getGenesisBlock().getHash());
            store.updateBlockEvaluationMilestone(genesisEvaluation.getBlockhash(), true);
            store.updateBlockEvaluationSolid(genesisEvaluation.getBlockhash(), true);
        } catch (Exception e) {
            logger.warn("create bean FullPrunedBlockStore store", e);
        }
        return store;
    }
}
