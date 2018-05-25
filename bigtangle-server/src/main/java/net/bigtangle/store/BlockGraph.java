/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import static com.google.common.base.Preconditions.checkArgument;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Context;
import net.bigtangle.core.FilteredBlock;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PrunedException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.TransactionOutputChanges;
import net.bigtangle.core.VerificationException;
import net.bigtangle.wallet.Wallet;

import java.util.ArrayList;
import java.util.List;


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
        StoredBlock newBlock =StoredBlock. build(blockHeader,storedPrev,storedPrevBranch);
        blockStore.put(newBlock);
        return newBlock;
    }
    
    @Override
    protected StoredBlock addToBlockStore(StoredBlock storedPrev, StoredBlock storedPrevBranch,Block blockHeader)
            throws BlockStoreException, VerificationException {
        StoredBlock newBlock = StoredBlock. build(blockHeader,storedPrev,storedPrevBranch);
        blockStore.put(newBlock);
        return newBlock;
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

	@Override
	protected void tryFirstSetSolidityAndHeight(Block block) throws BlockStoreException {
	}
}
