/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import static org.bitcoinj.core.Coin.FIFTY_COINS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.BlockFileLoader;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bignetcoin.store.FullPrunedBlockGraph;
import com.bignetcoin.store.FullPrunedBlockStore;
import com.google.common.collect.Lists;

/**
 * We don't do any wallet tests here, we leave that to {@link ChainSplitTest}
 */

public abstract class AbstractFullPrunedBlockChainTest {
    @org.junit.Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final Logger log = LoggerFactory.getLogger(AbstractFullPrunedBlockChainTest.class);

    protected static final NetworkParameters PARAMS = new UnitTestParams() {
        @Override public int getInterval() {
            return 10000;
        }
    };
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

   //TODO @Test
    public void testGeneratedChain() throws Exception {
        // Tests various test cases from FullBlockTestGenerator
        FullBlockTestGenerator generator = new FullBlockTestGenerator(PARAMS);
        RuleList blockList = generator.getBlocksToTest(false, false, null);
        
        store = createStore(PARAMS, blockList.maximumReorgBlockCount);
        blockgraph = new FullPrunedBlockGraph(PARAMS, store);

        for (Rule rule : blockList.list) {
            if (!(rule instanceof FullBlockTestGenerator.BlockAndValidity))
                continue;
            FullBlockTestGenerator.BlockAndValidity block = (FullBlockTestGenerator.BlockAndValidity) rule;
            log.info("Testing rule " + block.ruleName + " with block hash " + block.block.getHash());
            boolean threw = false;
            try {
                if (blockgraph.add(block.block) != block.connects) {
                    log.error("Block didn't match connects flag on block " + block.ruleName);
                    fail();
                }
            } catch (VerificationException e) {
                threw = true;
                if (!block.throwsException) {
                    log.error("Block didn't match throws flag on block " + block.ruleName);
                    throw e;
                }
                if (block.connects) {
             
                	log.error("Block didn't match connects flag on block " + block.ruleName);
                    fail();
                }
            }
            if (!threw && block.throwsException) {
                log.error("Block didn't match throws flag on block " + block.ruleName);
                fail();
            }
            if (!blockgraph.getChainHead().getHeader().getHash().equals(block.hashChainTipAfterBlock)) {
                log.error("New block head didn't match the correct value after block " + block.ruleName);
             //   fail();
            }
            if (blockgraph.getChainHead().getHeight() != block.heightAfterBlock) {
                log.error("New block head didn't match the correct height after block " + block.ruleName);
              //  fail();
            }
        }
        try {
            store.close();
        } catch (Exception e) {}
    }

