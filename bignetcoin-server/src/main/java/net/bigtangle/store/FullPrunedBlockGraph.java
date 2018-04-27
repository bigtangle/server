/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.StoredUndoableBlock;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.TransactionOutputChanges;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.script.Script;
import net.bigtangle.script.Script.VerifyFlag;
import net.bigtangle.server.service.BlockRequester;
import net.bigtangle.utils.ContextPropagatingThreadFactory;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.WalletExtension;

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
    public FullPrunedBlockGraph(NetworkParameters networkParameters, FullPrunedBlockStore blockStore) throws BlockStoreException {
        this(Context.getOrCreate(networkParameters), blockStore);
    }

    
    /**
     * Keeps a map of block hashes to StoredBlocks.
     */
    protected final FullPrunedBlockStore blockStore;
    @Autowired
    private BlockRequester blockRequester;

    // Whether or not to execute scriptPubKeys before accepting a transaction
    // (i.e.
    // check signatures).
    private boolean runScripts = true;

    /**
     * Constructs a block chain connected to the given wallet and store. To
     * obtain a {@link Wallet} you can construct one from scratch, or you can
     * deserialize a saved wallet from disk using
     * {@link Wallet#loadFromFile(java.io.File, WalletExtension...)}
     */
    public FullPrunedBlockGraph(Context context, Wallet wallet, FullPrunedBlockStore blockStore)
            throws BlockStoreException {
        this(context, new ArrayList<Wallet>(), blockStore);
        addWallet(wallet);
    }

    /**
     * Constructs a block chain connected to the given wallet and store. To
     * obtain a {@link Wallet} you can construct one from scratch, or you can
     * deserialize a saved wallet from disk using
     * {@link Wallet#loadFromFile(java.io.File, WalletExtension...)}
     */
    public FullPrunedBlockGraph(NetworkParameters params, Wallet wallet, FullPrunedBlockStore blockStore)
            throws BlockStoreException {
        this(Context.getOrCreate(params), wallet, blockStore);
    }

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
        // Ignore upgrading for now
        this.chainHead = blockStore.getVerifiedChainHead();
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
        StoredBlock newBlock = storedPrev.build(header, storedPrevBranch);
        blockStore.put(newBlock, new StoredUndoableBlock(newBlock.getHeader().getHash(), txOutChanges));
        return newBlock;
    }

    @Override
    protected StoredBlock addToBlockStore(StoredBlock storedPrev, StoredBlock storedPrevBranch, Block block)
            throws BlockStoreException, VerificationException {
        StoredBlock newBlock = storedPrev.build(block, storedPrevBranch);
        blockStore.put(newBlock, new StoredUndoableBlock(newBlock.getHeader().getHash(), block.getTransactions()));
        return newBlock;
    }

    @Override
    protected void rollbackBlockStore(int height) throws BlockStoreException {
        throw new BlockStoreException("Unsupported");
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
        }
        return address;
    }

    @Override
    protected TransactionOutputChanges connectTransactions(long height, Block block)
            throws VerificationException, BlockStoreException {
        checkState(lock.isHeldByCurrentThread());
        if (block.getTransactions() == null)
            throw new RuntimeException("connectTransactions called with Block that didn't have transactions!");
        if (!params.passesCheckpoint(height, block.getHash()))
            throw new VerificationException("Block failed checkpoint lockin at " + height);

        blockStore.beginDatabaseBatchWrite();

        LinkedList<UTXO> txOutsSpent = new LinkedList<UTXO>();
        LinkedList<UTXO> txOutsCreated = new LinkedList<UTXO>();
        long sigOps = 0;

        if (scriptVerificationExecutor.isShutdown())
            scriptVerificationExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        List<Future<VerificationException>> listScriptVerificationResults = new ArrayList<Future<VerificationException>>(
                block.getTransactions().size());
        try {
            if (!params.isCheckpoint(height)) {
                // BIP30 violator blocks are ones that contain a duplicated
                // transaction. They
                // are all in the
                // checkpoints list and we therefore only check non-checkpoints
                // for duplicated
                // transactions here. See the
                // BIP30 document for more details on this:
                // https://github.com/bitcoin/bips/blob/master/bip-0030.mediawiki
                for (Transaction tx : block.getTransactions()) {
                    final Set<VerifyFlag> verifyFlags = params.getTransactionVerificationFlags(block, tx,
                            getVersionTally());
                    Sha256Hash hash = tx.getHash();
                    // If we already have unspent outputs for this hash, we saw
                    // the tx already.
                    // Either the block is
                    // being added twice (bug) or the block is a BIP30 violator.
                    // if (blockStore.hasUnspentOutputs(hash,
                    // tx.getOutputs().size()))
                    // throw new VerificationException("Block failed BIP30
                    // test!");
                    if (verifyFlags.contains(VerifyFlag.P2SH)) // We already
                                                               // check
                                                               // non-BIP16
                                                               // sigops in
                                                               // Block.verifyTransactions(true)
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
                        
                        // Coinbases can't be spent until they mature, to avoid
                        // re-orgs destroying
                        // entire transaction
                        // chains. The assumption is there will ~never be
                        // re-orgs deeper than the
                        // spendable coinbase
                        // chain depth.
                        // if (prevOut.isCoinbase()) {
                        // if (height - prevOut.getHeight() <
                        // params.getSpendableCoinbaseDepth()) {
                        // throw new VerificationException("Tried to spend
                        // coinbase at depth " + (height
                        // - prevOut.getHeight()));
                        // }
                        // }
                        if (valueIn.containsKey(Utils.HEX.encode(prevOut.getValue().getTokenid()))) {
                            valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid()),
                                    valueIn.get(Utils.HEX.encode(prevOut.getValue().getTokenid())).add(prevOut.getValue()));
                        } else {
                            valueIn.put(Utils.HEX.encode(prevOut.getValue().getTokenid() ), prevOut.getValue());

                        }
                        if (verifyFlags.contains(VerifyFlag.P2SH)) {
                            if (prevOut.getScript().isPayToScriptHash())
                                sigOps += Script.getP2SHSigOpCount(in.getScriptBytes());
                            if (sigOps > Block.MAX_BLOCK_SIGOPS)
                                throw new VerificationException("Too many P2SH SigOps in block");
                        }
                        prevOutScripts.add(prevOut.getScript());
                        blockStore.updateTransactionOutputSpendPending(prevOut.getHash(), prevOut.getIndex(), true);
                        // txOutsSpent.add(prevOut);
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
                            getScriptAddress(script), block.getHash(), out.getFromaddress(), out.getDescription(),
                            block.getTokenid(), false, false, false);
                    blockStore.addUnspentTransactionOutput(newOut);
                    txOutsCreated.add(newOut);
                }
                if (!checkOutput(valueOut))
                    throw new VerificationException("Transaction output value out of range");
                if (isCoinBase) {
                    // coinbaseValue = valueOut;
                } else {
                    if (!checkInputOutput(valueIn, valueOut) )
                        throw new VerificationException("Transaction input value out of range");
                    // totalFees = totalFees.add(valueIn.subtract(valueOut));
                }

                if (!isCoinBase && runScripts) {
                    // Because correctlySpends modifies transactions, this must
                    // come after we are
                    // done with tx
                    FutureTask<VerificationException> future = new FutureTask<VerificationException>(
                            new Verifier(tx, prevOutScripts, verifyFlags));
                    scriptVerificationExecutor.execute(future);
                    listScriptVerificationResults.add(future);
                }
            }
            // CUI if (totalFees.compareTo(params.getMaxMoney()) > 0 ||
            // block.getBlockInflation(height).add(totalFees).compareTo(coinbaseValue)
            // < 0)
            // throw new VerificationException("Transaction fees out of range");
            for (Future<VerificationException> future : listScriptVerificationResults) {
                VerificationException e;
                try {
                    e = future.get();
                } catch (InterruptedException thrownE) {
                    throw new RuntimeException(thrownE); // Shouldn't happen
                } catch (ExecutionException thrownE) {
                    log.error("Script.correctlySpends threw a non-normal exception: " + thrownE.getCause());
                    throw new VerificationException(
                            "Bug in Script.correctlySpends, likely script malformed in some new and interesting way.",
                            thrownE);
                }
                if (e != null)
                    throw e;
            }
        } catch (VerificationException e) {
            scriptVerificationExecutor.shutdownNow();
            blockStore.abortDatabaseBatchWrite();
            throw e;
        } catch (BlockStoreException e) {
            scriptVerificationExecutor.shutdownNow();
            blockStore.abortDatabaseBatchWrite();
            throw e;
        }
        return new TransactionOutputChanges(txOutsCreated, txOutsSpent);
    }

    public boolean checkOutput(Map<String, Coin> valueOut) {

        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            // System.out.println(entry.getKey() + "/" + entry.getValue());
            if (entry.getValue().signum() < 0) {
                return false;
            }
        }
        return true;
    }

    public boolean checkInputOutput( Map<String, Coin> valueInput, Map<String, Coin> valueOut) {

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
    // @Override
    // /**
    // * Used during reorgs to connect a block previously on a fork
    // */
    // protected synchronized TransactionOutputChanges
    // connectTransactions(StoredBlock newBlock)
    // throws VerificationException, BlockStoreException, PrunedException {
    // checkState(lock.isHeldByCurrentThread());
    // if (!params.passesCheckpoint(newBlock.getHeight(),
    // newBlock.getHeader().getHash()))
    // throw new VerificationException("Block failed checkpoint lockin at " +
    // newBlock.getHeight());
    //
    // blockStore.beginDatabaseBatchWrite();
    // StoredUndoableBlock block =
    // blockStore.getUndoBlock(newBlock.getHeader().getHash());
    // if (block == null) {
    // // We're trying to re-org too deep and the data needed has been deleted.
    // blockStore.abortDatabaseBatchWrite();
    // throw new PrunedException(newBlock.getHeader().getHash());
    // }
    // TransactionOutputChanges txOutChanges;
    // try {
    // List<Transaction> transactions = block.getTransactions();
    // if (transactions != null) {
    // LinkedList<UTXO> txOutsSpent = new LinkedList<UTXO>();
    // LinkedList<UTXO> txOutsCreated = new LinkedList<UTXO>();
    // long sigOps = 0;
    //
    // if (!params.isCheckpoint(newBlock.getHeight())) {
    // for (Transaction tx : transactions) {
    // Sha256Hash hash = tx.getHash();
    // if (blockStore.hasUnspentOutputs(hash, tx.getOutputs().size()))
    // throw new VerificationException("Block failed BIP30 test!");
    // }
    // }
    // Coin totalFees = Coin.ZERO;
    // Coin coinbaseValue = null;
    //
    // if (scriptVerificationExecutor.isShutdown())
    // scriptVerificationExecutor = Executors
    // .newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    // List<Future<VerificationException>> listScriptVerificationResults = new
    // ArrayList<Future<VerificationException>>(
    // transactions.size());
    // for (final Transaction tx : transactions) {
    // final Set<VerifyFlag> verifyFlags =
    // params.getTransactionVerificationFlags(newBlock.getHeader(), tx,
    // getVersionTally());
    // boolean isCoinBase = tx.isCoinBase();
    // Coin valueIn = Coin.ZERO;
    // Coin valueOut = Coin.ZERO;
    // final List<Script> prevOutScripts = new LinkedList<Script>();
    //
    // if (!isCoinBase) {
    // for (int index = 0; index < tx.getInputs().size(); index++) {
    // final TransactionInput in = tx.getInputs().get(index);
    // final UTXO prevOut =
    // blockStore.getTransactionOutput(in.getOutpoint().getHash(),
    // in.getOutpoint().getIndex());
    // if (prevOut == null)
    // throw new VerificationException(
    // "Attempted spend of a non-existent or already spent output!");
    // if (prevOut.isCoinbase()
    // && newBlock.getHeight() - prevOut.getHeight() <
    // params.getSpendableCoinbaseDepth())
    // throw new VerificationException("Tried to spend coinbase at depth "
    // + (newBlock.getHeight() - prevOut.getHeight()));
    // valueIn = valueIn.add(prevOut.getValue());
    // if (verifyFlags.contains(VerifyFlag.P2SH)) {
    // if (prevOut.getScript().isPayToScriptHash())
    // sigOps += Script.getP2SHSigOpCount(in.getScriptBytes());
    // if (sigOps > Block.MAX_BLOCK_SIGOPS)
    // throw new VerificationException("Too many P2SH SigOps in block");
    // }
    //
    // // Missing: Enforce DER signature format
    //
    // prevOutScripts.add(prevOut.getScript());
    //
    // blockStore.removeUnspentTransactionOutput(prevOut.getHash(),
    // prevOut.getIndex());
    // txOutsSpent.add(prevOut);
    // }
    // }
    // Sha256Hash hash = tx.getHash();
    // for (TransactionOutput out : tx.getOutputs()) {
    // valueOut = valueOut.add(out.getValue());
    // Script script = getScript(out.getScriptBytes());
    // UTXO newOut = new UTXO(hash, out.getIndex(), out.getValue(),
    // newBlock.getHeight(), isCoinBase,
    // script, getScriptAddress(script), block.getHash(), out.getFromaddress(),
    // out.getDescription(), newBlock.getHeader().getTokenid(), true);
    // blockStore.addUnspentTransactionOutput(newOut);
    // txOutsCreated.add(newOut);
    // }
    // // All values were already checked for being non-negative (as it is
    // verified
    // in
    // // Transaction.verify())
    // // but we check again here just for defence in depth. Transactions with
    // zero
    // // output value are OK.
    // if (valueOut.signum() < 0 || valueOut.compareTo(params.getMaxMoney()) >
    // 0)
    // throw new VerificationException("Transaction output value out of range");
    // if (isCoinBase) {
    // coinbaseValue = valueOut;
    // } else {
    // if (valueIn.compareTo(valueOut) < 0 ||
    // valueIn.compareTo(params.getMaxMoney()) > 0)
    // throw new VerificationException("Transaction input value out of range");
    // totalFees = totalFees.add(valueIn.subtract(valueOut));
    // }
    //
    // if (!isCoinBase) {
    // // Because correctlySpends modifies transactions, this must come after we
    // are
    // // done with tx
    // FutureTask<VerificationException> future = new
    // FutureTask<VerificationException>(
    // new Verifier(tx, prevOutScripts, verifyFlags));
    // scriptVerificationExecutor.execute(future);
    // listScriptVerificationResults.add(future);
    // }
    // }
    // if (totalFees.compareTo(params.getMaxMoney()) > 0 || newBlock.getHeader()
    // .getBlockInflation(newBlock.getHeight()).add(totalFees).compareTo(coinbaseValue)
    // < 0)
    // throw new VerificationException("Transaction fees out of range");
    // txOutChanges = new TransactionOutputChanges(txOutsCreated, txOutsSpent);
    // for (Future<VerificationException> future :
    // listScriptVerificationResults) {
    // VerificationException e;
    // try {
    // e = future.get();
    // } catch (InterruptedException thrownE) {
    // throw new RuntimeException(thrownE); // Shouldn't happen
    // } catch (ExecutionException thrownE) {
    // log.error("Script.correctlySpends threw a non-normal exception: " +
    // thrownE.getCause());
    // throw new VerificationException(
    // "Bug in Script.correctlySpends, likely script malformed in some new and
    // interesting way.",
    // thrownE);
    // }
    // if (e != null)
    // throw e;
    // }
    // } else {
    // txOutChanges = block.getTxOutChanges();
    // if (!params.isCheckpoint(newBlock.getHeight()))
    // for (UTXO out : txOutChanges.txOutsCreated) {
    // Sha256Hash hash = out.getHash();
    // if (blockStore.getTransactionOutput(hash, out.getIndex()) != null)
    // throw new VerificationException("Block failed BIP30 test!");
    // }
    // for (UTXO out : txOutChanges.txOutsCreated)
    // blockStore.addUnspentTransactionOutput(out);
    // for (UTXO out : txOutChanges.txOutsSpent)
    // blockStore.removeUnspentTransactionOutput(out);
    // }
    // } catch (VerificationException e) {
    // scriptVerificationExecutor.shutdownNow();
    // blockStore.abortDatabaseBatchWrite();
    // throw e;
    // } catch (BlockStoreException e) {
    // scriptVerificationExecutor.shutdownNow();
    // blockStore.abortDatabaseBatchWrite();
    // throw e;
    // }
    // return txOutChanges;
    // }

    /**
     * Adds the specified block and all approved blocks to the milestone. This
     * will connect all transactions of the block by marking used UTXOs spent
     * and adding new UTXOs to the db.
     * 
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void addBlockToMilestone(BlockEvaluation blockEvaluation) throws BlockStoreException {
        blockEvaluation = blockStore.getBlockEvaluation(blockEvaluation.getBlockhash());
        Block block = blockStore.get(blockEvaluation.getBlockhash()).getHeader();

        // If already connected, return
        if (blockEvaluation.isMilestone())
            return;

        // Set milestone true and update latestMilestoneUpdateTime first to stop
        // infinite recursions
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockhash(), true);

        // Connect all approved blocks first (not actually needed)
        addBlockToMilestone(blockStore.getBlockEvaluation(block.getPrevBlockHash()));
        addBlockToMilestone(blockStore.getBlockEvaluation(block.getPrevBranchBlockHash()));

        // Now update the block's transactions in db
        for (final Transaction tx : block.getTransactions()) {
            // For each used input, set its corresponding UTXO to spent
            if (!tx.isCoinBase()) {
                for (TransactionInput in : tx.getInputs()) {
                    UTXO prevOut = blockStore.getTransactionOutput(in.getOutpoint().getHash(),
                            in.getOutpoint().getIndex());
                    if (prevOut == null || prevOut.isSpent() || !prevOut.isConfirmed())
                        throw new VerificationException(
                                "Attempted to spend a non-existent, already spent or unconfirmed output!");
                    blockStore.updateTransactionOutputSpent(prevOut.getHash(), prevOut.getIndex(), true,
                            block.getHash());
                }
            }

            // For each output, mark as confirmed now
            Sha256Hash hash = tx.getHash();
            for (TransactionOutput out : tx.getOutputs()) {
                blockStore.updateTransactionOutputConfirmed(tx.getHash(), out.getIndex(), true);
            }
        }
    }

    /**
     * Removes the specified block and all its output spenders and approvers
     * from the milestone. This will disconnect all transactions of the block by
     * marking used UTXOs unspent and removing UTXOs of the block from the db.
     * 
     * @param blockEvaluation
     * @throws BlockStoreException
     */
    public void removeBlockFromMilestone(BlockEvaluation blockEvaluation) throws BlockStoreException {
        blockEvaluation = blockStore.getBlockEvaluation(blockEvaluation.getBlockhash());
        Block block = blockStore.get(blockEvaluation.getBlockhash()).getHeader();

        // If already disconnected, return
        if (!blockEvaluation.isMilestone())
            return;

        // Set milestone false and update latestMilestoneUpdateTime to stop
        // infinite
        // recursions
        blockStore.updateBlockEvaluationMilestone(blockEvaluation.getBlockhash(), false);

        // Disconnect all approver blocks first
        for (StoredBlock approver : blockStore.getSolidApproverBlocks(blockEvaluation.getBlockhash())) {
            removeBlockFromMilestone(blockStore.getBlockEvaluation(approver.getHeader().getHash()));
        }

        removeTransactionsFromMilestone(block);
    }

    @Override
    public void removeTransactionsFromMilestone(Block block) throws BlockStoreException {
        // CUI checkState(lock.isHeldByCurrentThread());
        // blockStore.beginDatabaseBatchWrite();
        try {
            for (Transaction tx : block.getTransactions()) {
                // Mark all outputs used by tx input as unspent
                for (TransactionInput txin : tx.getInputs()) {
                    if (!txin.isCoinBase()) {
                        blockStore.updateTransactionOutputSpent(txin.getOutpoint().getHash(),
                                txin.getOutpoint().getIndex(), false, Sha256Hash.ZERO_HASH);
                    }
                }

                // Mark unconfirmed all tx outputs in db and disconnect their
                // spending blocks
                for (TransactionOutput txout : tx.getOutputs()) {
                    if (blockStore.getTransactionOutput(tx.getHash(), txout.getIndex()).isSpent()) {
                        removeBlockFromMilestone(
                                blockStore.getTransactionOutputSpender(tx.getHash(), txout.getIndex()));
                    }
                    blockStore.updateTransactionOutputConfirmed(tx.getHash(), txout.getIndex(), false);
                }
            }
        } catch (BlockStoreException e) {
            blockStore.abortDatabaseBatchWrite();
            throw e;
        }
    }

    @Override
    protected void doSetChainHead(StoredBlock chainHead) throws BlockStoreException {
        checkState(lock.isHeldByCurrentThread());
        // blockStore.setVerifiedChainHead(chainHead);
        blockStore.commitDatabaseBatchWrite();
    }

    @Override
    protected void notSettingChainHead() throws BlockStoreException {
        blockStore.abortDatabaseBatchWrite();
    }

    @Override
    protected StoredBlock getStoredBlockInCurrentScope(Sha256Hash hash) throws BlockStoreException {
        checkState(lock.isHeldByCurrentThread());
        return blockStore.getOnceUndoableStoredBlock(hash);
    }
    
    @Override
    protected void maybeSetSolidityAndHeight(Block block) throws BlockStoreException {			
		// Check previous blocks exist
		BlockEvaluation prevBlockEvaluation = blockStore.getBlockEvaluation(block.getPrevBlockHash());
		if (prevBlockEvaluation == null) {
		    blockRequester.requestBlock(block.getPrevBlockHash());
            log.warn("previous block does not exist for solidity update, requesting...");
		} 
	
		BlockEvaluation prevBranchBlockEvaluation = blockStore.getBlockEvaluation(block.getPrevBranchBlockHash());
		if (prevBranchBlockEvaluation == null) {
            blockRequester.requestBlock(block.getPrevBranchBlockHash());
            log.warn("previous block does not exist for solidity update, requesting...");
		} 
		
		if (prevBlockEvaluation == null || prevBranchBlockEvaluation == null) {
			return;
		}
	
		// If both previous blocks are solid, our block should be solidified
		if (prevBlockEvaluation.isSolid() && prevBranchBlockEvaluation.isSolid()) {
			solidifyBlock(block, prevBlockEvaluation, prevBranchBlockEvaluation);
		}
    }

	public void solidifyBlock(Block block, BlockEvaluation prev, BlockEvaluation prevBranch) throws BlockStoreException {
		blockStore.updateBlockEvaluationHeight(block.getHash(), Math.max(prev.getHeight() + 1, prevBranch.getHeight() + 1));
		blockStore.updateBlockEvaluationSolid(block.getHash(), true);
		
		// Update tips table
		blockStore.deleteTip(block.getPrevBlockHash());
		blockStore.deleteTip(block.getPrevBranchBlockHash());
		blockStore.deleteTip(block.getHash());
		blockStore.insertTip(block.getHash());
	}
}
