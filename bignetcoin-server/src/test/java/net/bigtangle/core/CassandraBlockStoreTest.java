/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import org.junit.After;

import net.bigtangle.store.CassandraBlockStore;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.MySQLFullPrunedBlockStore;

/**
 * A MySQL implementation of the {@link AbstractFullPrunedBlockChainTest}
 */

 //("enable the mysql driver dependency in the maven POM")
public class CassandraBlockStoreTest extends AbstractFullPrunedBlockChainTest {

    @After
    public void tearDown() throws Exception {
    //      ((MySQLFullPrunedBlockStore)store).deleteStore();
    }

    // Replace these with your mysql location/credentials and remove @Ignore to test
    private static final String DB_HOSTNAME = "cassandra-as-vdv-aus:9160";
    private static final String DB_NAME = "bigtangle";
    private static final String DB_USERNAME = null;
    private static final String DB_PASSWORD = null;

    @Override
    public FullPrunedBlockStore createStore(NetworkParameters params, int blockCount) throws BlockStoreException {
        try {
            store = new CassandraBlockStore(params, blockCount, DB_HOSTNAME, DB_NAME, DB_USERNAME, DB_PASSWORD);
        } catch (Exception e) {
            e.printStackTrace();
        }
        resetStore(store);
        return store;
    }
  

    @Override
    public void resetStore(FullPrunedBlockStore store) throws BlockStoreException {
        ((CassandraBlockStore)store).resetStore();
    }
}