/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Coin.FIFTY_COINS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.ref.WeakReference;
import java.util.List;

import org.junit.Before;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.script.Script;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.BriefLogFormatter;

/**
 * We don't do any wallet tests here, we leave that to {@link ChainSplitTest}
 */


public abstract class AbstractFullPrunedBlockChainTest {
    @org.junit.Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final Logger log = LoggerFactory.getLogger(AbstractFullPrunedBlockChainTest.class);

    protected static final NetworkParameters PARAMS = new MainNetParams();

    @Autowired
    protected FullPrunedBlockGraph blockgraph;
    protected FullPrunedBlockStore store;

    @Before
    public void setUp() throws Exception {

        BriefLogFormatter.init();
        Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
    }

    public abstract FullPrunedBlockStore createStore(NetworkParameters params, int blockCount)
            throws BlockStoreException;

    public abstract void resetStore(FullPrunedBlockStore store) throws BlockStoreException;

  //  @Test
    public void skipScripts() throws Exception {
        store = createStore(PARAMS, 10);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)

        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = PARAMS.getGenesisBlock().createNextBlock(PARAMS.getGenesisBlock());
        blockgraph.add(rollingBlock, true);
        TransactionOutput spendableOutput = rollingBlock.getTransactions().get(0).getOutput(0);
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = rollingBlock.createNextBlock(PARAMS.getGenesisBlock());
            blockgraph.add(rollingBlock, true);
        }

        rollingBlock = rollingBlock.createNextBlock(PARAMS.getGenesisBlock());
        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, FIFTY_COINS, new byte[] {}));
        TransactionInput input = t.addInput(rollingBlock.getHash(), spendableOutput);
        // Invalid script.
        input.clearScriptBytes();
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        
        // TAKEN OUT validatorService.setRunScripts(false);
        
        try {
            blockgraph.add(rollingBlock, true);
        } catch (VerificationException e) {
            fail();
        }
        try {
            store.close();
        } catch (Exception e) {
        }
    }

   // @Test
    public void testFinalizedBlocks() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)

        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = PARAMS.getGenesisBlock().createNextBlock(PARAMS.getGenesisBlock());
        blockgraph.add(rollingBlock, true);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, rollingBlock.getHash(),
                rollingBlock.getTransactions().get(0).getHash());
        byte[] spendableOutputScriptPubKey = rollingBlock.getTransactions().get(0).getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = rollingBlock.createNextBlock(PARAMS.getGenesisBlock());
            blockgraph.add(rollingBlock, true);
        }

        WeakReference<UTXO> out = new WeakReference<UTXO>(
                store.getTransactionOutput(spendableOutput.getBlockHash(), spendableOutput.getTxHash(), spendableOutput.getIndex()));
        rollingBlock = rollingBlock.createNextBlock(PARAMS.getGenesisBlock());

        Transaction t = new Transaction(PARAMS);
        // Entirely invalid scriptPubKey
        t.addOutput(new TransactionOutput(PARAMS, t, FIFTY_COINS, new byte[] {}));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();

        blockgraph.add(rollingBlock, true);

        try {
            store.close();
        } catch (Exception e) {
        }
    }

 

   // @Test
    public void testGetOpenTransactionOutputs() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        resetStore(store);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = PARAMS.getGenesisBlock().createNextBlock(PARAMS.getGenesisBlock());
        blockgraph.add(rollingBlock, true);
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, rollingBlock.getHash(), transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = rollingBlock.createNextBlock(PARAMS.getGenesisBlock());
            blockgraph.add(rollingBlock, true);
        }
        rollingBlock = rollingBlock.createNextBlock(PARAMS.getGenesisBlock());

        // Create bitcoin spend of 1 BTA.
        ECKey toKey = new ECKey();
        Coin amount = Coin.valueOf(10000, NetworkParameters.BIGTANGLE_TOKENID);
        Address address = new Address(PARAMS, toKey.getPubKeyHash());
        Coin totalAmount = Coin.ZERO;

        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock, true);

        totalAmount = totalAmount.add(amount);

        List<UTXO> outputs = store.getOpenTransactionOutputs(Lists.newArrayList(address));
        assertNotNull(outputs);
        assertEquals("Wrong Number of Outputs", 1, outputs.size());
        // UTXO output = outputs.get(0);
        // assertEquals("The address is not equal", address.toString(),
        // output.getAddress());
        // assertEquals("The amount is not equal", totalAmount,
        // output.getValue());

        outputs = null;
        // output = null;
        try {
            store.close();
        } catch (Exception e) {
        }
    }
 
 
}
