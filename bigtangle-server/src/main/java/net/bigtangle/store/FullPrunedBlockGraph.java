/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import static com.google.common.base.Preconditions.checkState;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.DataClassName;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OutputsMulti;
import net.bigtangle.core.PrunedException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.TransactionOutputChanges;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VOSExecute;
import net.bigtangle.core.VerificationException;
import net.bigtangle.script.Script;
import net.bigtangle.script.Script.VerifyFlag;
import net.bigtangle.server.service.MultiSignService;
import net.bigtangle.server.service.ValidatorService;
import net.bigtangle.utils.ContextPropagatingThreadFactory;
import net.bigtangle.wallet.Wallet;

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
        this(Context.getOrCreate(networkParameters), blockStore);
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

    @Autowired
    private MultiSignService multiSignService;

    // Whether or not to execute scriptPubKeys before accepting a transaction
    // (i.e.
    // check signatures).
    private boolean runScripts = true;

    /**
     * Constructs a block chain connected to the given store.
     */
    public FullPrunedBlockGraph(Context context, FullPrunedBlockStore blockStore) throws BlockStoreException {
        this(context, new ArrayList<Wallet>(), blockStore);
    }

    /**
     * Constructs a block chain connected to the given list of wallets and a
     * store.
     */
    public FullPrunedBlockGraph(Context context, List<Wallet> listeners, FullPrunedBlockStore blockStore)
            throws BlockStoreException {
        super(context, listeners, blockStore);
        this.blockStore = blockStore;
    }

    /**
     * See {@link #FullPrunedBlockChain(Context, List, FullPrunedBlockStore)}
     */
    public FullPrunedBlockGraph(NetworkParameters params, List<Wallet> listeners, FullPrunedBlockStore blockStore)
            throws BlockStoreException {
        this(Context.getOrCreate(params), listeners, blockStore);
    }

    @Override
    protected StoredBlock addToBlockStore(StoredBlock storedPrev, StoredBlock storedPrevBranch, Block header,
            TransactionOutputChanges txOutChanges) throws BlockStoreException, VerificationException {
        StoredBlock newBlock = StoredBlock.build(header, storedPrev, storedPrevBranch);
        blockStore.put(newBlock, new StoredUndoableBlock(newBlock.getHeader().getHash(), txOutChanges));
        return newBlock;
    }

    public boolean add(Block block, boolean allowConflicts) throws VerificationException, PrunedException {
        try {
            return add(block, true, null, null, allowConflicts);
        } catch (BlockStoreException e) {
            log.debug("", e);
            throw new RuntimeException(e);
        } catch (VerificationException e) {
            log.debug("", e);
            throw new VerificationException("Could not verify block:\n" + block.toString(), e);
        }
    }

    // filteredTxHashList contains all transactions, filteredTxn just a subset
    private boolean add(Block block, boolean tryConnecting, @Nullable List<Sha256Hash> filteredTxHashList,
            @Nullable Map<Sha256Hash, Transaction> filteredTxn, boolean allowConflicts)
            throws BlockStoreException, VerificationException, PrunedException {
        lock.lock();
        try {
            final StoredBlock storedPrev;
            final StoredBlock storedPrevBranch;
            final long height;
            final EnumSet<Block.VerifyFlag> flags;

            // Check the block is formally valid
            try {
                block.verifyHeader();
                storedPrev = blockStore.get(block.getPrevBlockHash());
                storedPrevBranch = blockStore.get(block.getPrevBranchBlockHash());

                if (storedPrev != null && storedPrevBranch != null) {
                    height = Math.max(storedPrev.getHeight(), storedPrevBranch.getHeight()) + 1;
                } else {
                    insertUnsolidBlock(block);
                    return false;
                }

                flags = params.getBlockVerificationFlags(block, versionTally, height);
                if (shouldVerifyTransactions())
                    block.verifyTransactions(height, flags);
            } catch (VerificationException e) {
                log.error("Failed to verify block: ", e);
                log.error(block.getHashAsString());
                throw e;
            }
            checkState(lock.isHeldByCurrentThread());

            if (checkSolidity(block, storedPrev, storedPrevBranch, height, allowConflicts)) {
                // Write to DB
                try {
                    blockStore.beginDatabaseBatchWrite();
                    connectUTXOs(block, storedPrev, storedPrevBranch, height, allowConflicts);
                    connectBlock(block, storedPrev, storedPrevBranch, shouldVerifyTransactions(), filteredTxHashList,
                            filteredTxn);
                    solidifyBlock(block);
                    blockStore.commitDatabaseBatchWrite();
                    return true;
                } catch (BlockStoreException e) {
                    blockStore.abortDatabaseBatchWrite();
                    throw e;
                }
            } else {
                insertUnsolidBlock(block);
                return false;
            }
        } catch (Exception exception) {
            log.debug("", exception);
            throw new BlockStoreException(exception);
        } finally {
            lock.unlock();
        }
    }

    // expensiveChecks enables checks that require looking at blocks further
    // back in the tangle
    // than the previous one when connecting (eg median timestamp check)
    // It could be exposed, but for now we just set it to
    // shouldVerifyTransactions()
    private void connectBlock(final Block block, StoredBlock storedPrev, StoredBlock storedPrevBranch,
            boolean expensiveChecks, @Nullable final List<Sha256Hash> filteredTxHashList,
            @Nullable final Map<Sha256Hash, Transaction> filteredTxn)
            throws BlockStoreException, VerificationException, PrunedException {
        checkState(lock.isHeldByCurrentThread());
        addToBlockStore(storedPrev, storedPrevBranch, block);
    }

    @Override
    protected StoredBlock addToBlockStore(StoredBlock storedPrev, StoredBlock storedPrevBranch, Block block)
            throws BlockStoreException, VerificationException {
        StoredBlock newBlock = StoredBlock.build(block, storedPrev, storedPrevBranch);
        blockStore.put(newBlock, new StoredUndoableBlock(newBlock.getHeader().getHash(), block.getTransactions()));
        return newBlock;
    }

    @Override
    public boolean shouldVerifyTransactions() {
        return true;
    }

    /**
     * Whether or not to run scripts whilst accepting blocks (i.e. checking
     * signatures, for most transactions). If you're accepting data from an
     * untrusted node, such as one found via the P2P network, this should be set
     * to true (which is the default). If you're downloading a chain from a node
     * you control, script execution is redundant because you know the connected
     * node won't relay bad data to you. In that case it's safe to set this to
     * false and obtain a significant speedup.
     */
    public void setRunScripts(boolean value) {
        this.runScripts = value;
    }

    ExecutorService scriptVerificationExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(), new ContextPropagatingThreadFactory("Script verification"));

    /**
     * A job submitted to the executor which verifies signatures.
     */
    private static class Verifier implements Callable<VerificationException> {
        final Transaction tx;
        final List<Script> prevOutScripts;
        final Set<VerifyFlag> verifyFlags;

        public Verifier(final Transaction tx, final List<Script> prevOutScripts, final Set<VerifyFlag> verifyFlags) {
            this.tx = tx;
            this.prevOutScripts = prevOutScripts;
            this.verifyFlags = verifyFlags;
        }

        @Nullable
        @Override
        public VerificationException call() throws Exception {
            try {
                ListIterator<Script> prevOutIt = prevOutScripts.listIterator();
                for (int index = 0; index < tx.getInputs().size(); index++) {
                    tx.getInputs().get(index).getScriptSig().correctlySpends(tx, index, prevOutIt.next(), verifyFlags);
                }
            } catch (VerificationException e) {
                return e;
            }
            return null;
        }
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

    private void confirmToken(String blockhash) throws BlockStoreException {
        //TODO unify confirm/unconfirm
//        blockStore.updateTokenConfirmed(tx.getHash(), out.getIndex(), true);
//        blockStore.updateTokenSpent(blockStore.getTokenPrevblockhash(blockhash), true, blockhash);
//        blockStore.updateTokenConfirmed(blockhash, true);
    }

    private void unconfirmToken(String blockhash) throws BlockStoreException {
        //TODO unify confirm/unconfirm
        //unconfirm spender if exists
//        if (blockStore.getTokenSpent(blockhash)) {
//            removeBlockFromMilestone(blockStore.getTokenSpender(blockhash));
//            blockStore.updateTokenSpent(blockhash, false, null);
//        }
//        blockStore.updateTokenConfirmed(blockhash, false);
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
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void addBlockToMilestone(Sha256Hash blockHash) throws BlockStoreException {
        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        Block block = blockWrap.getBlock();

        // If already connected, return
        if (blockEvaluation.isMilestone())
            return;

        // Set milestone true and update latestMilestoneUpdateTime first to stop
        // infinite recursions
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), true);

        // Connect all approved blocks first (check if actually needed)
        addBlockToMilestone(block.getPrevBlockHash());
        addBlockToMilestone(block.getPrevBranchBlockHash());

        confirmBlock(block);
    }

    private void confirmBlock(Block block) throws BlockStoreException {
        // Update block's transactions in db
        for (final Transaction tx : block.getTransactions()) {
            confirmTransaction(tx, block.getHash());
        }

        // For rewards, update reward to be confirmed now
        if (block.getBlockType() == Block.BLOCKTYPE_REWARD) {
            confirmReward(block);
        }

        // For token creations, update token db
        if (block.getBlockType() == Block.BLOCKTYPE_TOKEN_CREATION) {
            this.confirmToken(block.getHashAsString());
        }

        if (block.getBlockType() == Block.BLOCKTYPE_USERDATA || block.getBlockType() == Block.BLOCKTYPE_VOS) {
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
                            tx.getData(), (String) multiSignBy.get("publickey"), block.getBlockType());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (block.getBlockType() == Block.BLOCKTYPE_VOS_EXECUTE) {
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
                    this.synchronizationVOSData(tx.getData());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void confirmReward(Block block) throws BlockStoreException {
        blockStore.updateTxRewardConfirmed(block.getHash(), true);
    }

    private void confirmTransaction(final Transaction tx, Sha256Hash blockhash) throws BlockStoreException {
        // For each used input, set its corresponding UTXO to spent
        if (!tx.isCoinBase()) {
            for (TransactionInput in : tx.getInputs()) {
                UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getHash(),
                        in.getOutpoint().getIndex());
                if (prevOut == null || prevOut.isSpent() || !prevOut.isConfirmed())
                    throw new VerificationException(
                            "Attempted to spend a non-existent, already spent or unconfirmed output!");
                blockStore.updateTransactionOutputSpent(prevOut.getHash(), prevOut.getIndex(), true,
                        blockhash);
            }
        }

        // For each output, set it to confirmed
        for (TransactionOutput out : tx.getOutputs()) {
            blockStore.updateTransactionOutputConfirmed(tx.getHash(), out.getIndex(), true);
            blockStore.updateTransactionOutputConfirmingBlock(tx.getHash(), out.getIndex(), blockhash);
            blockStore.updateTransactionOutputSpent(tx.getHash(), out.getIndex(), false, null);
        }
    }

    /**
     * Removes the specified block and all its output spenders and approvers
     * from the milestone. This will disconnect all transactions of the block by
     * marking used UTXOs unspent and removing UTXOs of the block from the DB.
     * 
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void removeBlockFromMilestone(Sha256Hash blockHash) throws BlockStoreException {
        BlockWrap blockWrap = blockStore.getBlockWrap(blockHash);
        BlockEvaluation blockEvaluation = blockWrap.getBlockEvaluation();
        Block block = blockWrap.getBlock();

        // If already disconnected, return
        if (!blockEvaluation.isMilestone())
            return;

        // Set milestone false and update latestMilestoneUpdateTime
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockHash(), false);

        // Disconnect all approver blocks first
        for (Sha256Hash approver : blockStore.getSolidApproverBlockHashes(blockEvaluation.getBlockHash())) {
            removeBlockFromMilestone(approver);
        }

        unconfirmBlock(block);
    }

    /**
     * Disconnect the block, unconfirming all UTXOs and UTXO-like constructs.
     * 
     * @throws BlockStoreException
     *             if the block store had an underlying error or block does not
     *             exist in the block store at all.
     */
    private void unconfirmBlock(Block block) throws BlockStoreException {
        // Handle all transactions of the block
        for (Transaction tx : block.getTransactions()) {
            unconfirmTransaction(tx, block);
        }

        // For rewards, update reward db
        if (block.getBlockType() == Block.BLOCKTYPE_REWARD) {
            unconfirmReward(block);
        }

        // For token creations, update token db
        if (block.getBlockType() == Block.BLOCKTYPE_TOKEN_CREATION) {
            // TODO revert token db changes here (revert
            // synchronizationToken(TokenInfo))
            // TODO unsolidify other issuances of this public key?
        }
    }

    private void unconfirmReward(Block block) throws BlockStoreException {
        // TODO analogous to other confirm/unconfirms
        blockStore.updateTxRewardConfirmed(block.getHash(), false);
    }

    /**
     * Disconnects the UTXOs of the transaction
     * 
     * @param tx
     * @param parentBlock
     * @throws BlockStoreException
     */
    private void unconfirmTransaction(Transaction tx, Block parentBlock) throws BlockStoreException {
        // Mark all outputs unconfirmed and remove dependents
        for (TransactionOutput txout : tx.getOutputs()) {
            if (blockStore.getTransactionOutput(tx.getHash(), txout.getIndex()).isSpent()) {
                removeBlockFromMilestone(
                        blockStore.getTransactionOutputSpender(tx.getHash(), txout.getIndex()).getBlockHash());
                blockStore.updateTransactionOutputSpent(tx.getHash(), txout.getIndex(), false, null);
                blockStore.updateTransactionOutputConfirmingBlock(tx.getHash(), txout.getIndex(), null);
            }
            blockStore.updateTransactionOutputConfirmed(tx.getHash(), txout.getIndex(), false);
        }

        // Mark all outputs used by tx input as unspent
        if (!tx.isCoinBase()) {
            for (TransactionInput txin : tx.getInputs()) {
                blockStore.updateTransactionOutputSpent(txin.getOutpoint().getHash(), txin.getOutpoint().getIndex(),
                        false, null);
            }
        }
    }

    @Override
    protected void solidifyBlock(Block block) throws BlockStoreException {
        // Update tips table
        blockStore.deleteTip(block.getPrevBlockHash());
        blockStore.deleteTip(block.getPrevBranchBlockHash());
        blockStore.deleteTip(block.getHash());
        blockStore.insertTip(block.getHash());
    }

    @Override
    protected void insertUnsolidBlock(Block block) throws BlockStoreException {
        blockStore.insertUnsolid(block);
    }

    /*
     * Check if the block is made correctly. Allow conflicts for transaction
     * data (non-Javadoc)
     * 
     * @see
     * net.bigtangle.store.AbstractBlockGraph#checkSolidity(net.bigtangle.core.
     * Block, net.bigtangle.core.StoredBlock, net.bigtangle.core.StoredBlock,
     * long)
     */
    @Override
    protected boolean connectUTXOs(Block block, StoredBlock storedPrev, StoredBlock storedPrevBranch, long height,
            boolean allowConflicts) throws BlockStoreException, VerificationException {
        if (block.getBlockType() == Block.BLOCKTYPE_REWARD) {
            // Get reward data from previous reward cycle
            Sha256Hash prevRewardHash = null;
            long fromHeight = 0;
            byte[] hashBytes = new byte[32];
            ByteBuffer bb = ByteBuffer.wrap(block.getTransactions().get(0).getData());
            fromHeight = bb.getLong();
            bb.getLong(); // nextReward
            bb.get(hashBytes, 0, 32); // prevRewardHash
            prevRewardHash = Sha256Hash.wrap(hashBytes);
            // Reward must have been built correctly.
            Pair<Transaction, Boolean> referenceReward = validatorService.generateMiningRewardTX(
                    storedPrev.getHeader().getHash(), storedPrevBranch.getHeader().getHash(), prevRewardHash);

            blockStore.insertTxReward(block.getHash(), fromHeight, referenceReward.getRight());
        }

        if (block.getBlockType() == Block.BLOCKTYPE_TOKEN_CREATION) {
            Transaction tx = block.getTransactions().get(0);
            if (tx.getData() != null) {
                byte[] buf = tx.getData();
                try {
                    TokenInfo tokenInfo = new TokenInfo().parse(buf);
                    Tokens tokens = tokenInfo.getTokens();
                    tokens.setBlockhash(block.getHashAsString());
                    this.blockStore.saveTokens(tokens);
                } catch (Exception e) {
                    log.error("not possible checked before", e);
                }

            }
        }

        for (final Transaction tx : block.getTransactions()) {
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
                        getScriptAddress(script), block.getHash(), out.getFromaddress(), tx.getMemo(),
                        Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, 0);
                // Filter zero UTXO
                if (!newOut.getValue().isZero()) {
                    blockStore.addUnspentTransactionOutput(newOut);
                }
                if (script.isSentToMultiSig()) {
                    int minsignnumber = script.getNumberOfSignaturesRequiredToSpend();
                    for (ECKey ecKey : script.getPubKeys()) {
                        String toaddress = ecKey.toAddress(networkParameters).toBase58();
                        OutputsMulti outputsMulti = new OutputsMulti(newOut.getHash(), toaddress, newOut.getIndex(),
                                minsignnumber);
                        this.blockStore.insertOutputsMulti(outputsMulti);
                    }
                }
            }
        }

        return true;
    }

    /*
     * Check if the block is made correctly. Allow conflicts for transaction
     * data (non-Javadoc)
     * 
     * @see
     * net.bigtangle.store.AbstractBlockGraph#checkSolidity(net.bigtangle.core.
     * Block, net.bigtangle.core.StoredBlock, net.bigtangle.core.StoredBlock,
     * long)
     */
    @Override
    protected boolean checkSolidity(Block block, StoredBlock storedPrev, StoredBlock storedPrevBranch, long height,
            boolean allowConflicts) throws BlockStoreException, VerificationException {
        // Check timestamp
        if (block.getTimeSeconds() < storedPrev.getHeader().getTimeSeconds()
                || block.getTimeSeconds() < storedPrevBranch.getHeader().getTimeSeconds())
            return false;

        if (block.getBlockType() != Block.BLOCKTYPE_REWARD) {
            if (block.getLastMiningRewardBlock() == storedPrev.getHeader().getLastMiningRewardBlock()
                    && block.getDifficultyTarget() != storedPrev.getHeader().getDifficultyTarget())
                return false;

            if (block.getLastMiningRewardBlock() == storedPrevBranch.getHeader().getLastMiningRewardBlock()
                    && block.getDifficultyTarget() != storedPrevBranch.getHeader().getDifficultyTarget())
                return false;

            if (block.getLastMiningRewardBlock() != storedPrevBranch.getHeader().getLastMiningRewardBlock()
                    && block.getLastMiningRewardBlock() != storedPrev.getHeader().getLastMiningRewardBlock())
                return false;
        }

        // Check formal correctness of TXs and their data
        try {
            block.checkTransactionSolidity(height);
        } catch (VerificationException e) {
            return false;
        }

        // Check reward block specific solidity
        if (block.getBlockType() == Block.BLOCKTYPE_REWARD) {
            // Get reward data from previous reward cycle
            Sha256Hash prevRewardHash = null;
            @SuppressWarnings("unused")
            long fromHeight = 0, nextPerTxReward = 0;
            try {
                byte[] hashBytes = new byte[32];
                ByteBuffer bb = ByteBuffer.wrap(block.getTransactions().get(0).getData());
                fromHeight = bb.getLong();
                nextPerTxReward = bb.getLong();
                bb.get(hashBytes, 0, 32);
                prevRewardHash = Sha256Hash.wrap(hashBytes);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }

            // Reward must have been built correctly.
            Pair<Transaction, Boolean> referenceReward = validatorService.generateMiningRewardTX(
                    storedPrev.getHeader().getHash(), storedPrevBranch.getHeader().getHash(), prevRewardHash);
            if (!referenceReward.getLeft().getHash().equals(block.getTransactions().get(0).getHash()))
                return false;

            // TODO check correct difficulty
        }

        // Check genesis block specific validity, can only one genesis block
        if (block.getBlockType() == Block.BLOCKTYPE_INITIAL) {
            if (!block.getHash().equals(networkParameters.getGenesisBlock().getHash())) {
                return false;
            }
        }
        // Check issuance block specific validity
        if (block.getBlockType() == Block.BLOCKTYPE_TOKEN_CREATION) {
            try {
                if (!this.multiSignService.checkMultiSignPre(block, allowConflicts)) {
                    return false;
                }
                if (block.getTransactions().isEmpty()) {
                    return false;
                }
                Transaction tx = block.getTransactions().get(0);
                if (tx.getData() == null) {
                    return false;
                }
                byte[] buf = tx.getData();
                TokenInfo tokenInfo = new TokenInfo().parse(buf);
                if (tokenInfo.getTokens() == null) {
                    return false;
                }
                if (tokenInfo.getMultiSignAddresses() == null) {
                    return false;
                }
                Tokens tokens = tokenInfo.getTokens();
                tokens.setBlockhash(block.getHashAsString());
                this.blockStore.saveTokens(tokens);
                if (tokenInfo.getTokens().getTokenid().equals(NetworkParameters.BIGNETCOIN_TOKENID_STRING)) {
                    return false;
                }
            } catch (Exception e) {
                log.error("", e);
                return false;
            }
        }

        if (block.getBlockType() == Block.BLOCKTYPE_CROSSTANGLE) {
            return true;
        }

        checkSolidityTransfer(block, height);

        return true;
    }

    private void checkSolidityTransfer(Block block, long height) throws BlockStoreException {
        LinkedList<UTXO> txOutsSpent = new LinkedList<UTXO>();
        LinkedList<UTXO> txOutsCreated = new LinkedList<UTXO>();
        long sigOps = 0;

        try {
            if (scriptVerificationExecutor.isShutdown())
                scriptVerificationExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

            List<Future<VerificationException>> listScriptVerificationResults = new ArrayList<Future<VerificationException>>(
                    block.getTransactions().size());

            if (!params.isCheckpoint(height)) {
                for (Transaction tx : block.getTransactions()) {
                    final Set<VerifyFlag> verifyFlags = params.getTransactionVerificationFlags(block, tx,
                            getVersionTally());

                    if (verifyFlags.contains(VerifyFlag.P2SH))
                        sigOps += tx.getSigOpCount();
                }
            }

            for (final Transaction tx : block.getTransactions()) {
                boolean isCoinBase = tx.isCoinBase();
                Map<String, Coin> valueIn = new HashMap<String, Coin>();
                Map<String, Coin> valueOut = new HashMap<String, Coin>();

                final List<Script> prevOutScripts = new LinkedList<Script>();
                final Set<VerifyFlag> verifyFlags = params.getTransactionVerificationFlags(block, tx,
                        getVersionTally());
                if (!isCoinBase) {
                    for (int index = 0; index < tx.getInputs().size(); index++) {
                        TransactionInput in = tx.getInputs().get(index);
                        UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getHash(),
                                in.getOutpoint().getIndex());
                        if (prevOut == null)
                            throw new VerificationException("Block attempts to spend a not yet existent output!");

                        if (valueIn.containsKey(Utils.HEX.encode(prevOut.getValue().getTokenid()))) {
                            valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()), valueIn
                                    .get(Utils.HEX.encode(prevOut.getValue().getTokenid())).add(prevOut.getValue()));
                        } else {
                            valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()), prevOut.getValue());

                        }
                        if (verifyFlags.contains(VerifyFlag.P2SH)) {
                            if (prevOut.getScript().isPayToScriptHash())
                                sigOps += Script.getP2SHSigOpCount(in.getScriptBytes());
                            if (sigOps > Block.MAX_BLOCK_SIGOPS)
                                throw new VerificationException("Too many P2SH SigOps in block");
                        }
                        prevOutScripts.add(prevOut.getScript());
                        txOutsSpent.add(prevOut);
                    }
                }
                Sha256Hash hash = tx.getHash();
                for (TransactionOutput out : tx.getOutputs()) {
                    if (valueOut.containsKey(Utils.HEX.encode(out.getValue().getTokenid()))) {
                        valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()),
                                valueOut.get(Utils.HEX.encode(out.getValue().getTokenid())).add(out.getValue()));
                    } else {
                        valueOut.put(Utils.HEX.encode(out.getValue().getTokenid()), out.getValue());
                    }
                    // For each output, add it to the set of unspent outputs so
                    // it can be consumed
                    // in future.
                    Script script = getScript(out.getScriptBytes());
                    UTXO newOut = new UTXO(hash, out.getIndex(), out.getValue(), height, isCoinBase, script,
                            getScriptAddress(script), block.getHash(), out.getFromaddress(), tx.getMemo(),
                            Utils.HEX.encode(out.getValue().getTokenid()), false, false, false, 0);

                    txOutsCreated.add(newOut);
                }
                if (!checkOutput(valueOut))
                    throw new VerificationException("Transaction output value out of range");
                if (isCoinBase) {
                    // coinbaseValue = valueOut;
                } else {
                    if (!checkInputOutput(valueIn, valueOut))
                        throw new VerificationException("Transaction input value out of range");
                    // totalFees = totalFees.add(valueIn.subtract(valueOut));
                }

                if (!isCoinBase && runScripts) {
                    // Because correctlySpends modifies transactions, this must
                    // come after we are done with tx
                    FutureTask<VerificationException> future = new FutureTask<VerificationException>(
                            new Verifier(tx, prevOutScripts, verifyFlags));
                    scriptVerificationExecutor.execute(future);
                    listScriptVerificationResults.add(future);
                }
            }
            for (Future<VerificationException> future : listScriptVerificationResults) {
                VerificationException e;
                try {
                    e = future.get();
                } catch (InterruptedException thrownE) {
                    throw new RuntimeException(thrownE); // Shouldn't happen
                } catch (ExecutionException thrownE) {
                    // log.error("Script.correctlySpends threw a non-normal
                    // exception: " ,thrownE );
                    throw new VerificationException(
                            "Bug in Script.correctlySpends, likely script malformed in some new and interesting way.",
                            thrownE);
                }
                if (e != null)
                    throw e;
            }
        } catch (VerificationException e) {
            scriptVerificationExecutor.shutdownNow();

            throw e;
        } catch (BlockStoreException e) {
            scriptVerificationExecutor.shutdownNow();

            throw e;
        }
    }

    private boolean checkOutput(Map<String, Coin> valueOut) {
        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            // System.out.println(entry.getKey() + "/" + entry.getValue());
            if (entry.getValue().signum() < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean checkInputOutput(Map<String, Coin> valueInput, Map<String, Coin> valueOut) {
        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            if (!valueInput.containsKey(entry.getKey())) {
                return false;
            } else {
                if (valueInput.get(entry.getKey()).compareTo(entry.getValue()) < 0)
                    return false;
            }
        }
        return true;
    }
}
