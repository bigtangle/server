/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.PhoenixBlockStore;

@Configuration
public class PhoenixDBStoreConfiguration {

    private int fullStoreDepth = 10;
    
    @Autowired
    NetworkParameters networkParameters;
    
    private static final Logger logger = LoggerFactory.getLogger(PhoenixDBStoreConfiguration.class);
    
    private static final String hostname = "cn.phoenix.bigtangle.net:8765";
    
    private static final String username = null;
    
    private static final String password = null;
    
    @Bean
    public FullPrunedBlockStore store() throws BlockStoreException {
        PhoenixBlockStore store = new PhoenixBlockStore(networkParameters, fullStoreDepth,
                hostname, "", username, password);
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
