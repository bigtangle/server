/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.store;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockStore;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.FilteredBlock;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.StoredUndoableBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutputChanges;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.ReorganizeListener;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.utils.VersionTally;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

/**
 * <p>An AbstractBlockTangle holds a series of {@link Block} objects, links them together, and knows how to verify that
 * the tangle follows the rules of the {@link NetworkParameters} for this tangle.</p>
 *
 * <p>It can be connected to a {@link Wallet}, and also {@link TransactionReceivedInBlockListener}s that can receive transactions and
 * notifications of re-organizations.</p>
 *
 * <p>An AbstractBlockTangle implementation must be connected to a {@link BlockStore} implementation. The tangle object
 * by itself doesn't store any data, that's delegated to the store. Which store you use is a decision best made by
 * reading the getting started guide, but briefly, fully validating block tangles need fully validating stores. In
 * the lightweight SPV mode, a {@link org.bitcoinj.store.SPVBlockStore} is the right choice.</p>
 *
 * <p>This class implements an abstract class which makes it simple to create a BlockTangle that does/doesn't do full
 * verification.  It verifies headers and is implements most of what is required to implement SPV mode, but
 * also provides callback hooks which can be used to do full verification.</p>
 *
 * <p>There are two subclasses of AbstractBlockTangle that are useful: {@link BlockGraph}, which is the simplest
 * class and implements <i>simplified payment verification</i>. This is a lightweight and efficient mode that does
 * not verify the contents of blocks, just their headers. A {@link FullPrunedBlockGraph} paired with a
 * {@link org.bitcoinj.store.H2FullPrunedBlockStore} implements full verification, which is equivalent to
 * Bitcoin Core. To learn more about the alternative security models, please consult the articles on the
 * website.</p>
 *
 * <b>Theory</b>
 *
 * <p>The 'tangle' is actually a tree although in normal operation it operates mostly as a list of {@link Block}s.
 * When multiple new head blocks are found simultaneously, there are multiple stories of the economy competing to become
 * the one true consensus. This can happen naturally when two miners solve a block within a few seconds of each other,
 * or it can happen when the tangle is under attack.</p>
 *
 * <p>A reference to the head block of the best known tangle is stored. If you can reach the genesis block by repeatedly
 * walking through the prevBlock pointers, then we say this is a full tangle. If you cannot reach the genesis block
 * we say it is an orphan tangle. Orphan tangles can occur when blocks are solved and received during the initial block
 * tangle download, or if we connect to a peer that doesn't send us blocks in order.</p>
 *
 * <p>A reorganize occurs when the blocks that make up the best known tangle changes. Note that simply adding a
 * new block to the top of the best tangle isn't as reorganize, but that a reorganize is always triggered by adding
 * a new block that connects to some other (non best head) block. By "best" we mean the tangle representing the largest
 * amount of work done.</p>
 *
 * <p>Every so often the block tangle passes a difficulty transition point. At that time, all the blocks in the last
 * 2016 blocks are examined and a new difficulty target is calculated from them.</p>
 */
public abstract class AbstractBlockGraph {
    private static final Logger log = LoggerFactory.getLogger(AbstractBlockGraph.class);
    protected final ReentrantLock lock = Threading.lock("blocktangle");

    /** Keeps a map of block hashes to StoredBlocks. */
    private final BlockStore blockStore;

    /**
     * Tracks the top of the best known tangle.<p>
     *
     * Following this one down to the genesis block produces the story of the economy from the creation of Bitcoin
     * until the present day. The tangle head can change if a new set of blocks is received that results in a tangle of
     * greater work than the one obtained by following this one down. In that case a reorganize is triggered,
     * potentially invalidating transactions in our wallet.
     */
    protected StoredBlock chainHead;

    // TODO: Scrap this and use a proper read/write for all of the block tangle objects.
    // The tangleHead field is read/written synchronized with this object rather than BlockTangle. However writing is
    // also guaranteed to happen whilst BlockTangle is synchronized (see setTangleHead). The goal of this is to let
    // clients quickly access the tangle head even whilst the block tangle is downloading and thus the BlockTangle is
    // locked most of the time.
    private final Object chainHeadLock = new Object();

    protected final NetworkParameters params;
    private final CopyOnWriteArrayList<ListenerRegistration<NewBestBlockListener>> newBestBlockListeners;
    private final CopyOnWriteArrayList<ListenerRegistration<ReorganizeListener>> reorganizeListeners;
   
 
  

