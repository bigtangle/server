/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.math.BigInteger;
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
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
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
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenType;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.ScriptException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.GenericInvalidityException;
import net.bigtangle.core.exception.VerificationException.UnsolidException;
import net.bigtangle.core.ordermatch.OrderBookEvents;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;
import net.bigtangle.core.ordermatch.OrderBookEvents.Match;
import net.bigtangle.script.Script;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.core.BlockWrap;
import net.bigtangle.server.service.OrderTickerService;
import net.bigtangle.server.service.RewardService;
import net.bigtangle.server.service.SolidityState;
import net.bigtangle.server.service.SolidityState.State;
import net.bigtangle.server.service.ValidatorService;
import net.bigtangle.server.utils.OrderBook;

/**
 * <p>
 * A FullPrunedBlockChain works in conjunction with a
 * {@link FullPrunedBlockStore} to verify all the rules of the BigTangle system.
 * </p>
 */
@Service
public class FullPrunedBlockGraph extends AbstractBlockGraph {

    private static final Logger log = LoggerFactory.getLogger(FullPrunedBlockGraph.class);

    protected final FullPrunedBlockStore blockStore;

    @Autowired
    public FullPrunedBlockGraph(NetworkParameters networkParameters, FullPrunedBlockStore blockStore)
            throws BlockStoreException {

        super(Context.create(networkParameters), blockStore);
        this.blockStore = blockStore;
        this.networkParameters = networkParameters;
    }

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

    private void solidifyReward(Block block) throws BlockStoreException {

        RewardInfo rewardInfo = RewardInfo.parseChecked(block.getTransactions().get(0).getData());
        Sha256Hash prevRewardHash = rewardInfo.getPrevRewardHash();
        long currChainLength = blockStore.getRewardChainLength(prevRewardHash) + 1;
        long difficulty = rewardService.calculateNextChainDifficulty(prevRewardHash, currChainLength,
                block.getTimeSeconds());

        blockStore.insertReward(block.getHash(), prevRewardHash, difficulty, currChainLength);
    }

    public boolean add(Block block, boolean allowUnsolid) throws BlockStoreException {
        boolean a;
        if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
            chainlock.lock();
            try {
                a = addChain(block, allowUnsolid, true);
            } finally {
                chainlock.unlock();
            }
        } else {
            a = addNonChain(block, allowUnsolid);
        }

