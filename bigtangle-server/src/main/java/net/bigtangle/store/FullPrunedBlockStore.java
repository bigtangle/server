/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import net.bigtangle.core.BatchBlock;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProvider;
import net.bigtangle.core.UserData;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.kafka.KafkaMessageProducer;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.ordermatch.bean.MatchResult;
import net.bigtangle.server.service.Eligibility;
import net.bigtangle.server.service.SolidityState;

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
 * It must store the {@link Block} of all blocks.
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
     * Gets a {@link net.bigtangle.core.UTXO} with the given hash and index, or null
     * if none is found
     */
    UTXO getTransactionOutput(Sha256Hash blockHash, Sha256Hash txHash, long index) throws BlockStoreException;

    /**
     * Adds a {@link net.bigtangle.core.UTXO} to the list of unspent
     * TransactionOutputs
     */
    void addUnspentTransactionOutput(UTXO out) throws BlockStoreException;

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
     * should not be readable by any other threads until commitDatabaseBatchWrite()
     * has been called by this thread. Multiple calls to beginDatabaseBatchWrite()
     * in any given thread should be ignored and treated as one call.
     * </p>
     */
    public void beginDatabaseBatchWrite() throws BlockStoreException;

    public void commitDatabaseBatchWrite() throws BlockStoreException;

    public void abortDatabaseBatchWrite() throws BlockStoreException;

    public void resetStore() throws BlockStoreException;
    
    public void deleteStore() throws BlockStoreException;

    public void create() throws BlockStoreException;

    public long getMaxImportTime() throws BlockStoreException;

    /* Blocks */
    public List<BlockWrap> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException;

    public List<BlockWrap> getApproverBlocks(Sha256Hash hash) throws BlockStoreException;

    public List<BlockWrap> getBlocksInMilestoneDepthInterval(long minDepth, long maxDepth) throws BlockStoreException;

    public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash hash) throws BlockStoreException;

    public BlockWrap getBlockWrap(Sha256Hash hash) throws BlockStoreException;

    public List<BlockEvaluation> getAllBlockEvaluations() throws BlockStoreException;

    public List<BlockEvaluation> getSolidBlocksOfHeight(long height) throws BlockStoreException;

    public List<Sha256Hash> getConfirmedBlocksOfHeightHigherThan(long height) throws BlockStoreException;

    public List<Sha256Hash> getBlocksOfTimeHigherThan(long time) throws BlockStoreException;

    public BlockEvaluation getBlockEvaluation(Sha256Hash hash) throws BlockStoreException;

    public BlockEvaluation getTransactionOutputSpender(Sha256Hash blockHash, Sha256Hash txHash, long index) throws BlockStoreException;

    public PriorityQueue<BlockWrap> getSolidTipsDescending() throws BlockStoreException;

    public PriorityQueue<BlockWrap> getMaintainedBlocksDescending() throws BlockStoreException;

    public PriorityQueue<BlockWrap> getRatingEntryPointsAscending() throws BlockStoreException;

    public HashSet<BlockEvaluation> getBlocksToUnconfirm() throws BlockStoreException;

    public HashSet<BlockWrap> getBlocksToConfirm() throws BlockStoreException;

    public HashSet<Sha256Hash> getMaintainedBlockHashes() throws BlockStoreException;

    /* Block Evaluation */
    public void updateBlockEvaluationCumulativeWeight(Sha256Hash blockhash, long weight) throws BlockStoreException;

    public void updateBlockEvaluationDepth(Sha256Hash blockhash, long depth) throws BlockStoreException;

    public void updateBlockEvaluationWeightAndDepth(Sha256Hash blockHash, long weight, long depth)
            throws BlockStoreException;

    public void updateBlockEvaluationRating(Sha256Hash blockhash, long rating) throws BlockStoreException;

    public void updateBlockEvaluationConfirmed(Sha256Hash blockhash, boolean confirmed) throws BlockStoreException;

    public void updateBlockEvaluationMilestone(Sha256Hash blockhash, long milestone) throws BlockStoreException;

    public void updateBlockEvaluationMilestoneDepth(Sha256Hash blockhash, long milestoneDepth)
            throws BlockStoreException;

    public void updateBlockEvaluationMaintained(Sha256Hash blockhash, boolean relevant) throws BlockStoreException;

    public void updateBlockEvaluationSolid(Sha256Hash blockhash, long solid) throws BlockStoreException;

    public void updateBlockEvaluationCalculated(Sha256Hash blockhash, boolean calculated) throws BlockStoreException;

    public void deleteTip(Sha256Hash blockhash) throws BlockStoreException;

    public void insertTip(Sha256Hash blockhash) throws BlockStoreException;

    public void updateAllBlocksMaintained() throws BlockStoreException;

    /* TXOs */
    public void updateTransactionOutputSpent(Sha256Hash prevBlockHash, Sha256Hash prevTxHash,  long index, boolean b, Sha256Hash spenderBlock)
            throws BlockStoreException;

    public void updateTransactionOutputConfirmed(Sha256Hash blockHash, Sha256Hash txHash, long index, boolean b) throws BlockStoreException;

    public void updateAllTransactionOutputsConfirmed(Sha256Hash blockHash, boolean b) throws BlockStoreException;

    public void updateTransactionOutputSpendPending(Sha256Hash blockHash, Sha256Hash txHash, long index, boolean b, long spendpendingtime) throws BlockStoreException;

    /* Orders */
    public boolean getOrderSpent(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException;

    public boolean getOrderConfirmed(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash)
            throws BlockStoreException;

    public Sha256Hash getOrderSpender(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash)
            throws BlockStoreException;

    public OrderRecord getOrder(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException;

    public void insertOrder(OrderRecord record) throws BlockStoreException;

    public void updateOrderConfirmed(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash, boolean confirmed)
            throws BlockStoreException;

    public void updateOrderSpent(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash, boolean spent,
            Sha256Hash spenderBlockHash) throws BlockStoreException;

    public HashMap<Sha256Hash, OrderRecord> getOrderMatchingIssuedOrders(Sha256Hash issuingMatcherBlockHash)
            throws BlockStoreException;

    public List<Sha256Hash> getLostOrders(long toHeight) throws BlockStoreException;

    /* Reward TXOs */
    public Sha256Hash getMaxConfirmedRewardBlockHash() throws BlockStoreException;

    public List<Sha256Hash> getRewardBlocksWithPrevHash(Sha256Hash hash) throws BlockStoreException;

    public Eligibility getRewardEligible(Sha256Hash hash) throws BlockStoreException;

    public long getRewardNextTxReward(Sha256Hash blockHash) throws BlockStoreException;

    public long getRewardToHeight(Sha256Hash blockHash) throws BlockStoreException;

    public boolean getRewardConfirmed(Sha256Hash hash) throws BlockStoreException;

    public boolean getRewardSpent(Sha256Hash hash) throws BlockStoreException;

    public void insertReward(Sha256Hash hash, long toHeight, Eligibility eligibility, Sha256Hash prevBlockHash,
            long nextTxReward) throws BlockStoreException;

    public void updateRewardConfirmed(Sha256Hash hash, boolean b) throws BlockStoreException;

    public void updateRewardSpent(Sha256Hash hash, boolean b, Sha256Hash spenderHash) throws BlockStoreException;

    public void updateRewardNextTxReward(Sha256Hash hash, long nextTxReward) throws BlockStoreException;

    public Sha256Hash getRewardSpender(Sha256Hash hash) throws BlockStoreException;

    public Sha256Hash getRewardPrevBlockHash(Sha256Hash hash) throws BlockStoreException;

    /* Order Matching TXOs */
    public Sha256Hash getMaxConfirmedOrderMatchingBlockHash() throws BlockStoreException;

    public List<Sha256Hash> getOrderMatchingBlocksWithPrevHash(Sha256Hash hash) throws BlockStoreException;

    public Eligibility getOrderMatchingEligible(Sha256Hash hash) throws BlockStoreException;

    public long getOrderMatchingToHeight(Sha256Hash blockHash) throws BlockStoreException;

    public long getOrderMatchingFromHeight(Sha256Hash blockHash) throws BlockStoreException;

    public boolean getOrderMatchingConfirmed(Sha256Hash hash) throws BlockStoreException;

    public boolean getOrderMatchingSpent(Sha256Hash hash) throws BlockStoreException;

    public void insertOrderMatching(Sha256Hash hash, long toHeight, Eligibility eligibility, Sha256Hash prevBlockHash)
            throws BlockStoreException;

    public void updateOrderMatchingConfirmed(Sha256Hash hash, boolean b) throws BlockStoreException;

    public void updateOrderMatchingSpent(Sha256Hash hash, boolean b, Sha256Hash spenderHash) throws BlockStoreException;

    public Sha256Hash getOrderMatchingSpender(Sha256Hash hash) throws BlockStoreException;

    public Sha256Hash getOrderMatchingPrevBlockHash(Sha256Hash hash) throws BlockStoreException;

    /* Token TXOs */
    public void insertToken(String blockhash, Token tokens) throws BlockStoreException;

    public Token getToken(String blockhash) throws BlockStoreException;

    public List<Token> getTokenID(String tokenid) throws BlockStoreException;

    public String getTokenPrevblockhash(String blockhash) throws BlockStoreException;

    public boolean getTokenSpent(String blockhash) throws BlockStoreException;

    public boolean getTokenAnySpent(String tokenId, long tokenindex) throws BlockStoreException;

    public boolean getTokenConfirmed(String blockHash) throws BlockStoreException;

    public Sha256Hash getTokenSpender(String blockhash) throws BlockStoreException;

    public boolean getTokenAnyConfirmed(String tokenid, long tokenindex) throws BlockStoreException;

    public BlockWrap getTokenIssuingConfirmedBlock(String tokenid, long tokenindex) throws BlockStoreException;

    public BlockWrap getDomainIssuingConfirmedBlock(String domainName, String domainPred) throws BlockStoreException;

    public List<String> getDomainDescendantConfirmedBlocks(String domainPred) throws BlockStoreException;

    public void updateTokenSpent(String blockhash, boolean b, Sha256Hash spenderBlockHash) throws BlockStoreException;

    public void updateTokenConfirmed(String blockhash, boolean confirmed) throws BlockStoreException;

    /* For tests */
    public List<OrderRecord> getAllAvailableOrdersSorted(boolean spent) throws BlockStoreException;

    public List<OrderRecord> getAllAvailableOrdersSorted(boolean spent, String address, List<String> addresses)
            throws BlockStoreException;

    public List<OrderRecord> getAllAvailableOrdersSorted(boolean spent, String address, List<String> addresses,
            String tokenid) throws BlockStoreException;

    public List<UTXO> getAllAvailableUTXOsSorted() throws BlockStoreException;

    public List<UTXO> getAllUTXOsSorted() throws BlockStoreException;

    public List<OrderRecord> getAllOrdersSorted() throws BlockStoreException;

    /* Dependencies */
    public void removeDependents(Sha256Hash blockHash) throws BlockStoreException;

    public void insertDependents(Sha256Hash blockHash, Sha256Hash dependencyBlockHash) throws BlockStoreException;

    public List<Sha256Hash> getDependents(Sha256Hash blockHash) throws BlockStoreException;

    /* Wallet / Informational */
    public List<Token> getTokensList(Set<String> tokenids) throws BlockStoreException;

    public List<TokenSerial> getSearchTokenSerialInfo(String tokenid, List<String> addresses)
            throws BlockStoreException;

    public List<Token> getMarketTokenList() throws BlockStoreException;

    public List<Token> getTokensList(String name) throws BlockStoreException;

    public Map<String, Long> getTokenAmountMap(String name) throws BlockStoreException;

    public List<BlockEvaluation> getSearchBlockEvaluations(List<String> address) throws BlockStoreException;

    public List<BlockEvaluationDisplay> getSearchBlockEvaluations(List<String> address, String lastestAmount, long heigth)
            throws BlockStoreException;

    public List<BlockEvaluationDisplay> getSearchBlockEvaluations(String blockhash, String lastestAmount)
            throws BlockStoreException;

    public void streamBlocks(long heightstart, KafkaMessageProducer kafkaMessageProducer, String serveraddress)
            throws BlockStoreException;

    public List<byte[]> blocksFromHeight(long heightstart) throws BlockStoreException;

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

    public List<MultiSign> getMultiSignListByTokenid(String tokenid, Set<String> addresses, boolean isSign)
            throws BlockStoreException;

    public List<MultiSign> getMultiSignListByTokenid(String tokenid, long tokenindex) throws BlockStoreException;

    public List<OutputsMulti> queryOutputsMultiByHashAndIndex(byte[] hash, long index) throws BlockStoreException;

    public List<MultiSign> getMultiSignListByAddress(String address) throws BlockStoreException;
    
    public List<MultiSign> getMultiSignListByTokenidAndAddress(String tokenid, String address) throws BlockStoreException;

    int getCountMultiSignAlready(String tokenid, long tokenindex, String address) throws BlockStoreException;

    int getCountMultiSignNoSign(String tokenid, long tokenindex, int sign) throws BlockStoreException;

    void saveMultiSign(MultiSign multiSign) throws BlockStoreException;

    void updateMultiSign(String tokenid, long tokenindex, String address, byte[] bytes, int sign)
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

    byte[] getSettingValue(String name) throws BlockStoreException;

    public List<Block> getNonSolidBlocks() throws BlockStoreException;

    public Block getNonSolidBlocksFirst() throws BlockStoreException;

    void insertUnsolid(Block block, SolidityState solidityState) throws BlockStoreException;

    void deleteUnsolid(Sha256Hash blockhash) throws BlockStoreException;

    void deleteOldUnsolid(long time) throws BlockStoreException;

    Token getCalMaxTokenIndex(String tokenid) throws BlockStoreException;

    void insertBatchBlock(Block block) throws BlockStoreException;

    void deleteBatchBlock(Sha256Hash hash) throws BlockStoreException;

    List<BatchBlock> getBatchBlockList() throws BlockStoreException;

    List<UTXO> getOutputsHistory(String fromaddress, String toaddress, Long starttime, Long endtime)
            throws BlockStoreException;

    void insertSubtanglePermission(String pubkey, String userdatapubkey, String status) throws BlockStoreException;

    void deleteSubtanglePermission(String pubkey) throws BlockStoreException;

    void updateSubtanglePermission(String pubkey, String userdataPubkey, String status) throws BlockStoreException;

    List<Map<String, String>> getAllSubtanglePermissionList() throws BlockStoreException;

    List<Map<String, String>> getSubtanglePermissionListByPubkey(String pubkey) throws BlockStoreException;

    List<Map<String, String>> getSubtanglePermissionListByPubkeys(List<String> pubkeys) throws BlockStoreException;

    HashSet<Block> getUnsolidBlocks(byte[] dep) throws BlockStoreException;

    List<OrderRecord> getMyClosedOrders(String address) throws BlockStoreException;

    List<OrderRecord> getMyRemainingOpenOrders(String address) throws BlockStoreException;

    List<OrderRecord> getMyInitialOpenOrders(String address) throws BlockStoreException;

    List<OrderRecord> getBestOpenSellOrders(String tokenId, int count) throws BlockStoreException;

    List<OrderRecord> getBestOpenBuyOrders(String tokenId, int count) throws BlockStoreException;

    void insertMyserverblocks(Sha256Hash prevhash, Sha256Hash hash, Long inserttime) throws BlockStoreException;

    void deleteMyserverblocks(Sha256Hash prevhash) throws BlockStoreException;

    boolean existMyserverblocks(Sha256Hash prevhash) throws BlockStoreException;

    void insertMatchingEvent(MatchResult matchresults) throws BlockStoreException;

    void deleteMatchingEvents(String hashString) throws BlockStoreException;

    List<MatchResult> getLastMatchingEvents(Set<String> tokenId, int count) throws BlockStoreException;

    Token queryDomainnameToken(String domainPredecessorBlockHash) throws BlockStoreException;

    Token getTokensByDomainname(String domainname) throws BlockStoreException;
    
    Exchange getExchangeInfoByOrderid(String orderid) throws BlockStoreException;

    void updateExchangeSign(String orderid, String signtype, byte[] data) throws BlockStoreException;

    void saveExchange(Exchange exchange) throws BlockStoreException;

    public void updateExchangeSignData(String orderid, byte[] data) throws BlockStoreException;

}
