/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.MySQLFullPrunedBlockStore;
import org.junit.After;
import org.junit.Ignore;

/**
 * A MySQL implementation of the {@link AbstractFullPrunedBlockChainTest}
 */
//@Ignore("enable the mysql driver dependency in the maven POM")
public class MySQLFullPrunedBlockChainTest extends AbstractFullPrunedBlockChainTest {

    @After
    public void tearDown() throws Exception {
        ((MySQLFullPrunedBlockStore)store).deleteStore();
    }

    // Replace these with your mysql location/credentials and remove @Ignore to test
    private static final String DB_HOSTNAME = "localhost";
    private static final String DB_NAME = "bitcoinj_test";
    private static final String DB_USERNAME = "bitcoinj";
    private static final String DB_PASSWORD = "password";

    @Override
    public FullPrunedBlockStore createStore(NetworkParameters params, int blockCount)
            throws BlockStoreException {
        return new MySQLFullPrunedBlockStore(params, blockCount, DB_HOSTNAME, DB_NAME, DB_USERNAME, DB_PASSWORD);
    }

    @Override
    public void resetStore(FullPrunedBlockStore store) throws BlockStoreException {
        ((MySQLFullPrunedBlockStore)store).resetStore();
    }
}