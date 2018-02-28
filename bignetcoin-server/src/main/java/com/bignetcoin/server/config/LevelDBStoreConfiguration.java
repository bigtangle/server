/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server.config;

import java.io.File;
import java.io.IOException;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.LevelDBFullPrunedBlockStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LevelDBStoreConfiguration {
    @Bean
    public FullPrunedBlockStore store() {

        File f;
        try {
            f = File.createTempFile("test-leveldb", null);
            f.delete();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        NetworkParameters params = UnitTestParams.get();

        return new LevelDBFullPrunedBlockStore(params, "test-leveldb", 10);

    }
}
