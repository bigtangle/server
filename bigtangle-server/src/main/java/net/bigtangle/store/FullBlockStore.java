/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockEvaluationDisplay;
import net.bigtangle.core.BlockMCMC;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.OrderCancel;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PayMultiSign;
import net.bigtangle.core.PayMultiSignAddress;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Token;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProvider;
import net.bigtangle.core.UserData;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.UTXOProviderException;
import net.bigtangle.core.ordermatch.AVGMatchResult;
import net.bigtangle.core.ordermatch.MatchLastdayResult;
import net.bigtangle.core.ordermatch.MatchResult;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.BatchBlock;
import net.bigtangle.server.data.ChainBlockQueue;
import net.bigtangle.server.data.ContractEventRecord;
import net.bigtangle.server.data.ContractResult;
import net.bigtangle.server.data.DepthAndWeight;
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.data.Rating;

/**
 * <p>
 * An implementor of FullBlockStore saves StoredBlock objects to some storage
 * mechanism.
 * </p>
 *
 * 
 * <p>
 * A FullBlockStore should function well as a standard {@link BlockStore} and
 * then be able to trivially switch to being used as a FullBlockStore.
 * </p>
 *
 * 
 * <p>
 * It must store the {@link Block} of all blocks.
 * </p>
 *
 * <p>
 * A FullBlockStore contains a map of hashes to [Full]StoredBlock. The hash is
 * the double digest of the Bitcoin serialization of the block header,
 * <b>not</b> the header with the extra data as well.
 * </p>
 * 
 * <p>
 * A FullBlockStore also contains a map of hash+index to UTXO. Again, the hash
 * is a standard Bitcoin double-SHA256 hash of the transaction.
 * </p>
 *
 * <p>
 * FullPrunedBlockStores are thread safe.
 * </p>
 */
public interface FullBlockStore extends BlockStore, UTXOProvider {

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

	void addUnspentTransactionOutput(List<UTXO> utxos) throws BlockStoreException;

	/**
	 * <p>
	 * Begins/Commits/Aborts a database transaction.
	 * </p>
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

	public void defaultDatabaseBatchWrite() throws BlockStoreException;

	public void resetStore() throws BlockStoreException;

	public void deleteStore() throws BlockStoreException;

	public void create() throws BlockStoreException;

	/* Blocks */
	public List<BlockWrap> getNotInvalidApproverBlocks(Sha256Hash hash) throws BlockStoreException;

	public List<BlockWrap> getSolidApproverBlocks(Sha256Hash hash) throws BlockStoreException;

	public List<Sha256Hash> getSolidApproverBlockHashes(Sha256Hash hash) throws BlockStoreException;

	public BlockWrap getBlockWrap(Sha256Hash hash) throws BlockStoreException;

	public BlockEvaluation getTransactionOutputSpender(Sha256Hash blockHash, Sha256Hash txHash, long index)
			throws BlockStoreException;

	public PriorityQueue<BlockWrap> getSolidBlocksInIntervalDescending(long cutoffHeight, long maxHeight)
			throws BlockStoreException;

	public HashSet<BlockEvaluation> getBlocksToUnconfirm() throws BlockStoreException;

	public TreeSet<BlockWrap> getBlocksToConfirm(long cutoffHeight, long maxHeight) throws BlockStoreException;

	public BlockMCMC getMCMC(Sha256Hash hash) throws BlockStoreException;

	public void updateBlockEvaluationWeightAndDepth(List<DepthAndWeight> depthAndWeight) throws BlockStoreException;

	public void updateBlockEvaluationRating(List<Rating> ratings) throws BlockStoreException;

	public void updateBlockEvaluationConfirmed(Sha256Hash blockhash, boolean confirmed) throws BlockStoreException;

	public void updateBlockEvaluationMilestone(Sha256Hash blockhash, long milestone) throws BlockStoreException;

	public void updateBlockEvaluationSolid(Sha256Hash blockhash, long solid) throws BlockStoreException;

	public void deleteMCMC(long chainlenght) throws BlockStoreException;

	/* TXOs */
	public void updateTransactionOutputSpent(Sha256Hash prevBlockHash, Sha256Hash prevTxHash, long index, boolean b,
			Sha256Hash spenderBlock) throws BlockStoreException;

	public void updateTransactionOutputConfirmed(Sha256Hash blockHash, Sha256Hash txHash, long index, boolean b)
			throws BlockStoreException;

	public void updateAllTransactionOutputsConfirmed(Sha256Hash blockHash, boolean b) throws BlockStoreException;

	public void updateTransactionOutputSpendPending(List<UTXO> utxos) throws BlockStoreException;

