/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.HashSet;
import java.util.List;

import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Order;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProvider;

/**
 * <p>
 * An implementor of FullPrunedBlockStore saves StoredBlock objects to some
 * storage mechanism.
 * </p>
 * 
 * <p>
 * In addition to keeping track of a chain using {@link StoredBlock}s, it should
 * also keep track of a second copy of the chain which holds
 * {@link StoredUndoableBlock}s. In this way, an application can perform a
 * headers-only initial sync and then use that information to more efficiently
 * download a locally verified full copy of the block chain.
 * </p>
 * 
 * <p>
 * A FullPrunedBlockStore should function well as a standard {@link BlockStore}
 * and then be able to trivially switch to being used as a FullPrunedBlockStore.
 * </p>
 * 
 * <p>
 * It should store the {@link StoredUndoableBlock}s of a number of recent blocks
 * before verifiedHead.height and all those after verifiedHead.height. It is
 * advisable to store any {@link StoredUndoableBlock} which has a height >
 * verifiedHead.height - N. Because N determines the memory usage, it is
 * recommended that N be customizable. N should be chosen such that re-orgs
 * beyond that point are vanishingly unlikely, for example, a few thousand
 * blocks is a reasonable choice.
 * </p>
 * 
 * <p>
 * It must store the {@link StoredBlock} of all blocks.
 * </p>
 *
 * <p>
 * A FullPrunedBlockStore contains a map of hashes to [Full]StoredBlock. The
 * hash is the double digest of the Bitcoin serialization of the block header,
 * <b>not</b> the header with the extra data as well.
 * </p>
 * 
 * <p>
 * A FullPrunedBlockStore also contains a map of hash+index to UTXO. Again, the
 * hash is a standard Bitcoin double-SHA256 hash of the transaction.
 * </p>
 *
 * <p>
 * FullPrunedBlockStores are thread safe.
 * </p>
 */
public interface FullPrunedBlockStore extends BlockStore, UTXOProvider {
    /**
     * <p>
     * Saves the given {@link StoredUndoableBlock} and {@link StoredBlock}.
     * Calculates keys from the {@link StoredBlock}
     * </p>
     * 
     * <p>
     * Though not required for proper function of a FullPrunedBlockStore, any
     * user of a FullPrunedBlockStore should ensure that a StoredUndoableBlock
     * for each block up to the fully verified chain head has been added to this
     * block store using this function (not put(StoredBlock)), so that the
     * ability to perform reorgs is maintained.
     * </p>
     * 
     * @throws BlockStoreException
     *             if there is a problem with the underlying storage layer, such
     *             as running out of disk space.
     */
    void put(StoredBlock storedBlock, StoredUndoableBlock undoableBlock) throws BlockStoreException;

    /**
     * Returns the StoredBlock that was added as a StoredUndoableBlock given a
     * hash. The returned values block.getHash() method will be equal to the
     * parameter. If no such block is found, returns null.
     */
    StoredBlock getOnceUndoableStoredBlock(Sha256Hash hash) throws BlockStoreException;

    /**
     * Returns a {@link StoredUndoableBlock} whose block.getHash() method will
     * be equal to the parameter. If no such block is found, returns null. Note
     * that this may return null more often than get(Sha256Hash hash) as not all
     * {@link StoredBlock}s have a {@link StoredUndoableBlock} copy stored as
     * well.
     */
    StoredUndoableBlock getUndoBlock(Sha256Hash hash) throws BlockStoreException;

    /**
     * Gets a {@link net.bigtangle.core.UTXO} with the given hash and index, or
     * null if none is found
     */
    UTXO getTransactionOutput(Sha256Hash hash, long index) throws BlockStoreException;

    /**
     * Adds a {@link net.bigtangle.core.UTXO} to the list of unspent
     * TransactionOutputs
     */
    void addUnspentTransactionOutput(UTXO out) throws BlockStoreException;

    /**
     * Removes a {@link net.bigtangle.core.UTXO} from the list of unspent
     * TransactionOutputs Note that the coinbase of the genesis block should
     * NEVER be spendable and thus never in the list.
     * 
     * @throws BlockStoreException
     *             if there is an underlying storage issue, or out was not in
     *             the list.
     */
    void removeUnspentTransactionOutput(Sha256Hash prevTxHash, long index) throws BlockStoreException;

    /**
     * True if this store has any unspent outputs from a transaction with a hash
     * equal to the first parameter
     * 
     * @param numOutputs
     *            the number of outputs the given transaction has
     */
    boolean hasUnspentOutputs(Sha256Hash hash, int numOutputs) throws BlockStoreException;

    /**
     * Returns the {@link StoredBlock} that represents the top of the chain of
     * greatest total work that has been fully verified and the point in the
     * chain at which the unspent transaction output set in this store
     * represents.
     */
    StoredBlock getVerifiedChainHead() throws BlockStoreException;