    /** False positive estimation uses a double exponential moving average. */
    public static final double FP_ESTIMATOR_ALPHA = 0.0001;
    /** False positive estimation uses a double exponential moving average. */
    public static final double FP_ESTIMATOR_BETA = 0.01;

    private double falsePositiveRate;
    private double falsePositiveTrend;
    private double previousFalsePositiveRate;

    private final VersionTally versionTally;

    /** See {@link #AbstractBlockTangle(Context, List, BlockStore)} */
    public AbstractBlockGraph(NetworkParameters params, List<? extends Wallet> transactionReceivedListeners,
                              BlockStore blockStore) throws BlockStoreException {
        this(Context.getOrCreate(params), transactionReceivedListeners, blockStore);
    }

    /**
     * Constructs a BlockTangle connected to the given list of listeners (eg, wallets) and a store.
     */
    public AbstractBlockGraph(Context context, List<? extends Wallet> wallets,
                              BlockStore blockStore) throws BlockStoreException {
        this.blockStore = blockStore;
        this.params = context.getParams();

        this.newBestBlockListeners = new CopyOnWriteArrayList<ListenerRegistration<NewBestBlockListener>>();
        this.reorganizeListeners = new CopyOnWriteArrayList<ListenerRegistration<ReorganizeListener>>();

 

        this.versionTally = new VersionTally(context.getParams());
        //this.versionTally.initialize(blockStore, chainHead);
    }

    /**
     * Add a wallet to the BlockTangle. Note that the wallet will be unaffected by any blocks received while it
     * was not part of this BlockTangle. This method is useful if the wallet has just been created, and its keys
     * have never been in use, or if the wallet has been loaded along with the BlockTangle. Note that adding multiple
     * wallets is not well tested!
     */
    public final void addWallet(Wallet wallet) {

        int walletHeight = wallet.getLastBlockSeenHeight();
        int chainHeight = getBestChainHeight();
        if (walletHeight != chainHeight) {
            log.warn("Wallet/chain height mismatch: {} vs {}", walletHeight, chainHeight);
            log.warn("Hashes: {} vs {}", wallet.getLastBlockSeenHash(), getChainHead().getHeader().getHash());

            // This special case happens when the VM crashes because of a transaction received. It causes the updated
            // block store to persist, but not the wallet. In order to fix the issue, we roll back the block store to
            // the wallet height to make it look like as if the block has never been received.
            if (walletHeight < chainHeight && walletHeight > 0) {
                try {
                    rollbackBlockStore(walletHeight);
                    log.info("Rolled back block store to height {}.", walletHeight);
                } catch (BlockStoreException x) {
                    log.warn("Rollback of block store failed, continuing with mismatched heights. This can happen due to a replay.");
                }
            }
        }
    }
  

