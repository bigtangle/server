/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import org.junit.After;
import org.junit.Ignore;

import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.MySQLFullPrunedBlockStore;
import net.bigtangle.store.PhoenixBlockStore;

/**
 * A MySQL implementation of the {@link AbstractFullPrunedBlockChainTest}
 */
// @Ignore
 //("enable the mysql driver dependency in the maven POM")
public class PhoenixStoreTest extends AbstractFullPrunedBlockChainTest {

    @After
    public void tearDown() throws Exception {
    //      ((MySQLFullPrunedBlockStore)store).deleteStore();
    }

    // Replace these with your mysql location/credentials and remove @Ignore to test
    private static final String DB_HOSTNAME = "cn.phoenix.bigtangle.net:8765";
    private static final String DB_NAME = "info";
    private static final String DB_USERNAME = null;
    private static final String DB_PASSWORD = null;

    @Override
    public FullPrunedBlockStore createStore(NetworkParameters params, int blockCount) throws BlockStoreException {
        try {
            store = new PhoenixBlockStore(params, blockCount, DB_HOSTNAME, DB_NAME, DB_USERNAME, DB_PASSWORD);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        resetStore(store);
        return store;
    }
  

    @Override
    public void resetStore(FullPrunedBlockStore store) throws BlockStoreException {
        ((PhoenixBlockStore)store).resetStore();
    }
}