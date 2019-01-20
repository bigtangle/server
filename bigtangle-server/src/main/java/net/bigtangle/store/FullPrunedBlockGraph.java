/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OrderOpInfo;
import net.bigtangle.core.OrderOpInfo.OrderOp;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.OrderRecordInfo;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.VerificationException;
import net.bigtangle.core.VerificationException.GenericInvalidityException;
import net.bigtangle.core.VerificationException.MalformedTransactionDataException;
import net.bigtangle.script.Script;
import net.bigtangle.server.ordermatch.bean.OrderBook;
import net.bigtangle.server.ordermatch.bean.OrderBookEvents;
import net.bigtangle.server.ordermatch.bean.Side;
import net.bigtangle.server.service.RewardEligibility;
import net.bigtangle.server.service.SolidityState;
import net.bigtangle.server.service.SolidityState.State;
import net.bigtangle.server.service.ValidatorService;

/**
 * <p>
 * A FullPrunedBlockChain works in conjunction with a
 * {@link FullPrunedBlockStore} to verify all the rules of the Bitcoin system,
 * with the downside being a large cost in system resources. Fully verifying
 * means all unspent transaction outputs are stored. Once a transaction output
 * is spent and that spend is buried deep enough, the data related to it is
 * deleted to ensure disk space usage doesn't grow forever. For this reason a
 * pruning node cannot serve the full block chain to other clients, but it
 * nevertheless provides the same security guarantees as Bitcoin Core does.
 * </p>
 */
@Service
public class FullPrunedBlockGraph extends AbstractBlockGraph {
    private static final Logger log = LoggerFactory.getLogger(FullPrunedBlockGraph.class);

    @Autowired
    public FullPrunedBlockGraph(NetworkParameters networkParameters, FullPrunedBlockStore blockStore)
            throws BlockStoreException {
        super(Context.getOrCreate(networkParameters), blockStore);
        this.blockStore = blockStore;
        this.networkParameters = networkParameters;
    }

    /**
     * Keeps a map of block hashes to StoredBlocks.
     */
    protected final FullPrunedBlockStore blockStore;
    
    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    private ValidatorService validatorService;

    @Override
    public boolean add(Block block, boolean allowUnsolid) {
        lock.lock();
        try {
            // Check the block is partly formally valid and fulfills PoW 
            try {
                block.verifyHeader();
                block.verifyTransactions();
                
            } catch (VerificationException e) {
                log.warn("Failed to verify block: ", e);
                log.warn(block.getHashAsString());
                throw e;
            }
            checkState(lock.isHeldByCurrentThread());

            StoredBlock storedPrev = blockStore.get(block.getPrevBlockHash());
            StoredBlock storedPrevBranch = blockStore.get(block.getPrevBranchBlockHash());
            
            // Check the block's solidity, if dependency missing, put on waiting list unless disallowed
            // The class SolidityState is used for the Spark implementation and should stay.
            SolidityState solidityState = validatorService.checkBlockSolidity(block, storedPrev, storedPrevBranch, true);
            if (!(solidityState.getState() == State.Success)) {
                if (solidityState.getState() == State.Unfixable) {
                    // Drop if invalid
                    log.debug("Dropping invalid block!");
                    throw new GenericInvalidityException();
                } else {
                    // If dependency missing and allowing waiting list, add to list
                    if (allowUnsolid) 
                        insertUnsolidBlock(block, solidityState);
                    else
                        log.debug("Dropping unresolved block!");
                    return false;
                }
            } else {
                // Otherwise, all dependencies exist and the block has been validated
                try {
                    blockStore.beginDatabaseBatchWrite();
                    connect(block, Math.max(storedPrev.getHeight(), storedPrevBranch.getHeight()) + 1);
                    blockStore.commitDatabaseBatchWrite();
                    return true;
                } catch (BlockStoreException e) {
                    blockStore.abortDatabaseBatchWrite();
                    throw e;
                }
            } 
        } catch (BlockStoreException e) {
            log.error("", e);
            throw new VerificationException(e);
        } catch (VerificationException e) {
            log.debug("Could not verify block:\n" + e.toString() + "\n" + block.toString());
            throw e;
        } finally {
            lock.unlock();
        }
    }

    private void connect(final Block block, long height)
            throws BlockStoreException, VerificationException {
        checkState(lock.isHeldByCurrentThread());
        connectUTXOs(block, height);
        connectTypeSpecificUTXOs(block, height);
        StoredBlock newBlock = StoredBlock.build(block, height);
        blockStore.put(newBlock, new StoredUndoableBlock(newBlock.getHeader().getHash(), block.getTransactions()));
        solidifyBlock(block);
    }

    /**
     * Get the {@link Script} from the script bytes or return Script of empty
     * byte array.
     */
    private Script getScript(byte[] scriptBytes) {
        try {
            return new Script(scriptBytes);
        } catch (Exception e) {
            return new Script(new byte[0]);
        }
    }