    /**
     * Adds a {@link NewBestBlockListener} listener to the tangle.
     */
    public void addNewBestBlockListener(NewBestBlockListener listener) {
        addNewBestBlockListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds a {@link NewBestBlockListener} listener to the tangle.
     */
    public final void addNewBestBlockListener(Executor executor, NewBestBlockListener listener) {
        newBestBlockListeners.add(new ListenerRegistration<NewBestBlockListener>(listener, executor));
    }

    /**
     * Adds a generic {@link ReorganizeListener} listener to the tangle.
     */
    public void addReorganizeListener(ReorganizeListener listener) {
        addReorganizeListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds a generic {@link ReorganizeListener} listener to the tangle.
     */
    public final void addReorganizeListener(Executor executor, ReorganizeListener listener) {
        reorganizeListeners.add(new ListenerRegistration<ReorganizeListener>(listener, executor));
    }

  
  
    /**
     * Removes the given {@link NewBestBlockListener} from the tangle.
     */
    public void removeNewBestBlockListener(NewBestBlockListener listener) {
        ListenerRegistration.removeFromList(listener, newBestBlockListeners);
    }

    /**
     * Removes the given {@link ReorganizeListener} from the tangle.
     */
    public void removeReorganizeListener(ReorganizeListener listener) {
        ListenerRegistration.removeFromList(listener, reorganizeListeners);
    }

   
    /**
     * Returns the {@link BlockStore} the tangle was constructed with. You can use this to iterate over the tangle.
     */
    public BlockStore getBlockStore() {
        return blockStore;
    }
    
    /**
     * Adds/updates the given {@link Block} with the block store.
     * This version is used when the transactions have not been verified.
     * @param storedPrev The {@link StoredBlock} which immediately precedes block.
     * @param block The {@link Block} to add/update.
     * @return the newly created {@link StoredBlock}
     */
    protected abstract StoredBlock addToBlockStore(StoredBlock storedPrev, StoredBlock storedBlockPrevBranch, Block block)
            throws BlockStoreException, VerificationException;
    
    /**
     * Adds/updates the given {@link StoredBlock} with the block store.
     * This version is used when the transactions have already been verified to properly spend txOutputChanges.
     * @param storedPrev The {@link StoredBlock} which immediately precedes block.
     * @param header The {@link StoredBlock} to add/update.
     * @param txOutputChanges The total sum of all changes made by this block to the set of open transaction outputs
     *                        (from a call to connectTransactions), if in fully verifying mode (null otherwise).
     * @return the newly created {@link StoredBlock}
     */
    protected abstract StoredBlock addToBlockStore(StoredBlock storedPrev,StoredBlock storedBlockPrevBranch,  Block header,
                                                   @Nullable TransactionOutputChanges txOutputChanges)
            throws BlockStoreException, VerificationException;

    /**
     * Rollback the block store to a given height. This is currently only supported by {@link BlockGraph} instances.
     * 
     * @throws BlockStoreException
     *             if the operation fails or is unsupported.
     */
    protected abstract void rollbackBlockStore(int height) throws BlockStoreException;

    /**
     * Called before setting tangle head in memory.
     * Should write the new head to block store and then commit any database transactions
     * that were started by disconnectTransactions/connectTransactions.
     */
    protected abstract void doSetChainHead(StoredBlock chainHead) throws BlockStoreException;
    
    /**
     * Called if we (possibly) previously called disconnectTransaction/connectTransactions,
     * but will not be calling preSetChainHead as a block failed verification.
     * Can be used to abort database transactions that were started by
     * disconnectTransactions/connectTransactions.
     */
    protected abstract void notSettingChainHead() throws BlockStoreException;
    
    /**
     * For a standard BlockChain, this should return blockStore.get(hash),
     * for a FullPrunedBlockChain blockStore.getOnceUndoableStoredBlock(hash)
     */
    protected abstract StoredBlock getStoredBlockInCurrentScope(Sha256Hash hash) throws BlockStoreException;

    /**
     * Processes a received block and tries to add it to the chain. If there's something wrong with the block an
     * exception is thrown. If the block is OK but cannot be connected to the chain at this time, returns false.
     * If the block can be connected to the chain, returns true.
     * Accessing block's transactions in another thread while this method runs may result in undefined behavior.
     */
    public boolean add(Block block) throws VerificationException, PrunedException {
        try {
            return add(block, true, null, null);
        } catch (BlockStoreException e) {
            // TODO: Figure out a better way to propagate this exception to the user.
            throw new RuntimeException(e);
        } catch (VerificationException e) {
            e.printStackTrace();
            try {
                notSettingChainHead();
            } catch (BlockStoreException e1) {
                throw new RuntimeException(e1);
            }
            throw new VerificationException("Could not verify block:\n" +
                    block.toString(), e);
        }
    }
    
    /**
     * Processes a received block and tries to add it to the tangle. If there's something wrong with the block an
     * exception is thrown. If the block is OK but cannot be connected to the tangle at this time, returns false.
     * If the block can be connected to the tangle, returns true.
     */
    public boolean add(FilteredBlock block) throws VerificationException, PrunedException {
        try {
            // The block has a list of hashes of transactions that matched the Bloom filter, and a list of associated
            // Transaction objects. There may be fewer Transaction objects than hashes, this is expected. It can happen
            // in the case where we were already around to witness the initial broadcast, so we downloaded the
            // transaction and sent it to the wallet before this point (the wallet may have thrown it away if it was
            // a false positive, as expected in any Bloom filtering scheme). The filteredTxn list here will usually
            // only be full of data when we are catching up to the head of the tangle and thus haven't witnessed any
            // of the transactions.
            return add(block.getBlockHeader(), true, block.getTransactionHashes(), block.getAssociatedTransactions());
        } catch (BlockStoreException e) {
            // TODO: Figure out a better way to propagate this exception to the user.
            throw new RuntimeException(e);
        } catch (VerificationException e) {
            try {
                notSettingChainHead();
            } catch (BlockStoreException e1) {
                throw new RuntimeException(e1);
            }
            throw new VerificationException("Could not verify block " + block.getHash().toString() + "\n" +
                    block.toString(), e);
        }
    }
    
    /**
     * Whether or not we are maintaining a set of unspent outputs and are verifying all transactions.
     * Also indicates that all calls to add() should provide a block containing transactions
     */
     public abstract boolean shouldVerifyTransactions();
    
    /**
     * Connect each transaction in block.transactions, verifying them as we go and removing spent outputs
     * If an error is encountered in a transaction, no changes should be made to the underlying BlockStore.
     * and a VerificationException should be thrown.
     * Only called if(shouldVerifyTransactions())
     * @throws VerificationException if an attempt was made to spend an already-spent output, or if a transaction incorrectly solved an output script.
     * @throws BlockStoreException if the block store had an underlying error.
     * @return The full set of all changes made to the set of open transaction outputs.
     */
    protected abstract TransactionOutputChanges connectTransactions(long height, Block block) throws VerificationException, BlockStoreException;

    /**
     * Load newBlock from BlockStore and connect its transactions, returning changes to the set of unspent transactions.
     * If an error is encountered in a transaction, no changes should be made to the underlying BlockStore.
     * Only called if(shouldVerifyTransactions())
     * @throws PrunedException if newBlock does not exist as a {@link StoredUndoableBlock} in the block store.
     * @throws VerificationException if an attempt was made to spend an already-spent output, or if a transaction incorrectly solved an output script.
     * @throws BlockStoreException if the block store had an underlying error or newBlock does not exist in the block store at all.
     * @return The full set of all changes made to the set of open transaction outputs.
     */
//    protected abstract TransactionOutputChanges connectTransactions(StoredBlock newBlock) throws VerificationException, BlockStoreException, PrunedException;    
    
    // filteredTxHashList contains all transactions, filteredTxn just a subset
    private boolean add(Block block, boolean tryConnecting,
                        @Nullable List<Sha256Hash> filteredTxHashList, @Nullable Map<Sha256Hash, Transaction> filteredTxn)
            throws BlockStoreException, VerificationException, PrunedException {
        // TODO: Use read/write locks to ensure that during tangle download properties are still low latency.
        lock.lock();
        try {
            // Quick check for duplicates to avoid an expensive check further down (in findSplit). This can happen a lot
            // when connecting orphan transactions due to the dumb brute force algorithm we use.
            if (block.equals(getChainHead().getHeader())) {
                return true;
            }
         
            // If we want to verify transactions (ie we are running with full blocks), verify that block has transactions
            if (shouldVerifyTransactions() && block.getTransactions() == null)
                throw new VerificationException("Got a block header while running in full-block mode");

            // Check for already-seen block, but only for full pruned mode, where the DB is
            // more likely able to handle these queries quickly.
            if (shouldVerifyTransactions() && blockStore.get(block.getHash()) != null) {
                return true;
            }

            final StoredBlock storedPrev;
            final StoredBlock storedPrevBranch;
            final int height;
            final EnumSet<Block.VerifyFlag> flags;

            // Prove the block is internally valid: hash is lower than target, etc. This only checks the block contents
            // if there is a tx sending or receiving coins using an address in one of our wallets. And those transactions
            // are only lightly verified: presence in a valid connecting block is taken as proof of validity. See the
            // article here for more details: https://bitcoinj.github.io/security-model
            try {
                block.verifyHeader();
                storedPrev = getStoredBlockInCurrentScope(block.getPrevBlockHash());
                storedPrevBranch = getStoredBlockInCurrentScope(block.getPrevBranchBlockHash());
                
                if (storedPrev != null && storedPrevBranch != null) {
                    height = Math.max(storedPrev.getHeight(), storedPrevBranch.getHeight()) + 1;
                } else {
                    height = Block.BLOCK_HEIGHT_UNKNOWN;
                }
                 
                
                flags = params.getBlockVerificationFlags(block, versionTally, height);
                if (shouldVerifyTransactions())
                    block.verifyTransactions(height, flags);
            } catch (VerificationException e) {
                log.error("Failed to verify block: ", e);
                log.error(block.getHashAsString());
                throw e;
            }

            // Try linking it to a place in the currently known blocks.

           
                checkState(lock.isHeldByCurrentThread());
                // It connects to somewhere on the tangle. Not necessarily the top of the best known tangle.
               //  params.checkDifficultyTransitions(storedPrev, block, blockStore);
                connectBlock(block, storedPrev,storedPrevBranch,  shouldVerifyTransactions(), filteredTxHashList, filteredTxn);
            
 
            return true;
        } finally {
            lock.unlock();
        }
    }

    
    // expensiveChecks enables checks that require looking at blocks further back in the tangle
    // than the previous one when connecting (eg median timestamp check)
    // It could be exposed, but for now we just set it to shouldVerifyTransactions()
    private void connectBlock(final Block block, StoredBlock storedPrev,StoredBlock storedPrevBranch, boolean expensiveChecks,
                              @Nullable final List<Sha256Hash> filteredTxHashList,
                              @Nullable final Map<Sha256Hash, Transaction> filteredTxn) throws BlockStoreException, VerificationException, PrunedException {
        checkState(lock.isHeldByCurrentThread());
        boolean filtered = filteredTxHashList != null && filteredTxn != null;
        // Check that we aren't connecting a block that fails a checkpoint check
        if (!params.passesCheckpoint(
                Math.max( storedPrev.getHeight(),storedPrevBranch.getHeight()) + 1, block.getHash()))
            throw new VerificationException("Block failed checkpoint lockin at " + (storedPrev.getHeight() + 1));
        if (shouldVerifyTransactions()) {
            checkNotNull(block.getTransactions());
            for (Transaction tx : block.getTransactions())
                if (!tx.isFinal(Math.max( storedPrev.getHeight(),storedPrevBranch.getHeight()) + 1, block.getTimeSeconds()))
                   throw new VerificationException("Block contains non-final transaction");
        }
        
        StoredBlock head = getChainHead();
        if (storedPrev.equals(head)) {
            if (filtered && filteredTxn.size() > 0)  {
                log.debug("Block {} connects to top of best chain with {} transaction(s) of which we were sent {}",
                        block.getHashAsString(), filteredTxHashList.size(), filteredTxn.size());
                for (Sha256Hash hash : filteredTxHashList) log.debug("  matched tx {}", hash);
            }
            //TODO change check to previous two blocks
            if (expensiveChecks && block.getTimeSeconds() <= getMedianTimestampOfRecentBlocks(head, blockStore))
                throw new VerificationException("Block's timestamp is too early");

            // BIP 66 & 65: Enforce block version 3/4 once they are a supermajority of blocks
            // NOTE: This requires 1,000 blocks since the last checkpoint (on main
            // net, less on test) in order to be applied. It is also limited to
            // stopping addition of new v2/3 blocks to the tip of the chain.
            if (block.getVersion() == Block.BLOCK_VERSION_BIP34
                || block.getVersion() == Block.BLOCK_VERSION_BIP66) {
                final Integer count = versionTally.getCountAtOrAbove(block.getVersion() + 1);
                if (count != null
                    && count >= params.getMajorityRejectBlockOutdated()) {
                    throw new VerificationException.BlockVersionOutOfDate(block.getVersion());
                }
            }

            // This block connects to the best known block, it is a normal continuation of the system.
            TransactionOutputChanges txOutChanges = null;
//            if (shouldVerifyTransactions())
//                txOutChanges = connectTransactions(Math.max( storedPrev.getHeight(),storedPrevBranch.getHeight()) + 1, block);
//            StoredBlock newStoredBlock = addToBlockStore(storedPrev,storedPrevBranch,
//                    block.transactions == null ? block : block.cloneAsHeader(), txOutChanges);
            StoredBlock newStoredBlock = addToBlockStore(storedPrev,storedPrevBranch,
            block, txOutChanges);
            versionTally.add(block.getVersion());
            //setChainHead(newStoredBlock);
            log.debug("Chain is now {} blocks high, running listeners", newStoredBlock.getHeight());
          //  informListenersForNewBlock(block, NewBlockType.BEST_CHAIN, filteredTxHashList, filteredTxn, newStoredBlock);
        } else {
            // This block connects to somewhere other than the top of the best known chain. We treat these differently.
            //
            // Note that we send the transactions to the wallet FIRST, even if we're about to re-organize this block
            // to become the new best chain head. This simplifies handling of the re-org in the Wallet class.
            StoredBlock newBlock = storedPrev.build(block, storedPrevBranch);
        
                StoredBlock splitPoint = findSplit(newBlock, head, blockStore);
                if (splitPoint != null && splitPoint.equals(newBlock)) {
                    // newStoredBlock is a part of the same chain, there's no fork. This happens when we receive a block
                    // that we already saw and linked into the chain previously, which isn't the chain head.
                    // Re-processing it is confusing for the wallet so just skip.
                    log.warn("Saw duplicated block in main chain at height {}: {}",
                            newBlock.getHeight(), newBlock.getHeader().getHash());
                    return;
                }
                if (splitPoint == null) {
                    // This should absolutely never happen
                    // (lets not write the full block to disk to keep any bugs which allow this to happen
                    //  from writing unreasonable amounts of data to disk)
                    throw new VerificationException("Block forks the chain but splitPoint is null");
                } else {
                    // We aren't actually spending any transactions (yet) because we are on a fork
                    addToBlockStore(storedPrev,storedPrevBranch, block);
                    int splitPointHeight = splitPoint.getHeight();
                    String splitPointHash = splitPoint.getHeader().getHashAsString();
                    log.info("Block forks the chain at height {}/block {}, but it did not cause a reorganize:\n{}",
                            splitPointHeight, splitPointHash, newBlock.getHeader().getHashAsString());
                }
            
            
            // We may not have any transactions if we received only a header, which can happen during fast catchup.
            // If we do, send them to the wallet but state that they are on a side chain so it knows not to try and
            // spend them until they become activated.
            if (block.getTransactions() != null || filtered) {
             //   informListenersForNewBlock(block, NewBlockType.SIDE_CHAIN, filteredTxHashList, filteredTxn, newBlock);
            }
            
            
        }
    }



 

    /**
     * Gets the median timestamp of the last 11 blocks
     */
    private static long getMedianTimestampOfRecentBlocks(StoredBlock storedBlock,
                                                         BlockStore store) throws BlockStoreException {
        long[] timestamps = new long[11];
        int unused = 9;
        timestamps[10] = storedBlock.getHeader().getTimeSeconds();
        while (unused >= 0 && (storedBlock = storedBlock.getPrev(store)) != null)
            timestamps[unused--] = storedBlock.getHeader().getTimeSeconds();
        
        Arrays.sort(timestamps, unused+1, 11);
        return timestamps[unused + (11-unused)/2];
    }
    
    /**
     * Disconnect each transaction in the block (after reading it from the block store)
     * Only called if(shouldVerifyTransactions())
     * @throws PrunedException if block does not exist as a {@link StoredUndoableBlock} in the block store.
     * @throws BlockStoreException if the block store had an underlying error or block does not exist in the block store at all.
     */
    protected abstract void disconnectTransactions(Block block) throws PrunedException, BlockStoreException;

    /**
     * Called as part of connecting a block when the new block results in a different chain having higher total work.
     * 
     * if (shouldVerifyTransactions)
     *     Either newChainHead needs to be in the block store as a FullStoredBlock, or (block != null && block.transactions != null)
     */
//    private void handleNewBestChain(StoredBlock storedPrev, StoredBlock storedPrevBranch, StoredBlock newChainHead, Block block, boolean expensiveChecks)
//            throws BlockStoreException, VerificationException, PrunedException {
//        checkState(lock.isHeldByCurrentThread());
//        // This chain has overtaken the one we currently believe is best. Reorganize is required.
//        //
//        // Firstly, calculate the block at which the chain diverged. We only need to examine the
//        // chain from beyond this block to find differences.
//        StoredBlock head = getChainHead();
//        final StoredBlock splitPoint = findSplit(newChainHead, head, blockStore);
//        log.info("Re-organize after split at height {}", splitPoint.getHeight());
//        log.info("Old chain head: {}", head.getHeader().getHashAsString());
//        log.info("New chain head: {}", newChainHead.getHeader().getHashAsString());
//        log.info("Split at block: {}", splitPoint.getHeader().getHashAsString());
//        // Then build a list of all blocks in the old part of the chain and the new part.
//        final LinkedList<StoredBlock> oldBlocks = getPartialChain(head, splitPoint, blockStore);
//        final LinkedList<StoredBlock> newBlocks = getPartialChain(newChainHead, splitPoint, blockStore);
//        // Disconnect each transaction in the previous main chain that is no longer in the new main chain
//        StoredBlock storedNewHead = splitPoint;
//        if (shouldVerifyTransactions()) {
//            for (StoredBlock oldBlock : oldBlocks) {
//                try {
//                    //disconnectTransactions(oldBlock);
//                } catch (PrunedException e) {
//                    // We threw away the data we need to re-org this deep! We need to go back to a peer with full
//                    // block contents and ask them for the relevant data then rebuild the indexs. Or we could just
//                    // give up and ask the human operator to help get us unstuck (eg, rescan from the genesis block).
//                    // TODO: Retry adding this block when we get a block with hash e.getHash()
//                    throw e;
//                }
//            }
//            StoredBlock cursor;
//            // Walk in ascending chronological order.
//            for (Iterator<StoredBlock> it = newBlocks.descendingIterator(); it.hasNext();) {
//                cursor = it.next();
//                Block cursorBlock = cursor.getHeader();
//                if (expensiveChecks && cursorBlock.getTimeSeconds() <= getMedianTimestampOfRecentBlocks(cursor.getPrev(blockStore), blockStore))
//                    throw new VerificationException("Block's timestamp is too early during reorg");
//                TransactionOutputChanges txOutChanges;
//                if (cursor != newChainHead || block == null)
//                    txOutChanges = connectTransactions(cursor);
//                else
//                    txOutChanges = connectTransactions(newChainHead.getHeight(), block);
//                storedNewHead = addToBlockStore(storedNewHead, storedPrevBranch,cursorBlock.cloneAsHeader(), txOutChanges);
//            }
//        } else {
//            // (Finally) write block to block store
//            storedNewHead = addToBlockStore(storedPrev, storedPrevBranch, newChainHead.getHeader());
//        }
//        // Now inform the listeners. This is necessary so the set of currently active transactions (that we can spend)
//        // can be updated to take into account the re-organize. We might also have received new coins we didn't have
//        // before and our previous spends might have been undone.
//        for (final ListenerRegistration<ReorganizeListener> registration : reorganizeListeners) {
//            if (registration.executor == Threading.SAME_THREAD) {
//                // Short circuit the executor so we can propagate any exceptions.
//                // TODO: Do we really need to do this or should it be irrelevant?
//                registration.listener.reorganize(splitPoint, oldBlocks, newBlocks);
//            } else {
//                registration.executor.execute(new Runnable() {
//                    @Override
//                    public void run() {
//                        try {
//                            registration.listener.reorganize(splitPoint, oldBlocks, newBlocks);
//                        } catch (VerificationException e) {
//                            log.error("Block chain listener threw exception during reorg", e);
//                        }
//                    }
//                });
//            }
//        }
//        // Update the pointer to the best known block.
//        setChainHead(storedNewHead);
//    }

    /**
     * Returns the set of contiguous blocks between 'higher' and 'lower'. Higher is included, lower is not.
     */
    private static LinkedList<StoredBlock> getPartialChain(StoredBlock higher, StoredBlock lower, BlockStore store) throws BlockStoreException {
        checkArgument(higher.getHeight() > lower.getHeight(), "higher and lower are reversed");
        LinkedList<StoredBlock> results = new LinkedList<StoredBlock>();
        StoredBlock cursor = higher;
        while (true) {
            results.add(cursor);
            cursor = checkNotNull(cursor.getPrev(store), "Ran off the end of the chain");
            if (cursor.equals(lower)) break;
        }
        return results;
    }

    /**
     * Locates the point in the chain at which newStoredBlock and chainHead diverge. Returns null if no split point was
     * found (ie they are not part of the same chain). Returns newChainHead or chainHead if they don't actually diverge
     * but are part of the same chain.
     */
    private static StoredBlock findSplit(StoredBlock newChainHead, StoredBlock oldChainHead,
                                         BlockStore store) throws BlockStoreException {
        StoredBlock currentChainCursor = oldChainHead;
        StoredBlock newChainCursor = newChainHead;
        // Loop until we find the block both chains have in common. Example:
        //
        //    A -> B -> C -> D
        //         \--> E -> F -> G
        //
        // findSplit will return block B. oldChainHead = D and newChainHead = G.
        while (!currentChainCursor.equals(newChainCursor)) {
            if (currentChainCursor.getHeight() > newChainCursor.getHeight()) {
                currentChainCursor = currentChainCursor.getPrev(store);
                checkNotNull(currentChainCursor, "Attempt to follow an orphan chain");
            } else {
                newChainCursor = newChainCursor.getPrev(store);
                checkNotNull(newChainCursor, "Attempt to follow an orphan chain");
            }
        }
        return currentChainCursor;
    }

    /**
     * @return the height of the best known chain, convenience for <tt>getChainHead().getHeight()</tt>.
     */
    public final int getBestChainHeight() {
        return getChainHead().getHeight();
    }

    public enum NewBlockType {
        BEST_CHAIN,
        SIDE_CHAIN
    }

 

    protected void setChainHead(StoredBlock chainHead) throws BlockStoreException {
        doSetChainHead(chainHead);
        synchronized (chainHeadLock) {
            this.chainHead = chainHead;
        }
    }

    
    /**
     * Returns the block at the head of the current best chain. This is the block which represents the greatest
     * amount of cumulative work done.
     */
    public StoredBlock getChainHead() {
        synchronized (chainHeadLock) {
            return chainHead;
        }
    }
 
 
    /**
     * Returns an estimate of when the given block will be reached, assuming a perfect 10 minute average for each
     * block. This is useful for turning transaction lock times into human readable times. Note that a height in
     * the past will still be estimated, even though the time of solving is actually known (we won't scan backwards
     * through the chain to obtain the right answer).
     */
    public Date estimateBlockTime(int height) {
        synchronized (chainHeadLock) {
            long offset = height - chainHead.getHeight();
            long headTime = chainHead.getHeader().getTimeSeconds();
            long estimated = (headTime * 1000) + (1000L * 60L * 10L * offset);
            return new Date(estimated);
        }
    }

    /**
     * Returns a future that completes when the block chain has reached the given height. Yields the
     * {@link StoredBlock} of the block that reaches that height first. The future completes on a peer thread.
     */
    public ListenableFuture<StoredBlock> getHeightFuture(final int height) {
        final SettableFuture<StoredBlock> result = SettableFuture.create();
        addNewBestBlockListener(Threading.SAME_THREAD, new NewBestBlockListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                if (block.getHeight() >= height) {
                    removeNewBestBlockListener(this);
                    result.set(block);
                }
            }
        });
        return result;
    }