    @Test
    public void skipScripts() throws Exception {
        store = createStore(PARAMS, 10);
        blockgraph = new FullPrunedBlockGraph(PARAMS, store);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)

        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        TransactionOutput spendableOutput = rollingBlock.getTransactions().get(0).getOutput(0);
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }

        rollingBlock = BlockForTest.createNextBlock(rollingBlock,null,PARAMS.getGenesisBlock().getHash());
        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, FIFTY_COINS, new byte[] {}));
        TransactionInput input = t.addInput(spendableOutput);
        // Invalid script.
        input.clearScriptBytes();
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.setRunScripts(false);
        try {
            blockgraph.add(rollingBlock);
        } catch (VerificationException e) {
            fail();
        }
        try {
            store.close();
        } catch (Exception e) {}
    }

    @Test
    public void testFinalizedBlocks() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        blockgraph = new FullPrunedBlockGraph(PARAMS, store);
        
        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        
        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, rollingBlock.getTransactions().get(0).getHash());
        byte[] spendableOutputScriptPubKey = rollingBlock.getTransactions().get(0).getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        
        WeakReference<UTXO> out = new WeakReference<UTXO>
                                       (store.getTransactionOutput(spendableOutput.getHash(), spendableOutput.getIndex()));
        rollingBlock = BlockForTest.createNextBlock(rollingBlock,null,PARAMS.getGenesisBlock().getHash());
        
        Transaction t = new Transaction(PARAMS);
        // Entirely invalid scriptPubKey
        t.addOutput(new TransactionOutput(PARAMS, t, FIFTY_COINS, new byte[]{}));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        
        blockgraph.add(rollingBlock);
        WeakReference<StoredUndoableBlock> undoBlock = new WeakReference<StoredUndoableBlock>(store.getUndoBlock(rollingBlock.getHash()));

        StoredUndoableBlock storedUndoableBlock = undoBlock.get();
        assertNotNull(storedUndoableBlock);
        assertNull(storedUndoableBlock.getTransactions());
        WeakReference<TransactionOutputChanges> changes = new WeakReference<TransactionOutputChanges>(storedUndoableBlock.getTxOutChanges());
        assertNotNull(changes.get());
        storedUndoableBlock = null;   // Blank the reference so it can be GCd.
        
        // Create a blockgraph longer than UNDOABLE_BLOCKS_STORED
        for (int i = 0; i < UNDOABLE_BLOCKS_STORED; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock,null,PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        // Try to get the garbage collector to run
        System.gc();
        assertNull(undoBlock.get());
        assertNull(changes.get());
        assertNull(out.get());
        try {
            store.close();
        } catch (Exception e) {}
    }
    
    //No old data @Test
    public void testFirst100KBlocks() throws Exception {
        NetworkParameters params = MainNetParams.get();
        Context context = new Context(params);
        File blockFile = new File(getClass().getResource("first-100k-blocks.dat").getFile());
        BlockFileLoader loader = new BlockFileLoader(params, Arrays.asList(blockFile));
        
        store = createStore(params, 10);
        resetStore(store);
        blockgraph = new FullPrunedBlockGraph(context, store);
        for (Block block : loader) {
            block.setPrevBlockHash(PARAMS.getGenesisBlock().getHash());
            //block.setDifficultyTarget(Block.CLIENT_DIFFICULTY_TARGET);
            block.solve();
            blockgraph.add(block);
        }
        try {
            store.close();
        } catch (Exception e) {}
    }

    @Test
    public void testGetOpenTransactionOutputs() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        resetStore(store);
        blockgraph = new FullPrunedBlockGraph(PARAMS, store);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        rollingBlock = BlockForTest.createNextBlock(rollingBlock,null,PARAMS.getGenesisBlock().getHash());

        // Create bitcoin spend of 1 BTA.
        ECKey toKey = new ECKey();
        Coin amount = Coin.valueOf(100000000,NetworkParameters.BIGNETCOIN_TOKENID);
        Address address = new Address(PARAMS, toKey.getPubKeyHash());
        Coin totalAmount = Coin.ZERO;

        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);
        totalAmount = totalAmount.add(amount);

        List<UTXO> outputs = store.getOpenTransactionOutputs(Lists.newArrayList(address));
        assertNotNull(outputs);
        assertEquals("Wrong Number of Outputs", 1, outputs.size());
        UTXO output = outputs.get(0);
        assertEquals("The address is not equal", address.toString(), output.getAddress());
        assertEquals("The amount is not equal", totalAmount, output.getValue());

        outputs = null;
        output = null;
        try {
            store.close();
        } catch (Exception e) {}
    }

   //TODO   @Test
    public void testUTXOProviderWithWallet() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = 1000;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        blockgraph = new FullPrunedBlockGraph(PARAMS, store);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output.
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
      //  rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,null,PARAMS.getGenesisBlock().getHash());

        // Create 1 BTA spend to a key in this wallet (to ourselves).
        Wallet wallet = new Wallet(PARAMS);
        assertEquals("Available balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.AVAILABLE));
        assertEquals("Estimated balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.ESTIMATED));

        wallet.setUTXOProvider(store);
        ECKey toKey = wallet.freshReceiveKey();
        Coin amount = Coin.valueOf(10000000, NetworkParameters.BIGNETCOIN_TOKENID);

        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);

        // Create another spend of 1/2 the value of BTA we have available using the wallet (store coin selector).
        ECKey toKey2 = new ECKey();
        Coin amount2 = amount.divide(2);
        Address address2 = new Address(PARAMS, toKey2.getPubKeyHash());
        SendRequest req = SendRequest.to(address2, amount2);
        wallet.completeTx(req);
        wallet.commitTx(req.tx);
        Coin fee = Coin.ZERO;

        // There should be one pending tx (our spend).
        assertEquals("Wrong number of PENDING.4", 1, wallet.getPoolSize(WalletTransaction.Pool.PENDING));
        Coin totalPendingTxAmount = Coin.ZERO;
        for (Transaction tx : wallet.getPendingTransactions()) {
            totalPendingTxAmount = totalPendingTxAmount.add(tx.getValueSentToMe(wallet));
        }

        // The availbale balance should be the 0 (as we spent the 1 BTA that's pending) and estimated should be 1/2 - fee BTA
        assertEquals("Available balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.AVAILABLE));
        assertEquals("Estimated balance is incorrect", amount2.subtract(fee), wallet.getBalance(Wallet.BalanceType.ESTIMATED));
        assertEquals("Pending tx amount is incorrect", amount2.subtract(fee), totalPendingTxAmount);
        try {
            store.close();
        } catch (Exception e) {}
    }

    /**
     * Test that if the block height is missing from coinbase of a version 2
     * block, it's rejected.
     */
    @Test
    public void missingHeightFromCoinbase() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = PARAMS.getMajorityEnforceBlockUpgrade() + 1;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        try {
            blockgraph = new FullPrunedBlockGraph(PARAMS, store);
            ECKey outKey = new ECKey();
            int height = 1;
            Block chainHead = PARAMS.getGenesisBlock();

            // Build some blocks on genesis block to create a spendable output.

            // Put in just enough v1 blocks to stop the v2 blocks from forming a majority
            for (height = 1; height <= (PARAMS.getMajorityWindow() - PARAMS.getMajorityEnforceBlockUpgrade()); height++) {
                chainHead = BlockForTest.createNextBlockWithCoinbase(chainHead,Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height,PARAMS.getGenesisBlock().getHash());
                blockgraph.add(chainHead);
            }

            // Fill the rest of the window in with v2 blocks
            for (; height < PARAMS.getMajorityWindow(); height++) {
                chainHead = BlockForTest.createNextBlockWithCoinbase(chainHead,Block.BLOCK_VERSION_BIP34,
                    outKey.getPubKey(), height,PARAMS.getGenesisBlock().getHash());
                blockgraph.add(chainHead);
            }
            // Throw a broken v2 block in before we have a supermajority to enable
            // enforcement, which should validate as-is
            chainHead = BlockForTest.createNextBlockWithCoinbase(chainHead,Block.BLOCK_VERSION_BIP34,
                outKey.getPubKey(), height * 2,PARAMS.getGenesisBlock().getHash());
            blockgraph.add(chainHead);
            height++;

            // Trying to add a broken v2 block should now result in rejection as
            // we have a v2 supermajority
            thrown.expect(VerificationException.CoinbaseHeightMismatch.class);
            chainHead = BlockForTest.createNextBlockWithCoinbase(chainHead,Block.BLOCK_VERSION_BIP34,
                outKey.getPubKey(), height * 2,PARAMS.getGenesisBlock().getHash());
            blockgraph.add(chainHead);
        }  catch(final VerificationException ex) {
            throw (Exception) ex.getCause();
        } finally {
            try {
                store.close();
            } catch(Exception e) {
                // Catch and drop any exception so a break mid-test doesn't result
                // in a new exception being thrown and the original lost
            }
        }
    }
}
