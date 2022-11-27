/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Stopwatch;
import com.google.common.math.LongMath;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ContractEventInfo;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MemoInfo;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderCancel;
import net.bigtangle.core.OrderCancelInfo;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.TXReward;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.GenericInvalidityException;
import net.bigtangle.core.exception.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.exception.VerificationException.UnsolidException;
import net.bigtangle.core.ordermatch.OrderBookEvents;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;
import net.bigtangle.core.ordermatch.OrderBookEvents.Match;
import net.bigtangle.core.ordermatch.TradePair;
import net.bigtangle.script.Script;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.data.ChainBlockQueue;
import net.bigtangle.server.data.ContractEventRecord;
import net.bigtangle.server.data.DepthAndWeight;
import net.bigtangle.server.data.LockObject;
import net.bigtangle.server.data.OrderMatchingResult;
import net.bigtangle.server.data.SolidityState;
import net.bigtangle.server.data.SolidityState.State;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.OrderTickerService;
import net.bigtangle.server.service.OutputService;
import net.bigtangle.server.service.RewardService;
import net.bigtangle.server.service.StoreService;
import net.bigtangle.server.service.ValidatorService;
import net.bigtangle.server.utils.OrderBook;
import net.bigtangle.utils.Gzip;
import net.bigtangle.utils.Json;

/**
 * <p>
 * A FullBlockGraph works in conjunction with a {@link FullBlockStore} to verify
 * all the rules of the BigTangle system. Chain block as reward block is added
 * first into ChainBlockQueue as other blocks will be added in parallel. The
 * process of ChainBlockQueue by update chain is locked by database.
 * Chain block will add to chain if there is no exception. if the
 * reward block is unsolid as missing previous block, then it will trigger a
 * sync and be deleted.
 * UpdateConfirm can add UTXO using MCMC and can run only as part of update
 * chain and will be boxed timeout.
 * 
 * </p>
 */
@Service
public class FullBlockGraph {

    private static final long DaySeconds = 24 * 60 * 60L;
    private static final Logger log = LoggerFactory.getLogger(FullBlockGraph.class);

    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    private ValidatorService validatorService;

    @Autowired
    private RewardService rewardService;
    @Autowired
    private OrderTickerService tickerService;
    @Autowired
    ServerConfiguration serverConfiguration;
    @Autowired
    private BlockService blockService;
    @Autowired
    private StoreService storeService;

    @Autowired
    private OutputService outputService;
    
    public boolean add(Block block, boolean allowUnsolid, FullBlockStore store) throws BlockStoreException {
        boolean added;
        if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
            added = addChain(block, allowUnsolid, true, store);
        } else {
            added = addNonChain(block, allowUnsolid, store);
        }

        // TODO move this function to wallet,
        // update spend of origin UTXO to avoid create of double spent
        if (added) {
            updateTransactionOutputSpendPending(block);
        }

