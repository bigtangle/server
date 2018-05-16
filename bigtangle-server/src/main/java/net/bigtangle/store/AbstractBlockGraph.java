/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import static com.google.common.base.Preconditions.checkState;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Context;
import net.bigtangle.core.FilteredBlock;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PrunedException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutputChanges;
import net.bigtangle.core.VerificationException;
import net.bigtangle.core.listeners.NewBestBlockListener;
import net.bigtangle.core.listeners.ReorganizeListener;
import net.bigtangle.utils.ListenerRegistration;
import net.bigtangle.utils.Threading;
import net.bigtangle.utils.VersionTally;
import net.bigtangle.wallet.Wallet;

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
        long chainHeight = getBestChainHeight();
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
            // TODO add begindatabasebatchwrite and aborts in case of failure
            return add(block, true, null, null);
        } catch (BlockStoreException e) {
            e.printStackTrace();
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
    
    // filteredTxHashList contains all transactions, filteredTxn just a subset
    private boolean add(Block block, boolean tryConnecting,
                        @Nullable List<Sha256Hash> filteredTxHashList, @Nullable Map<Sha256Hash, Transaction> filteredTxn)
            throws BlockStoreException, VerificationException, PrunedException {
        lock.lock();
        try {
            // If we want to verify transactions (ie we are running with full blocks), verify that block has transactions
            if (shouldVerifyTransactions() && block.getTransactions() == null)
                throw new VerificationException("Got a block header while running in full-block mode");

            // Check for already-seen block, but only for full pruned mode, where the DB is
            // more likely able to handle these queries quickly.
//            if (shouldVerifyTransactions() && blockStore.get(block.getHash()) != null) {
//                return true;
//            }

            final StoredBlock storedPrev;
            final StoredBlock storedPrevBranch;
            final long height;
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
        }
        catch (Exception exception) {
            exception.printStackTrace();
            throw new BlockStoreException(exception);
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

        // This block connects to the best known block, it is a normal continuation of the system.
        TransactionOutputChanges txOutChanges = null;
        if (shouldVerifyTransactions())
            txOutChanges = connectTransactions(Math.max( storedPrev.getHeight(),storedPrevBranch.getHeight()) + 1, block);
        StoredBlock newStoredBlock = addToBlockStore(storedPrev,storedPrevBranch, block, txOutChanges);
        tryFirstSetSolidityAndHeight(newStoredBlock.getHeader());
		doSetChainHead(newStoredBlock);
    }
    
    protected abstract void tryFirstSetSolidityAndHeight(Block block) throws BlockStoreException;

	/**
     * Disconnect each transaction in the block (after reading it from the block store)
     * Only called if(shouldVerifyTransactions())
     * @throws PrunedException if block does not exist as a {@link StoredUndoableBlock} in the block store.
     * @throws BlockStoreException if the block store had an underlying error or block does not exist in the block store at all.
     */
    protected abstract void removeTransactionsFromMilestone(Block block) throws PrunedException, BlockStoreException;


    
    // TODO Remove these
    /**
     * @return the height of the best known chain, convenience for <tt>getChainHead().getHeight()</tt>.
     */
    public final long getBestChainHeight() {
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