        return a;
    }

    public boolean addChain(Block block, boolean allowUnsolid, boolean tryConnecting) throws BlockStoreException {

        // Check the block is partially formally valid and fulfills PoW
        block.verifyHeader();
        block.verifyTransactions();

        SolidityState solidityState = validatorService.checkChainSolidity(block, !allowUnsolid);

        if (solidityState.isDirectlyMissing()) {
            log.warn("Block does not connect: {} prev {}", block, block.getPrevBlockHash());
            orphanBlocks.put(block.getHash(), new OrphanBlock(block));
            if (tryConnecting) {
                tryConnectingOrphans();
            }
            return false;
        }

        if (solidityState.isFailState()) {
            return false;
        }

        // Inherit solidity from predecessors if they are not solid
        solidityState = validatorService.getMinPredecessorSolidity(block, false);

        // Sanity check
        if (solidityState.isFailState() || solidityState.getState() == State.MissingPredecessor) {
            return false;
        }

        // save the block
        try {
            blockStore.beginDatabaseBatchWrite();
            connectRewardBlock(block, solidityState);
            blockStore.commitDatabaseBatchWrite();
        } catch (Exception e) {
            blockStore.abortDatabaseBatchWrite();
            throw e;
        } finally {
            blockStore.defaultDatabaseBatchWrite();
        }
        if (tryConnecting)
            tryConnectingOrphans();

        return true;
    }

    public boolean addNonChain(Block block, boolean allowUnsolid) throws BlockStoreException {

        // Check the block is partially formally valid and fulfills PoW

        block.verifyHeader();
        block.verifyTransactions();

        SolidityState solidityState = validatorService.checkSolidity(block, !allowUnsolid);

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
            connect(block, solidityState);
            blockStore.commitDatabaseBatchWrite();
        } catch (Exception e) {
            blockStore.abortDatabaseBatchWrite();
            throw e;
        } finally {
            blockStore.defaultDatabaseBatchWrite();
        }
        return true;
    }

    private void connectRewardBlock(final Block block, SolidityState solidityState)
            throws BlockStoreException, VerificationException {

        if (solidityState.isFailState()) {
            connect(block, solidityState);
            return;
        }
        Block head = blockStore.get(blockStore.getMaxConfirmedReward().getBlockHash());
        if (block.getRewardInfo().getPrevRewardHash().equals(head.getHash())) {
            connect(block, solidityState);
            rewardService.buildRewardChain(block);
        } else {
            // This block connects to somewhere other than the top of the best
            // known chain. We treat these differently.

            boolean haveNewBestChain = block.getRewardInfo().getChainlength() > head.getRewardInfo().getChainlength();
            // TODO check this
            // block.getRewardInfo().moreWorkThan(head.getRewardInfo());
            if (haveNewBestChain) {
                log.info("Block is causing a re-organize");
                connect(block, solidityState);
                rewardService.handleNewBestChain(block);
            } else {
                // parallel chain, save as unconfirmed
                connect(block, solidityState);
                solidifyBlock(block, solidityState, false);
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
    private void connect(final Block block, SolidityState solidityState)
            throws BlockStoreException, VerificationException {

        blockStore.put(block);
        solidifyBlock(block, solidityState, false);
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
    private void synchronizationVOSData(byte[] data) throws BlockStoreException {
        String jsonStr = new String(data);
        HashMap<String, Object> map;
        try {
            map = Json.jsonmapper().readValue(jsonStr, HashMap.class);
        } catch (IOException e) {
            throw new BlockStoreException(e);
        }
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
     * Adds the specified block and all approved blocks to the confirmed set.
     * This will connect all transactions of the block by marking used UTXOs
     * spent and adding new UTXOs to the db.
     * 
     * @param blockHash
     * @param cutoffHeight
     * @param milestoneNumber
     * @throws BlockStoreException
     * @throws IOException
     * @throws JsonMappingException
     * @throws JsonParseException
     */
    public void confirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes, long cutoffHeight,
            long milestoneNumber) throws BlockStoreException {
        // If already confirmed, return
        if (traversedBlockHashes.contains(blockHash))
            return;

        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        // Block block = blockWrap.getBlock();

        // Cutoff
        if (blockEvaluation.getHeight() <= cutoffHeight)
            return;

        // If already confirmed, return
        if (blockEvaluation.isConfirmed())
            return;

        // Set confirmed
        blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), true);
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), milestoneNumber);

        // Connect all approved blocks first if not traversed already
        // Set<Sha256Hash> allRequiredBlockHashes =
        // blockService.getAllRequiredBlockHashes(block);
        // for (Sha256Hash req : allRequiredBlockHashes) {
        // confirmUntil(req, traversedBlockHashes, cutoffHeight);
        // }

        // Confirm the block
        confirmBlock(blockWrap);

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
    public Optional<OrderMatchingResult> calculateBlock(Block block) throws BlockStoreException {

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
            tx = generateVirtualMiningRewardTX(block);
            insertVirtualUTXOs(block, tx);

            // Get list of consumed orders, virtual order matching tx and newly
            // generated remaining order book
            matchingResult = generateOrderMatching(block);
            tx = matchingResult.getOutputTx();

            insertVirtualUTXOs(block, tx);
            insertVirtualOrderRecords(block, matchingResult.getRemainingOrders());
            break;
        case BLOCKTYPE_TOKEN_CREATION:
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
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }

        // Return the computation result
        return Optional.ofNullable(matchingResult);
    }

    private void confirmBlock(BlockWrap block) throws BlockStoreException {

        // Update block's transactions in db
        for (final Transaction tx : block.getBlock().getTransactions()) {
            confirmTransaction(block, tx);
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
            confirmReward(block);
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
            confirmVOSOrUserData(block);
            break;
        case BLOCKTYPE_VOS_EXECUTE:
            confirmVOSExecute(block);
            break;
        case BLOCKTYPE_ORDER_OPEN:
            confirmOrderOpen(block);
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void confirmVOSOrUserData(BlockWrap block) {
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
                this.synchronizationUserData(block.getBlock().getHash(), DataClassName.valueOf(tx.getDataClassName()),
                        tx.getData(), (String) multiSignBy.get("publickey"), block.getBlock().getBlockType().ordinal());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void confirmVOSExecute(BlockWrap block) throws BlockStoreException {
        Transaction tx1 = block.getBlock().getTransactions().get(0);
        if (tx1.getData() != null && tx1.getDataSignature() != null) {

            List<HashMap<String, Object>> multiSignBies;
            try {
                multiSignBies = Json.jsonmapper().readValue(tx1.getDataSignature(), List.class);
            } catch (IOException e) {
                throw new BlockStoreException(e);
            }
            Map<String, Object> multiSignBy = multiSignBies.get(0);
            byte[] pubKey = Utils.HEX.decode((String) multiSignBy.get("publickey"));
            byte[] data = tx1.getHash().getBytes();
            byte[] signature = Utils.HEX.decode((String) multiSignBy.get("signature"));
            boolean success = ECKey.verify(data, signature, pubKey);
            if (!success) {
                throw new BlockStoreException("multisign signature error");
            }
            this.synchronizationVOSData(tx1.getData());

        }
    }

    private void confirmOrderMatching(BlockWrap block) throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        // TODO don't calculate again, it should already have been calculated
        // before
        OrderMatchingResult actualCalculationResult = generateOrderMatching(block.getBlock());

        // All consumed order records are now spent by this block

        for (OrderRecord o : actualCalculationResult.getSpentOrders()) {
            o.setSpent(true);
            o.setSpenderBlockHash(block.getBlock().getHash());
        }
        blockStore.updateOrderSpent(actualCalculationResult.getSpentOrders());

        // Set virtual outputs confirmed
        confirmVirtualCoinbaseTransaction(block);

        // Set new orders confirmed

        blockStore.updateOrderConfirmed(actualCalculationResult.getRemainingOrders());

        // Update the matching history in db
        tickerService.addMatchingEvents(actualCalculationResult,
                actualCalculationResult.getOutputTx().getHashAsString(), block.getBlock().getTimeSeconds());
    }

    private void confirmOrderOpen(BlockWrap block) throws BlockStoreException {
        // Set own output confirmed
        blockStore.updateOrderConfirmed(block.getBlock().getHash(), Sha256Hash.ZERO_HASH, true);
    }

    private void confirmReward(BlockWrap block) throws BlockStoreException {
        // Set virtual reward tx outputs confirmed
        confirmVirtualCoinbaseTransaction(block);

        // Set used other output spent
        blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getBlock().getHash()), true,
                block.getBlock().getHash());

        // Set own output confirmed
        blockStore.updateRewardConfirmed(block.getBlock().getHash(), true);
    }

    private void insertVirtualOrderRecords(Block block, Collection<OrderRecord> orders) {
        try {

            blockStore.insertOrder(orders);

        } catch (BlockStoreException e) {
            // Expected after reorgs
            log.warn("Probably reinserting orders: ", e);
        }
    }

    private void insertVirtualUTXOs(Block block, Transaction virtualTx) {
        try {
            ArrayList<Transaction> txs = new ArrayList<Transaction>();
            txs.add(virtualTx);
            connectUTXOs(block, txs);
        } catch (BlockStoreException e) {
            // Expected after reorgs
            log.warn("Probably reinserting reward: ", e);
        }
    }

    private void confirmToken(BlockWrap block) throws BlockStoreException {
        // Set used other output spent
        if (blockStore.getTokenPrevblockhash(block.getBlock().getHash()) != null)
            blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(block.getBlock().getHash()), true,
                    block.getBlock().getHash());

        // Set own output confirmed
        blockStore.updateTokenConfirmed(block.getBlock().getHash(), true);
    }

    private void confirmTransaction(BlockWrap block, Transaction tx) throws BlockStoreException {
        // Set used other outputs spent
        if (!tx.isCoinBase()) {
            for (TransactionInput in : tx.getInputs()) {
                UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getBlockHash(),
                        in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());

                // Sanity check
                if (prevOut == null)
                    throw new RuntimeException("Attempted to spend a non-existent output!");
                if (prevOut.isSpent())
                    throw new RuntimeException("Attempted to spend an already spent output!");

                blockStore.updateTransactionOutputSpent(prevOut.getBlockHash(), prevOut.getTxHash(), prevOut.getIndex(),
                        true, block.getBlockHash());
            }
        }

        // Set own outputs confirmed
        for (TransactionOutput out : tx.getOutputs()) {
            blockStore.updateTransactionOutputConfirmed(block.getBlockHash(), tx.getHash(), out.getIndex(), true);
        }
    }

    private void confirmVirtualCoinbaseTransaction(BlockWrap block) throws BlockStoreException {
        // Set own outputs confirmed
        blockStore.updateAllTransactionOutputsConfirmed(block.getBlock().getHash(), true);
    }

    public void unconfirm(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
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
        unconfirmBlockOutputs(block);

        // Set unconfirmed
        blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), -1);

        // Keep track of unconfirmed blocks
        traversedBlockHashes.add(blockHash);
    }

    public void unconfirmRecursive(Sha256Hash blockHash, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {
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
        unconfirmDependents(block, traversedBlockHashes);

        // Then unconfirm the block itself
        unconfirmBlockOutputs(block);

        // Set unconfirmed
        blockStore.updateBlockEvaluationConfirmed(blockEvaluation.getBlockHash(), false);
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), -1);

        // Keep track of unconfirmed blocks
        traversedBlockHashes.add(blockHash);
    }

    private void unconfirmDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes) throws BlockStoreException {
        // Unconfirm all approver blocks first
        for (Sha256Hash approver : blockStore.getSolidApproverBlockHashes(block.getHash())) {
            unconfirmRecursive(approver, traversedBlockHashes);
        }

        // Disconnect all transaction output dependents
        for (Transaction tx : block.getTransactions()) {
            for (TransactionOutput txout : tx.getOutputs()) {
                UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
                if (utxo.isSpent()) {
                    unconfirmRecursive(
                            blockStore.getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex())
                                    .getBlockHash(),
                            traversedBlockHashes);
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
            unconfirmOrderMatchingDependents(block, traversedBlockHashes);
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
            unconfirmOrderOpenDependents(block, traversedBlockHashes);
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void unconfirmOrderMatchingDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        OrderMatchingResult matchingResult = generateOrderMatching(block);

        // Disconnect all virtual transaction output dependents
        Transaction tx = matchingResult.getOutputTx();
        ;
        for (TransactionOutput txout : tx.getOutputs()) {
            UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
            if (utxo != null && utxo.isSpent()) {
                unconfirmRecursive(blockStore
                        .getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
                        traversedBlockHashes);
            }
        }
    }

    private void unconfirmOrderOpenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {

        // Disconnect order record spender
        if (blockStore.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH)) {
            unconfirmRecursive(blockStore.getOrderSpender(block.getHash(), Sha256Hash.ZERO_HASH), traversedBlockHashes);
        }
    }

    private void unconfirmTokenDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {

        // Disconnect token record spender
        if (blockStore.getTokenSpent(block.getHash())) {
            unconfirmRecursive(blockStore.getTokenSpender(block.getHashAsString()), traversedBlockHashes);
        }

        // If applicable: Disconnect all domain definitions that were based on
        // this domain
        Token token = blockStore.getTokenByBlockHash(block.getHash());
        if (token.getTokentype() == TokenType.domainname.ordinal()) {
            List<String> dependents = blockStore.getDomainDescendantConfirmedBlocks(token.getDomainNameBlockHash());
            for (String b : dependents) {
                unconfirmRecursive(Sha256Hash.wrap(b), traversedBlockHashes);
            }
        }
    }

    private void unconfirmRewardDependents(Block block, HashSet<Sha256Hash> traversedBlockHashes)
            throws BlockStoreException {

        // Disconnect reward record spender
        if (blockStore.getRewardSpent(block.getHash())) {
            unconfirmRecursive(blockStore.getRewardSpender(block.getHash()), traversedBlockHashes);
        }

        // Disconnect all virtual transaction output dependents
        Transaction tx = generateVirtualMiningRewardTX(block);
        for (TransactionOutput txout : tx.getOutputs()) {
            UTXO utxo = blockStore.getTransactionOutput(block.getHash(), tx.getHash(), txout.getIndex());
            if (utxo != null && utxo.isSpent()) {
                unconfirmRecursive(blockStore
                        .getTransactionOutputSpender(block.getHash(), tx.getHash(), txout.getIndex()).getBlockHash(),
                        traversedBlockHashes);
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
    private void unconfirmBlockOutputs(Block block) throws BlockStoreException {
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
            unconfirmOrderMatching(block);
            break;
        case BLOCKTYPE_TOKEN_CREATION:
            unconfirmToken(block);
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
            unconfirmOrderOpen(block);
            break;
        case BLOCKTYPE_ORDER_CANCEL:
            break;
        default:
            throw new RuntimeException("Not Implemented");

        }
    }

    private void unconfirmOrderMatching(Block block) throws BlockStoreException {
        // Get list of consumed orders, virtual order matching tx and newly
        // generated remaining order book
        OrderMatchingResult matchingResult = generateOrderMatching(block);

        // All consumed order records are now unspent by this block
        for (OrderRecord o : matchingResult.getSpentOrders()) {
            blockStore.updateOrderSpent(o.getBlockHash(), o.getIssuingMatcherBlockHash(), false, null);
        }

        // Set virtual outputs unconfirmed
        unconfirmVirtualCoinbaseTransaction(block);

        // Set new orders unconfirmed
        for (OrderRecord o : matchingResult.getRemainingOrders())
            blockStore.updateOrderConfirmed(o.getBlockHash(), o.getIssuingMatcherBlockHash(), false);

        // Update the matching history in db
        tickerService.removeMatchingEvents(matchingResult.getOutputTx(), matchingResult.tokenId2Events);
    }

    private void unconfirmOrderOpen(Block block) throws BlockStoreException {
        // Set own output unconfirmed
        blockStore.updateOrderConfirmed(block.getHash(), Sha256Hash.ZERO_HASH, false);
    }

    private void unconfirmReward(Block block) throws BlockStoreException {
        // Unconfirm virtual tx
        unconfirmVirtualCoinbaseTransaction(block);

        // Set used other output unspent
        blockStore.updateRewardSpent(blockStore.getRewardPrevBlockHash(block.getHash()), false, null);

        // Set own output unconfirmed
        blockStore.updateRewardConfirmed(block.getHash(), false);
    }

    private void unconfirmToken(Block block) throws BlockStoreException {
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
    private void unconfirmTransaction(Transaction tx, Block parentBlock) throws BlockStoreException {
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

    private void unconfirmVirtualCoinbaseTransaction(Block parentBlock) throws BlockStoreException {
        // Set own outputs unconfirmed
        blockStore.updateAllTransactionOutputsConfirmed(parentBlock.getHash(), false);
    }

    public void solidifyBlock(Block block, SolidityState solidityState, boolean setMilestoneSuccess)
            throws BlockStoreException {

        switch (solidityState.getState()) {
        case MissingCalculation:
            blockStore.updateBlockEvaluationSolid(block.getHash(), 1);

            // Reward blocks follow different logic: If this is new, run
            // consensus logic
            if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
                solidifyReward(block);
                return;
            }
            // Insert other blocks into waiting list
            insertUnsolidBlock(block, solidityState);
            break;
        case MissingPredecessor:
            if (block.getBlockType() == Type.BLOCKTYPE_INITIAL
                    && blockStore.getBlockWrap(block.getHash()).getBlockEvaluation().getSolid() > 0) {
                throw new RuntimeException("Should not happen");
            }

            blockStore.updateBlockEvaluationSolid(block.getHash(), 0);

            // Insert into waiting list
            insertUnsolidBlock(block, solidityState);
            break;
        case Success:
            // If already set, nothing to do here...
            if (blockStore.getBlockEvaluation(block.getHash()).getSolid() == 2)
                return;

            // TODO don't calculate again, it may already have been calculated
            // before
            connectUTXOs(block);
            connectTypeSpecificUTXOs(block);
            calculateBlock(block);

            if (block.getBlockType() == Type.BLOCKTYPE_REWARD && !setMilestoneSuccess) {
                // If we don't want to set the milestone success, initialize as
                // missing calc
                blockStore.updateBlockEvaluationSolid(block.getHash(), 1);
            } else {
                // Else normal update
                blockStore.updateBlockEvaluationSolid(block.getHash(), 2);
            }
            if (block.getBlockType() == Type.BLOCKTYPE_REWARD) {
                solidifyReward(block);
                return;
            }

            break;
        case Invalid:

            blockStore.updateBlockEvaluationSolid(block.getHash(), -1);
            break;
        }
    }

    protected void insertUnsolidBlock(Block block, SolidityState solidityState) throws BlockStoreException {
        if (solidityState.getState() == State.Success || solidityState.getState() == State.Invalid) {
            log.warn("Block should not be inserted into waiting list");
            return;
        }

        // Insert waiting into solidity waiting queue until dependency is
        // resolved
        blockStore.insertUnsolid(block, solidityState);
    }

    protected void connectUTXOs(Block block) throws BlockStoreException, VerificationException {
        List<Transaction> transactions = block.getTransactions();
        connectUTXOs(block, transactions);
    }

    private void connectUTXOs(Block block, List<Transaction> transactions) throws BlockStoreException {
        for (final Transaction tx : transactions) {
            boolean isCoinBase = tx.isCoinBase();
            if (!isCoinBase) {
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    TransactionInput in = tx.getInputs().get(index);
                    UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getBlockHash(),
                            in.getOutpoint().getTxHash(), in.getOutpoint().getIndex());
                    if (prevOut != null)
                        blockStore.updateTransactionOutputSpendPending(prevOut.getBlockHash(), prevOut.getTxHash(),
                                prevOut.getIndex(), true, System.currentTimeMillis());
                }
            }
            List<UTXO> utxos = new ArrayList<UTXO>();
            for (TransactionOutput out : tx.getOutputs()) {
                Script script = getScript(out.getScriptBytes());
                String fromAddress = "";
                try {
                    if (!isCoinBase) {
                        fromAddress = tx.getInputs().get(0).getFromAddress().toBase58();
                    }
                } catch (ScriptException e) {
                    // No address found.
                }
                int minsignnumber = 1;
                if (script.isSentToMultiSig()) {
                    minsignnumber = script.getNumberOfSignaturesRequiredToSpend();
                }
                UTXO newOut = new UTXO(tx.getHash(), out.getIndex(), out.getValue(), isCoinBase, script,
                        getScriptAddress(script), block.getHash(), fromAddress, tx.getMemo(),
                        Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, minsignnumber, 0);
                newOut.setTime(System.currentTimeMillis() / 1000);
                if (!newOut.isZero()) {
                    utxos.add(newOut);
                    if (script.isSentToMultiSig()) {

                        for (ECKey ecKey : script.getPubKeys()) {
                            String toaddress = ecKey.toAddress(params).toBase58();
                            OutputsMulti outputsMulti = new OutputsMulti(newOut.getTxHash(), toaddress,
                                    newOut.getIndex());
                            this.blockStore.insertOutputsMulti(outputsMulti);
                        }
                    }
                }
            }
            blockStore.addUnspentTransactionOutput(utxos);
        }
    }

    private void connectTypeSpecificUTXOs(Block block) throws BlockStoreException {
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
        case BLOCKTYPE_ORDER_CANCEL:
            connectCancelOrder(block);
            break;
        default:
            break;

        }
    }

    private void connectCancelOrder(Block block) throws BlockStoreException {
        try {
            OrderCancelInfo info = OrderCancelInfo.parse(block.getTransactions().get(0).getData());
            OrderCancel record = new OrderCancel(info.getBlockHash());
            record.setBlockHash(block.getHash());
            blockStore.insertCancelOrder(record);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void connectOrder(Block block) throws BlockStoreException {
        try {
            OrderOpenInfo reqInfo = OrderOpenInfo.parse(block.getTransactions().get(0).getData());

            Coin offer = validatorService.countBurnedToken(block);
            Side side = offer.getTokenHex().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING) ? Side.BUY : Side.SELL;

            OrderRecord record = new OrderRecord(block.getHash(), Sha256Hash.ZERO_HASH, offer.getValue().longValue(),
                    offer.getTokenHex(), false, false, null, reqInfo.getTargetValue(), reqInfo.getTargetTokenid(),
                    reqInfo.getBeneficiaryPubKey(), reqInfo.getValidToTime(), reqInfo.getValidFromTime(), side.name(),
                    reqInfo.getBeneficiaryAddress());
            List<OrderRecord> orders = new ArrayList<OrderRecord>();
            orders.add(record);
            blockStore.insertOrder(orders);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void connectToken(Block block) throws BlockStoreException {
        Transaction tx = block.getTransactions().get(0);
        if (tx.getData() != null) {
            try {
                byte[] buf = tx.getData();
                TokenInfo tokenInfo = TokenInfo.parse(buf);

                // Correctly insert tokens
                tokenInfo.getToken().setConfirmed(false);
                tokenInfo.getToken().setBlockHash(block.getHash());

                this.blockStore.insertToken(block.getHash(), tokenInfo.getToken());

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

    private void insertIntoOrderBooks(OrderRecord o, TreeMap<String, OrderBook> orderBooks,
            ArrayList<OrderRecord> orderId2Order, long orderId) {

        Side side = o.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING) ? Side.BUY : Side.SELL;
        long price = o.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING)
                ? o.getOfferValue() / o.getTargetValue()
                : o.getTargetValue() / o.getOfferValue();
        long size = o.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING) ? o.getTargetValue()
                : o.getOfferValue();
        String tokenId = o.getOfferTokenid().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING) ? o.getTargetTokenid()
                : o.getOfferTokenid();

        OrderBook orderBook = orderBooks.get(tokenId);
        if (orderBook == null) {
            orderBook = new OrderBook(new OrderBookEvents());
            orderBooks.put(tokenId, orderBook);
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
    public OrderMatchingResult generateOrderMatching(Block block) throws BlockStoreException {

        RewardInfo rewardInfo = RewardInfo.parseChecked(block.getTransactions().get(0).getData());

        return generateOrderMatching(block, rewardInfo);
    }

    public OrderMatchingResult generateOrderMatching(Block block, RewardInfo rewardInfo) throws BlockStoreException {
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
        collectOrdersWithCancel(block, collectedBlocks, cancels, sortedNewOrders, toBeSpentOrders);
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
        TreeMap<String, OrderBook> orderBooks = new TreeMap<String, OrderBook>();

        // Add old orders first without not valid yet
        for (OrderRecord o : sortedOldOrders.values()) {
            if (o.isValidYet(block.getTimeSeconds()) && o.isValidYet(prevMatchingBlock.getTimeSeconds()))
                insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++);
        }

        // Now orders not valid before but valid now
        for (OrderRecord o : sortedOldOrders.values()) {
            if (o.isValidYet(block.getTimeSeconds()) && !o.isValidYet(prevMatchingBlock.getTimeSeconds()))
                insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++);
        }

        // Now new orders that are valid yet
        for (OrderRecord o : sortedNewOrders.values()) {
            if (o.isValidYet(block.getTimeSeconds()))
                insertIntoOrderBooks(o, orderBooks, orderId2Order, orderId++);
        }

        // Collect and process all matching events
        Map<String, List<Event>> tokenId2Events = new HashMap<>();
        for (Entry<String, OrderBook> orderBook : orderBooks.entrySet()) {
            processOrderBook(payouts, remainingOrders, orderId2Order, tokenId2Events, orderBook);
        }

        for (OrderRecord o : remainingOrders.values())
            o.setDefault();

        // Make deterministic tx with proceeds
        Transaction tx = createOrderPayoutTransaction(block, payouts);
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
        return tx;
    }

    private void processOrderBook(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
            HashMap<Sha256Hash, OrderRecord> remainingOrders, ArrayList<OrderRecord> orderId2Order,
            Map<String, List<Event>> tokenId2Events, Entry<String, OrderBook> orderBook) {
        String tokenId = orderBook.getKey();
        List<Event> events = ((OrderBookEvents) orderBook.getValue().listener()).collect();
        tokenId2Events.put(tokenId, events);

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

            if (matchEvent.incomingSide == Side.BUY) {
                processIncomingBuy(payouts, remainingOrders, tokenId, restingOrder, incomingOrder, restingPubKey,
                        incomingPubKey, executedPrice, executedAmount);

            } else {
                processIncomingSell(payouts, remainingOrders, tokenId, restingOrder, incomingOrder, restingPubKey,
                        incomingPubKey, executedPrice, executedAmount);
            }
        }
    }

    private void processIncomingSell(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
            HashMap<Sha256Hash, OrderRecord> remainingOrders, String tokenId, OrderRecord restingOrder,
            OrderRecord incomingOrder, byte[] restingPubKey, byte[] incomingPubKey, long executedPrice,
            long executedAmount) {
        long sellableAmount = incomingOrder.getOfferValue();
        long buyableAmount = restingOrder.getTargetValue();
        long incomingPrice = incomingOrder.getTargetValue() / incomingOrder.getOfferValue();

        // The resting order receives the tokens
        payout(payouts, restingPubKey, tokenId, executedAmount);

        // The incoming order receives the BIG according to the
        // resting price
        payout(payouts, incomingPubKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, executedAmount * executedPrice);

        // Finally, the orders could be fulfilled now, so we can
        // remove them from the order list
        // Otherwise, we will make the orders smaller by the
        // executed amounts
        incomingOrder.setOfferValue(incomingOrder.getOfferValue() - executedAmount);
        incomingOrder.setTargetValue(incomingOrder.getTargetValue() - executedAmount * incomingPrice);
        restingOrder.setOfferValue(restingOrder.getOfferValue() - executedAmount * executedPrice);
        restingOrder.setTargetValue(restingOrder.getTargetValue() - executedAmount);
        if (sellableAmount == executedAmount) {
            remainingOrders.remove(incomingOrder.getBlockHash());
        }
        if (buyableAmount == executedAmount) {
            remainingOrders.remove(restingOrder.getBlockHash());
        }
    }

    private void processIncomingBuy(TreeMap<ByteBuffer, TreeMap<String, BigInteger>> payouts,
            HashMap<Sha256Hash, OrderRecord> remainingOrders, String tokenId, OrderRecord restingOrder,
            OrderRecord incomingOrder, byte[] restingPubKey, byte[] incomingPubKey, long executedPrice,
            long executedAmount) {
        long sellableAmount = restingOrder.getOfferValue();
        long buyableAmount = incomingOrder.getTargetValue();
        long incomingPrice = incomingOrder.getOfferValue() / incomingOrder.getTargetValue();

        // The resting order receives the BIG according to its price
        payout(payouts, restingPubKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, executedAmount * executedPrice);

        // The incoming order receives the tokens
        payout(payouts, incomingPubKey, tokenId, executedAmount);

        // The difference in price is returned to the incoming
        // beneficiary
        payout(payouts, incomingPubKey, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                executedAmount * (incomingPrice - executedPrice));

        // Finally, the orders could be fulfilled now, so we can
        // remove them from the order list
        restingOrder.setOfferValue(restingOrder.getOfferValue() - executedAmount);
        restingOrder.setTargetValue(restingOrder.getTargetValue() - executedAmount * executedPrice);
        incomingOrder.setOfferValue(incomingOrder.getOfferValue() - executedAmount * incomingPrice);
        incomingOrder.setTargetValue(incomingOrder.getTargetValue() - executedAmount);
        if (sellableAmount == executedAmount) {
            remainingOrders.remove(restingOrder.getBlockHash());
        }
        if (buyableAmount == executedAmount) {
            remainingOrders.remove(incomingOrder.getBlockHash());
        }
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
            String offerTokenid, long offerValue) {
        TreeMap<String, BigInteger> proceeds = payouts.get(ByteBuffer.wrap(beneficiaryPubKey));
        if (proceeds == null) {
            proceeds = new TreeMap<>();
            payouts.put(ByteBuffer.wrap(beneficiaryPubKey), proceeds);
        }
        BigInteger offerTokenProceeds = proceeds.get(offerTokenid);
        if (offerTokenProceeds == null) {
            offerTokenProceeds = BigInteger.ZERO;
            proceeds.put(offerTokenid, offerTokenProceeds);
        }
        proceeds.put(offerTokenid, offerTokenProceeds.add(BigInteger.valueOf(offerValue)));
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
            Map<Sha256Hash, OrderRecord> newOrders, Set<OrderRecord> spentOrders) throws BlockStoreException {
        for (Sha256Hash bHash : collectedBlocks) {
            BlockWrap b = blockStore.getBlockWrap(bHash);
            if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_OPEN) {
                final Sha256Hash blockHash = b.getBlock().getHash();
                OrderRecord order = blockStore.getOrder(blockHash, Sha256Hash.ZERO_HASH);
                newOrders.put(blockHash, OrderRecord.cloneOrderRecord(order));
                spentOrders.add(order);

            } else if (b.getBlock().getBlockType() == Type.BLOCKTYPE_ORDER_CANCEL) {
                OrderCancelInfo info = OrderCancelInfo.parseChecked(b.getBlock().getTransactions().get(0).getData());
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
    public Transaction generateVirtualMiningRewardTX(Block block) throws BlockStoreException {

        RewardInfo rewardInfo = RewardInfo.parseChecked(block.getTransactions().get(0).getData());
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

    /**
     * For each block in orphanBlocks, see if we can now fit it on top of the
     * chain and if so, do so.
     */
    private void tryConnectingOrphans() throws VerificationException, BlockStoreException {
        checkState(chainlock.isHeldByCurrentThread());
        // For each block in our orphan list, try and fit it onto the head of
        // the chain. If we succeed remove it
        // from the list and keep going. If we changed the head of the list at
        // the end of the round try again until
        // we can't fit anything else on the top.
        //
        // This algorithm is kind of crappy, we should do a topo-sort then just
        // connect them in order, but for small
        // numbers of orphan blocks it does OK.

        int blocksConnectedThisRound;
        do {
            blocksConnectedThisRound = 0;
            if (orphanBlocks.size() > 0) {
                log.debug("Orphan  size = {}", orphanBlocks.size());
            }
            Iterator<OrphanBlock> iter = orphanBlocks.values().iterator();
            while (iter.hasNext()) {
                OrphanBlock orphanBlock = iter.next();
                // Look up the blocks previous.
                Block prev = blockStore.get(orphanBlock.block.getRewardInfo().getPrevRewardHash());
                if (prev == null) {
                    // This is still an unconnected/orphan block.
                    // if (log.isDebugEnabled())
                    // log.debug("Orphan block {} is not connectable right now",
                    // orphanBlock.block.getHash());

                    continue;
                }
                // Otherwise we can connect it now.
                // False here ensures we don't recurse infinitely downwards when
                // connecting huge chains.
                log.info("Connected orphan {}", orphanBlock.block.getHash());
                if (addChain(orphanBlock.block, true, false)) {
                    iter.remove();
                    blocksConnectedThisRound++;
                }
            }
            if (blocksConnectedThisRound > 0) {
                log.info("Connected {} orphan blocks.", blocksConnectedThisRound);
            }
        } while (blocksConnectedThisRound > 0);

        if (orphanBlocks.size() > 100)
            orphanBlocks.clear();
    }

    /**
     * Returns the hashes of the currently stored orphan blocks and then deletes
     * them from this objects storage. Used by Peer when a filter exhaustion
     * event has occurred and thus any orphan blocks that have been downloaded
     * might be inaccurate/incomplete.
     */
    public Set<Sha256Hash> drainOrphanBlocks() {
        chainlock.lock();
        try {
            Set<Sha256Hash> hashes = new HashSet<>(orphanBlocks.keySet());
            orphanBlocks.clear();
            return hashes;
        } finally {
            chainlock.unlock();
        }
    }
}
