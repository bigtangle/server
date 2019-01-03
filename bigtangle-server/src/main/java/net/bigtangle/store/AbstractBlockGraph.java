/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.concurrent.locks.ReentrantLock;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Context;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.VerificationException;
import net.bigtangle.server.service.SolidityState;
import net.bigtangle.utils.Threading;
import net.bigtangle.wallet.Wallet;

/**
 * <p>
 * An AbstractBlockTangle holds a series of {@link Block} objects, links them
 * together, and knows how to verify that the tangle follows the rules of the
 * {@link NetworkParameters} for this tangle.
 * </p>
 *
 * <p>
 * It can be connected to a {@link Wallet}, and also
 * {@link TransactionReceivedInBlockListener}s that can receive transactions and
 * notifications of re-organizations.
 * </p>
 *
 * <p>
 * An AbstractBlockTangle implementation must be connected to a
 * {@link BlockStore} implementation. The tangle object by itself doesn't store
 * any data, that's delegated to the store. Which store you use is a decision
 * best made by reading the getting started guide, but briefly, fully validating
 * block tangles need fully validating stores.
 *
 * <p>
 * This class implements an abstract class which makes it simple to create a
 * BlockTangle that does/doesn't do full verification. It verifies headers and
 * is implements most of what is required to implement SPV mode, but also
 * provides callback hooks which can be used to do full verification.
 * </p>
 *
 * <p>
 * There are two subclasses of AbstractBlockTangle that are useful:
 * {@link BlockGraph}, which is the simplest class and implements <i>simplified
 * payment verification</i>. This is a lightweight and efficient mode that does
 * not verify the contents of blocks, just their headers. A
 * {@link FullPrunedBlockGraph} paired with a
 * {@link org.bitcoinj.store.H2FullPrunedBlockStore} implements full
 * verification, which is equivalent to Bitcoin Core. To learn more about the
 * alternative security models, please consult the articles on the website.
 * </p>
 *
 * <b>Theory</b>
 *
 * <p>
 * The 'tangle' is actually a tree although in normal operation it operates
 * mostly as a list of {@link Block}s. When multiple new head blocks are found
 * simultaneously, there are multiple stories of the economy competing to become
 * the one true consensus. This can happen naturally when two miners solve a
 * block within a few seconds of each other, or it can happen when the tangle is
 * under attack.
 * </p>
 *
 * <p>
 * A reference to the head block of the best known tangle is stored. If you can
 * reach the genesis block by repeatedly walking through the prevBlock pointers,
 * then we say this is a full tangle. If you cannot reach the genesis block we
 * say it is an orphan tangle. Orphan tangles can occur when blocks are solved
 * and received during the initial block tangle download, or if we connect to a
 * peer that doesn't send us blocks in order.
 * </p>
 *
 * <p>
 * A reorganize occurs when the blocks that make up the best known tangle
 * changes. Note that simply adding a new block to the top of the best tangle
 * isn't as reorganize, but that a reorganize is always triggered by adding a
 * new block that connects to some other (non best head) block. By "best" we
 * mean the tangle representing the largest amount of work done.
 * </p>
 *
 * <p>
 * Every so often the block tangle passes a difficulty transition point. At that
 * time, all the blocks in the last 2016 blocks are examined and a new
 * difficulty target is calculated from them.
 * </p>
 */
public abstract class AbstractBlockGraph {
    protected final ReentrantLock lock = Threading.lock("blocktangle");

    /** Keeps a map of block hashes to StoredBlocks. */
    protected final BlockStore blockStore;
    protected final NetworkParameters params;

    /**
     * Constructs a BlockTangle connected to the given list of listeners (eg,
     * wallets) and a store.
     */
    public AbstractBlockGraph(Context context, BlockStore blockStore) throws BlockStoreException {
        this.blockStore = blockStore;
        this.params = context.getParams();
    }

    /**
     * Returns the {@link BlockStore} the tangle was constructed with. You can
     * use this to iterate over the tangle.
     */
    public BlockStore getBlockStore() {
        return blockStore;
    }

    /**
     * Insert a currently unsolid block with the given SolidityState. The block
     * can then wait until the missing dependency is resolved.
     * 
     * @param block
     *            The unsolid block
     * @param solidityState
     *            The current solidity state of the block
     * @throws BlockStoreException
     */
    protected abstract void insertUnsolidBlock(Block block, SolidityState solidityState)
            throws BlockStoreException;

    /**
     * Processes a received block and tries to add it to the chain. If there's
     * something wrong with the block an exception is thrown. If the block is OK
     * but cannot be connected to the chain at this time, returns false. If the
     * block can be connected to the chain, returns true.
     * 
     * @param block
     *            The block to add.
     * @param allowUnsolid
     *            If set to false, will not allow unsolid blocks onto the
     *            waiting list.
     * @return true if the block can be connected to the chain, false if not
     *         possible currently.
     * @throws VerificationException
     *             if the block cannot be added to the chain.
     */
    public abstract boolean add(Block block, boolean allowUnsolid) throws VerificationException;
}