    /**
     * Get the address from the {@link Script} if it exists otherwise return
     * empty string "".
     *
     * @param script
     *            The script.
     * @return The address.
     */
    private String getScriptAddress(@Nullable Script script) {
        String address = "";
        try {
            if (script != null) {
                address = script.getToAddress(params, true).toString();
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return address;
    }

    private void synchronizationUserData(Sha256Hash blockhash, DataClassName dataClassName, byte[] data, String pubKey,
            long blocktype) throws BlockStoreException {
        UserData userData = this.blockStore.queryUserDataWithPubKeyAndDataclassname(dataClassName.name(), pubKey);
        if (userData == null) {
            userData = new UserData();
            userData.setBlockhash(blockhash);
            userData.setData(data);
            userData.setDataclassname(dataClassName.name());
            userData.setPubKey(pubKey);
            userData.setBlocktype(blocktype);
            this.blockStore.insertUserData(userData);
            return;
        }
        userData.setBlockhash(blockhash);
        userData.setData(data);
        this.blockStore.updateUserData(userData);
    }

    @SuppressWarnings("unchecked")
    private void synchronizationVOSData(byte[] data) throws Exception {
        String jsonStr = new String(data);
        HashMap<String, Object> map = Json.jsonmapper().readValue(jsonStr, HashMap.class);
        String vosKey = (String) map.get("vosKey");
        String pubKey = (String) map.get("pubKey");
        VOSExecute vosExecute_ = this.blockStore.getVOSExecuteWith(vosKey, pubKey);
        if (vosExecute_ == null) {
            vosExecute_ = new VOSExecute();
            vosExecute_.setVosKey(vosKey);
            vosExecute_.setPubKey(pubKey);
            vosExecute_.setData(Utils.HEX.decode((String) map.get("dataHex")));
            vosExecute_.setStartDate(new Date((Long) map.get("startDate")));
            vosExecute_.setEndDate(new Date((Long) map.get("endDate")));
            vosExecute_.setExecute(1);
            this.blockStore.insertVOSExecute(vosExecute_);
            return;
        }
        vosExecute_.setData(Utils.HEX.decode((String) map.get("dataHex")));
        vosExecute_.setStartDate(new Date((Long) map.get("startDate")));
        vosExecute_.setEndDate(new Date((Long) map.get("endDate")));
        vosExecute_.setExecute(vosExecute_.getExecute() + 1);
        this.blockStore.updateVOSExecute(vosExecute_);
    }
    
    /**
     * Adds the specified block and all approved blocks to the milestone. This
     * will connect all transactions of the block by marking used UTXOs spent
     * and adding new UTXOs to the db.
     * 
     * @param blockHash
     * @throws BlockStoreException
     */
    public void confirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
        // Write to DB
        try {
            blockStore.beginDatabaseBatchWrite();
            addBlockToMilestone(blockHash, traversedBlockHashes);
            blockStore.commitDatabaseBatchWrite();
        } catch (BlockStoreException e) {
            blockStore.abortDatabaseBatchWrite();
            throw e;
        }
    }

    private void addBlockToMilestone(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException { 
        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        Block block = blockWrap.getBlock();
        
        // If already confirmed, return
        if (blockEvaluation.isMilestone() || traversedBlockHashes.contains(blockHash))
            return;

        // Set milestone true and update latestMilestoneUpdateTime
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), true);

        // Connect all approved blocks first if not traversed already
        if (!traversedBlockHashes.contains(block.getPrevBlockHash()))
            addBlockToMilestone(block.getPrevBlockHash(), traversedBlockHashes);
        if (!traversedBlockHashes.contains(block.getPrevBranchBlockHash()))
            addBlockToMilestone(block.getPrevBranchBlockHash(), traversedBlockHashes);

        // Confirm the block
        confirmBlock(block);
        
        // Keep track of confirmed blocks
        traversedBlockHashes.add(blockHash);
    }