    /**
     * Sets the {@link StoredBlock} that represents the top of the chain of
     * greatest total work that has been fully verified. It should generally be
     * set after a batch of updates to the transaction unspent output set,
     * before a call to commitDatabaseBatchWrite.
     * 
     * If chainHead has a greater height than the non-verified chain head (ie
     * that set with {@link BlockStore#setChainHead}) the non-verified chain
     * head should be set to the one set here. In this way a class using a
     * FullPrunedBlockStore only in full-verification mode can ignore the
     * regular {@link BlockStore} functions implemented as a part of a
     * FullPrunedBlockStore.
     */
    void setVerifiedChainHead(StoredBlock chainHead) throws BlockStoreException;

    /**
     * <p>
     * Begins/Commits/Aborts a database transaction.
     * </p>
     *
     * <p>
     * If abortDatabaseBatchWrite() is called by the same thread that called
     * beginDatabaseBatchWrite(), any data writes between this call and
     * abortDatabaseBatchWrite() made by the same thread should be discarded.
     * </p>
     *
     * <p>
     * Furthermore, any data written after a call to beginDatabaseBatchWrite()
     * should not be readable by any other threads until
     * commitDatabaseBatchWrite() has been called by this thread. Multiple calls
     * to beginDatabaseBatchWrite() in any given thread should be ignored and
     * treated as one call.
     * </p>
     */
    void beginDatabaseBatchWrite() throws BlockStoreException;

    void commitDatabaseBatchWrite() throws BlockStoreException;

    void abortDatabaseBatchWrite() throws BlockStoreException;

    /*
     * get the blocks which confirm the block
     */
    public List<StoredBlock> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException;

    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash hash) throws BlockStoreException;

    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException;

    public void insertBlockEvaluation(BlockEvaluation blockEvaluation) throws BlockStoreException;
    
    public void removeBlockEvaluation(Sha256Hash hash) throws BlockStoreException;

	public long getMaxSolidHeight() throws BlockStoreException;

	public List<Sha256Hash> getNonSolidBlocks() throws BlockStoreException;

	public List<BlockEvaluation> getSolidBlockEvaluations() throws BlockStoreException;
	
	public List<BlockEvaluation> getAllBlockEvaluations() throws BlockStoreException;

	public List<BlockEvaluation> getSolidBlocksOfHeight(long height) throws BlockStoreException;

	public List<BlockEvaluation> getSolidTips() throws BlockStoreException;

	public HashSet<BlockEvaluation> getBlocksToRemoveFromMilestone() throws BlockStoreException;

	public HashSet<BlockEvaluation> getBlocksToAddToMilestone(long minDepth) throws BlockStoreException;

	public void updateBlockEvaluationSolid(Sha256Hash blockhash, boolean b) throws BlockStoreException;

	public void updateBlockEvaluationHeight(Sha256Hash blockhash, long i) throws BlockStoreException;

	public void updateBlockEvaluationCumulativeweight(Sha256Hash blockhash, long i) throws BlockStoreException;

	public void updateBlockEvaluationDepth(Sha256Hash blockhash, long i) throws BlockStoreException;

	public void updateBlockEvaluationRating(Sha256Hash blockhash, long i) throws BlockStoreException;

	public void updateBlockEvaluationMilestone(Sha256Hash blockhash, boolean b) throws BlockStoreException;

	public void updateBlockEvaluationMilestoneLastUpdateTime(Sha256Hash blockhash, long now) throws BlockStoreException;
	

	public void updateBlockEvaluationMilestoneDepth(Sha256Hash blockhash, long i) throws BlockStoreException;

	public void updateBlockEvaluationMaintained(Sha256Hash blockhash, boolean b) throws BlockStoreException;

	public void updateBlockEvaluationRewardValid(Sha256Hash blockhash, boolean b) throws BlockStoreException;


	public void deleteTip(Sha256Hash blockhash) throws BlockStoreException;

	public void insertTip(Sha256Hash blockhash) throws BlockStoreException;

	public BlockEvaluation getTransactionOutputSpender(Sha256Hash prevBlockHash, long index) throws BlockStoreException;

	public void updateTransactionOutputSpent(Sha256Hash prevBlockHash, long index, boolean b, Sha256Hash spenderBlock) throws BlockStoreException;

	public void updateTransactionOutputConfirmed(Sha256Hash hash, long index, boolean b) throws BlockStoreException;

	public void updateTransactionOutputSpendPending(Sha256Hash hash, long index, boolean b) throws BlockStoreException;

    public int getMaxTokenId() throws BlockStoreException;

    public List<Tokens> getTokensList() throws BlockStoreException;
    
    public void saveTokens(Tokens tokens) throws BlockStoreException;

    public void saveTokens(byte[] tokenid, String tokenname, long amount, String description, int blocktype) throws BlockStoreException;

    public void saveOrder(Order order) throws BlockStoreException;

    public List<Order> getOrderList() throws BlockStoreException;
}
