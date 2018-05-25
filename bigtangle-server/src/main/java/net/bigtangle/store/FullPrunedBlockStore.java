/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.apache.commons.lang3.tuple.Triple;

import net.bigtangle.core.Address;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.OrderMatch;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProvider;
import net.bigtangle.kafka.KafkaMessageProducer;

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
    boolean hasUnspentOutputs(Sha256Hash hash) throws BlockStoreException;

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

    public List<BlockEvaluation> getBlocksInMilestoneDepthInterval(long minDepth, long maxDepth)
            throws BlockStoreException;

    public void updateBlockEvaluationSolid(Sha256Hash blockhash, boolean b) throws BlockStoreException;

    public void updateBlockEvaluationHeight(Sha256Hash blockhash, long i) throws BlockStoreException;

    public void updateBlockEvaluationCumulativeweight(Sha256Hash blockhash, long i) throws BlockStoreException;

    public void updateBlockEvaluationDepth(Sha256Hash blockhash, long i) throws BlockStoreException;

    public void updateBlockEvaluationRating(Sha256Hash blockhash, long i) throws BlockStoreException;

    public void updateBlockEvaluationMilestone(Sha256Hash blockhash, boolean b) throws BlockStoreException;

    public void updateBlockEvaluationMilestoneDepth(Sha256Hash blockhash, long i) throws BlockStoreException;

    public void updateBlockEvaluationMaintained(Sha256Hash blockhash, boolean b) throws BlockStoreException;

    public void updateBlockEvaluationRewardValid(Sha256Hash blockhash, boolean b) throws BlockStoreException;

    public void deleteTip(Sha256Hash blockhash) throws BlockStoreException;

    public void insertTip(Sha256Hash blockhash) throws BlockStoreException;

    public BlockEvaluation getTransactionOutputSpender(Sha256Hash prevBlockHash, long index) throws BlockStoreException;

    public void updateTransactionOutputSpent(Sha256Hash prevBlockHash, long index, boolean b, Sha256Hash spenderBlock)
            throws BlockStoreException;

    public void updateTransactionOutputConfirmed(Sha256Hash hash, long index, boolean b) throws BlockStoreException;

    public void updateTransactionOutputSpendPending(Sha256Hash hash, long index, boolean b) throws BlockStoreException;

    public int getMaxTokenId() throws BlockStoreException;

    public List<Tokens> getTokensList() throws BlockStoreException;

    public List<Tokens> getTokensList(String name) throws BlockStoreException;

    public Map<String, Long> getTokenAmountMap(String name) throws BlockStoreException;

    public void saveTokens(Tokens tokens) throws BlockStoreException;

    public void saveTokens(String tokenid, String tokenname, String description, String url, long signnumber,
            boolean multiserial, boolean asmarket, boolean tokenstop) throws BlockStoreException;

    public void saveOrderPublish(OrderPublish orderPublish) throws BlockStoreException;

    public void saveOrderMatch(OrderMatch orderMatch) throws BlockStoreException;

    public List<OrderPublish> getOrderPublishListWithCondition(Map<String, Object> request) throws BlockStoreException;

    public void saveExchange(Exchange exchange) throws BlockStoreException;

    public List<Exchange> getExchangeListWithAddress(String address) throws BlockStoreException;

    public void updateExchangeSign(String orderid, String signtype, byte[] data) throws BlockStoreException;

    public OrderPublish getOrderPublishByOrderid(String orderid) throws BlockStoreException;

    public Exchange getExchangeInfoByOrderid(String orderid) throws BlockStoreException;

    public void updateOrderPublishState(String orderid, int state) throws BlockStoreException;

    public List<BlockEvaluation> getSearchBlockEvaluations(List<String> address) throws BlockStoreException;

    public List<BlockEvaluation> getSearchBlockEvaluations(List<String> address, String lastestAmount)
            throws BlockStoreException;

    public void resetStore() throws BlockStoreException;

    public void updateUnmaintainAll() throws BlockStoreException;

    public void streamBlocks(long heightstart, KafkaMessageProducer kafkaMessageProducer) throws BlockStoreException;

    public List<OrderPublish> getOrderPublishListWithNotMatch() throws BlockStoreException;

    public List<MultiSignAddress> getMultiSignAddressListByTokenid(String tokenid) throws BlockStoreException;

    public void insertMultiSignAddress(MultiSignAddress multiSignAddress) throws BlockStoreException;

    public void deleteMultiSignAddress(String tokenid, String address) throws BlockStoreException;

    public void insertTokenSerial(TokenSerial tokenSerial) throws BlockStoreException;

    public void insertMultisignby(MultiSignBy multisignby) throws BlockStoreException;

    public int getCountMultiSignAddress(String tokenid) throws BlockStoreException;

    public Tokens getTokensInfo(String tokenid) throws BlockStoreException;

    public List<TokenSerial> getSearchTokenSerialInfo(String tokenid, List<String> addresses)
            throws BlockStoreException;

    public TokenSerial getTokenSerialInfo(String tokenid, long tokenindex) throws BlockStoreException;

    public MultiSignAddress getMultiSignAddressInfo(String tokenid, String address) throws BlockStoreException;

    public int getCountMultiSignByTokenIndexAndAddress(String tokenid, long tokenindex, String address)
            throws BlockStoreException;

    public int getCountMultiSignByAlready(String tokenid, long tokenindex) throws BlockStoreException;

    public void updateTokens(Tokens tokens) throws BlockStoreException;

    public void updateTokenSerial(TokenSerial tokenSerial0) throws BlockStoreException;

    public List<MultiSign> getMultiSignListByTokenid(String tokenid, List<String> addresses) throws BlockStoreException;

    public List<MultiSign> getMultiSignListByTokenid(String tokenid, long tokenindex) throws BlockStoreException;

    public List<MultiSign> getMultiSignListByAddress(String address) throws BlockStoreException;

    int getCountMultiSignAlready(String tokenid, long tokenindex, String address) throws BlockStoreException;

    int getCountMultiSignNoSign(String tokenid, long tokenindex, int sign) throws BlockStoreException;

    void saveMultiSign(MultiSign multiSign) throws BlockStoreException;

    void updateMultiSign(String tokenid, int tokenindex, String address, byte[] bytes, int sign)
            throws BlockStoreException;

    void updateMultiSignBlockHash(String tokenid, long tokenindex, String address, byte[] bytes)
            throws BlockStoreException;

    void deleteMultiSignAddressByTokenid(String tokenid) throws BlockStoreException;

    int getCountTokenSerialNumber(String tokenid) throws BlockStoreException;

    void deleteMultiSign(String tokenid) throws BlockStoreException;

    long getCountMilestoneBlocksInInterval(long fromHeight, long toHeight) throws BlockStoreException;

    long getTxReward(Sha256Hash hash) throws BlockStoreException;

    void insertTxReward(Sha256Hash hash, long nextPerTxReward) throws BlockStoreException;

    PriorityQueue<Triple<Sha256Hash, byte[], Long>> getSortedMiningRewardCalculations(Sha256Hash hash) throws BlockStoreException;
    
    void insertMiningRewardCalculation(Sha256Hash hash, Address key, long l) throws BlockStoreException;

    // public List<TokenSerial> getTokenSerialListByTokenid(String tokenid);
    //
    // public List<MultiSignBy> getMultiSignByListByTokenid(String tokenid);
}