    private void confirmBlock(Block block) throws BlockStoreException {
        // Update block's transactions in db
        for (final Transaction tx : block.getTransactions()) {
            confirmTransaction(tx, block.getHash());
        }
        
        // type-specific updates
        switch (block.getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            break;
        case BLOCKTYPE_REWARD:
            // For rewards, update reward to be confirmed now
            confirmReward(block);
            
            // Also do the orders here, since we merged order matching into the rewards
            confirmOrderMatching(block);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // For token creations, update token db
            confirmToken(block);
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
        case BLOCKTYPE_VOS:
            Transaction tx = block.getTransactions().get(0);
            if (tx.getData() != null && tx.getDataSignature() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    List<HashMap<String, Object>> multiSignBies = Json.jsonmapper().readValue(tx.getDataSignature(),
                            List.class);
                    Map<String, Object> multiSignBy = multiSignBies.get(0);
                    byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
                    byte[] data = tx.getHash().getBytes();
                    byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
                    boolean success = ECKey.verify(data, signature, pubKey);
                    if (!success) {
                        throw new BlockStoreException("multisign signature error");
                    }
                    this.synchronizationUserData(block.getHash(), DataClassName.valueOf(tx.getDataClassName()),
                            tx.getData(), (String) multiSignBy.get("publickey"), block.getBlockType().ordinal());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            Transaction tx1 = block.getTransactions().get(0);
            if (tx1.getData() != null && tx1.getDataSignature() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    List<HashMap<String, Object>> multiSignBies = Json.jsonmapper().readValue(tx1.getDataSignature(),
                            List.class);
                    Map<String, Object> multiSignBy = multiSignBies.get(0);
                    byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
                    byte[] data = tx1.getHash().getBytes();
                    byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
                    boolean success = ECKey.verify(data, signature, pubKey);
                    if (!success) {
                        throw new BlockStoreException("multisign signature error");
                    }
                    this.synchronizationVOSData(tx1.getData());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            break;
		case BLOCKTYPE_ORDER_OPEN:
			confirmOrderOpen(block);
			break;
		case BLOCKTYPE_ORDER_OP:
			break;
		case BLOCKTYPE_ORDER_RECLAIM:
			confirmOrderReclaim(block);
			break;
        default:
            throw new NotImplementedException();
        
        }
    }
    
    private void confirmOrderMatching(Block block) throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly generated remaining order book
        Triple<Collection<OrderRecord>, Transaction, Collection<OrderRecord>> matchingResult = generateOrderMatching(block);
        
        // All consumed order records are now spent by this block
        for (OrderRecord o : matchingResult.getLeft()) {
            blockStore.updateOrderSpent(o.getTxHash(), o.getIssuingMatcherBlockHash(), true, block.getHash());
        }

        // If virtual outputs have not been inserted yet, insert them  
        insertVirtualUTXOs(block, matchingResult.getMiddle());
        // And confirm them
        confirmTransaction(matchingResult.getMiddle(), block.getHash());
        
        // Finally, if the new orders have not been inserted yet, insert them (as confirmed)
        insertVirtualOrderRecords(block, matchingResult.getRight());
    }

    private void confirmOrderReclaim(Block block) throws BlockStoreException {
        // Read the requested reclaim
        OrderReclaimInfo info = null;
        try {
            info = OrderReclaimInfo.parse(block.getTransactions().get(0).getData());
        } catch (IOException e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }
        
        // Read the referenced order block's tx's hash from db
        Sha256Hash txHash = blockStore.get(info.getOrderBlockHash()).getHeader().getTransactions().get(0).getHash(); 

        // Set consumed order record to spent and set spender block to this block's hash
        blockStore.updateOrderSpent(txHash, Sha256Hash.ZERO_HASH, true, block.getHash());

        // Get virtual reclaim tx
        Transaction tx = generateReclaimTX(block);
        
        // If virtual outputs have not been inserted yet, insert them        
        insertVirtualUTXOs(block, tx);
        
        // Set virtual outputs confirmed
        confirmTransaction(tx, block.getHash());
    }

    private void confirmOrderOpen(Block block) throws BlockStoreException {
        // Set own output confirmed
        blockStore.updateOrderConfirmed(block.getTransactions().get(0).getHash(), Sha256Hash.ZERO_HASH, true);
    }

    private void confirmReward(Block block) throws BlockStoreException {
        // Get virtual tx
        Transaction tx = generateVirtualMiningRewardTX(block);
        
        // If virtual reward tx outputs have not been inserted yet, insert them        
        insertVirtualUTXOs(block, tx);
        
        // Set virtual reward tx outputs confirmed
        confirmTransaction(tx, block.getHash());
        
        // Set used other output spent
        blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getHash()), true, block.getHash());

