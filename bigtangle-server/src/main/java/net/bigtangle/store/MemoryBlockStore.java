/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang.NotImplementedException;

import net.bigtangle.core.*;

/**
 * Keeps {@link net.bigtangle.core.StoredBlock}s in memory. Used primarily for unit testing.
 */
public class MemoryBlockStore implements BlockStore {
    private LinkedHashMap<Sha256Hash, StoredBlock> blockMap = new LinkedHashMap<Sha256Hash, StoredBlock>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, StoredBlock> eldest) {
            return blockMap.size() > 5000;
        }
    };
    private NetworkParameters params;

    public MemoryBlockStore(NetworkParameters params) {
        // Insert the genesis block.
        try {
            Block genesisHeader = params.getGenesisBlock().cloneAsHeader();
            StoredBlock storedGenesis = new StoredBlock(genesisHeader,  0);
            put(storedGenesis);
            this.params = params;
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);  // Cannot happen.
        } catch (VerificationException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
    }

    @Override
    public synchronized final void put(StoredBlock block) throws BlockStoreException {
        if (blockMap == null) throw new BlockStoreException("MemoryBlockStore is closed");
        Sha256Hash hash = block.getHeader().getHash();
        blockMap.put(hash, block);
    }

    @Override
    public synchronized StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        if (blockMap == null) throw new BlockStoreException("MemoryBlockStore is closed");
        return blockMap.get(hash);
    }

    
    @Override
    public void close() {
        blockMap = null;
    }

    @Override
    public NetworkParameters getParams() {
        return params;
    }
}