        return added;
    }

    /*
     * speedup of sync without updateTransactionOutputSpendPending.
     */
    public boolean addNoSpendPending(Block block, boolean allowUnsolid, FullBlockStore store)
            throws BlockStoreException {
        boolean a;
        if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
            a = addChain(block, allowUnsolid, true, store);
        } else {
            a = addNonChain(block, allowUnsolid, store);
        }
        return a;
    }

    public boolean add(Block block, boolean allowUnsolid, boolean updatechain, FullBlockStore store)
            throws BlockStoreException {
        boolean a = add(block, allowUnsolid, store);
        if (updatechain) {
            updateChain();
        }
        return a;
    }

    public boolean addChain(Block block, boolean allowUnsolid, boolean tryConnecting, FullBlockStore store)
            throws BlockStoreException {

        // Check the block is partially formally valid and fulfills PoW
        block.verifyHeader();
        block.verifyTransactions();
        // no more check add data
        saveChainBlockQueue(block, store, false);

        return true;
    }

    public void updateChain() throws BlockStoreException {
        updateChainConnected();
        updateConfirmedTimeBoxed();
    }

    public void updateChainConnected() throws BlockStoreException {
        String LOCKID = "chain";
        int LockTime = 1000000;
        FullBlockStore store = storeService.getStore();
        try {
            // log.info("create Reward started");
            LockObject lock = store.selectLockobject(LOCKID);
            boolean canrun = false;
            if (lock == null) {
                try {
                    store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
                    canrun = true;
                } catch (Exception e) {
                    // ignore
                }
            } else {
                if (lock.getLocktime() < System.currentTimeMillis() - LockTime) {
                    store.deleteLockobject(LOCKID);
                    store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
                    canrun = true;
                } else {
                    if (lock.getLocktime() < System.currentTimeMillis() - 10000)
                        log.info("updateChain running start = " + Utils.dateTimeFormat(lock.getLocktime()));
                }
            }
            if (canrun) {
                Stopwatch watch = Stopwatch.createStarted();
                saveChainConnected(store, false);
                store.deleteLockobject(LOCKID);
                if (watch.elapsed(TimeUnit.MILLISECONDS) > 1000) {
                    log.info("updateChain time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
                }
                watch.stop();
            }
        } catch (Exception e) {
            store.deleteLockobject(LOCKID);
            throw e;
        } finally {
            if (store != null)
                store.close();
        }

    }

    private void saveChainBlockQueue(Block block, FullBlockStore store, boolean orphan) throws BlockStoreException {
        // save the block
        try {
            store.beginDatabaseBatchWrite();
            ChainBlockQueue chainBlockQueue = new ChainBlockQueue(block.getHash().getBytes(),
                    Gzip.compress(block.unsafeBitcoinSerialize()), block.getLastMiningRewardBlock(), orphan,
                    block.getTimeSeconds());
            store.insertChainBlockQueue(chainBlockQueue);
            store.commitDatabaseBatchWrite();
        } catch (Exception e) {
            store.abortDatabaseBatchWrite();
            throw e;
        } finally {
            store.defaultDatabaseBatchWrite();

        }
    }

    /*
     *  
     */
    public void saveChainConnected(FullBlockStore store, boolean updatelowchain)
            throws VerificationException, BlockStoreException {
        List<ChainBlockQueue> cbs = store.selectChainblockqueue(false, serverConfiguration.getSyncblocks());
        if (cbs != null && !cbs.isEmpty()) {
            Stopwatch watch = Stopwatch.createStarted();
            log.info("selectChainblockqueue with size  " + cbs.size());
            // check only do add if there is longer chain as saved in database
            TXReward maxConfirmedReward = store.getMaxConfirmedReward();
            ChainBlockQueue maxFromQuery = cbs.get(cbs.size() - 1);
            if (!updatelowchain && maxConfirmedReward.getChainLength() > maxFromQuery.getChainlength()) {
                log.info("not longest chain in  selectChainblockqueue {}  < {}", maxFromQuery.toString(),
                        maxConfirmedReward.toString());
                return;
            }
            for (ChainBlockQueue chainBlockQueue : cbs) {
                saveChainConnected(chainBlockQueue, store);
            }
            log.info("saveChainConnected time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    private void saveChainConnected(ChainBlockQueue chainBlockQueue, FullBlockStore store)
            throws VerificationException, BlockStoreException {

        try {
            store.beginDatabaseBatchWrite();

            // It can be down lock for update of this on database
            Block block = networkParameters.getDefaultSerializer().makeBlock(chainBlockQueue.getBlock());

            // Check the block is partially formally valid and fulfills PoW
            block.verifyHeader();
         //   block.verifyTransactions();

            SolidityState solidityState = validatorService.checkChainSolidity(block, true, store);

            if (solidityState.isDirectlyMissing()) {
                log.debug("Block isDirectlyMissing. remove it from ChainBlockQueue,  Chain is out of date."
                        + block.toString());
                // sync the lastest chain from remote start from the -2 rewards
                // syncBlockService.startSingleProcess(block.getLastMiningRewardBlock()
                // - 2, false);
                return;
            }

            if (solidityState.isFailState()) {
                log.debug("Block isFailState. remove it from ChainBlockQueue." + block.toString());
                return;
            }
            // Inherit solidity from predecessors if they are not solid
            solidityState = validatorService.getMinPredecessorSolidity(block, false, store, false);

            // Sanity check
            if (solidityState.isFailState() || solidityState.getState() == State.MissingPredecessor) {
                log.debug("Block isFailState. remove it from ChainBlockQueue." + block.toString());
                return;
            }
            connectRewardBlock(block, solidityState, store);
            store.commitDatabaseBatchWrite();
        } catch (Exception e) {
            store.abortDatabaseBatchWrite();
            throw e;
        } finally {
            deleteChainQueue(chainBlockQueue, store);
            store.defaultDatabaseBatchWrite();
        }
    }

    private void deleteChainQueue(ChainBlockQueue chainBlockQueue, FullBlockStore store) throws BlockStoreException {
        List<ChainBlockQueue> l = new ArrayList<ChainBlockQueue>();
        l.add(chainBlockQueue);
        store.deleteChainBlockQueue(l);
    }

    public boolean addNonChain(Block block, boolean allowUnsolid, FullBlockStore blockStore)
            throws BlockStoreException {

        // Check the block is partially formally valid and fulfills PoW

        block.verifyHeader();
          block.verifyTransactions();

          //allow non chain block predecessors not solid
        SolidityState solidityState = validatorService.checkSolidity(block, !allowUnsolid, blockStore,false);
        if (solidityState.isFailState()) {
            log.debug(solidityState.toString());
        }
        // If explicitly wanted (e.g. new block from local clients), this
        // block must strictly be solid now.
        if (!allowUnsolid) {
            switch (solidityState.getState()) {
            case MissingPredecessor:
                throw new UnsolidException();
            case MissingCalculation:
            case Success:
                break;
            case Invalid:
                throw new GenericInvalidityException();
            }
        }

        // Accept the block
        try {
            blockStore.beginDatabaseBatchWrite();
            connect(block, solidityState, blockStore);
            blockStore.commitDatabaseBatchWrite();
        } catch (Exception e) {
            blockStore.abortDatabaseBatchWrite();
            throw e;
        } finally {
            blockStore.defaultDatabaseBatchWrite();
        }

        return true;
    }

    private void connectRewardBlock(final Block block, SolidityState solidityState, FullBlockStore store)
            throws BlockStoreException, VerificationException {

        if (solidityState.isFailState()) {
            connect(block, solidityState, store);
            return;
        }
        Block head = store.get(store.getMaxConfirmedReward().getBlockHash());
        if (block.getRewardInfo().getPrevRewardHash().equals(head.getHash())) {
            connect(block, solidityState, store);
            rewardService.buildRewardChain(block, store);
        } else {
            // This block connects to somewhere other than the top of the best
            // known chain. We treat these differently.

            boolean haveNewBestChain = block.getRewardInfo().getChainlength() > head.getRewardInfo().getChainlength();
            // TODO check this
            // block.getRewardInfo().moreWorkThan(head.getRewardInfo());
            if (haveNewBestChain) {
                log.info("Block is causing a re-organize");
                connect(block, solidityState, store);
                rewardService.handleNewBestChain(block, store);
            } else {
                // parallel chain, save as unconfirmed
                connect(block, solidityState, store);
            }

        }
    }

    /**
     * Inserts the specified block into the DB
     * 
     * @param block
     *            the block
     * @param solidityState
     * @param height
     *            the block's height
     * @throws BlockStoreException
     * @throws VerificationException
     */
    private void connect(final Block block, SolidityState solidityState, FullBlockStore store)
            throws BlockStoreException, VerificationException {

        store.put(block);
        solidifyBlock(block, solidityState, false, store);
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
                address = script.getToAddress(networkParameters, true).toString();
            }
        } catch (Exception e) {
            // e.printStackTrace();
        }
        return address;
    }

    private void synchronizationUserData(Sha256Hash blockhash, DataClassName dataClassName, byte[] data, String pubKey,
            long blocktype, FullBlockStore blockStore) throws BlockStoreException {
        UserData userData = blockStore.queryUserDataWithPubKeyAndDataclassname(dataClassName.name(), pubKey);
        if (userData == null) {
            userData = new UserData();
            userData.setBlockhash(blockhash);
            userData.setData(data);
            userData.setDataclassname(dataClassName.name());
            userData.setPubKey(pubKey);
            userData.setBlocktype(blocktype);
            blockStore.insertUserData(userData);
            return;
        }
        userData.setBlockhash(blockhash);
        userData.setData(data);
        blockStore.updateUserData(userData);
    }

    /**
     * Adds the specified block and all approved blocks to the confirmed set.
     * This will connect all transactions of the block by marking used UTXOs
     * spent and adding new UTXOs to the db.
     * 
     * @param blockHash
     * @param milestoneNumber
     * @throws BlockStoreException
     */
    public void confirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes, long milestoneNumber,
            FullBlockStore blockStore) throws BlockStoreException {
        // If already confirmed, return
        if (traversedBlockHashes.contains(blockHash))
            return;

        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();

        // If already confirmed, return
        if (blockEvaluation.isConfirmed())
            return;

        // Set confirmed, only if it is not confirmed
        if (!blockEvaluation.isConfirmed()) {
            blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), true);
        }
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), milestoneNumber);

        // Confirm the block
        confirmBlock(blockWrap, blockStore);

        // Keep track of confirmed blocks
        traversedBlockHashes.add(blockHash);
    }

    /**
     * Calculates and inserts any virtual transaction outputs so dependees can
     * become solid
     * 
     * @param block
     * @return
     * @throws BlockStoreException
     */
    public Optional<OrderMatchingResult> calculateBlock(Block block, FullBlockStore blockStore)
            throws BlockStoreException {

        Transaction tx = null;
        OrderMatchingResult matchingResult = null;

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
            tx = generateVirtualMiningRewardTX(block, blockStore);
            insertVirtualUTXOs(block, tx, blockStore);

            // Get list of consumed orders, virtual order matching tx and newly
            // generated remaining order book
            matchingResult = generateOrderMatching(block, blockStore);
            tx = matchingResult.getOutputTx();

            insertVirtualUTXOs(block, tx, blockStore);
            insertVirtualOrderRecords(block, matchingResult.getRemainingOrders(), blockStore);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_CONTRACT_EVENT:
            break;
        case BLOCKTYPE_CONTRACT_EXECUTE:
            break;
        case BLOCKTYPE_ORDER_OPEN:
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }

        // Return the computation result
        return Optional.ofNullable(matchingResult);
    }

    private void confirmBlock(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {

        // Update block's transactions in db
        for (final Transaction tx : block.getBlock().getTransactions()) {
            confirmTransaction(block, tx, blockStore);
        }

        // type-specific updates
        switch (block.getBlock().getBlockType()) {
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
            confirmReward(block, blockStore);
            confirmOrderMatching(block, blockStore);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // For token creations, update token db
            confirmToken(block, blockStore);
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
        case BLOCKTYPE_CONTRACT_EVENT:
            confirmVOSOrUserData(block, blockStore);
            break;
        case BLOCKTYPE_CONTRACT_EXECUTE:
            // confirmVOSExecute(block);
            break;
        case BLOCKTYPE_ORDER_OPEN:
            confirmOrderOpen(block, blockStore);
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void confirmVOSOrUserData(BlockWrap block, FullBlockStore blockStore) {
        Transaction tx = block.getBlock().getTransactions().get(0);
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
                synchronizationUserData(block.getBlock().getHash(), DataClassName.valueOf(tx.getDataClassName()),
                        tx.getData(), (String) multiSignBy.get("publickey"), block.getBlock().getBlockType().ordinal(),
                        blockStore);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void confirmOrderMatching(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        // TODO don't calculate again, it should already have been calculated
        // before
        OrderMatchingResult actualCalculationResult = generateOrderMatching(block.getBlock(), blockStore);

        // All consumed order records are now spent by this block

        for (OrderRecord o : actualCalculationResult.getSpentOrders()) {
            o.setSpent(true);
            o.setSpenderBlockHash(block.getBlock().getHash());
        }
        blockStore.updateOrderSpent(actualCalculationResult.getSpentOrders());

        // Set virtual outputs confirmed
        confirmVirtualCoinbaseTransaction(block, blockStore);

        // Set new orders confirmed

        blockStore.updateOrderConfirmed(actualCalculationResult.getRemainingOrders(), true);

        // Update the matching history in db
        tickerService.addMatchingEvents(actualCalculationResult,
                actualCalculationResult.getOutputTx().getHashAsString(), block.getBlock().getTimeSeconds(), blockStore);
    }

    private void confirmOrderOpen(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {
        // Set own output confirmed
        blockStore.updateOrderConfirmed(block.getBlock().getHash(), Sha256Hash.ZERO_HASH, true);
    }

    private void confirmReward(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {
        // Set virtual reward tx outputs confirmed
        confirmVirtualCoinbaseTransaction(block, blockStore);

        // Set used other output spent
        blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getBlock().getHash()), true,
                block.getBlock().getHash());

        // Set own output confirmed
        blockStore.updateRewardConfirmed(block.getBlock().getHash(), true);
    }

    private void insertVirtualOrderRecords(Block block, Collection<OrderRecord> orders, FullBlockStore blockStore) {
        try {

            blockStore.insertOrder(orders);

        } catch (BlockStoreException e) {
            // Expected after reorgs
            log.warn("Probably reinserting orders: ", e);
        }
    }

    private void insertVirtualUTXOs(Block block, Transaction virtualTx, FullBlockStore blockStore) {
        try {
            ArrayList<Transaction> txs = new ArrayList<Transaction>();
            txs.add(virtualTx);
            connectUTXOs(block, txs, blockStore);
        } catch (BlockStoreException e) {
            // Expected after reorgs
            log.warn("Probably reinserting reward: ", e);
        }
    }

    private void confirmToken(BlockWrap block, FullBlockStore blockStore) throws BlockStoreException {
        // Set used other output spent
        if (blockStore.getTokenPrevblockhash(block.getBlock().getHash()) != null)
            blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(block.getBlock().getHash()), true,
                    block.getBlock().getHash());

        // Set own output confirmed
        blockStore.updateTokenConfirmed(block.getBlock().getHash(), true);
    }

    private void confirmTransaction(BlockWrap block, Transaction tx, FullBlockStore blockStore)
            throws BlockStoreException {
        // Set used other outputs spent
        if (!tx.isCoinBase()) {
            for (TransactionInput in : tx.getInputs()) {
                UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getBlockHash(),
                        in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());

                // Sanity check
                if (prevOut == null)
                    throw new RuntimeException("Attempted to spend a non-existent output!");
            //FIXME transaction check at connected    if (prevOut.isSpent())
            //        throw new RuntimeException("Attempted to spend an already spent output!");

                blockStore.updateTransactionOutputSpent(prevOut.getBlockHash(), prevOut.getTxHash(), prevOut.getIndex(),
                        true, block.getBlockHash());
            }
        }

        // Set own outputs confirmed
        for (TransactionOutput out : tx.getOutputs()) {
            blockStore.updateTransactionOutputConfirmed(block.getBlockHash(), tx.getHash(), out.getIndex(), true);
        }
    }

    private void confirmVirtualCoinbaseTransaction(BlockWrap block, FullBlockStore blockStore)
            throws BlockStoreException {
        // Set own outputs confirmed
        blockStore.updateAllTransactionOutputsConfirmed(block.getBlock().getHash(), true);
    }

    public void unconfirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes, FullBlockStore blockStore)
            throws BlockStoreException {
        // If already unconfirmed, return
        if (traversedBlockHashes.contains(blockHash))
            return;

        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        Block block = blockWrap.getBlock();

        // If already unconfirmed, return
        if (!blockEvaluation.isConfirmed())
            return;

        // Then unconfirm the block outputs
        unconfirmBlockOutputs(block, blockStore);

        // Set unconfirmed
        blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), -1);

        // Keep track of unconfirmed blocks
        traversedBlockHashes.add(blockHash);
    }

    public void unconfirmRecursive(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes,
            FullBlockStore blockStore) throws BlockStoreException {
        // If already confirmed, return
        if (traversedBlockHashes.contains(blockHash))
            return;

        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        Block block = blockWrap.getBlock();

        // If already unconfirmed, return
        if (!blockEvaluation.isConfirmed())
            return;

        // Unconfirm all dependents
        unconfirmDependents(block, traversedBlockHashes, blockStore);

        // Then unconfirm the block itself
        unconfirmBlockOutputs(block, blockStore);

        // Set unconfirmed
        blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), -1);

        // Keep track of unconfirmed blocks
        traversedBlockHashes.add(blockHash);
    }

    private void unconfirmDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes, FullBlockStore blockStore)
            throws BlockStoreException {
        // Unconfirm all approver blocks first
        for (Sha256Hash approver : blockStore.getSolidApproverBlockHashes(block.getHash())) {
            unconfirmRecursive(approver, traversedBlockHashes, blockStore);
        }

        // Disconnect all transaction output dependents
        for (Transaction tx : block.getTransactions()) {
            for (TransactionOutput txout : tx.getOutputs()) {
                UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
                if (utxo != null && utxo.isSpent()) {
                    unconfirmRecursive(
                            blockStore.getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex())
                                    .getBlockHash(),
                            traversedBlockHashes, blockStore);
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
            unconfirmRewardDependents(block, traversedBlockHashes, blockStore);
            unconfirmOrderMatchingDependents(block, traversedBlockHashes, blockStore);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            // Unconfirm dependents
            unconfirmTokenDependents(block, traversedBlockHashes, blockStore);
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_CONTRACT_EVENT:
            break;
        case BLOCKTYPE_CONTRACT_EXECUTE:
            break;
        case BLOCKTYPE_ORDER_OPEN:
            unconfirmOrderOpenDependents(block, traversedBlockHashes, blockStore);
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void unconfirmOrderMatchingDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
            FullBlockStore blockStore) throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        OrderMatchingResult matchingResult = generateOrderMatching(block, blockStore);

        // Disconnect all virtual transaction output dependents
        Transaction tx = matchingResult.getOutputTx();
        ;
        for (TransactionOutput txout : tx.getOutputs()) {
            UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
            if (utxo != null && utxo.isSpent()) {
                unconfirmRecursive(blockStore
                        .getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
                        traversedBlockHashes, blockStore);
            }
        }
    }

    private void unconfirmOrderOpenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
            FullBlockStore blockStore) throws BlockStoreException {

        // Disconnect order record spender
        if (blockStore.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH)) {
            unconfirmRecursive(blockStore.getOrderSpender(block.getHash(), Sha256Hash.ZERO_HASH), traversedBlockHashes,
                    blockStore);
        }
    }

    private void unconfirmTokenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
            FullBlockStore blockStore) throws BlockStoreException {

        // Disconnect token record spender
        if (blockStore.getTokenSpent(block.getHash())) {
            unconfirmRecursive(blockStore.getTokenSpender(block.getHashAsString()), traversedBlockHashes, blockStore);
        }

        // If applicable: Disconnect all domain definitions that were based on
        // this domain
        Token token = blockStore.getTokenByBlockHash(block.getHash());

        List<String> dependents = blockStore.getDomainDescendantConfirmedBlocks(token.getBlockHashHex());
        for (String b : dependents) {
            unconfirmRecursive(Sha256Hash.wrap(b), traversedBlockHashes, blockStore);

        }
    }

    private void unconfirmRewardDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes,
            FullBlockStore blockStore) throws BlockStoreException {

        // Disconnect reward record spender
        if (blockStore.getRewardSpent(block.getHash())) {
            unconfirmRecursive(blockStore.getRewardSpender(block.getHash()), traversedBlockHashes, blockStore);
        }

        // Disconnect all virtual transaction output dependents
        Transaction tx = generateVirtualMiningRewardTX(block, blockStore);
        for (TransactionOutput txout : tx.getOutputs()) {
            UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
            if (utxo != null && utxo.isSpent()) {
                unconfirmRecursive(blockStore
                        .getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
                        traversedBlockHashes, blockStore);
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
    private void unconfirmBlockOutputs(Block block, FullBlockStore blockStore) throws BlockStoreException {
        // Unconfirm all transactions of the block
        for (Transaction tx : block.getTransactions()) {
            unconfirmTransaction(tx, block, blockStore);
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
            unconfirmReward(block, blockStore);
            unconfirmOrderMatching(block, blockStore);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            unconfirmToken(block, blockStore);
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_CONTRACT_EVENT:
            break;
        case BLOCKTYPE_CONTRACT_EXECUTE:
            break;
        case BLOCKTYPE_ORDER_OPEN:
            unconfirmOrderOpen(block, blockStore);
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void unconfirmOrderMatching(Block block, FullBlockStore blockStore) throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        OrderMatchingResult matchingResult = generateOrderMatching(block, blockStore);

        // All consumed order records are now unspent by this block
        Set<OrderRecord> updateOrder = new HashSet<OrderRecord>(matchingResult.getSpentOrders());
        for (OrderRecord o : updateOrder) {
            o.setSpent(false);
            o.setSpenderBlockHash(null);
        }
        blockStore.updateOrderSpent(updateOrder);

        // Set virtual outputs unconfirmed
        unconfirmVirtualCoinbaseTransaction(block, blockStore);

        blockStore.updateOrderConfirmed(matchingResult.getRemainingOrders(), false);

        // Update the matching history in db
        tickerService.removeMatchingEvents(matchingResult.getOutputTx(), blockStore);
    }

    private void unconfirmOrderOpen(Block block, FullBlockStore blockStore) throws BlockStoreException {
        // Set own output unconfirmed

        blockStore.updateOrderConfirmed(block.getHash(), Sha256Hash.ZERO_HASH, false);
    }

    private void unconfirmReward(Block block, FullBlockStore blockStore) throws BlockStoreException {
        // Unconfirm virtual tx
        unconfirmVirtualCoinbaseTransaction(block, blockStore);

        // Set used other output unspent
        blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getHash()), false, null);

        // Set own output unconfirmed
        blockStore.updateRewardConfirmed(block.getHash(), false);
    }

    private void unconfirmToken(Block block, FullBlockStore blockStore) throws BlockStoreException {
        // Set used other output unspent
        if (blockStore.getTokenPrevblockhash(block.getHash()) != null)
            blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(block.getHash()), false, null);

        // Set own output unconfirmed
        blockStore.updateTokenConfirmed(block.getHash(), false);
    }

    /**
     * Disconnects the UTXOs of the transaction
     * 
     * @param tx
     * @param parentBlock
     * @throws BlockStoreException
     */
    private void unconfirmTransaction(Transaction tx, Block parentBlock, FullBlockStore blockStore)
            throws BlockStoreException {
        // Set used outputs as unspent
        if (!tx.isCoinBase()) {
            for (TransactionInput txin : tx.getInputs()) {
                blockStore.updateTransactionOutputSpent(txin.getOutpoint().getBlockHash(),
                        txin.getOutpoint().getTxHash(), txin.getOutpoint().getIndex(), false, null);
            }
        }

        // Set own outputs unconfirmed
        for (TransactionOutput txout : tx.getOutputs()) {
            blockStore.updateTransactionOutputConfirmed(parentBlock.getHash(), tx.getHash(), txout.getIndex(), false);
        }
    }

    private void unconfirmVirtualCoinbaseTransaction(Block parentBlock, FullBlockStore blockStore)
            throws BlockStoreException {
        // Set own outputs unconfirmed
        blockStore.updateAllTransactionOutputsConfirmed(parentBlock.getHash(), false);
    }

    public void solidifyBlock(Block block, SolidityState solidityState, boolean setMilestoneSuccess,
            FullBlockStore blockStore) throws BlockStoreException {

        switch (solidityState.getState()) {
        case MissingCalculation:
            blockStore.updateBlockEvaluationSolid(block.getHash(), 1);

            // Reward blocks follow different logic: If this is new, run
            // consensus logic
            if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
                solidifyReward(block, blockStore);
                return;
            }
            // Insert other blocks into waiting list
            // insertUnsolidBlock(block, solidityState, blockStore);
            break;
        case MissingPredecessor:
            if (block.getBlockType() == Type.BLOCKTYPE_INITIAL
                    && blockStore.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() > 0) {
                throw new RuntimeException("Should not happen");
            }

            blockStore.updateBlockEvaluationSolid(block.getHash(), 0);

            // Insert into waiting list
            // insertUnsolidBlock(block, solidityState, blockStore);
            break;
        case Success:
            // If already set, nothing to do here...
            if (blockStore.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() == 2)
                return;

            // TODO don't calculate again, it may already have been calculated
            // before
            connectUTXOs(block, blockStore);
            connectTypeSpecificUTXOs(block, blockStore);
            calculateBlock(block, blockStore);

            if (block.getBlockType() == Type.BLOCKTYPE_REWARD && !setMilestoneSuccess) {
                // If we don't want to set the milestone success, initialize as
                // missing calc
                blockStore.updateBlockEvaluationSolid(block.getHash(), 1);
            } else {
                // Else normal update
                blockStore.updateBlockEvaluationSolid(block.getHash(), 2);
            }
            if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
                solidifyReward(block, blockStore);
                return;
            }

            break;
        case Invalid:

            blockStore.updateBlockEvaluationSolid(block.getHash(), -1);
            break;
        }
    }

    protected void connectUTXOs(Block block, FullBlockStore blockStore)
            throws BlockStoreException, VerificationException {
        List<Transaction> transactions = block.getTransactions();
        connectUTXOs(block, transactions, blockStore);
    }

    // TODO update other output data can be deadlock, as non chain block
    // run in parallel
    private void updateTransactionOutputSpendPending(Block block) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {

                FullBlockStore blockStore = storeService.getStore();
                try {
                    updateTransactionOutputSpendPending(block, blockStore);

                    // Initialize MCMC
                    if (blockStore.getMCMC(block.getHash()) == null) {
                        ArrayList<DepthAndWeight> depthAndWeight = new ArrayList<DepthAndWeight>();
                        depthAndWeight.add(new DepthAndWeight(block.getHash().toString(), 1, 0));
                        blockStore.updateBlockEvaluationWeightAndDepth(depthAndWeight);
                    }
                } finally {
                    if (blockStore != null)
                        blockStore.close();
                }
                return "";
            }
        });
        try {
            handler.get(2000l, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.info("TimeoutException cancel updateTransactionOutputSpendPending ");
            handler.cancel(true);
        } catch (Exception e) {
            // ignore
            log.info("updateTransactionOutputSpendPending", e);
        } finally {
            executor.shutdownNow();
        }

    }

    private void updateTransactionOutputSpendPending(Block block, FullBlockStore blockStore)
            throws BlockStoreException {
        for (final Transaction tx : block.getTransactions()) {
            boolean isCoinBase = tx.isCoinBase();
            List<UTXO> spendPending = new ArrayList<UTXO>();
            if (!isCoinBase) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getBlockHash(),
                            in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
                    if (prevOut != null) {
                        spendPending.add(prevOut);
                    }
                }
            }

            blockStore.updateTransactionOutputSpendPending(spendPending);

        }
    }

    private void connectUTXOs(Block block, List<Transaction> transactions, FullBlockStore blockStore)
            throws BlockStoreException {
        for (final Transaction tx : transactions) {
            boolean isCoinBase = tx.isCoinBase();
            List<UTXO> utxos = new ArrayList<UTXO>();
            for (TransactionOutput out : tx.getOutputs()) {
                Script script = getScript(out.getScriptBytes());
                String fromAddress = fromAddress(tx, isCoinBase);
                int minsignnumber = 1;
                if (script.isSentToMultiSig()) {
                    minsignnumber = script.getNumberOfSignaturesRequiredToSpend();
                }
                UTXO newOut = new UTXO(tx.getHash(), out.getIndex(), out.getValue(), isCoinBase, script,
                        getScriptAddress(script), block.getHash(), fromAddress, tx.getMemo(),
                        Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, minsignnumber, 0,
                        block.getTimeSeconds(), null);

                if (!newOut.isZero()) {
                    utxos.add(newOut);
                    if (script.isSentToMultiSig()) {

                        for (ECKey ecKey : script.getPubKeys()) {
                            String toaddress = ecKey.toAddress(networkParameters).toBase58();
                            OutputsMulti outputsMulti = new OutputsMulti(newOut.getTxHash(), toaddress,
                                    newOut.getIndex());
                            blockStore.insertOutputsMulti(outputsMulti);
                        }
                    }
                }
                //reset cache
                outputService.evictTransactionOutputs(fromAddress);
                outputService.evictTransactionOutputs( getScriptAddress(script));
            }
            blockStore.addUnspentTransactionOutput(utxos);
        }
    }

    private String fromAddress(final Transaction tx, boolean isCoinBase) {
        String fromAddress = "";
        if (!isCoinBase) {
            for (TransactionInput t : tx.getInputs()) {
                try {
                    if (t.getConnectedOutput().getScriptPubKey().isSentToAddress()) {
                        fromAddress = t.getFromAddress().toBase58();
                    } else {
                        fromAddress = new Address(networkParameters,
                                Utils.sha256hash160(t.getConnectedOutput().getScriptPubKey().getPubKey())).toBase58();

                    }

                    if (!fromAddress.equals(""))
                        return fromAddress;
                } catch (Exception e) {
                    // No address found.
                }
            }
            return fromAddress;
        }
        return fromAddress;
    }

    private void connectTypeSpecificUTXOs(Block block, FullBlockStore blockStore) throws BlockStoreException {
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
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            connectToken(block, blockStore);
            break;
        case BLOCKTYPE_TRANSFER:
            break;
        case BLOCKTYPE_USERDATA:
            break;
        case BLOCKTYPE_CONTRACT_EXECUTE:
            break;
        case BLOCKTYPE_ORDER_OPEN:
            connectOrder(block, blockStore);
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            connectCancelOrder(block, blockStore);
            break;
        case BLOCKTYPE_CONTRACT_EVENT:
            connectContractEvent(block, blockStore);
        default:
            break;

        }
    }

    private void connectCancelOrder(Block block, FullBlockStore blockStore) throws BlockStoreException {
        try {
            OrderCancelInfo info = new OrderCancelInfo().parse(block.getTransactions().get(0).getData());
            OrderCancel record = new OrderCancel(info.getBlockHash());
            record.setBlockHash(block.getHash());
            blockStore.insertCancelOrder(record);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void connectOrder(Block block, FullBlockStore blockStore) throws BlockStoreException {
        try {
            OrderOpenInfo reqInfo = new OrderOpenInfo().parse(block.getTransactions().get(0).getData());
            Coin burned = validatorService.countBurnedToken(block, blockStore);
            // calculate the offervalue for version == 1
            if (reqInfo.getVersion() == 1) {
                reqInfo.setOfferValue(burned.getValue().longValue());
                reqInfo.setOfferTokenid(burned.getTokenHex());
            }
            boolean buy = reqInfo.buy();
            Side side = buy ? Side.BUY : Side.SELL;
            int decimals = 0;
            if (buy) {
                decimals = blockStore.getTokenID(reqInfo.getTargetTokenid()).get(0).getDecimals();
            } else {
                decimals = blockStore.getTokenID(reqInfo.getOfferTokenid()).get(0).getDecimals();
            }
            OrderRecord record = new OrderRecord(block.getHash(), Sha256Hash.ZERO_HASH, burned.getValue().longValue(),
                    burned.getTokenHex(), false, false, null, reqInfo.getTargetValue(), reqInfo.getTargetTokenid(),
                    reqInfo.getBeneficiaryPubKey(), reqInfo.getValidToTime(), reqInfo.getValidFromTime(), side.name(),
                    reqInfo.getBeneficiaryAddress(), reqInfo.getOrderBaseToken(), reqInfo.getPrice(), decimals);
            versionPrice(record, reqInfo);
            List<OrderRecord> orders = new ArrayList<OrderRecord>();
            orders.add(record);
            blockStore.insertOrder(orders);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * price is in version 1 not in OrderOpenInfo
     */
    /*
     * Buy order is defined by given
     * targetValue=buy amount,
     * targetToken=buytoken
     * 
     * offervalue = targetValue * price / 10**targetDecimal
     * price= offervalue * 10**targetDecimal/targetValue
     * offerToken=orderBaseToken
     * 
     */
    /*
     * Sell Order is defined by given
     * offerValue= sell amount,
     * offentoken=sellToken
     * 
     * targetvalue = offervalue * price / 10**offerDecimal
     * targetToken=orderBaseToken
     */
    public void versionPrice(OrderRecord record, OrderOpenInfo reqInfo) {
        if (reqInfo.getVersion() == 1) {
            boolean buy = record.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING);
            if (buy) {
                record.setPrice(calc(record.getOfferValue(), LongMath.checkedPow(10, record.getTokenDecimals()),
                        record.getTargetValue()));
            } else {
                record.setPrice(calc(record.getTargetValue(), LongMath.checkedPow(10, record.getTokenDecimals()),
                        record.getOfferValue()));
            }
        }
    }

    public Long calc(long m, long factor, long d) {
        return BigInteger.valueOf(m).multiply(BigInteger.valueOf(factor)).divide(BigInteger.valueOf(d)).longValue();
    }

    private void connectContractEvent(Block block, FullBlockStore blockStore) throws BlockStoreException {
        try {
            ContractEventInfo reqInfo = new ContractEventInfo().parse(block.getTransactions().get(0).getData());

            ContractEventRecord record = new ContractEventRecord(block.getHash(), Sha256Hash.ZERO_HASH,
                    reqInfo.getContractTokenid(), false, false, null, reqInfo.getTargetValue(),
                    reqInfo.getTargetTokenid(), reqInfo.getBeneficiaryPubKey(), reqInfo.getValidToTime(),
                    reqInfo.getValidFromTime(), reqInfo.getBeneficiaryAddress());
            List<ContractEventRecord> orders = new ArrayList<ContractEventRecord>();
            orders.add(record);
            blockStore.insertContractEvent(orders);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void connectToken(Block block, FullBlockStore blockStore) throws BlockStoreException {
        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() != null) {
            try {
                byte[] buf = tx.getData();
                TokenInfo tokenInfo = new TokenInfo().parse(buf);

                // Correctly insert tokens
                tokenInfo.getToken().setConfirmed(false);
                tokenInfo.getToken().setBlockHash(block.getHash());

                blockStore.insertToken(block.getHash(), tokenInfo.getToken());

                // Correctly insert additional data
                for (MultiSignAddress permissionedAddress : tokenInfo.getMultiSignAddresses()) {
                    if (permissionedAddress == null)
                        continue;
                    // The primary key must be the correct block
                    permissionedAddress.setBlockhash(block.getHash());
                    permissionedAddress.setTokenid(tokenInfo.getToken().getTokenid());
                    if (permissionedAddress.getAddress() != null)
                        blockStore.insertMultiSignAddress(permissionedAddress);
                }
            } catch (IOException e) {

                throw new RuntimeException(e);
            }

        }
    }

    private void insertIntoOrderBooks(OrderRecord o, TreeMap<TradePair, OrderBook> orderBooks,
            ArrayList<OrderRecord> orderId2Order, long orderId, FullBlockStore blockStore) throws BlockStoreException {

        Side side = o.getSide();
        // must be in in base unit ;

        long price = o.getPrice();
        if (price <= 0)
            log.warn(" price is wrong " + price);
        // throw new RuntimeException(" price is wrong " +price);
        String tradetokenId = o.getOfferTokenid().equals(o.getOrderBaseToken()) ? o.getTargetTokenid()
                : o.getOfferTokenid();

        long size = o.getOfferTokenid().equals(o.getOrderBaseToken()) ? o.getTargetValue() : o.getOfferValue();

        TradePair tokenPaar = new TradePair(tradetokenId, o.getOrderBaseToken());

        OrderBook orderBook = orderBooks.get(tokenPaar);
        if (orderBook == null) {
            orderBook = new OrderBook(new OrderBookEvents());
            orderBooks.put(tokenPaar, orderBook);
        }
        orderId2Order.add(o);
        orderBook.enter(orderId, side, price, size);
    }

    /**
     * Deterministically execute the order matching algorithm on this block.
     * 
     * @return new consumed orders, virtual order matching tx and newly
     *         generated remaining MODIFIED order book
     * @throws BlockStoreException
     */
    public OrderMatchingResult generateOrderMatching(Block rewardBlock, FullBlockStore blockStore)
            throws BlockStoreException {

        RewardInfo rewardInfo = new RewardInfo().parseChecked(rewardBlock.getTransactions().get(0).getData());

        return generateOrderMatching(rewardBlock, rewardInfo, blockStore);
    }

    public OrderMatchingResult generateOrderMatching(Block block, RewardInfo rewardInfo, FullBlockStore blockStore)
            throws BlockStoreException {
        TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts = new TreeMap<>();

        // Get previous order matching block
        Sha256Hash prevHash = rewardInfo.getPrevRewardHash();
        Set<Sha256Hash> collectedBlocks = rewardInfo.getBlocks();
        final Block prevMatchingBlock = blockStore.getBlockWrap(prevHash).getBlock();

        // Deterministic randomization
        byte[] randomness = Utils.xor(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes());

        // Collect all orders approved by this block in the interval
        List<OrderCancelInfo> cancels = new ArrayList<>();
        Map<Sha256Hash, OrderRecord> sortedNewOrders = new TreeMap<>(
                Comparator.comparing(hash -> Sha256Hash.wrap(Utils.xor(((Sha256Hash) hash).getBytes(), randomness))));
        HashMap<Sha256Hash, OrderRecord> remainingOrders = blockStore.getOrderMatchingIssuedOrders(prevHash);
        Set<OrderRecord> toBeSpentOrders = new HashSet<>();
        Set<OrderRecord> cancelledOrders = new HashSet<>();
        for (OrderRecord r : remainingOrders.values()) {
            toBeSpentOrders.add(OrderRecord.cloneOrderRecord(r));
        }
        collectOrdersWithCancel(block, collectedBlocks, cancels, sortedNewOrders, toBeSpentOrders, blockStore);
        // sort order for execute in deterministic randomness
        Map<Sha256Hash, OrderRecord> sortedOldOrders = new TreeMap<>(
                Comparator.comparing(hash -> Sha256Hash.wrap(Utils.xor(((Sha256Hash) hash).getBytes(), randomness))));
        sortedOldOrders.putAll(remainingOrders);
        remainingOrders.putAll(sortedNewOrders);

        // Issue timeout cancels, set issuing order blockhash
        setIssuingBlockHash(block, remainingOrders);
        timeoutOrdersToCancelled(block, remainingOrders, cancelledOrders);
        cancelOrderstoCancelled(cancels, remainingOrders, cancelledOrders);

        // Remove the now cancelled orders from rest of orders
        for (OrderRecord c : cancelledOrders) {
            remainingOrders.remove(c.getBlockHash());
            sortedOldOrders.remove(c.getBlockHash());
            sortedNewOrders.remove(c.getBlockHash());
        }

        // Add to proceeds all cancelled orders going back to the beneficiary
        payoutCancelledOrders(payouts, cancelledOrders);

        // From all orders and ops, begin order matching algorithm by filling
        // order books
        int orderId = 0;
        ArrayList<OrderRecord> orderId2Order = new ArrayList<>();
        TreeMap<TradePair, OrderBook> orderBooks = new TreeMap<TradePair, OrderBook>();

        // Add old orders first without not valid yet
        for (OrderRecord o : sortedOldOrders.values()) {
            if (o.isValidYet(block.getTimeSeconds()) && o.isValidYet(prevMatchingBlock.getTimeSeconds()))
                insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
        }

        // Now orders not valid before but valid now
        for (OrderRecord o : sortedOldOrders.values()) {
            if (o.isValidYet(block.getTimeSeconds()) && !o.isValidYet(prevMatchingBlock.getTimeSeconds()))
                insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
        }

        // Now new orders that are valid yet
        for (OrderRecord o : sortedNewOrders.values()) {
            if (o.isValidYet(block.getTimeSeconds()))
                insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++, blockStore);
        }

        // Collect and process all matching events
        Map<TradePair, List<Event>> tokenId2Events = new HashMap<>();
        for (Entry<TradePair, OrderBook> orderBook : orderBooks.entrySet()) {
            processOrderBook(payouts, remainingOrders, orderId2Order, tokenId2Events, orderBook);
        }

        for (OrderRecord o : remainingOrders.values()) {
            o.setDefault();
        }

        // Make deterministic tx with proceeds
        Transaction tx = createOrderPayoutTransaction(block, payouts);
        // log.debug(tx.toString());
        return new OrderMatchingResult(toBeSpentOrders, tx, remainingOrders.values(), tokenId2Events);
    }

    private Transaction createOrderPayoutTransaction(Block block,
            TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts) {
        Transaction tx = new Transaction(networkParameters);
        for (Entry<ByteBuffer, TreeMap<String, BigInteger>> payout : payouts.entrySet()) {
            byte[] beneficiaryPubKey = payout.getKey().array();

            for (Entry<String, BigInteger> tokenProceeds : payout.getValue().entrySet()) {
                String tokenId = tokenProceeds.getKey();
                BigInteger proceedsValue = tokenProceeds.getValue();

                if (proceedsValue.signum() != 0)
                    tx.addOutput(new Coin(proceedsValue, tokenId), ECKey.fromPublicOnly(beneficiaryPubKey));
            }
        }

        // The coinbase input does not really need to be a valid signature
        TransactionInput input = new TransactionInput(networkParameters, tx, Script
                .createInputScript(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
        tx.addInput(input);
        tx.setMemo(new MemoInfo("Order Payout"));

        return tx;
    }

    private void processOrderBook(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
            HashMap<Sha256Hash, OrderRecord> remainingOrders, ArrayList<OrderRecord> orderId2Order,
            Map<TradePair, List<Event>> tokenId2Events, Entry<TradePair, OrderBook> orderBook)
            throws BlockStoreException {
        String orderBaseToken = orderBook.getKey().getOrderBaseToken();

        List<Event> events = ((OrderBookEvents) orderBook.getValue().listener()).collect();

        for (Event event : events) {
            if (!(event instanceof Match))
                continue;

            Match matchEvent = (Match) event;
            OrderRecord restingOrder = orderId2Order.get(Integer.parseInt(matchEvent.restingOrderId));
            OrderRecord incomingOrder = orderId2Order.get(Integer.parseInt(matchEvent.incomingOrderId));
            byte[] restingPubKey = restingOrder.getBeneficiaryPubKey();
            byte[] incomingPubKey = incomingOrder.getBeneficiaryPubKey();

            // Now disburse proceeds accordingly
            long executedPrice = matchEvent.price;
            long executedAmount = matchEvent.executedQuantity;
            // if
            // (!orderBook.getKey().getOrderToken().equals(incomingOrder.getTargetTokenid()))

            if (matchEvent.incomingSide == Side.BUY) {
                processIncomingBuy(payouts, remainingOrders, orderBaseToken, restingOrder, incomingOrder, restingPubKey,
                        incomingPubKey, executedPrice, executedAmount);
            } else {
                processIncomingSell(payouts, remainingOrders, orderBaseToken, restingOrder, incomingOrder,
                        restingPubKey, incomingPubKey, executedPrice, executedAmount);

            }
        }
        tokenId2Events.put(orderBook.getKey(), events);
    }

    private void processIncomingSell(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
            HashMap<Sha256Hash, OrderRecord> remainingOrders, String baseToken, OrderRecord restingOrder,
            OrderRecord incomingOrder, byte[] restingPubKey, byte[] incomingPubKey, long executedPrice,
            long executedAmount) {
        long sellableAmount = incomingOrder.getOfferValue();
        long buyableAmount = restingOrder.getTargetValue();
        long incomingPrice = incomingOrder.getPrice();
        Integer priceshift = networkParameters.getOrderPriceShift(baseToken);
        // The resting order receives the tokens
        payout(payouts, restingPubKey, restingOrder.getTargetTokenid(), executedAmount);

        // The incoming order receives the base token according to the
        // resting price
        payout(payouts, incomingPubKey, baseToken,
                totalAmount(executedAmount, executedPrice, incomingOrder.getTokenDecimals() + priceshift));

        // Finally, the orders could be fulfilled now, so we can
        // remove them from the order list
        // Otherwise, we will make the orders smaller by the
        // executed amounts
        incomingOrder.setOfferValue(incomingOrder.getOfferValue() - executedAmount);
        incomingOrder.setTargetValue(incomingOrder.getTargetValue()
                - totalAmount(executedAmount, incomingPrice, incomingOrder.getTokenDecimals() + priceshift));
        restingOrder.setOfferValue(restingOrder.getOfferValue()
                - totalAmount(executedAmount, executedPrice, restingOrder.getTokenDecimals() + priceshift));
        restingOrder.setTargetValue(restingOrder.getTargetValue() - executedAmount);
        if (sellableAmount == executedAmount) {
            remainingOrders.remove(incomingOrder.getBlockHash());
        }
        if (buyableAmount == executedAmount) {
            remainingOrders.remove(restingOrder.getBlockHash());
        }
    }

    private void processIncomingBuy(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
            HashMap<Sha256Hash, OrderRecord> remainingOrders, String baseToken, OrderRecord restingOrder,
            OrderRecord incomingOrder, byte[] restingPubKey, byte[] incomingPubKey, long executedPrice,
            long executedAmount) {
        long sellableAmount = restingOrder.getOfferValue();
        long buyableAmount = incomingOrder.getTargetValue();
        long incomingPrice = incomingOrder.getPrice();

        // The resting order receives the basetoken according to its price
        // resting is sell order
        Integer priceshift = networkParameters.getOrderPriceShift(baseToken);
        payout(payouts, restingPubKey, baseToken,
                totalAmount(executedAmount, executedPrice, restingOrder.getTokenDecimals() + priceshift));

        // The incoming order receives the tokens
        payout(payouts, incomingPubKey, incomingOrder.getTargetTokenid(), executedAmount);

        // The difference in price is returned to the incoming
        // beneficiary
        payout(payouts, incomingPubKey, baseToken, totalAmount(executedAmount, (incomingPrice - executedPrice),
                incomingOrder.getTokenDecimals() + priceshift));

        // Finally, the orders could be fulfilled now, so we can
        // remove them from the order list
        restingOrder.setOfferValue(restingOrder.getOfferValue() - executedAmount);
        restingOrder.setTargetValue(restingOrder.getTargetValue()
                - totalAmount(executedAmount, executedPrice, restingOrder.getTokenDecimals() + priceshift));
        incomingOrder.setOfferValue(incomingOrder.getOfferValue()
                - totalAmount(executedAmount, incomingPrice, incomingOrder.getTokenDecimals() + priceshift));
        incomingOrder.setTargetValue(incomingOrder.getTargetValue() - executedAmount);
        if (sellableAmount == executedAmount) {
            remainingOrders.remove(restingOrder.getBlockHash());
        }
        if (buyableAmount == executedAmount) {
            remainingOrders.remove(incomingOrder.getBlockHash());
        }
    }

    /*
     * It must use BigInteger to calculation to avoid overflow. Order can handle
     * only Long
     */
    public Long totalAmount(long price, long amount, int tokenDecimal) {

        BigInteger[] rearray = BigInteger.valueOf(price).multiply(BigInteger.valueOf(amount))
                .divideAndRemainder(BigInteger.valueOf(LongMath.checkedPow(10, tokenDecimal)));
        BigInteger re = rearray[0];
        BigInteger remainder = rearray[1];
        if (remainder.compareTo(BigInteger.ZERO) > 0) {
            // This remainder will cut
            // log.debug("Price and quantity value with remainder " + remainder
            // + "/"
            // + BigInteger.valueOf(LongMath.checkedPow(10, tokenDecimal)));
        }

        if (re.compareTo(BigInteger.ZERO) < 0 || re.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
            throw new InvalidTransactionDataException("Invalid target total value: " + re);
        }
        return re.longValue();
    }

    private void payoutCancelledOrders(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
            Set<OrderRecord> cancelledOrders) {
        for (OrderRecord o : cancelledOrders) {
            byte[] beneficiaryPubKey = o.getBeneficiaryPubKey();
            String offerTokenid = o.getOfferTokenid();
            long offerValue = o.getOfferValue();

            payout(payouts, beneficiaryPubKey, offerTokenid, offerValue);
        }
    }

    private void payout(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts, byte[] beneficiaryPubKey,
            String tokenid, long tokenValue) {
        TreeMap<String, BigInteger> proceeds = payouts.get(ByteBuffer.wrap(beneficiaryPubKey));
        if (proceeds == null) {
            proceeds = new TreeMap<>();
            payouts.put(ByteBuffer.wrap(beneficiaryPubKey), proceeds);
        }
        BigInteger offerTokenProceeds = proceeds.get(tokenid);
        if (offerTokenProceeds == null) {
            offerTokenProceeds = BigInteger.ZERO;
            proceeds.put(tokenid, offerTokenProceeds);
        }
        proceeds.put(tokenid, offerTokenProceeds.add(BigInteger.valueOf(tokenValue)));
    }

    private void cancelOrderstoCancelled(List<OrderCancelInfo> cancels,
            HashMap<Sha256Hash, OrderRecord> remainingOrders, Set<OrderRecord> cancelledOrders) {
        for (OrderCancelInfo c : cancels) {
            if (remainingOrders.containsKey(c.getBlockHash())) {
                cancelledOrders.add(remainingOrders.get(c.getBlockHash()));
            }
        }
    }

    private void setIssuingBlockHash(Block block, HashMap<Sha256Hash, OrderRecord> remainingOrders) {
        Iterator<Entry<Sha256Hash, OrderRecord>> it = remainingOrders.entrySet().iterator();
        while (it.hasNext()) {
            OrderRecord order = it.next().getValue();
            order.setIssuingMatcherBlockHash(block.getHash());
        }
    }

    private void timeoutOrdersToCancelled(Block block, HashMap<Sha256Hash, OrderRecord> remainingOrders,
            Set<OrderRecord> cancelledOrders) {
        // Issue timeout cancels, set issuing order blockhash
        Iterator<Entry<Sha256Hash, OrderRecord>> it = remainingOrders.entrySet().iterator();
        while (it.hasNext()) {
            OrderRecord order = it.next().getValue();
            if (order.isTimeouted(block.getTimeSeconds())) {
                cancelledOrders.add(order);
            }
        }
    }

    private void collectOrdersWithCancel(Block block, Set<Sha256Hash> collectedBlocks, List<OrderCancelInfo> cancels,
            Map<Sha256Hash, OrderRecord> newOrders, Set<OrderRecord> spentOrders, FullBlockStore blockStore)
            throws BlockStoreException {
        for (Sha256Hash bHash : collectedBlocks) {
            BlockWrap b = blockStore.getBlockWrap(bHash);
            if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OPEN) {
                final Sha256Hash blockHash = b.getBlock().getHash();
                OrderRecord order = blockStore.getOrder(blockHash, Sha256Hash.ZERO_HASH);
                newOrders.put(blockHash, OrderRecord.cloneOrderRecord(order));
                spentOrders.add(order);

            } else if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_CANCEL) {
                OrderCancelInfo info = new OrderCancelInfo()
                        .parseChecked(b.getBlock().getTransactions().get(0).getData());
                cancels.add(info);
            }
        }
    }

    /**
     * Deterministically creates a mining reward transaction based on the
     * previous blocks and previous reward transaction. DOES NOT CHECK FOR
     * SOLIDITY. You have to ensure that the approved blocks result in an
     * eligible reward block.
     * 
     * @return mining reward transaction
     * @throws BlockStoreException
     */
    public Transaction generateVirtualMiningRewardTX(Block block, FullBlockStore blockStore)
            throws BlockStoreException {

        RewardInfo rewardInfo = new RewardInfo().parseChecked(block.getTransactions().get(0).getData());
        Set<Sha256Hash> candidateBlocks = rewardInfo.getBlocks();

        // Count how many blocks from miners in the reward interval are approved
        // and build rewards
        Queue<BlockWrap> blockQueue = new PriorityQueue<BlockWrap>(
                Comparator.comparingLong((BlockWrap b) -> b.getBlockEvaluation().getHeight()).reversed());
        for (Sha256Hash bHash : candidateBlocks) {
            blockQueue.add(blockStore.getBlockWrap(bHash));
        }

        // Initialize
        Set<BlockWrap> currentHeightBlocks = new HashSet<>();
        Map<BlockWrap, Set<Sha256Hash>> snapshotWeights = new HashMap<>();
        Map<Address, Long> finalRewardCount = new HashMap<>();
        BlockWrap currentBlock = null, approvedBlock = null;
        Address consensusBlockMiner = new Address(networkParameters, block.getMinerAddress());
        long currentHeight = Long.MAX_VALUE;
        long totalRewardCount = 0;

        for (BlockWrap tip : blockQueue) {
            snapshotWeights.put(tip, new HashSet<>());
        }

        // Go backwards by height
        while ((currentBlock = blockQueue.poll()) != null) {

            // If we have reached a new height level, trigger payout
            // calculation
            if (currentHeight > currentBlock.getBlockEvaluation().getHeight()) {

                // Calculate rewards
                totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
                        totalRewardCount);

                // Finished with this height level, go to next level
                currentHeightBlocks.clear();
                long currentHeight_ = currentHeight;
                snapshotWeights.entrySet().removeIf(e -> e.getKey().getBlockEvaluation().getHeight() == currentHeight_);
                currentHeight = currentBlock.getBlockEvaluation().getHeight();
            }

            // Stop criterion: Block not in candidate list
            if (!candidateBlocks.contains(currentBlock.getBlockHash()))
                continue;

            // Add your own hash to approver hashes of current approver hashes
            snapshotWeights.get(currentBlock).add(currentBlock.getBlockHash());

            // Count the blocks of current height
            currentHeightBlocks.add(currentBlock);

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

        // Exception for height 0 (genesis): since prevblock does not exist,
        // finish payout
        // calculation
        if (currentHeight == 0) {
            totalRewardCount = calculateHeightRewards(currentHeightBlocks, snapshotWeights, finalRewardCount,
                    totalRewardCount);
        }

        // Build transaction outputs sorted by addresses
        Transaction tx = new Transaction(networkParameters);

        // Reward the consensus block with the static reward
        tx.addOutput(Coin.SATOSHI.times(NetworkParameters.REWARD_AMOUNT_BLOCK_REWARD), consensusBlockMiner);

        // Reward twice: once the consensus block, once the normal block maker
        // of good quantiles
        for (Entry<Address, Long> entry : finalRewardCount.entrySet().stream()
                .sorted(Comparator.comparing((Entry<Address, Long> e) -> e.getKey())).collect(Collectors.toList())) {
            tx.addOutput(Coin.SATOSHI.times(entry.getValue() * NetworkParameters.PER_BLOCK_REWARD),
                    consensusBlockMiner);
            tx.addOutput(Coin.SATOSHI.times(entry.getValue() * NetworkParameters.PER_BLOCK_REWARD), entry.getKey());
        }

        // The input does not really need to be a valid signature, as long
        // as it has the right general form and is slightly different for
        // different tx
        TransactionInput input = new TransactionInput(networkParameters, tx, Script
                .createInputScript(block.getPrevBlockHash().getBytes(), block.getPrevBranchBlockHash().getBytes()));
        tx.addInput(input);
        tx.setMemo(new MemoInfo("MiningRewardTX"));
        return tx;
    }

    // For each height, throw away anything below the 99-percentile
    // in terms of reduced weight
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

    public void updateConfirmedDo(TXReward maxConfirmedReward, FullBlockStore blockStore) throws BlockStoreException {

        // First remove any blocks that should no longer be in the milestone
        HashSet<BlockEvaluation> blocksToRemove = blockStore.getBlocksToUnconfirm();
        HashSet<Sha256Hash> traversedUnconfirms = new HashSet<>();
        for (BlockEvaluation block : blocksToRemove) {

            try {
                blockStore.beginDatabaseBatchWrite();
                unconfirm(block.getBlockHash(), traversedUnconfirms, blockStore);
                blockStore.commitDatabaseBatchWrite();
            } catch (Exception e) {
                blockStore.abortDatabaseBatchWrite();
                throw e;
            } finally {
                blockStore.defaultDatabaseBatchWrite();
            }
        }
        // TXReward maxConfirmedReward = blockStore.getMaxConfirmedReward();
        long cutoffHeight = blockService.getCurrentCutoffHeight(maxConfirmedReward, blockStore);
        long maxHeight = blockService.getCurrentMaxHeight(maxConfirmedReward, blockStore);

        // Now try to find blocks that can be added to the milestone.
        // DISALLOWS UNSOLID
        TreeSet<BlockWrap> blocksToAdd = blockStore.getBlocksToConfirm(cutoffHeight, maxHeight);

        // VALIDITY CHECKS
        validatorService.resolveAllConflicts(blocksToAdd, cutoffHeight, blockStore);

        // Finally add the resolved new blocks to the confirmed set
        HashSet<Sha256Hash> traversedConfirms = new HashSet<>();
        for (BlockWrap block : blocksToAdd) {
            try {
                blockStore.beginDatabaseBatchWrite();
                confirm(block.getBlockEvaluation().getBlockHash(), traversedConfirms, (long) -1, blockStore);
                blockStore.commitDatabaseBatchWrite();
            } catch (Exception e) {
                blockStore.abortDatabaseBatchWrite();
                throw e;
            } finally {
                blockStore.defaultDatabaseBatchWrite();
            }
        }

    }

    private void solidifyReward(Block block, FullBlockStore blockStore) throws BlockStoreException {

        RewardInfo rewardInfo = new RewardInfo().parseChecked(block.getTransactions().get(0).getData());
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        long currChainLength = blockStore.getRewardChainLength(prevRewardHash) + 1;
        long difficulty = rewardInfo.getDifficultyTargetReward();

        blockStore.insertReward(block.getHash(), prevRewardHash, difficulty, currChainLength);
    }

    public void updateConfirmed() throws BlockStoreException {
        String LOCKID = "chain";
        int LockTime = 1000000;
        FullBlockStore store = storeService.getStore();
        try {
            // log.info("create Reward started");
            LockObject lock = store.selectLockobject(LOCKID);
            boolean canrun = false;
            if (lock == null) {
                try {
                    store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
                    canrun = true;
                } catch (Exception e) {
                    // ignore
                }
            } else {
                if (lock.getLocktime() < System.currentTimeMillis() - LockTime) {
                    store.deleteLockobject(LOCKID);
                    store.insertLockobject(new LockObject(LOCKID, System.currentTimeMillis()));
                    canrun = true;
                } else {
                    if (lock.getLocktime() < System.currentTimeMillis() - 2000)
                        log.info("updateConfirmed running start = " + Utils.dateTimeFormat(lock.getLocktime()));
                }
            }
            if (canrun) {
                Stopwatch watch = Stopwatch.createStarted();
                TXReward maxConfirmedReward = store.getMaxConfirmedReward();
                updateConfirmedDo(maxConfirmedReward, store);
                if (watch.elapsed(TimeUnit.MILLISECONDS) > 1000) {
                    log.info("updateConfirmedDo time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
                }

             cleanUp(maxConfirmedReward, store);
                if (watch.elapsed(TimeUnit.MILLISECONDS) > 1000) {
                    log.info("cleanUp time {} ms.", watch.elapsed(TimeUnit.MILLISECONDS));
                }

                store.deleteLockobject(LOCKID);
                watch.stop();
            }
        } catch (Exception e) {
            store.deleteLockobject(LOCKID);
            log.info(" ", e);
            throw e;
        } finally {
            if (store != null)
                store.close();
        }
    }

    public void cleanUp(TXReward maxConfirmedReward, FullBlockStore store) throws BlockStoreException {
    //TODO    cleanUpDo(maxConfirmedReward, store);
    }
    public void cleanUpDo(TXReward maxConfirmedReward, FullBlockStore store) throws BlockStoreException {

        Block rewardblock = store.get(maxConfirmedReward.getBlockHash());
        log.info(" prunedClosedOrders until block " +""+  rewardblock );
        store.prunedClosedOrders(rewardblock.getTimeSeconds());
        //max keep 500 blockchain as spendblock number
      //  store.prunedHistoryUTXO(rewardblock.getLastMiningRewardBlock()- 500);
        // store.prunedPriceTicker(rewardblock.getTimeSeconds() - 30 *
        // DaySeconds);

    }

    private void cleanupBlocks(FullBlockStore store, Block rewardblock) throws BlockStoreException {
        RewardInfo currRewardInfo = new RewardInfo().parseChecked(rewardblock.getTransactions().get(0).getData());

        long cutoffHeight = blockService.getRewardCutoffHeight(currRewardInfo.getPrevRewardHash(), store);
        // NetworkParameters.INTERVAL is used for difficulty calculation
        store.prunedBlocks(cutoffHeight - 1000,
                rewardblock.getLastMiningRewardBlock() - 1 - NetworkParameters.INTERVAL);
    }

    /*
     * run timeboxed updateConfirmed, there is no transaction here.
     * Timeout will cancel the rest of update confirm and can be update from
     * next run
     */
    public void updateConfirmedTimeBoxed() throws BlockStoreException {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        @SuppressWarnings({ "unchecked", "rawtypes" })
        final Future<String> handler = executor.submit(new Callable() {
            @Override
            public String call() throws Exception {
                updateConfirmed();
                return "";
            }
        });
        try {
            handler.get(3000l, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.info("Timeout cancel updateConfirmed ");
            handler.cancel(true);
        } catch (Exception e) {
            // ignore
            log.info("updateConfirmed", e);
        } finally {
            executor.shutdownNow();
        }

    }

}
