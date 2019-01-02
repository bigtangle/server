/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.bigtangle.core.BatchBlock;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.LogResult;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProvider;
import net.bigtangle.core.UserData;
import net.bigtangle.core.VOSExecute;
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
    public void beginDatabaseBatchWrite() throws BlockStoreException;

    public void commitDatabaseBatchWrite() throws BlockStoreException;

    public void abortDatabaseBatchWrite() throws BlockStoreException;

    public void resetStore() throws BlockStoreException;

    public long getMaxImportTime() throws BlockStoreException;

    /* Blocks */
    public List<BlockWrap> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException;

    public List<BlockWrap> getBlocksInMilestoneDepthInterval(long minDepth, long maxDepth) throws BlockStoreException;

    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash hash) throws BlockStoreException;

    public BlockWrap getBlockWrap(Sha256Hash hash) throws BlockStoreException;

    public List<BlockEvaluation> getAllBlockEvaluations() throws BlockStoreException;

    public List<BlockEvaluation> getSolidBlocksOfHeight(long height) throws BlockStoreException;

    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException;

    public BlockEvaluation getTransactionOutputSpender(Sha256Hash prevBlockHash, long index) throws BlockStoreException;

    public PriorityQueue<BlockWrap> getSolidTipsDescending() throws BlockStoreException;

    public PriorityQueue<BlockWrap> getRatingEntryPointsAscending() throws BlockStoreException;

    public HashSet<BlockEvaluation> getBlocksToRemoveFromMilestone() throws BlockStoreException;

    public HashSet<BlockWrap> getBlocksToAddToMilestone() throws BlockStoreException;

    public HashSet<Sha256Hash> getMaintainedBlockHashes() throws BlockStoreException;

    /* Block Evaluation */
    public void updateBlockEvaluationCumulativeWeight(Sha256Hash blockhash, long weight) throws BlockStoreException;

    public void updateBlockEvaluationDepth(Sha256Hash blockhash, long depth) throws BlockStoreException;

    public void updateBlockEvaluationWeightAndDepth(Sha256Hash blockHash, long weight, long depth)
            throws BlockStoreException;

    public void updateBlockEvaluationRating(Sha256Hash blockhash, long rating) throws BlockStoreException;

    public void updateBlockEvaluationMilestone(Sha256Hash blockhash, boolean isMilestone) throws BlockStoreException;

    public void updateBlockEvaluationMilestoneDepth(Sha256Hash blockhash, long milestoneDepth)
            throws BlockStoreException;

    public void updateBlockEvaluationMaintained(Sha256Hash blockhash, boolean relevant) throws BlockStoreException;

    public void deleteTip(Sha256Hash blockhash) throws BlockStoreException;

    public void insertTip(Sha256Hash blockhash) throws BlockStoreException;

    public long getCountMilestoneBlocksInInterval(long fromHeight, long toHeight) throws BlockStoreException;

    /* TXOs */
    public void updateTransactionOutputSpent(Sha256Hash prevBlockHash, long index, boolean b, Sha256Hash spenderBlock)
            throws BlockStoreException;

    public void updateTransactionOutputConfirmed(Sha256Hash hash, long index, boolean b) throws BlockStoreException;

    public void updateTransactionOutputSpendPending(Sha256Hash hash, long index, boolean b) throws BlockStoreException;

    /* Reward TXOs */
    public Sha256Hash getConfirmedRewardBlock(long height) throws BlockStoreException;

    public boolean getTxRewardEligible(Sha256Hash hash) throws BlockStoreException;

    public boolean getTxRewardConfirmed(Sha256Hash hash) throws BlockStoreException;

    public void insertTxReward(Sha256Hash hash, long prevHeight, boolean eligibility, Sha256Hash prevBlockHash)
            throws BlockStoreException;

    public void updateTxRewardConfirmed(Sha256Hash hash, boolean b) throws BlockStoreException;

    public void updateTxRewardSpent(Sha256Hash hash, boolean b, Sha256Hash spenderHash) throws BlockStoreException;

    public long getMaxPrevTxRewardHeight() throws BlockStoreException;

    public boolean getTxRewardSpent(Sha256Hash hash) throws BlockStoreException;

    public Sha256Hash getTxRewardSpender(Sha256Hash hash) throws BlockStoreException;

    public Sha256Hash getTxRewardPrevBlockHash(Sha256Hash hash) throws BlockStoreException;

    /* Token TXOs */
    public void insertToken(String blockhash, Token tokens) throws BlockStoreException;

    public void insertToken(String blockhash, boolean confirmed, String tokenid, int tokenindex, long amount,
            String tokenname, String description, String url, int signnumber, boolean multiserial, int tokentype,
            boolean tokenstop, String prevblockhash) throws BlockStoreException;

    public Token getToken(String blockhash) throws BlockStoreException;

    public String getTokenPrevblockhash(String blockhash) throws BlockStoreException;

    public boolean getTokenSpent(String blockhash) throws BlockStoreException;

    public boolean getTokenConfirmed(String blockHash) throws BlockStoreException;

    public String getTokenSpender(String blockhash) throws BlockStoreException;

    public boolean getTokenAnyConfirmed(String tokenid, int tokenindex) throws BlockStoreException;

    public BlockWrap getTokenIssuingConfirmedBlock(String tokenid, int tokenindex) throws BlockStoreException;

    public void updateTokenSpent(String tokenPrevblockhash, boolean spent, String blockhash) throws BlockStoreException;

    public void updateTokenConfirmed(String blockhash, boolean confirmed) throws BlockStoreException;

    /* Wallet / Informational */
    public List<Token> getTokensList() throws BlockStoreException;

    public List<TokenSerial> getSearchTokenSerialInfo(String tokenid, List<String> addresses)
            throws BlockStoreException;

    public List<Token> getMarketTokenList() throws BlockStoreException;

    public List<Token> getTokensList(String name) throws BlockStoreException;

    public Map<String, Long> getTokenAmountMap(String name) throws BlockStoreException;

    public List<BlockEvaluation> getSearchBlockEvaluations(List<String> address) throws BlockStoreException;

    public List<BlockEvaluation> getSearchBlockEvaluations(List<String> address, String lastestAmount)
            throws BlockStoreException;

    public void streamBlocks(long heightstart, KafkaMessageProducer kafkaMessageProducer) throws BlockStoreException;

    void updateMultiSignBlockBitcoinSerialize(String tokenid, long tokenindex, byte[] bytes) throws BlockStoreException;

    public List<MultiSignAddress> getMultiSignAddressListByTokenidAndBlockHashHex(String tokenid, String prevblockhash)
            throws BlockStoreException;

    public void insertMultiSignAddress(MultiSignAddress multiSignAddress) throws BlockStoreException;

    public void deleteMultiSignAddress(String tokenid, String address) throws BlockStoreException;

    public void insertMultisignby(MultiSignBy multisignby) throws BlockStoreException;

    public int getCountMultiSignAddress(String tokenid) throws BlockStoreException;

    public MultiSignAddress getMultiSignAddressInfo(String tokenid, String address) throws BlockStoreException;

    public int getCountMultiSignByTokenIndexAndAddress(String tokenid, long tokenindex, String address)
            throws BlockStoreException;

    public int getCountMultiSignByAlready(String tokenid, long tokenindex) throws BlockStoreException;

    public List<MultiSign> getMultiSignListByTokenid(String tokenid, List<String> addresses, boolean isSign)
            throws BlockStoreException;

    public List<MultiSign> getMultiSignListByTokenid(String tokenid, long tokenindex) throws BlockStoreException;

    public List<OutputsMulti> queryOutputsMultiByHashAndIndex(byte[] hash, long index) throws BlockStoreException;

    public List<MultiSign> getMultiSignListByAddress(String address) throws BlockStoreException;

    int getCountMultiSignAlready(String tokenid, long tokenindex, String address) throws BlockStoreException;

    int getCountMultiSignNoSign(String tokenid, long tokenindex, int sign) throws BlockStoreException;

    void saveMultiSign(MultiSign multiSign) throws BlockStoreException;

    void updateMultiSign(String tokenid, int tokenindex, String address, byte[] bytes, int sign)
            throws BlockStoreException;

    void updateMultiSignBlockHash(String tokenid, long tokenindex, String address, byte[] bytes)
            throws BlockStoreException;

    void deleteMultiSignAddressByTokenidAndBlockhash(String tokenid, String blockhash) throws BlockStoreException;

    void deleteMultiSign(String tokenid) throws BlockStoreException;

    public void insertOutputsMulti(OutputsMulti outputsMulti) throws BlockStoreException;

    UserData queryUserDataWithPubKeyAndDataclassname(String dataclassname, String pubKey) throws BlockStoreException;

    void insertUserData(UserData userData) throws BlockStoreException;

    void updateUserData(UserData userData) throws BlockStoreException;

    void insertPayPayMultiSign(PayMultiSign payMultiSign) throws BlockStoreException;

    void insertPayMultiSignAddress(PayMultiSignAddress payMultiSignAddress) throws BlockStoreException;

    void updatePayMultiSignAddressSign(String orderid, String pubKey, int sign, byte[] signInputData)
            throws BlockStoreException;

    int getMaxPayMultiSignAddressSignIndex(String orderid) throws BlockStoreException;

    PayMultiSign getPayMultiSignWithOrderid(String orderid) throws BlockStoreException;

    List<PayMultiSignAddress> getPayMultiSignAddressWithOrderid(String orderid) throws BlockStoreException;

    void updatePayMultiSignBlockhash(String orderid, byte[] blockhash) throws BlockStoreException;

    List<PayMultiSign> getPayMultiSignList(List<String> pubKeys) throws BlockStoreException;

    int getCountPayMultiSignAddressStatus(String orderid) throws BlockStoreException;

    UTXO getOutputsWithHexStr(byte[] hash, long outputindex) throws BlockStoreException;

    List<UserData> getUserDataListWithBlocktypePubKeyList(int blocktype, List<String> pubKeyList)
            throws BlockStoreException;

    List<VOSExecute> getVOSExecuteList(String vosKey) throws BlockStoreException;

    VOSExecute getVOSExecuteWith(String vosKey, String pubKey) throws BlockStoreException;

    void insertVOSExecute(VOSExecute vosExecute) throws BlockStoreException;

    void updateVOSExecute(VOSExecute vosExecute) throws BlockStoreException;

    void updateTransactionOutputConfirmingBlock(Sha256Hash hash, int index, Sha256Hash hash2)
            throws BlockStoreException;

    byte[] getSettingValue(String name) throws BlockStoreException;

    public List<Block> getNonSolidBlocks() throws BlockStoreException;

    void insertUnsolid(Block block) throws BlockStoreException;

    void deleteUnsolid(Sha256Hash blockhash) throws BlockStoreException;

    void deleteOldUnsolid(long time) throws BlockStoreException;

    void insertLogResult(LogResult logResult) throws BlockStoreException;

    LogResult queryLogResultById(String logResultId) throws BlockStoreException;

    Token getCalMaxTokenIndex(String tokenid) throws BlockStoreException;

    void insertBatchBlock(Block block) throws BlockStoreException;

    void deleteBatchBlock(Sha256Hash hash) throws BlockStoreException;

    List<BatchBlock> getBatchBlockList() throws BlockStoreException;

    List<UTXO> getOutputsHistory(String fromaddress, String toaddress, Long starttime, Long endtime)
            throws BlockStoreException;

    void insertSubtanglePermission(String pubkey, String userdatapubkey, String status) throws BlockStoreException;

    void deleteSubtanglePermission(String pubkey) throws BlockStoreException;

    List<Map<String, String>> getAllSubtanglePermissionList() throws BlockStoreException;

    List<Map<String, String>> getSubtanglePermissionListByPubkey(String pubkey) throws BlockStoreException;

    List<Map<String, String>> getSubtanglePermissionListByPubkeys(List<String> pubkeys) throws BlockStoreException;
    // List<TokenSerial> getSearchTokenSerialInfo(String tokenid, List<String>
    // addresses) throws BlockStoreException;
}