        // Set own output confirmed
        blockStore.updateRewardConfirmed(block.getHash(), true);
    }

    private void insertVirtualOrderRecords(Block block, Collection<OrderRecord> orders) {
        try {
            for (OrderRecord o : orders)
                blockStore.insertOrder(o);
        } catch (BlockStoreException e) {
            // Expected after reorgs
            log.warn("Probably reinserting orders: ", e);
        }
    }
    
    private void insertVirtualUTXOs(Block block, Transaction virtualTx) {
        try {
            ArrayList<Transaction> txs = new ArrayList<Transaction>();
            txs.add(virtualTx);
            long height = blockStore.getBlockEvaluation(block.getHash()).getHeight();
            connectUTXOs(height, txs, block.getTimeSeconds()); 
            // TODO remove height from UTXOs, such a thing does not exist. Instead it is the confirming blockhashes height
        } catch (BlockStoreException e) {
            // Expected after reorgs
            log.warn("Probably reinserting reward: ", e);
        }
    }

    private void confirmToken(Block block) throws BlockStoreException {
        // Set used other output spent
        blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(block.getHashAsString()), true, block.getHash());

        // Set own output confirmed
        blockStore.updateTokenConfirmed(block.getHashAsString(), true);
    }

    private void confirmTransaction(final Transaction tx, Sha256Hash blockhash) throws BlockStoreException {
        // Set used other outputs spent
        if (!tx.isCoinBase()) {
            for (TransactionInput in : tx.getInputs()) {
                UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getHash(), in.getOutpoint().getIndex());
                
                // Sanity check
                if (prevOut == null)
                    throw new RuntimeException("Attempted to spend a non-existent output!");
                if (prevOut.isSpent())
                    throw new RuntimeException("Attempted to spend an already spent output!");
                
                blockStore.updateTransactionOutputSpent(prevOut.getHash(), prevOut.getIndex(), true, blockhash);
            }
        }

        // Set own outputs confirmed
        for (TransactionOutput out : tx.getOutputs()) {
            blockStore.updateTransactionOutputConfirmed(tx.getHash(), out.getIndex(), true);
            blockStore.updateTransactionOutputConfirmingBlock(tx.getHash(), out.getIndex(), blockhash);
            blockStore.updateTransactionOutputSpent(tx.getHash(), out.getIndex(), false, null);
        }
    }

    /**
     * Adds the specified block and all approved blocks to the milestone. This
     * will connect all transactions of the block by marking used UTXOs spent
     * and adding new UTXOs to the db.
     * 
     * @param blockHash
     * @throws BlockStoreException
     */
    public void unconfirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
        // Write to DB
        try {
            blockStore.beginDatabaseBatchWrite();
            removeBlockFromMilestone(blockHash, traversedBlockHashes);
            blockStore.commitDatabaseBatchWrite();
        } catch (BlockStoreException e) {
            blockStore.abortDatabaseBatchWrite();
            throw e;
        }
    }

    private void removeBlockFromMilestone(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        Block block = blockWrap.getBlock();

        // If already unconfirmed, return
        if (!blockEvaluation.isMilestone() || traversedBlockHashes.contains(blockHash))
            return;
        
        // Unconfirm all dependents
        unconfirmDependents(block, traversedBlockHashes);
         
        // Then unconfirm the block itself
        unconfirmBlock(block);

        // Set milestone false and update latestMilestoneUpdateTime
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), false);
        
        // Keep track of unconfirmed blocks
        traversedBlockHashes.add(blockHash);
    }

    private void unconfirmDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
        // Unconfirm all approver blocks first
        for (Sha256Hash approver : blockStore.getSolidApproverBlockHashes(block.getHash())) {
            removeBlockFromMilestone(approver, traversedBlockHashes);
        }
        
        // Disconnect all transaction output dependents
        for (Transaction tx : block.getTransactions()) {
            for (TransactionOutput txout : tx.getOutputs()) {
                UTXO utxo = blockStore.getTransactionOutput(tx.getHash(), txout.getIndex());
                if (utxo.isSpent()) {
                    removeBlockFromMilestone(
                            blockStore.getTransactionOutputSpender(tx.getHash(), txout.getIndex()).getBlockHash(), traversedBlockHashes);
                }
            }
        }
        
        // Disconnect all type-specific dependents
        switch (block.getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            break;
        case BLOCKTYPE_REWARD:
            // Unconfirm dependents
            unconfirmRewardDependents(block, traversedBlockHashes);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // Unconfirm dependents
            unconfirmTokenDependents(block, traversedBlockHashes);
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_VOS:
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            break;
		case BLOCKTYPE_ORDER_OPEN:
			// TODO
			break;
		case BLOCKTYPE_ORDER_OP:
			// TODO
			break;
		case BLOCKTYPE_ORDER_RECLAIM:
			// TODO
			break;
        default:
            throw new NotImplementedException();
        
        }
    }

    private void unconfirmTokenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {
        if (blockStore.getTokenSpent(block.getHashAsString())) {
            removeBlockFromMilestone(blockStore.getTokenSpender(block.getHashAsString()), traversedBlockHashes);
        }
    }

    private void unconfirmRewardDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {
        if (blockStore.getRewardSpent(block.getHash())) {
            removeBlockFromMilestone(blockStore.getRewardSpender(block.getHash()), traversedBlockHashes);
        }
        // Disconnect all virtual transaction output dependents
        Transaction tx = generateVirtualMiningRewardTX(block);
        for (TransactionOutput txout : tx.getOutputs()) {
            UTXO utxo = blockStore.getTransactionOutput(tx.getHash(), txout.getIndex());
            if (utxo.isSpent()) {
                removeBlockFromMilestone(
                        blockStore.getTransactionOutputSpender(tx.getHash(), txout.getIndex()).getBlockHash(), traversedBlockHashes);
            }
        }
    }

    /**
     * Disconnect the block, unconfirming all UTXOs and UTXO-like constructs.
     * 
     * @throws BlockStoreException
     *             if the block store had an underlying error or block does not
     *             exist in the block store at all.
     */
    private void unconfirmBlock(Block block) throws BlockStoreException {
        // Unconfirm all transactions of the block
        for (Transaction tx : block.getTransactions()) {
            unconfirmTransaction(tx, block);
        }
        
        // Then unconfirm type-specific stuff
        switch (block.getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            break;
        case BLOCKTYPE_REWARD:
            unconfirmReward(block);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            unconfirmToken(block.getHashAsString());
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_VOS:
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            break;
		case BLOCKTYPE_ORDER_OPEN:
			// TODO
			break;
		case BLOCKTYPE_ORDER_OP:
			// TODO
			break;
		case BLOCKTYPE_ORDER_RECLAIM:
			// TODO
			break;
        default:
            throw new NotImplementedException();
        
        }
    }

    private void unconfirmReward(Block block) throws BlockStoreException {
        // Get virtual tx
        Transaction tx = generateVirtualMiningRewardTX(block);
        
        // Unconfirm virtual tx
        unconfirmTransaction(tx, block);
        
        // Set used other output unspent
        blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getHash()), false, null);

        // Set own output unconfirmed
        blockStore.updateRewardConfirmed(block.getHash(), false);

    }

    private void unconfirmToken(String blockhash) throws BlockStoreException {
        // Set used other output unspent
        blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(blockhash), false, null);

        // Set own output unconfirmed
        blockStore.updateTokenConfirmed(blockhash, false);
    }

    /**
     * Disconnects the UTXOs of the transaction
     * 
     * @param tx
     * @param parentBlock
     * @throws BlockStoreException
     */
    private void unconfirmTransaction(Transaction tx, Block parentBlock) throws BlockStoreException {
        // Set used outputs as unspent
        if (!tx.isCoinBase()) {
            for (TransactionInput txin : tx.getInputs()) {
                blockStore.updateTransactionOutputSpent(txin.getOutpoint().getHash(), txin.getOutpoint().getIndex(),
                        false, null);
            }
        }
        
        // Set own outputs unconfirmed
        for (TransactionOutput txout : tx.getOutputs()) {
            blockStore.updateTransactionOutputConfirmingBlock(tx.getHash(), txout.getIndex(), null);
            blockStore.updateTransactionOutputConfirmed(tx.getHash(), txout.getIndex(), false);
        }
    }

    protected void solidifyBlock(Block block) throws BlockStoreException {
        // Update tips table
        blockStore.deleteTip(block.getPrevBlockHash());
        blockStore.deleteTip(block.getPrevBranchBlockHash());
        blockStore.deleteTip(block.getHash());
        blockStore.insertTip(block.getHash());

        blockStore.commitDatabaseBatchWrite();
        // Finally, look in the solidity waiting queue for blocks that are still waiting
        // It could be a missing block...
        for (Block b : blockStore.getUnsolidBlocks(block.getHash().getBytes())) {
            try {
                // Clear from waiting list
                blockStore.deleteUnsolid(b.getHash());
                
                // If going through or waiting for more dependencies, all is good
                add(b, true);
            } catch (VerificationException e) {
                // If the block is deemed invalid, we do not propagate the error upwards
                log.debug(e.getLocalizedMessage());
            }
        }
        
        // Or it could be a missing transaction
        for (TransactionOutput txout : block.getTransactions().stream().flatMap(t -> t.getOutputs().stream()).collect(Collectors.toList())) {
            for (Block b : blockStore.getUnsolidBlocks(txout.getOutPointFor().bitcoinSerialize())) {
                try {
                    // Clear from waiting list
                    blockStore.deleteUnsolid(b.getHash());
                    
                    // If going through or waiting for more dependencies, all is good
                    add(b, true);
                } catch (VerificationException e) {
                    // If the block is deemed invalid, we do not propagate the error upwards
                    log.debug(e.getLocalizedMessage());
                }
            }
        }
    }

    @Override
    protected void insertUnsolidBlock(Block block, SolidityState solidityState) throws BlockStoreException {
        if (solidityState.getState() == State.Success || solidityState.getState() == State.Unfixable)
            return;
        
        // Insert waiting into solidity waiting queue until dependency is resolved
        blockStore.insertUnsolid(block, solidityState);
    }

    protected void connectUTXOs(Block block, long height) throws BlockStoreException, VerificationException {
        List<Transaction> transactions = block.getTransactions();
        long timeSeconds = block.getTimeSeconds();
        connectUTXOs(height, transactions, timeSeconds);
    }

    private void connectUTXOs(long height, List<Transaction> transactions, long timeSeconds)
            throws BlockStoreException {
        for (final Transaction tx : transactions) {
            boolean isCoinBase = tx.isCoinBase();
            if (!isCoinBase) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getHash(),
                            in.getOutpoint().getIndex());
                    blockStore.updateTransactionOutputSpendPending(prevOut.getHash(), prevOut.getIndex(), true);
                }
            }
            Sha256Hash hash = tx.getHash();
            for (TransactionOutput out : tx.getOutputs()) {
                Script script = getScript(out.getScriptBytes());
                UTXO newOut = new UTXO(hash, out.getIndex(), out.getValue(), height, isCoinBase, script,
                        getScriptAddress(script), null, out.getFromaddress(), tx.getMemo(),
                        Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, 0);
                newOut.setTime(timeSeconds);
                blockStore.addUnspentTransactionOutput(newOut);
                if (script.isSentToMultiSig()) {
                    int minsignnumber = script.getNumberOfSignaturesRequiredToSpend();
                    for (ECKey ecKey : script.getPubKeys()) {
                        String toaddress = ecKey.toAddress(params).toBase58();
                        OutputsMulti outputsMulti = new OutputsMulti(newOut.getHash(), toaddress, newOut.getIndex(),
                                minsignnumber);
                        this.blockStore.insertOutputsMulti(outputsMulti);
                    }
                }
            }
        }
    }

    private void connectTypeSpecificUTXOs(Block block, long height)
            throws BlockStoreException {
        switch (block.getBlockType()) {
        case BLOCKTYPE_CROSSTANGLE:
            break;
        case BLOCKTYPE_FILE:
            break;
        case BLOCKTYPE_GOVERNANCE:
            break;
        case BLOCKTYPE_INITIAL:
            break;
        case BLOCKTYPE_REWARD:
            connectReward(block);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            connectToken(block);
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_VOS:
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            break;
		case BLOCKTYPE_ORDER_OPEN:
			connectOrder(block);
			break;
		case BLOCKTYPE_ORDER_OP:
			break;
		case BLOCKTYPE_ORDER_RECLAIM:
			break;
        default:
            break;
        
        }
    }

    private void connectOrder(Block block) throws BlockStoreException {
        try {
            OrderOpenInfo reqInfo = OrderOpenInfo.parse(block.getTransactions().get(0).getData());
            
            String offerTokenid = null;
            long offerValue = 0;
            for (final Transaction tx : block.getTransactions()) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getHash(),
                            in.getOutpoint().getIndex());
                    if (prevOut == null) {
                        // Cannot happen due to solidity checks before
                        throw new RuntimeException("Block attempts to spend a not yet existent output!");
                    }
                    
                    offerTokenid = Utils.HEX.encode(prevOut.getValue().getTokenid());
                    offerValue += prevOut.getValue().getValue();
                }
            }
            
            OrderRecord record = new OrderRecord(block.getTransactions().get(0).getHash(), Sha256Hash.ZERO_HASH, 
            		offerValue, offerTokenid, false, false, null, reqInfo.getTargetValue(), reqInfo.getTargetTokenid(), 
            		reqInfo.getBeneficiaryPubKey(), NetworkParameters.INITIAL_ORDER_TTL, 0);
            
            blockStore.insertOrder(record);
        } catch (IOException e) {
            // Cannot happen when connecting
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void connectReward(Block block) throws BlockStoreException {
        // Check if eligible:
        Pair<RewardEligibility, Long> eligiblityAndTxReward = validatorService.checkRewardEligibility(block);    
        
        try {
            RewardInfo rewardInfo = RewardInfo.parse(block.getTransactions().get(0).getData());
            Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
            long toHeight = rewardInfo.getToHeight();
   
            blockStore.insertReward(block.getHash(), toHeight, eligiblityAndTxReward.getLeft(), prevRewardHash, eligiblityAndTxReward.getRight());
        } catch (IOException e) {
            // Cannot happen when connecting
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private void connectToken(Block block) {
        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() != null) {
            try {
                byte[] buf = tx.getData();
                TokenInfo tokenInfo = TokenInfo.parse(buf);
                
                // Correctly insert tokens
                tokenInfo.getTokens().setConfirmed(false);
                tokenInfo.getTokens().setBlockhash(block.getHashAsString());
                
                this.blockStore.insertToken(block.getHashAsString(), tokenInfo.getTokens());
                for (MultiSignAddress permissionedAddress : tokenInfo.getMultiSignAddresses()) {
                    permissionedAddress.setBlockhash(block.getHashAsString()); // The primary key must be the correct block
                    blockStore.insertMultiSignAddress(permissionedAddress);
                }
            } catch (Exception e) {
                log.error("not possible checked before", e);
            }

        }
    }

    /**
     * Deterministically execute the order matching algorithm on this block.
     * 
     * @return MODIFIED new consumed orders, virtual order matching tx and newly generated remaining MODIFIED order book
     * @throws BlockStoreException
     */
    public Triple<Collection<OrderRecord>, Transaction, Collection<OrderRecord>> generateOrderMatching(Block block) throws BlockStoreException {

        /*TODO
        -> Order Match: all consumed order records to spent and set spender block to this block's hash
        -> Order Match: Insert if nonexistent and set virtual UTXOs to confirmed (cancelled due to ttl or cancelop, executed)
        -> Order Match: Insert if nonexistent and set new remaining orders to confirmed (where new ttl, remaining value valid) 
        */
        // Get previous order matching block
        Sha256Hash prevRewardHash = null;
        try {
            RewardInfo rewardInfo = RewardInfo.parse(block.getTransactions().get(0).getData());
            prevRewardHash = rewardInfo.getPrevRewardHash();
        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // Collect all orders approved by this block in the reward interval
        List<BlockWrap> relevantBlocks = collectConsumedOrdersAndOpsBlocks(block, prevRewardHash);
        
        // Find all new Cancels, Refreshs and Orders from collected
        List<OrderOpInfo> cancels = new ArrayList<>(), refreshs = new ArrayList<>();
        Map<Sha256Hash, OrderRecord> newOrders = new HashMap<>();
        
        for (BlockWrap b : relevantBlocks) {
            if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OPEN) {
                final Sha256Hash txHash = b.getBlock().getTransactions().get(0).getHash();
                newOrders.put(txHash, blockStore.getOrder(txHash, Sha256Hash.ZERO_HASH));
            } else if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OP) {
                OrderOpInfo info = null;
                try {
                    info = OrderOpInfo.parse(b.getBlock().getTransactions().get(0).getData());
                } catch (IOException e) {
                    // Cannot happen since checked before
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
                if (info.getOp() == OrderOp.CANCEL)
                    cancels.add(info);
                else if (info.getOp() == OrderOp.REFRESH)
                    refreshs.add(info);
            }
        }
        
        // Add the old leftover orders from the db to get all open orders
        Map<Sha256Hash, OrderRecord> remainingOrders = blockStore.getOrderMatchingIssuedOrders(prevRewardHash);
        remainingOrders.putAll(newOrders);
        
        // From all orders and ops, begin order matching algorithm:
        ConcurrentHashMap<String, OrderBook> orderBooks = new ConcurrentHashMap<String, OrderBook>();
        List<OrderRecord> cancelledOrders = new ArrayList<>();
        
        // Process cancel ops
        for (OrderOpInfo c : cancels) {
            cancelledOrders.add(remainingOrders.get(c.getTxHash()));
            remainingOrders.remove(c.getTxHash());
        }
        
        // Match orders
        Map<ByteBuffer, Map<String, Long>> pubKey2ReceivedTokens = new HashMap<>();
        // TODO order match, remove fully executed orders, modify partially executed orders
        // TODO order book: add old ones first, then new ones sorted by their hash xor deterministic randomness (some kind of FIFO)
        // TODO after order matching, write back by adding executed orders to beneficiary proceeds, dropping fully executed orders and updating partly executed orders
        // refactor: include the cancels and ttl timeouts below and above in the order book?
        
        
        
//      OrderBook orderBook = orderBooks.get(order.getTokenId());
//      if (orderBook == null) {
//          orderBook = new OrderBook(new OrderBookEvents());
//          orderBooks.put(order.getTokenId(), orderBook);
//      }
        
        
        // New remaining: TTL decrease by one unless refreshed, process order timeouts
        Iterator<Entry<Sha256Hash, OrderRecord>> it = remainingOrders.entrySet().iterator();
        while (it.hasNext()) {
            final Entry<Sha256Hash, OrderRecord> next = it.next();
            OrderRecord order = next.getValue();
            order.setTtl(order.getTtl() - 1);
            if (order.getTtl() <= 0) { // TODO unless refreshed, then set to full value again
                cancelledOrders.add(order);
                it.remove();
            }
        }
        
        // Make tx with cancelled and executed orders
        Transaction tx = new Transaction(networkParameters);
        // TODO cancelled/executed proceeds tx.addOutput(Coin.valueOf(value, tokenid), ECKey.fromPublicOnly(blockStore.getOrder(orderTx.getHash(), Sha256Hash.ZERO_HASH).getBeneficiaryPubKey()));
        
        // The input does not really need to be a valid signature
        TransactionInput input = new TransactionInput(networkParameters, tx, Script.createInputScript(
                block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
        tx.addInput(input);
        
        // Return MODIFIED new consumed orders, virtual order matching tx and newly generated remaining MODIFIED order book
        return Triple.of(newOrders.values(), tx, remainingOrders.values());
    }

    private List<BlockWrap> collectConsumedOrdersAndOpsBlocks(Block block, Sha256Hash prevRewardHash) throws BlockStoreException {
        List<BlockWrap> relevantBlocks = new ArrayList<>();
        
        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        BlockWrap prevTrunkBlock = blockStore.getBlockWrap(block.getPrevBlockHash());
        BlockWrap prevBranchBlock = blockStore.getBlockWrap(block.getPrevBranchBlockHash());
        blockQueue.add(prevTrunkBlock);
        blockQueue.add(prevBranchBlock);

        // Read previous reward block's data
        BlockWrap prevRewardBlock = blockStore.getBlockWrap(prevRewardHash);
        long prevToHeight = 0;
        try {
            RewardInfo rewardInfo = RewardInfo.parse(prevRewardBlock.getBlock().getTransactions().get(0).getData());
            prevToHeight = rewardInfo.getToHeight();
        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        
        long fromHeight = prevToHeight + 1;
        long toHeight = prevToHeight + (long) NetworkParameters.REWARD_HEIGHT_INTERVAL;

        // Initialize
        BlockWrap currentBlock = null, approvedBlock = null;
        long currentHeight;

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {
            currentHeight = currentBlock.getBlockEvaluation().getHeight();

            // Stop criterion: Block height lower than approved interval height
            if (currentHeight < fromHeight)
                continue;

            // If in relevant reward height interval and a relevant block, collect it
            if (currentHeight <= toHeight) {
                relevantBlocks.add(currentBlock);
            }

            // Continue with both approved blocks
            approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                }
            } 
            approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                }
            } 
        }
        
        return relevantBlocks;
    }
    
    /**
     * Deterministically create a reclaim tx for a lost order. Assumes the block is a valid reclaim block.
     * 
     * @return reclaim transaction 
     * @throws BlockStoreException
     */
    public Transaction generateReclaimTX(Block block) throws BlockStoreException {
        // Read the requested reclaim
        OrderReclaimInfo info = null;
        try {
            info = OrderReclaimInfo.parse(block.getTransactions().get(0).getData());
        } catch (IOException e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }
        
        // Read the referenced order block's tx
        Transaction orderTx = blockStore.get(info.getOrderBlockHash()).getHeader().getTransactions().get(0); 
        
        // Find out how much was spent on the order
        long value = 0;
        byte[] tokenid = null;
        for (TransactionOutput output : orderTx.getOutputs()) {
            tokenid = output.getValue().getTokenid();
            value += output.getValue().getValue();
        }
        
        // Build transaction returning the spent tokens
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(Coin.valueOf(value, tokenid), ECKey.fromPublicOnly(blockStore.getOrder(orderTx.getHash(), Sha256Hash.ZERO_HASH).getBeneficiaryPubKey()));
        
        // The input does not really need to be a valid signature, as long
        // as it has the right general form and is slightly different for
        // different tx
        TransactionInput input = new TransactionInput(networkParameters, tx, Script.createInputScript(
                block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
        tx.addInput(input);

        return tx;
    }

    /**
     * Deterministically creates a mining reward transaction based on the
     * previous blocks and previous reward transaction. DOES NOT CHECK FOR SOLIDITY.
     * You have to ensure that the approved blocks result in an eligible reward block.
     * 
     * @return mining reward transaction 
     * @throws BlockStoreException
     */
    public Transaction generateVirtualMiningRewardTX(Block block) throws BlockStoreException {
        
        Sha256Hash prevRewardHash = null;
        try {
            RewardInfo rewardInfo = RewardInfo.parse(block.getTransactions().get(0).getData());
            prevRewardHash = rewardInfo.getPrevRewardHash();
        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        
        // Count how many blocks from miners in the reward interval are approved
        // and build rewards
        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());

        BlockWrap prevTrunkBlock = blockStore.getBlockWrap(block.getPrevBlockHash());
        BlockWrap prevBranchBlock = blockStore.getBlockWrap(block.getPrevBranchBlockHash());
        blockQueue.add(prevTrunkBlock);
        blockQueue.add(prevBranchBlock);

        // Read previous reward block's data
        BlockWrap prevRewardBlock = blockStore.getBlockWrap(prevRewardHash);
        long prevToHeight = 0, perTxReward = 0;
        try {
            RewardInfo rewardInfo = RewardInfo.parse(prevRewardBlock.getBlock().getTransactions().get(0).getData());
            
            prevToHeight = rewardInfo.getToHeight();
            perTxReward = blockStore.getRewardNextTxReward(prevRewardHash);

        } catch (IOException e) {
            // Cannot happen since checked before
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        
        long fromHeight = prevToHeight + 1;
        long toHeight = prevToHeight + (long) NetworkParameters.REWARD_HEIGHT_INTERVAL;

        // Initialize
        Set<BlockWrap> currentHeightBlocks = new HashSet<>();
        Map<BlockWrap, Set<Sha256Hash>> snapshotWeights = new HashMap<>();
        Map<Address, Long> finalRewardCount = new HashMap<>();
        BlockWrap currentBlock = null, approvedBlock = null;
        long currentHeight = Long.MAX_VALUE;
        long totalRewardCount = 0;

        for (BlockWrap tip : blockQueue) {
            snapshotWeights.put(tip, new HashSet<>());
        }

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {

            // If we have reached a new height level, try trigger payout
            // calculation
            if (currentHeight > currentBlock.getBlockEvaluation().getHeight()) {

                // Calculate rewards if in reward interval height
                if (currentHeight <= toHeight) {
                    totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
                            totalRewardCount);
                }

                // Finished with this height level, go to next level
                currentHeightBlocks.clear();
                long currentHeightTmp = currentHeight;
                snapshotWeights.entrySet()
                        .removeIf(e -> e.getKey().getBlockEvaluation().getHeight() == currentHeightTmp);
                currentHeight = currentBlock.getBlockEvaluation().getHeight();
            }

            // Stop criterion: Block height lower than approved interval height
            if (currentHeight < fromHeight)
                continue;

            // Add your own hash to approver hashes of current approver hashes
            snapshotWeights.get(currentBlock).add(currentBlock.getBlockHash());

            // If in relevant reward height interval, count it
            if (currentHeight <= toHeight) {
                // Count the blocks of current height
                currentHeightBlocks.add(currentBlock);
            }

            // Continue with both approved blocks
            approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                    snapshotWeights.put(approvedBlock, new HashSet<>(snapshotWeights.get(currentBlock)));
                }
            } else {
                snapshotWeights.get(approvedBlock).add(currentBlock.getBlockHash());
            }
            approvedBlock = blockStore.getBlockWrap(currentBlock.getBlock().getPrevBranchBlockHash());
            if (!blockQueue.contains(approvedBlock)) {
                if (approvedBlock != null) {
                    blockQueue.add(approvedBlock);
                    snapshotWeights.put(approvedBlock, new HashSet<>(snapshotWeights.get(currentBlock)));
                }
            } else {
                snapshotWeights.get(approvedBlock).add(currentBlock.getBlockHash());
            }
        }

        // Exception for height 0 (genesis): since prevblock null, finish payout
        // calculation
        if (currentHeight == 0) {
            // For each height, throw away anything below the 99-percentile
            // in terms of reduced weight
            totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
                    totalRewardCount);
        }
        
        // Build transaction outputs sorted by addresses
        Transaction tx = new Transaction(networkParameters);
        for (Entry<Address, Long> entry : finalRewardCount.entrySet().stream()
                .sorted(Comparator.comparing((Entry<Address, Long> e) -> e.getKey())).collect(Collectors.toList()))
            tx.addOutput(Coin.SATOSHI.times(entry.getValue() * perTxReward), entry.getKey());
        
        // The input does not really need to be a valid signature, as long
        // as it has the right general form and is slightly different for
        // different tx
        TransactionInput input = new TransactionInput(networkParameters, tx, Script.createInputScript(
                prevTrunkBlock.getBlockHash().getBytes(), prevBranchBlock.getBlockHash().getBytes()));
        tx.addInput(input);

        return tx;
    }

    private long calculateHeightRewards(Set<BlockWrap> currentHeightBlocks,
            Map<BlockWrap, Set<Sha256Hash>> snapshotWeights, Map<Address, Long> finalRewardCount,
            long totalRewardCount) {
        long heightRewardCount = (long) Math.ceil(0.95d * currentHeightBlocks.size());
        totalRewardCount += heightRewardCount;

        long rewarded = 0;
        for (BlockWrap rewardedBlock : currentHeightBlocks.stream()
                .sorted(Comparator.comparingLong(b -> snapshotWeights.get(b).size()).reversed())
                .collect(Collectors.toList())) {
            if (rewarded >= heightRewardCount)
                break;

            Address miner = new Address(networkParameters, rewardedBlock.getBlock().getMinerAddress());
            if (!finalRewardCount.containsKey(miner))
                finalRewardCount.put(miner, 1L);
            else
                finalRewardCount.put(miner, finalRewardCount.get(miner) + 1);
            rewarded++;
        }
        return totalRewardCount;
    }
}
