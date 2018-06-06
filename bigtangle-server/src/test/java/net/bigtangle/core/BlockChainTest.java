/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Coin.COIN;
import static net.bigtangle.core.Coin.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import net.bigtangle.params.UnitTestParams;
import net.bigtangle.store.AbstractBlockGraph;
import net.bigtangle.store.BlockGraph;
import net.bigtangle.store.MemoryBlockStore;
import net.bigtangle.testing.FakeTxBuilder;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.wallet.Wallet;

// Handling of chain splits/reorgs are in ChainSplitTests.
@Ignore
public class BlockChainTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private BlockGraph testNetChain;

    private Wallet wallet;
    private BlockGraph chain;
    private BlockStore blockStore;
    private Address coinbaseTo;
    private static final NetworkParameters PARAMS = UnitTestParams.get();
    private final StoredBlock[] block = new StoredBlock[1];
    private Transaction coinbaseTransaction;

  

    private void resetBlockStore() {
        blockStore = new MemoryBlockStore(PARAMS);
    }

    @Before
    public void setUp() throws Exception {
        BriefLogFormatter.initVerbose();
         Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
        wallet = new Wallet(PARAMS) {
         
        };
        wallet.freshReceiveKey();

        resetBlockStore();
        chain = new BlockGraph(PARAMS,   blockStore);

        coinbaseTo = wallet.currentReceiveKey().toAddress(PARAMS);
    }
  
//    // TODO @Test
//    public void unconnectedBlocks() throws Exception {
//        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinbaseTo,
//                PARAMS.getGenesisBlock().getHash());
//        Block b2 = BlockForTest.createNextBlock(b1, coinbaseTo, PARAMS.getGenesisBlock().getHash());
//        Block b3 = BlockForTest.createNextBlock(b2, coinbaseTo, b1.getHash());
//        // Connected.
//        assertTrue(chain.add(b1));
//        // Unconnected but stored. The head of the chain is still b1.
//        assertFalse(chain.add(b3));
//        assertEquals(chain.getChainHead().getHeader(), b1.cloneAsHeader());
//        // Add in the middle block.
//        assertTrue(chain.add(b2));
//        assertEquals(chain.getChainHead().getHeader(), b3.cloneAsHeader());
//    }

 
    private void testDeprecatedBlockVersion(final long deprecatedVersion, final long newVersion) throws Exception {
        final BlockStore versionBlockStore = new MemoryBlockStore(PARAMS);
        final BlockGraph versionChain = new BlockGraph(PARAMS, versionBlockStore);

        // Build a historical chain of version 3 blocks
        long timeSeconds = 1231006505;
        int height = 0;
        FakeTxBuilder.BlockPair chainHead = null;

        // Put in just enough v2 blocks to be a minority
        for (height = 0; height < (PARAMS.getMajorityWindow() - PARAMS.getMajorityRejectBlockOutdated()); height++) {
            chainHead = FakeTxBuilder.createFakeBlock(versionBlockStore, deprecatedVersion, timeSeconds, height);
            versionChain.add(chainHead.block);
            timeSeconds += 60;
        }
        // Fill the rest of the window with v3 blocks
        for (; height < PARAMS.getMajorityWindow(); height++) {
            chainHead = FakeTxBuilder.createFakeBlock(versionBlockStore, newVersion, timeSeconds, height);
            versionChain.add(chainHead.block);
            timeSeconds += 60;
        }

        chainHead = FakeTxBuilder.createFakeBlock(versionBlockStore, deprecatedVersion, timeSeconds, height);
        // Trying to add a new v2 block should result in rejection
        thrown.expect(VerificationException.BlockVersionOutOfDate.class);
        try {
            versionChain.add(chainHead.block);
        } catch (final VerificationException ex) {
            throw (Exception) ex.getCause();
        }
    }

