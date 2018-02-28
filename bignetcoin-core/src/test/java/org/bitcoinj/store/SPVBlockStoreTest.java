/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.store;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class SPVBlockStoreTest {

    @Test
    public void basics() throws Exception {
        NetworkParameters params = UnitTestParams.get();
        File f = File.createTempFile("spvblockstore", null);
        f.delete();
        f.deleteOnExit();
        SPVBlockStore store = new SPVBlockStore(params, f);

        Address to = new ECKey().toAddress(params);
        // Check the first block in a new store is the genesis block.
        StoredBlock genesis = store.getChainHead();
        assertEquals(params.getGenesisBlock(), genesis.getHeader());
        assertEquals(0, genesis.getHeight());


        // Build a new block.
        StoredBlock b1 = genesis.build(genesis.getHeader().createNextBlock(to, params.getGenesisBlock().getHash()).cloneAsHeader());
        store.put(b1);
        store.setChainHead(b1);
        store.close();

        // Check we can get it back out again if we rebuild the store object.
        store = new SPVBlockStore(params, f);
        StoredBlock b2 = store.get(b1.getHeader().getHash());
        assertEquals(b1, b2);
        // Check the chain head was stored correctly also.
        StoredBlock chainHead = store.getChainHead();
        assertEquals(b1, chainHead);
    }
}