    /**
     * The false positive rate is the average over all blockchain transactions of:
     *
     * - 1.0 if the transaction was false-positive (was irrelevant to all listeners)
     * - 0.0 if the transaction was relevant or filtered out
     */
    public double getFalsePositiveRate() {
        return falsePositiveRate;
    }

    /*
     * We completed handling of a filtered block. Update false-positive estimate based
     * on the total number of transactions in the original block.
     *
     * count includes filtered transactions, transactions that were passed in and were relevant
     * and transactions that were false positives (i.e. includes all transactions in the block).
     */
    public void trackFilteredTransactions(int count) {
        // Track non-false-positives in batch.  Each non-false-positive counts as
        // 0.0 towards the estimate.
        //
        // This is slightly off because we are applying false positive tracking before non-FP tracking,
        // which counts FP as if they came at the beginning of the block.  Assuming uniform FP
        // spread in a block, this will somewhat underestimate the FP rate (5% for 1000 tx block).
        double alphaDecay = Math.pow(1 - FP_ESTIMATOR_ALPHA, count);

        // new_rate = alpha_decay * new_rate
        falsePositiveRate = alphaDecay * falsePositiveRate;

        double betaDecay = Math.pow(1 - FP_ESTIMATOR_BETA, count);

        // trend = beta * (new_rate - old_rate) + beta_decay * trend
        falsePositiveTrend =
                FP_ESTIMATOR_BETA * count * (falsePositiveRate - previousFalsePositiveRate) +
                betaDecay * falsePositiveTrend;

        // new_rate += alpha_decay * trend
        falsePositiveRate += alphaDecay * falsePositiveTrend;

        // Stash new_rate in old_rate
        previousFalsePositiveRate = falsePositiveRate;
    }

    /* Irrelevant transactions were received.  Update false-positive estimate. */
   public void trackFalsePositives(int count) {
        // Track false positives in batch by adding alpha to the false positive estimate once per count.
        // Each false positive counts as 1.0 towards the estimate.
        falsePositiveRate += FP_ESTIMATOR_ALPHA * count;
        if (count > 0)
            log.debug("{} false positives, current rate = {} trend = {}", count, falsePositiveRate, falsePositiveTrend);
    }

    /** Resets estimates of false positives. Used when the filter is sent to the peer. */
    public void resetFalsePositiveEstimate() {
        falsePositiveRate = 0;
        falsePositiveTrend = 0;
        previousFalsePositiveRate = 0;
    }

    protected VersionTally getVersionTally() {
        return versionTally;
    }

    
}
