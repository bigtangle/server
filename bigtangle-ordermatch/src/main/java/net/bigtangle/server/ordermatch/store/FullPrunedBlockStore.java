/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.ordermatch.store;

import java.util.List;
import java.util.Map;

import net.bigtangle.core.BlockStore;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.ExchangeMulti;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.UTXOProvider;
import net.bigtangle.server.ordermatch.bean.OrderMatch;

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

    void beginDatabaseBatchWrite() throws BlockStoreException;

    void commitDatabaseBatchWrite() throws BlockStoreException;

    void abortDatabaseBatchWrite() throws BlockStoreException;

    public void saveOrderPublish(OrderPublish orderPublish) throws BlockStoreException;

    public void saveExchangeMulti(ExchangeMulti exchangeMulti) throws BlockStoreException;

    public void updataExchangeMulti(String orderid, String address, byte[] data) throws BlockStoreException;

    public void saveOrderMatch(OrderMatch orderMatch) throws BlockStoreException;

    public List<OrderPublish> getOrderPublishListWithCondition(Map<String, Object> request) throws BlockStoreException;

    public void saveExchange(Exchange exchange) throws BlockStoreException;

    public List<Exchange> getExchangeListWithAddress(String address) throws BlockStoreException;

    public List<Exchange> getExchangeListWithAddressA(String address) throws BlockStoreException;

    public void updateExchangeSign(String orderid, String signtype, byte[] data) throws BlockStoreException;

    public void updateExchangeSignData(String orderid, byte[] data) throws BlockStoreException;

    public OrderPublish getOrderPublishByOrderid(String orderid) throws BlockStoreException;

    public Exchange getExchangeInfoByOrderid(String orderid) throws BlockStoreException;

    public void updateOrderPublishState(String orderid, int state) throws BlockStoreException;

    public void resetStore() throws BlockStoreException;

    public List<OrderPublish> getOrderPublishListWithNotMatch() throws BlockStoreException;

    void deleteOrderPublish(String orderid) throws BlockStoreException;

    void deleteExchangeInfo(String orderid) throws BlockStoreException;

    void deleteOrderMatch(String orderid) throws BlockStoreException;

    List<OrderPublish> getOrderPublishListRemoveDaily(int i) throws BlockStoreException;
}
