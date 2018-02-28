/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.LevelDBFullPrunedBlockStore;
import org.junit.After;

import java.io.File;

/**
 * An H2 implementation of the FullPrunedBlockStoreTest
 */
public class LevelDBFullPrunedBlockChainTest extends
        AbstractFullPrunedBlockChainTest {
    @After
    public void tearDown() throws Exception {
        deleteFiles();
    }

    @Override
    public FullPrunedBlockStore createStore(NetworkParameters params,
            int blockCount) throws BlockStoreException {
        deleteFiles();
        return new LevelDBFullPrunedBlockStore(params, "test-leveldb",
                blockCount);
    }

    private void deleteFiles() {
        File f = new File("test-leveldb");
        if (f != null && f.exists()) {
            for (File c : f.listFiles())
                c.delete();
        }
    }

    @Override
    public void resetStore(FullPrunedBlockStore store)
            throws BlockStoreException {
        ((LevelDBFullPrunedBlockStore) store).resetStore();
    }
}