	/* Orders */
	public boolean getOrderSpent(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException;

	public boolean getOrderConfirmed(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash)
			throws BlockStoreException;

	public Sha256Hash getOrderSpender(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash)
			throws BlockStoreException;

	public OrderRecord getOrder(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash) throws BlockStoreException;

	public void insertOrder(Collection<OrderRecord> records) throws BlockStoreException;

	public void insertCancelOrder(OrderCancel orderCancel) throws BlockStoreException;
	public List<OrderCancel> getOrderCancelConfirmed() throws BlockStoreException;
	public void updateOrderCancelSpent(Set<Sha256Hash> cancels, Sha256Hash blockhash, Boolean spent) throws BlockStoreException;
	public void updateOrderConfirmed(Sha256Hash blockHash, Sha256Hash issuingMatcherBlockHash, boolean confirmed)
			throws BlockStoreException;
	public void updateOrderConfirmed(Set<Sha256Hash> hashs, Sha256Hash issuingMatcherBlockHash, boolean confirmed)
			throws BlockStoreException;

	public void updateOrderConfirmed(Collection<OrderRecord> orderRecords, boolean confirm) throws BlockStoreException;
 
	public void updateOrderSpent(Set<OrderRecord> orderRecords) throws BlockStoreException;
	public void updateOrderSpent(Set<Sha256Hash> orderRecords, Sha256Hash blockhash, Boolean spent) throws BlockStoreException;
	
	public HashMap<Sha256Hash, OrderRecord> getOrderMatchingIssuedOrders(Sha256Hash issuingMatcherBlockHash)
			throws BlockStoreException;
	public HashMap<Sha256Hash, OrderRecord> getOrderMatchingIssuedOrdersNotSpent(Sha256Hash issuingMatcherBlockHash)
			throws BlockStoreException;

	public void prunedHistoryUTXO(Long maxRewardblock) throws BlockStoreException;

	public void prunedPriceTicker(Long timeInSeconds) throws BlockStoreException;

	public void prunedClosedOrders(Long timeInSeconds) throws BlockStoreException;

	public void prunedBlocks(Long heigth, Long chain) throws BlockStoreException;

	public TXReward getMaxConfirmedReward() throws BlockStoreException;

	public List<TXReward> getAllConfirmedReward() throws BlockStoreException;

	public List<Sha256Hash> getRewardBlocksWithPrevHash(Sha256Hash hash) throws BlockStoreException;

	public boolean getRewardConfirmed(Sha256Hash hash) throws BlockStoreException;

	public boolean getRewardSpent(Sha256Hash hash) throws BlockStoreException;

	public long getRewardChainLength(Sha256Hash hash) throws BlockStoreException;

	public long getRewardDifficulty(Sha256Hash hash) throws BlockStoreException;

	public void insertReward(Sha256Hash hash, Sha256Hash prevBlockHash, long difficulty, long chainLength)
			throws BlockStoreException;

	public void updateRewardConfirmed(Sha256Hash hash, boolean b) throws BlockStoreException;

	public void updateRewardSpent(Sha256Hash hash, boolean b, Sha256Hash spenderHash) throws BlockStoreException;

	public Sha256Hash getRewardSpender(Sha256Hash hash) throws BlockStoreException;

	public Sha256Hash getRewardPrevBlockHash(Sha256Hash hash) throws BlockStoreException;

	/* Token TXOs */
	public void insertToken(Sha256Hash blockhash, Token tokens) throws BlockStoreException;

	public Token getTokenByBlockHash(Sha256Hash blockhash) throws BlockStoreException;

	public List<Token> getTokenID(String tokenid) throws BlockStoreException;

	public Sha256Hash getTokenPrevblockhash(Sha256Hash blockhash) throws BlockStoreException;

	public boolean getTokenSpent(Sha256Hash blockhash) throws BlockStoreException;

	public boolean getTokenConfirmed(Sha256Hash blockHash) throws BlockStoreException;

	public Sha256Hash getTokenSpender(String blockhash) throws BlockStoreException;

	public boolean getTokenAnyConfirmed(String tokenid, long tokenindex) throws BlockStoreException;

	public BlockWrap getTokenIssuingConfirmedBlock(String tokenid, long tokenindex) throws BlockStoreException;

	public BlockWrap getDomainIssuingConfirmedBlock(String domainName, String domainPred, long index)
			throws BlockStoreException;

	public List<String> getDomainDescendantConfirmedBlocks(String domainPred) throws BlockStoreException;

	public void updateTokenSpent(Sha256Hash blockhash, boolean b, Sha256Hash spenderBlockHash)
			throws BlockStoreException;

	public void updateTokenConfirmed(Sha256Hash blockhash, boolean confirmed) throws BlockStoreException;

	public List<OrderRecord> getAllOpenOrdersSorted(List<String> addresses, String tokenid) throws BlockStoreException;

	public List<UTXO> getAllAvailableUTXOsSorted() throws BlockStoreException;

	public List<Token> getTokensList(Set<String> tokenids) throws BlockStoreException;

	public List<Token> getTokenTypeList(int type) throws BlockStoreException;

	public List<Token> getTokensList(String name) throws BlockStoreException;

	public Map<String, BigInteger> getTokenAmountMap() throws BlockStoreException;

	public List<BlockEvaluationDisplay> getSearchBlockEvaluations(List<String> address, String lastestAmount,
			long height, long maxblocks) throws BlockStoreException;

	public List<Block> findRetryBlocks(long minheight) throws BlockStoreException;

	public List<BlockEvaluationDisplay> getSearchBlockEvaluationsByhashs(List<String> blockhashs)
			throws BlockStoreException;

	public List<byte[]> blocksFromChainLength(long start, long end) throws BlockStoreException;

	public List<byte[]> blocksFromNonChainHeigth(long heigth) throws BlockStoreException;

	void updateMultiSignBlockBitcoinSerialize(String tokenid, long tokenindex, byte[] bytes) throws BlockStoreException;

	public List<MultiSignAddress> getMultiSignAddressListByTokenidAndBlockHashHex(String tokenid,
			Sha256Hash prevblockhash) throws BlockStoreException;

	public void insertMultiSignAddress(MultiSignAddress multiSignAddress) throws BlockStoreException;

	public void deleteMultiSignAddress(String tokenid, String address) throws BlockStoreException;

	public int getCountMultiSignAddress(String tokenid) throws BlockStoreException;

	public int getCountMultiSignByTokenIndexAndAddress(String tokenid, long tokenindex, String address)
			throws BlockStoreException;

	public List<MultiSign> getMultiSignListByTokenid(String tokenid, int tokenindex, Set<String> addresses,
			boolean isSign) throws BlockStoreException;

	public List<MultiSign> getMultiSignListByTokenid(String tokenid, long tokenindex) throws BlockStoreException;

	public List<OutputsMulti> queryOutputsMultiByHashAndIndex(byte[] hash, long index) throws BlockStoreException;

	public List<MultiSign> getMultiSignListByAddress(String address) throws BlockStoreException;

	public List<MultiSign> getMultiSignListByTokenidAndAddress(String tokenid, String address)
			throws BlockStoreException;

	int getCountMultiSignAlready(String tokenid, long tokenindex, String address) throws BlockStoreException;

	int countMultiSign(String tokenid, long tokenindex, int sign) throws BlockStoreException;

	void saveMultiSign(MultiSign multiSign) throws BlockStoreException;

	void updateMultiSign(String tokenid, long tokenindex, String address, byte[] bytes, int sign)
			throws BlockStoreException;

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

	byte[] getSettingValue(String name) throws BlockStoreException;

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

	void insertMyserverblocks(Sha256Hash prevhash, Sha256Hash hash, Long inserttime) throws BlockStoreException;

	void deleteMyserverblocks(Sha256Hash prevhash) throws BlockStoreException;

	boolean existMyserverblocks(Sha256Hash prevhash) throws BlockStoreException;

	void insertMatchingEvent(List<MatchResult> matchresults) throws BlockStoreException;

	void deleteMatchingEvents(String hashString) throws BlockStoreException;

	List<MatchLastdayResult> getLastMatchingEvents(Set<String> tokenId, String basetoken) throws BlockStoreException;

	Token queryDomainnameToken(Sha256Hash domainNameBlockHash) throws BlockStoreException;

	Token getTokensByDomainname(String domainname) throws BlockStoreException;

	Exchange getExchangeInfoByOrderid(String orderid) throws BlockStoreException;

	public List<Exchange> getExchangeListWithAddressA(String address) throws BlockStoreException;

	void updateExchangeSign(String orderid, String signtype, byte[] data) throws BlockStoreException;

	void saveExchange(Exchange exchange) throws BlockStoreException;

	void deleteExchange(String orderid) throws BlockStoreException;

	public void updateExchangeSignData(String orderid, byte[] data) throws BlockStoreException;

	List<Sha256Hash> getWhereConfirmedNotMilestone() throws BlockStoreException;

	long getHeightTransactions(List<Sha256Hash> txHashs) throws BlockStoreException;

	TXReward getRewardConfirmedAtHeight(long chainlength) throws BlockStoreException;

	List<BlockWrap> getBlocksInMilestoneInterval(long minMilestone, long maxMilestone) throws BlockStoreException;

	List<BlockWrap> getEntryPoints(long currChainLength) throws BlockStoreException;

	List<OrderCancel> getOrderCancelByOrderBlockHash(HashSet<String> orderBlockHashs) throws BlockStoreException;

	List<OrderRecord> getMyClosedOrders(List<String> addresses) throws BlockStoreException;

	boolean getTokennameAndDomain(String tokenname, String domainpre) throws BlockStoreException;

	List<MatchLastdayResult> getTimeBetweenMatchingEvents(String tokenids, String basetoken, Long startDate,
			Long endDate, int count) throws BlockStoreException;

	List<MatchLastdayResult> getTimeAVGBetweenMatchingEvents(String tokenids, String basetoken, Long startDate,
			Long endDate, int count) throws BlockStoreException;

	void insertAccessPermission(String pubKey, String accessToken) throws BlockStoreException;

	int getCountAccessPermissionByPubKey(String pubKey, String accessToken) throws BlockStoreException;

	void insertAccessGrant(String address) throws BlockStoreException;

	void deleteAccessGrant(String address) throws BlockStoreException;

	int getCountAccessGrantByAddress(String address) throws BlockStoreException;

	List<Token> getTokensListFromDomain(String domainname) throws BlockStoreException;

	void updateDatabse() throws BlockStoreException, SQLException;

	void insertChainBlockQueue(ChainBlockQueue chainBlockQueue) throws BlockStoreException;

	List<UTXO> getOpenOutputsByBlockhash(String blockhash) throws UTXOProviderException;

	List<ChainBlockQueue> selectChainblockqueue(boolean orphan, int limit) throws BlockStoreException;

	void deleteChainBlockQueue(List<ChainBlockQueue> chainBlockQueues) throws BlockStoreException;

	void deleteAllChainBlockQueue() throws BlockStoreException;

	LockObject selectLockobject(String lockobjectid) throws BlockStoreException;

	void deleteLockobject(String lockobjectid) throws BlockStoreException;

	void deleteAllLockobject() throws BlockStoreException;

	void insertLockobject(LockObject lockobject) throws BlockStoreException;

	void batchAddAvgPrice() throws Exception;

	void saveAvgPrice(AVGMatchResult matchResult) throws BlockStoreException;

	List<AVGMatchResult> queryTickerByTime(long starttime, long endtime) throws BlockStoreException;

	List<Token> getTokenID(Set<String> tokenids) throws BlockStoreException;

	void insertContractEvent(Collection<ContractEventRecord> records) throws BlockStoreException;

	public void updateContractEventSpent(Set<Sha256Hash> contractEventRecords, Sha256Hash spentBlock, boolean spent)
			throws BlockStoreException;

	public void updateContractEventConfirmed(Collection<Sha256Hash> contracts, boolean confirm)
			throws BlockStoreException;

	public Set<ContractEventRecord> getOpenContractEvent(String contractid, Sha256Hash prevHash) throws BlockStoreException;

	public List<String> getOpenContractid() throws BlockStoreException;

	public Sha256Hash getContractEventSpent(Sha256Hash contractEvent) throws BlockStoreException;

	public boolean checkContractEventConfirmed(List<Sha256Hash> contractEventRecords) throws BlockStoreException;

	void insertContractResult(ContractResult record) throws BlockStoreException;

	public void updateContractResultSpent(Sha256Hash contractResult, Sha256Hash spentBlock, boolean spent)
			throws BlockStoreException;

	public void updateContractResultConfirmed(Sha256Hash contract, boolean confirm) throws BlockStoreException;

	public Sha256Hash getLastContractResultBlockHash(String contractid) throws BlockStoreException;

	public Sha256Hash checkContractResultSpent(Sha256Hash contractResultRecords) throws BlockStoreException;

	public boolean checkContractResultConfirmed(Sha256Hash contractResultRecords) throws BlockStoreException;

	public List<Coin> queryAccountCoinList(String address, String tokenid) throws BlockStoreException;

	public List<UTXO> queryAccountUtxoList(String address, String tokenid) throws BlockStoreException;

	public Coin queryAccountCoin(String address, String tokenid) throws BlockStoreException;

	public void addAccountCoin(String address, String tokenid, Coin coin, Sha256Hash contractResult)
			throws BlockStoreException;

	public void updateAccountCoin(String address, String tokenid, Coin coin, Sha256Hash contractResult)
			throws BlockStoreException;

	public Map<String, Map<String, Coin>> queryOutputsMap(String address, String tokenid) throws BlockStoreException;

	public void addAccountCoinBatch(Map<String, Map<String, Coin>> toaddressMap) throws BlockStoreException;

	public void updateAccountCoinBatch(Map<String, Map<String, Coin>> fromaddressMap) throws BlockStoreException;

	public void deleteAccountCoin(String address, String tokenid) throws BlockStoreException;

	public void calculateAccount(List<UTXO> utxos) throws BlockStoreException;
}
