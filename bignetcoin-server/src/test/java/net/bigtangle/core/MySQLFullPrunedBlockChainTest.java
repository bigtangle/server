/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import org.junit.After;
import org.junit.Ignore;

import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.store.MySQLFullPrunedBlockStore;

/**
 * A MySQL implementation of the {@link AbstractFullPrunedBlockChainTest}
 */
@Ignore
 //("enable the mysql driver dependency in the maven POM")
public class MySQLFullPrunedBlockChainTest extends AbstractFullPrunedBlockChainTest {

    @After
    public void tearDown() throws Exception {
    //      ((MySQLFullPrunedBlockStore)store).deleteStore();
    }

    // Replace these with your mysql location/credentials and remove @Ignore to test
    private static final String DB_HOSTNAME = "localhost";
    private static final String DB_NAME = "info";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "test1234";

    @Override
    public FullPrunedBlockStore createStore(NetworkParameters params, int blockCount) throws BlockStoreException {
        try {
            store = new MySQLFullPrunedBlockStore(params, blockCount, DB_HOSTNAME, DB_NAME, DB_USERNAME, DB_PASSWORD);
        } catch (RuntimeException e) {
        }
        resetStore(store);
        return store;
    }
  

    @Override
    public void resetStore(FullPrunedBlockStore store) throws BlockStoreException {
        ((MySQLFullPrunedBlockStore)store).resetStore();
    }
}