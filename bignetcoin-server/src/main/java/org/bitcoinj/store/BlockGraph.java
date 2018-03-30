/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.store;

import static com.google.common.base.Preconditions.checkArgument;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockStore;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.TransactionOutputChanges;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;

// TODO: Rename this class to SPVBlockChain at some point.

/**
 * A BlockChain implements the <i>simplified payment verification</i> mode of the Bitcoin protocol. It is the right
 * choice to use for programs that have limited resources as it won't verify transactions signatures or attempt to store
 * all of the block chain. Really, this class should be called SPVBlockChain but for backwards compatibility it is not.
 */
public class BlockGraph extends AbstractBlockGraph {
    /** Keeps a map of block hashes to StoredBlocks. */
    protected final BlockStore blockStore;

     
    /**
     * Constructs a BlockChain that has no wallet at all. This is helpful when you don't actually care about sending
     * and receiving coins but rather, just want to explore the network data structures.
     */
    public BlockGraph(Context context, BlockStore blockStore) throws BlockStoreException {
        this(context, new ArrayList<Wallet>(), blockStore);
    }

    /** See {@link #BlockChain(Context, BlockStore)} */
    public BlockGraph(NetworkParameters params, BlockStore blockStore) throws BlockStoreException {
        this(params, new ArrayList<Wallet>(), blockStore);
    }

    /**
     * Constructs a BlockChain connected to the given list of listeners and a store.
     */
    public BlockGraph(Context params, List<? extends Wallet> wallets, BlockStore blockStore) throws BlockStoreException {
        super(params, wallets, blockStore);
        this.blockStore = blockStore;
    }

    /** See {@link #BlockChain(Context, List, BlockStore)} */
    public BlockGraph(NetworkParameters params, List<? extends Wallet> wallets, BlockStore blockStore) throws BlockStoreException {
        this(Context.getOrCreate(params), wallets, blockStore);
    }

    @Override
    protected StoredBlock addToBlockStore(StoredBlock storedPrev, StoredBlock storedPrevBranch,  Block blockHeader, TransactionOutputChanges txOutChanges)
            throws BlockStoreException, VerificationException {
        StoredBlock newBlock = storedPrev.build(blockHeader,storedPrevBranch);
        blockStore.put(newBlock);
        return newBlock;
    }
    
    @Override
    protected StoredBlock addToBlockStore(StoredBlock storedPrev, StoredBlock storedPrevBranch,Block blockHeader)
            throws BlockStoreException, VerificationException {
        StoredBlock newBlock = storedPrev.build(blockHeader, storedPrevBranch);
        blockStore.put(newBlock);
        return newBlock;
    }

    @Override
    protected void rollbackBlockStore(int height) throws BlockStoreException {
        lock.lock();
        try {
            int currentHeight = getBestChainHeight();
            checkArgument(height >= 0 && height <= currentHeight, "Bad height: %s", height);
            if (height == currentHeight)
                return; // nothing to do

            // Look for the block we want to be the new chain head
            StoredBlock newChainHead = blockStore.getChainHead();
            while (newChainHead.getHeight() > height) {
                newChainHead = newChainHead.getPrev(blockStore);
                if (newChainHead == null)
                    throw new BlockStoreException("Unreachable height");
            }

            // Modify store directly
            blockStore.put(newChainHead);
            this.setChainHead(newChainHead);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean shouldVerifyTransactions() {
        return false;
    }

    @Override
    protected TransactionOutputChanges connectTransactions(long height, Block block) {
        // Don't have to do anything as this is only called if(shouldVerifyTransactions())
        throw new UnsupportedOperationException();
    }

//    @Override
//    protected TransactionOutputChanges connectTransactions(StoredBlock newBlock) {
//        // Don't have to do anything as this is only called if(shouldVerifyTransactions())
//        throw new UnsupportedOperationException();
//    }

    @Override
    protected void removeTransactionsFromMilestone(Block block) {
        // Don't have to do anything as this is only called if(shouldVerifyTransactions())
        throw new UnsupportedOperationException();
    }

    @Override
    protected void doSetChainHead(StoredBlock chainHead) throws BlockStoreException {
        blockStore.setChainHead(chainHead);
    }

    @Override
    protected void notSettingChainHead() throws BlockStoreException {
        // We don't use DB transactions here, so we don't need to do anything
    }

    @Override
    protected StoredBlock getStoredBlockInCurrentScope(Sha256Hash hash) throws BlockStoreException {
        return blockStore.get(hash);
    }

    @Override
    public boolean add(FilteredBlock block) throws VerificationException, PrunedException {
        boolean success = super.add(block);
        if (success) {
            trackFilteredTransactions(block.getTransactionCount());
        }
        return success;
    }
}
