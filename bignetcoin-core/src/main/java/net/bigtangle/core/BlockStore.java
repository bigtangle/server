/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

/**
 * An implementor of BlockStore saves StoredBlock objects to disk. Different implementations store them in
 * different ways. An in-memory implementation (MemoryBlockStore) exists for unit testing but real apps will want to
 * use implementations that save to disk.<p>
 *
 * A BlockStore is a map of hashes to StoredBlock. The hash is the double digest of the Bitcoin serialization
 * of the block header, <b>not</b> the header with the extra data as well.<p>
 *
 * BlockStores are thread safe.
 */
public interface BlockStore {
    /**
     * Saves the given block header+extra data. The key isn't specified explicitly as it can be calculated from the
     * StoredBlock directly. Can throw if there is a problem with the underlying storage layer such as running out of
     * disk space.
     */
    void put(StoredBlock block) throws BlockStoreException;

    /**
     * Returns the StoredBlock given a hash. The returned values block.getHash() method will be equal to the
     * parameter. If no such block is found, returns null.
     */
    StoredBlock get(Sha256Hash hash) throws BlockStoreException;

    /**
     * Returns the {@link StoredBlock} that represents the top of the chain of greatest total work. Note that this
     * can be arbitrarily expensive, you probably should use {@link org.bitcoinj.core.BlockChain#getChainHead()}
     * or perhaps {@link org.bitcoinj.core.BlockChain#getBestChainHeight()} which will run in constant time and
     * not take any heavyweight locks.
     */
    StoredBlock getChainHead() throws BlockStoreException;

    /**
     * Sets the {@link StoredBlock} that represents the top of the chain of greatest total work.
     */
    void setChainHead(StoredBlock chainHead) throws BlockStoreException;
    
    /** Closes the store. */
    void close() throws BlockStoreException;

    /**
     * Get the {@link net.bigtangle.core.NetworkParameters} of this store.
     * @return The network params.
     */
    NetworkParameters getParams();
}