//  //TODO no duplicated  @Test
//    public void duplicates() throws Exception {
//        // Adding a block twice should not have any effect, in particular it
//        // should not send the block to the wallet.
//        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinbaseTo,
//                PARAMS.getGenesisBlock().getHash());
//        Block b2 = BlockForTest.createNextBlock(b1, coinbaseTo, PARAMS.getGenesisBlock().getHash());
//        Block b3 = BlockForTest.createNextBlock(b2, coinbaseTo, b1.getHash());
//        assertTrue(chain.add(b1));
//        assertEquals(b1, block[0].getHeader());
//        assertTrue(chain.add(b2));
//        assertEquals(b2, block[0].getHeader());
//        assertTrue(chain.add(b3));
//        assertEquals(b3, block[0].getHeader());
//        assertEquals(b3, chain.getChainHead().getHeader());
//        assertTrue(chain.add(b2));
//        assertEquals(b3, chain.getChainHead().getHeader());
//        // Wallet was NOT called with the new block because the duplicate add
//        // was spotted.
//        assertEquals(b3, block[0].getHeader());
//    }

    @Test
    public void intraBlockDependencies() throws Exception {
        // Covers issue 166 in which transactions that depend on each other
        // inside a block were not always being
        // considered relevant.
        Address somebodyElse = new ECKey().toAddress(PARAMS);
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), somebodyElse,
                PARAMS.getGenesisBlock().getHash());
        ECKey key = wallet.freshReceiveKey();
        Address addr = key.toAddress(PARAMS);
        // Create a tx that gives us some coins, and another that spends it to
        // someone else in the same block.
        Transaction t1 = FakeTxBuilder.createFakeTx(PARAMS, COIN, addr);
        Transaction t2 = new Transaction(PARAMS);
        t2.addInput(t1.getOutputs().get(0));
        t2.addOutput(valueOf(2, NetworkParameters.BIGNETCOIN_TOKENID), somebodyElse);
        b1.addTransaction(t1);
        b1.addTransaction(t2);
        b1.solve();
        chain.add(b1);
     
    }

