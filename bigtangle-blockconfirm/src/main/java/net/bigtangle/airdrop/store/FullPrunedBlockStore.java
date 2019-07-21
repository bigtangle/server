/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.airdrop.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bigtangle.airdrop.bean.Vm_deposit;
import net.bigtangle.core.BlockStore;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.exception.BlockStoreException;

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
public interface FullPrunedBlockStore {

    void beginDatabaseBatchWrite() throws BlockStoreException;

    void commitDatabaseBatchWrite() throws BlockStoreException;

    void abortDatabaseBatchWrite() throws BlockStoreException;

    public void resetStore() throws BlockStoreException;

    /** Closes the store. */
    void close() throws BlockStoreException;

    /**
     * Get the {@link net.bigtangle.core.NetworkParameters} of this store.
     * 
     * @return The network params.
     */
    NetworkParameters getParams();

    
    HashMap<String, String> queryByUWechatInvitePubKeyMapping(Set<String> keySet) throws BlockStoreException;

    List<Vm_deposit> queryDepositKeyFromOrderKey() throws BlockStoreException;

    Map<String, HashMap<String, String>> queryByUWechatInvitePubKeyInviterIdMap(Collection<String> keySet)
            throws BlockStoreException;

    void updateWechatInviteStatus(String id, int status) throws BlockStoreException;

    void updateDepositStatus(Long id, String status) throws BlockStoreException;

    void updateWechatInviteStatusByWechatId(String wechatId, int status) throws BlockStoreException;

    void clearWechatInviteStatusZero() throws BlockStoreException;

    void resetDepositPaid() throws BlockStoreException;

    Map<String, String> queryEmailByPubkeys(Collection<String> keySet) throws BlockStoreException;

    void batchAddReward(Map<String, Long> pubkeyAmountMap, Map<String, String> pubkeyEmailMap)
            throws BlockStoreException;
}
