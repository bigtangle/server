package net.bigtangle.server.service;

import org.apache.spark.sql.SparkSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.server.config.SparkConfig;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.store.SparkStore;

@Service
public class StoreService {

    @Autowired
    SparkSession sparkSession;
    @Autowired
    SparkConfig sparkConfig;

    @Autowired
    protected NetworkParameters networkParameters;

    public FullBlockStore getStore() throws BlockStoreException {

        return new SparkStore(networkParameters, sparkSession, sparkConfig.getAppPath());

    }

}