//    @Test
//    public void coinbaseTransactionAvailability() throws Exception {
//        // Check that a coinbase transaction is only available to spend after
//        // NetworkParameters.getSpendableCoinbaseDepth() blocks.
//
//        // Create a second wallet to receive the coinbase spend.
//        Wallet wallet2 = new Wallet(PARAMS);
//        ECKey receiveKey = wallet2.freshReceiveKey();
//        int height = 1;
//        chain.addWallet(wallet2);
//
//        Address addressToSendTo = receiveKey.toAddress(PARAMS);
//
//        // Create a block, sending the coinbase to the coinbaseTo address (which
//        // is in the wallet).
//        Block b1 = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
//                wallet.currentReceiveKey().getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
//        chain.add(b1);
//
//        // Check a transaction has been received.
//        assertNotNull(coinbaseTransaction);
//
//        // The coinbase tx is not yet available to spend.
// 
//        assertTrue(!coinbaseTransaction.isMature());
//
//        // Attempt to spend the coinbase - this should fail as the coinbase is
//        // not mature yet.
//        try {
//            wallet.createSend(addressToSendTo, valueOf(49, NetworkParameters.BIGNETCOIN_TOKENID));
//            fail();
//        } catch (InsufficientMoneyException e) {
//        }
//
//        // Check that the coinbase is unavailable to spend for the next
//        // spendableCoinbaseDepth - 2 blocks.
//        for (int i = 0; i < PARAMS.getSpendableCoinbaseDepth() - 2; i++) {
//            // Non relevant tx - just for fake block creation.
//            Transaction tx2 = createFakeTx(PARAMS, COIN, new ECKey().toAddress(PARAMS));
//
//            Block b2 = createFakeBlock(blockStore, height++, tx2).block;
//            chain.add(b2);
//
//            // Wallet still does not have the coinbase transaction available for
//            // spend.
//      
//            // The coinbase transaction is still not mature.
//            assertTrue(!coinbaseTransaction.isMature());
//
//            // Attempt to spend the coinbase - this should fail.
//            try {
//                wallet.createSend(addressToSendTo, valueOf(49, NetworkParameters.BIGNETCOIN_TOKENID));
//                fail();
//            } catch (InsufficientMoneyException e) {
//            }
//        }
//
//        // Give it one more block - should now be able to spend coinbase
//        // transaction. Non relevant tx.
//        Transaction tx3 = createFakeTx(PARAMS, COIN, new ECKey().toAddress(PARAMS));
//        Block b3 = createFakeBlock(blockStore, height++, tx3).block;
//        chain.add(b3);
//
//    
//        assertTrue(coinbaseTransaction.isMature());
//
//        // Create a spend with the coinbase BTA to the address in the second
//        // wallet - this should now succeed.
//        Transaction coinbaseSend2 = wallet.createSend(addressToSendTo, valueOf(49, NetworkParameters.BIGNETCOIN_TOKENID));
//        assertNotNull(coinbaseSend2);
//
// 
//        // Give it one more block - change from coinbaseSpend should now be
//        // available in the first wallet.
//        Block b4 = createFakeBlock(blockStore, height++, coinbaseSend2).block;
//        chain.add(b4);
// 
// 
//    }

 
    @Test
    public void falsePositives() throws Exception {
        double decay = AbstractBlockGraph.FP_ESTIMATOR_ALPHA;
        assertTrue(0 == chain.getFalsePositiveRate()); // Exactly
        chain.trackFalsePositives(55);
        assertEquals(decay * 55, chain.getFalsePositiveRate(), 1e-4);
        chain.trackFilteredTransactions(550);
        double rate1 = chain.getFalsePositiveRate();
        // Run this scenario a few more time for the filter to converge
        for (int i = 1; i < 10; i++) {
            chain.trackFalsePositives(55);
            chain.trackFilteredTransactions(550);
        }

        // Ensure we are within 10%
        assertEquals(0.1, chain.getFalsePositiveRate(), 0.01);

        // Check that we get repeatable results after a reset
        chain.resetFalsePositiveEstimate();
        assertTrue(0 == chain.getFalsePositiveRate()); // Exactly

        chain.trackFalsePositives(55);
        assertEquals(decay * 55, chain.getFalsePositiveRate(), 1e-4);
        chain.trackFilteredTransactions(550);
        assertEquals(rate1, chain.getFalsePositiveRate(), 1e-4);
    }

//    //TODO no rollback @Test
//    public void rollbackBlockStore() throws Exception {
//        // This test simulates an issue on Android, that causes the VM to crash
//        // while receiving a block, so that the
//        // block store is persisted but the wallet is not.
//        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinbaseTo,
//                PARAMS.getGenesisBlock().getHash());
//        Block b2 = BlockForTest.createNextBlock(b1, coinbaseTo, PARAMS.getGenesisBlock().getHash());
//        // Add block 1, no frills.
//        assertTrue(chain.add(b1));
//        assertEquals(b1.cloneAsHeader(), chain.getChainHead().getHeader());
//        assertEquals(1, chain.getMaxHeight());
//        assertEquals(1, wallet.getLastBlockSeenHeight());
//        // Add block 2 while wallet is disconnected, to simulate crash.
//       // chain.removeWallet(wallet);
//        assertTrue(chain.add(b2));
//        assertEquals(b2.cloneAsHeader(), chain.getChainHead().getHeader());
//        assertEquals(2, chain.getMaxHeight());
//        assertEquals(1, wallet.getLastBlockSeenHeight());
//        // Add wallet back. This will detect the height mismatch and repair the
//        // damage done.
//        chain.addWallet(wallet);
//        assertEquals(b1.cloneAsHeader(), chain.getChainHead().getHeader());
//        assertEquals(1, chain.getMaxHeight());
//        assertEquals(1, wallet.getLastBlockSeenHeight());
//        // Now add block 2 correctly.
//        assertTrue(chain.add(b2));
//        assertEquals(b2.cloneAsHeader(), chain.getChainHead().getHeader());
//        assertEquals(2, chain.getMaxHeight());
//        assertEquals(2, wallet.getLastBlockSeenHeight());
//    }
}
