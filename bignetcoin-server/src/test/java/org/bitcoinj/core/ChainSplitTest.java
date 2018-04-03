/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Coin.CENT;
import static org.bitcoinj.core.Coin.COIN;
import static org.bitcoinj.core.Coin.FIFTY_COINS;
import static org.bitcoinj.core.Coin.ZERO;
import static org.bitcoinj.core.Coin.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bitcoinj.core.TransactionConfidence.ConfidenceType;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.testing.FakeTxBuilder;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletTransaction;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.wallet.listeners.WalletReorganizeEventListener;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bignetcoin.store.BlockGraph;
import com.bignetcoin.store.MemoryBlockStore;
@Ignore
public class ChainSplitTest {
    private static final Logger log = LoggerFactory.getLogger(ChainSplitTest.class);
    private static final NetworkParameters PARAMS = UnitTestParams.get();
    private Wallet wallet;
    private BlockGraph chain;
    private Address coinsTo;
    private Address coinsTo2;
    private Address someOtherGuy;

    @Before
    public void setUp() throws Exception {
        BriefLogFormatter.init();
        Utils.setMockClock(); // Use mock clock
        Context.propagate(new Context(PARAMS, 100, Coin.ZERO, false));
        MemoryBlockStore blockStore = new MemoryBlockStore(PARAMS);
        wallet = new Wallet(PARAMS);
        ECKey key1 = wallet.freshReceiveKey();
        ECKey key2 = wallet.freshReceiveKey();
        chain = new BlockGraph(PARAMS,  blockStore);
        coinsTo = key1.toAddress(PARAMS);
        coinsTo2 = key2.toAddress(PARAMS);
        someOtherGuy = new ECKey().toAddress(PARAMS);
    }

    @Test
    public void testForking1() throws Exception {
        // Check that if the block chain forks, we end up using the right chain.
        // Only tests inbound transactions
        // (receiving coins). Checking that we understand reversed spends is in
        // testForking2.
        final AtomicBoolean reorgHappened = new AtomicBoolean();
        final AtomicInteger walletChanged = new AtomicInteger();
        wallet.addReorganizeEventListener(new WalletReorganizeEventListener() {
            @Override
            public void onReorganize(Wallet wallet) {
                reorgHappened.set(true);
            }
        });
        wallet.addChangeEventListener(new WalletChangeEventListener() {

            @Override
            public void onWalletChanged(Wallet wallet) {
                walletChanged.incrementAndGet();
            }
        });

        // Start by building a couple of blocks on top of the genesis block.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        Block b2 = BlockForTest.createNextBlock(b1, coinsTo, PARAMS.getGenesisBlock().getHash());
        assertTrue(chain.add(b1));
        assertTrue(chain.add(b2));
        Threading.waitForUserCode();
        assertFalse(reorgHappened.get());
        assertEquals(2, walletChanged.get());
        // We got two blocks which sent 50 coins each to us.
 
        // We now have the following chain:
        // genesis -> b1 -> b2
        //
        // so fork like this:
        //
        // genesis -> b1 -> b2
        // \-> b3
        //
        // Nothing should happen at this point. We saw b2 first so it takes
        // priority.
        Block b3 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        assertTrue(chain.add(b3));
        Threading.waitForUserCode();
        assertFalse(reorgHappened.get()); // No re-org took place.
        assertEquals(2, walletChanged.get());
 
        // Check we can handle multi-way splits: this is almost certainly going
        // to be extremely rare, but we have to
        // handle it anyway. The same transaction appears in b7/b8 (side chain)
        // but not b2 or b3.
        // genesis -> b1--> b2
        // |-> b3
        // |-> b7 (x)
        // \-> b8 (x)
        Block b7 = BlockForTest.createNextBlock(b1, coinsTo, PARAMS.getGenesisBlock().getHash());
        assertTrue(chain.add(b7));
        Block b8 = BlockForTest.createNextBlock(b1, coinsTo, PARAMS.getGenesisBlock().getHash());
        final Transaction t = b7.getTransactions().get(1);
        final Sha256Hash tHash = t.getHash();
        b8.addTransaction(t);
        b8.solve();
        assertTrue(chain.add(roundtrip(b8)));
        Threading.waitForUserCode();
        assertEquals(2, wallet.getTransaction(tHash).getAppearsInHashes().size());
        assertFalse(reorgHappened.get()); // No re-org took place.
        assertEquals(5, walletChanged.get());
 
        // Now we add another block to make the alternative chain longer.
        // assertTrue(chain.add(BlockForTest.createNextBlock(b3,someOtherGuy)));
        Threading.waitForUserCode();
        assertTrue(reorgHappened.get()); // Re-org took place.
        assertEquals(6, walletChanged.get());
        reorgHappened.set(false);
        //
        // genesis -> b1 -> b2
        // \-> b3 -> b4
        // We lost some coins! b2 is no longer a part of the best chain so our
        // available balance should drop to 50.
        // It's now pending reconfirmation.
  
        // ... and back to the first chain.
        Block b5 = BlockForTest.createNextBlock(b2, coinsTo, PARAMS.getGenesisBlock().getHash());
        Block b6 = BlockForTest.createNextBlock(b5, coinsTo, PARAMS.getGenesisBlock().getHash());
        assertTrue(chain.add(b5));
        assertTrue(chain.add(b6));
        //
        // genesis -> b1 -> b2 -> b5 -> b6
        // \-> b3 -> b4
        //
        Threading.waitForUserCode();
        assertTrue(reorgHappened.get());
        assertEquals(9, walletChanged.get());
 
    }

    @Test
    public void testForking2() throws Exception {
        // Check that if the chain forks and new coins are received in the
        // alternate chain our balance goes up
        // after the re-org takes place.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), someOtherGuy,
                PARAMS.getGenesisBlock().getHash());
        Block b2 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        assertTrue(chain.add(b1));
        assertTrue(chain.add(b2));
        // genesis -> b1 -> b2
        // \-> b3 -> b4
 
        Block b3 = BlockForTest.createNextBlock(b1, coinsTo, PARAMS.getGenesisBlock().getHash());
        Block b4 = BlockForTest.createNextBlock(b3, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        assertTrue(chain.add(b3));
     
    }

    @Test
    public void testForking3() throws Exception {
        // Check that we can handle our own spends being rolled back by a fork.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        chain.add(b1);
 
        Address dest = new ECKey().toAddress(PARAMS);
        Transaction spend = wallet.createSend(dest, valueOf(Coin.COIN_VALUE*10, NetworkParameters.BIGNETCOIN_TOKENID));
 
 
        spend.getConfidence()
                .markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[] { 1, 2, 3, 4 })));
        spend.getConfidence()
                .markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByAddress(new byte[] { 5, 6, 7, 8 })));
        assertEquals(ConfidenceType.PENDING, spend.getConfidence().getConfidenceType());
  
        Block b2 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        b2.addTransaction(spend);
        b2.solve();
        chain.add(roundtrip(b2));
        // We have 40 coins in change.
        assertEquals(ConfidenceType.BUILDING, spend.getConfidence().getConfidenceType());
        // genesis -> b1 (receive coins) -> b2 (spend coins)
        // \-> b3 -> b4
        Block b3 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        Block b4 = BlockForTest.createNextBlock(b3, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        chain.add(b3);
        chain.add(b4);
        // b4 causes a re-org that should make our spend go pending again.
          assertEquals(ConfidenceType.PENDING, spend.getConfidence().getConfidenceType());
    }

    @Test
    public void testForking4() throws Exception {
        // Check that we can handle external spends on an inactive chain
        // becoming active. An external spend is where
        // we see a transaction that spends our own coins but we did not
        // broadcast it ourselves. This happens when
        // keys are being shared between wallets.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        chain.add(b1);
 
        Address dest = new ECKey().toAddress(PARAMS);
        Transaction spend = wallet.createSend(dest, FIFTY_COINS);
        // We do NOT confirm the spend here. That means it's not considered to
        // be pending because createSend is
        // stateless. For our purposes it is as if some other program with our
        // keys created the tx.
        //
        // genesis -> b1 (receive 50) --> b2
        // \-> b3 (external spend) -> b4
        Block b2 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        chain.add(b2);
        Block b3 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        b3.addTransaction(spend);
        b3.solve();
        chain.add(roundtrip(b3));
        // The external spend is now pending.
     
        Transaction tx = wallet.getTransaction(spend.getHash());
        assertEquals(ConfidenceType.PENDING, tx.getConfidence().getConfidenceType());
        Block b4 = BlockForTest.createNextBlock(b3, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        chain.add(b4);
        // The external spend is now active.
 
        assertEquals(ConfidenceType.BUILDING, tx.getConfidence().getConfidenceType());
    }

    @Test
    public void testForking5() throws Exception {
        // Test the standard case in which a block containing identical
        // transactions appears on a side chain.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        chain.add(b1);
        final Transaction t = b1.transactions.get(1);
  
        // genesis -> b1
        // -> b2
        Block b2 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        Transaction b2coinbase = b2.transactions.get(0);
        b2.transactions.clear();
        b2.addTransaction(b2coinbase);
        b2.addTransaction(t);
        b2.solve();
        chain.add(roundtrip(b2));
 
        assertTrue(wallet.isConsistent());
        assertEquals(2, wallet.getTransaction(t.getHash()).getAppearsInHashes().size());
        // -> b2 -> b3
        Block b3 = BlockForTest.createNextBlock(b2, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        chain.add(b3);
 

    }

    private Block roundtrip(Block b2) throws ProtocolException {
        return PARAMS.getDefaultSerializer().makeBlock(b2.bitcoinSerialize());
    }

    @Test
    public void testForking6() throws Exception {
        // Test the case in which a side chain block contains a tx, and then it
        // appears in the main chain too.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), someOtherGuy,
                PARAMS.getGenesisBlock().getHash());
        chain.add(b1);
        // genesis -> b1
        // -> b2
        Block b2 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        chain.add(b2);
 
        // genesis -> b1 -> b3
        // -> b2
        Block b3 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        b3.addTransaction(b2.transactions.get(1));
        b3.solve();
        chain.add(roundtrip(b3));
 
    }

    @Test
    public void testDoubleSpendOnFork() throws Exception {
        // Check what happens when a re-org happens and one of our confirmed
        // transactions becomes invalidated by a
        // double spend on the new best chain.

        final boolean[] eventCalled = new boolean[1];
      

        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        chain.add(b1);

        Transaction t1 = wallet.createSend(someOtherGuy, valueOf(Coin.COIN_VALUE*10, NetworkParameters.BIGNETCOIN_TOKENID));
        Address yetAnotherGuy = new ECKey().toAddress(PARAMS);
        Transaction t2 = wallet.createSend(yetAnotherGuy, valueOf(Coin.COIN_VALUE*20, NetworkParameters.BIGNETCOIN_TOKENID));
   
        // Receive t1 as confirmed by the network.
        Block b2 = BlockForTest.createNextBlock(b1, new ECKey().toAddress(PARAMS), PARAMS.getGenesisBlock().getHash());
        b2.addTransaction(t1);
        b2.solve();
        chain.add(roundtrip(b2));

        // Now we make a double spend become active after a re-org.
        Block b3 = BlockForTest.createNextBlock(b1, new ECKey().toAddress(PARAMS), PARAMS.getGenesisBlock().getHash());
        b3.addTransaction(t2);
        b3.solve();
        chain.add(roundtrip(b3)); // Side chain.
        Block b4 = BlockForTest.createNextBlock(b3, new ECKey().toAddress(PARAMS), PARAMS.getGenesisBlock().getHash());
        chain.add(b4); // New best chain.
        Threading.waitForUserCode();
        // Should have seen a double spend.
        assertTrue(eventCalled[0]);
        
    }

    @Test
    public void testDoubleSpendOnForkPending() throws Exception {
        // Check what happens when a re-org happens and one of our unconfirmed
        // transactions becomes invalidated by a
        // double spend on the new best chain.
        final Transaction[] eventDead = new Transaction[1];
        final Transaction[] eventReplacement = new Transaction[1];
 

        // Start with 50 coins.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        chain.add(b1);

        Transaction t1 = checkNotNull(
                wallet.createSend(someOtherGuy, valueOf(Coin.COIN_VALUE*10, NetworkParameters.BIGNETCOIN_TOKENID)));
        Address yetAnotherGuy = new ECKey().toAddress(PARAMS);
        Transaction t2 = checkNotNull(
                wallet.createSend(yetAnotherGuy, valueOf(Coin.COIN_VALUE*20, NetworkParameters.BIGNETCOIN_TOKENID)));
    
        // t1 is still pending ...
        Block b2 = BlockForTest.createNextBlock(b1, new ECKey().toAddress(PARAMS), PARAMS.getGenesisBlock().getHash());
        chain.add(b2);
       // Now we make a double spend become active after a re-org.
        // genesis -> b1 -> b2 [t1 pending]
        // \-> b3 (t2) -> b4
        Block b3 = BlockForTest.createNextBlock(b1, new ECKey().toAddress(PARAMS), PARAMS.getGenesisBlock().getHash());
        b3.addTransaction(t2);
        b3.solve();
        chain.add(roundtrip(b3)); // Side chain.
        Block b4 = BlockForTest.createNextBlock(b3, new ECKey().toAddress(PARAMS), PARAMS.getGenesisBlock().getHash());
        chain.add(b4); // New best chain.
        Threading.waitForUserCode();
        // Should have seen a double spend against the pending pool.
        // genesis -> b1 -> b2 [t1 dead and exited the miners mempools]
        // \-> b3 (t2) -> b4
        assertEquals(t1, eventDead[0]);
        assertEquals(t2, eventReplacement[0]);
      

        // ... and back to our own parallel universe.
        Block b5 = BlockForTest.createNextBlock(b2, new ECKey().toAddress(PARAMS), PARAMS.getGenesisBlock().getHash());
        chain.add(b5);
        Block b6 = BlockForTest.createNextBlock(b5, new ECKey().toAddress(PARAMS), PARAMS.getGenesisBlock().getHash());
        chain.add(b6);
        // genesis -> b1 -> b2 -> b5 -> b6 [t1 still dead]
        // \-> b3 [t2 resurrected and now pending] -> b4
 
        // t2 is pending - resurrected double spends take precedence over our
        // dead transactions (which are in nobodies
        // mempool by this point).
        t1 = checkNotNull(wallet.getTransaction(t1.getHash()));
        t2 = checkNotNull(wallet.getTransaction(t2.getHash()));
        assertEquals(ConfidenceType.DEAD, t1.getConfidence().getConfidenceType());
        assertEquals(ConfidenceType.PENDING, t2.getConfidence().getConfidenceType());
    }

    @Test
    public void txConfidenceLevels() throws Exception {
        // Check that as the chain forks and re-orgs, the confidence data
        // associated with each transaction is
        // maintained correctly.
        final ArrayList<Transaction> txns = new ArrayList<Transaction>(3);
        wallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                txns.add(tx);
            }
        });

        // Start by building three blocks on top of the genesis block. All send
        // to us.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), coinsTo, PARAMS.getGenesisBlock().getHash());
        BigInteger work1 = b1.getWork();
        Block b2 = BlockForTest.createNextBlock(b1, coinsTo2, PARAMS.getGenesisBlock().getHash());
        BigInteger work2 = b2.getWork();
        Block b3 = BlockForTest.createNextBlock(b2, coinsTo2, PARAMS.getGenesisBlock().getHash());
        BigInteger work3 = b3.getWork();

        assertTrue(chain.add(b1));
        assertTrue(chain.add(b2));
        assertTrue(chain.add(b3));
        Threading.waitForUserCode();
        // Check the transaction confidence levels are correct.
        assertEquals(3, txns.size());

        assertEquals(1, txns.get(0).getConfidence().getAppearedAtChainHeight());
        assertEquals(2, txns.get(1).getConfidence().getAppearedAtChainHeight());
        assertEquals(3, txns.get(2).getConfidence().getAppearedAtChainHeight());

        assertEquals(3, txns.get(0).getConfidence().getDepthInBlocks());
        assertEquals(2, txns.get(1).getConfidence().getDepthInBlocks());
        assertEquals(1, txns.get(2).getConfidence().getDepthInBlocks());

        // We now have the following chain:
        // genesis -> b1 -> b2 -> b3
        //
        // so fork like this:
        //
        // genesis -> b1 -> b2 -> b3
        // \-> b4 -> b5
        //
        // Nothing should happen at this point. We saw b2 and b3 first so it
        // takes priority.
        Block b4 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        BigInteger work4 = b4.getWork();

        Block b5 = BlockForTest.createNextBlock(b4, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        BigInteger work5 = b5.getWork();

        assertTrue(chain.add(b4));
        assertTrue(chain.add(b5));
        Threading.waitForUserCode();
        assertEquals(3, txns.size());

        assertEquals(1, txns.get(0).getConfidence().getAppearedAtChainHeight());
        assertEquals(2, txns.get(1).getConfidence().getAppearedAtChainHeight());
        assertEquals(3, txns.get(2).getConfidence().getAppearedAtChainHeight());

        assertEquals(3, txns.get(0).getConfidence().getDepthInBlocks());
        assertEquals(2, txns.get(1).getConfidence().getDepthInBlocks());
        assertEquals(1, txns.get(2).getConfidence().getDepthInBlocks());

        // Now we add another block to make the alternative chain longer.
        Block b6 = BlockForTest.createNextBlock(b5, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        BigInteger work6 = b6.getWork();
        assertTrue(chain.add(b6));
        //
        // genesis -> b1 -> b2 -> b3
        // \-> b4 -> b5 -> b6
        //

        assertEquals(3, txns.size());
        assertEquals(1, txns.get(0).getConfidence().getAppearedAtChainHeight());
        assertEquals(4, txns.get(0).getConfidence().getDepthInBlocks());

        // Transaction 1 (in block b2) is now on a side chain, so it goes
        // pending (not see in chain).
        assertEquals(ConfidenceType.PENDING, txns.get(1).getConfidence().getConfidenceType());
        try {
            txns.get(1).getConfidence().getAppearedAtChainHeight();
            fail();
        } catch (IllegalStateException e) {
        }
        assertEquals(0, txns.get(1).getConfidence().getDepthInBlocks());

        // ... and back to the first chain.
        Block b7 = BlockForTest.createNextBlock(b3, coinsTo, PARAMS.getGenesisBlock().getHash());
        BigInteger work7 = b7.getWork();
        Block b8 = BlockForTest.createNextBlock(b7, coinsTo, PARAMS.getGenesisBlock().getHash());
        BigInteger work8 = b7.getWork();

        assertTrue(chain.add(b7));
        assertTrue(chain.add(b8));
        //
        // genesis -> b1 -> b2 -> b3 -> b7 -> b8
        // \-> b4 -> b5 -> b6
        //

        // This should be enabled, once we figure out the best way to inform the
        // user of how the wallet is changing
        // during the re-org.
        // assertEquals(5, txns.size());

        assertEquals(1, txns.get(0).getConfidence().getAppearedAtChainHeight());
        assertEquals(2, txns.get(1).getConfidence().getAppearedAtChainHeight());
        assertEquals(3, txns.get(2).getConfidence().getAppearedAtChainHeight());

        assertEquals(5, txns.get(0).getConfidence().getDepthInBlocks());
        assertEquals(4, txns.get(1).getConfidence().getDepthInBlocks());
        assertEquals(3, txns.get(2).getConfidence().getDepthInBlocks());

      

        // Now add two more blocks that don't send coins to us. Despite being
        // irrelevant the wallet should still update.
        Block b9 = BlockForTest.createNextBlock(b8, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        Block b10 = BlockForTest.createNextBlock(b9, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        chain.add(b9);
        chain.add(b10);
        BigInteger extraWork = b9.getWork().add(b10.getWork());
        assertEquals(7, txns.get(0).getConfidence().getDepthInBlocks());
        assertEquals(6, txns.get(1).getConfidence().getDepthInBlocks());
        assertEquals(5, txns.get(2).getConfidence().getDepthInBlocks());
    }

   //TODO no protobuf  @Test
    public void orderingInsideBlock() throws Exception {
        // Test that transactions received in the same block have their ordering
        // preserved when reorganising.
        // This covers issue 468.

        // Receive some money to the wallet.
        Transaction t1 = FakeTxBuilder.createFakeTx(PARAMS, COIN, coinsTo);
        final Block b1 = FakeTxBuilder.makeSolvedTestBlock(PARAMS.genesisBlock, t1);
        chain.add(b1);

        // Send a couple of payments one after the other (so the second depends
        // on the change output of the first).
        wallet.allowSpendingUnconfirmedTransactions();
        Transaction t2 = checkNotNull(
                wallet.createSend(new ECKey().toAddress(PARAMS), CENT));
 
        Transaction t3 = checkNotNull(
                wallet.createSend(new ECKey().toAddress(PARAMS), CENT));
 
        chain.add(FakeTxBuilder.makeSolvedTestBlock(b1, t2, t3));

        final Coin coins0point98 = COIN.subtract(CENT).subtract(CENT);
 

        // Now round trip the wallet and force a re-org.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wallet.saveToFileStream(bos);
        wallet = Wallet.loadFromFileStream(new ByteArrayInputStream(bos.toByteArray()));
        final Block b2 = FakeTxBuilder.makeSolvedTestBlock(b1, t2, t3);
        final Block b3 = FakeTxBuilder.makeSolvedTestBlock(b2);
        chain.add(b2);
        chain.add(b3);

        // And verify that the balance is as expected. Because new ECKey() is
        // non-deterministic, if the order
        // isn't being stored correctly this should fail 50% of the time.
 
    }

    @Test
    public void coinbaseDeath() throws Exception {
        // Check that a coinbase tx is marked as dead after a reorg rather than
        // pending as normal non-double-spent
        // transactions would be. Also check that a dead coinbase on a sidechain
        // is resurrected if the sidechain
        // becomes the best chain once more. Finally, check that dependent
        // transactions are killed recursively.
        final ArrayList<Transaction> txns = new ArrayList<Transaction>(3);
        wallet.addCoinsReceivedEventListener(Threading.SAME_THREAD, new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                txns.add(tx);
            }
        });

        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(), someOtherGuy,
                PARAMS.getGenesisBlock().getHash());
        final ECKey coinsTo2 = wallet.freshReceiveKey();
        Block b2 = BlockForTest.createNextBlockWithCoinbase(b1, Block.BLOCK_VERSION_GENESIS, coinsTo2.getPubKey(), 2,
                PARAMS.getGenesisBlock().getHash());
        Block b3 = BlockForTest.createNextBlock(b2, someOtherGuy, PARAMS.getGenesisBlock().getHash());

        log.debug("Adding block b1");
        assertTrue(chain.add(b1));
        log.debug("Adding block b2");
        assertTrue(chain.add(b2));
        log.debug("Adding block b3");
        assertTrue(chain.add(b3));

        // We now have the following chain:
        // genesis -> b1 -> b2 -> b3
        //

        // Check we have seen the coinbase.
        assertEquals(1, txns.size());

        // Check the coinbase transaction is building and in the unspent pool
        // only.
        final Transaction coinbase = txns.get(0);
        assertEquals(ConfidenceType.BUILDING, coinbase.getConfidence().getConfidenceType());
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.PENDING, coinbase.getHash()));
        assertTrue(wallet.poolContainsTxHash(WalletTransaction.Pool.UNSPENT, coinbase.getHash()));
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.SPENT, coinbase.getHash()));
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.DEAD, coinbase.getHash()));

        // Add blocks to b3 until we can spend the coinbase.
        Block firstTip = b3;
        for (int i = 0; i < PARAMS.getSpendableCoinbaseDepth() - 2; i++) {
            firstTip = BlockForTest.createNextBlock(firstTip, someOtherGuy, PARAMS.getGenesisBlock().getHash());
            chain.add(firstTip);
        }
        // ... and spend.
        Transaction fodder = wallet.createSend(new ECKey().toAddress(PARAMS), FIFTY_COINS );
     
        final AtomicBoolean fodderIsDead = new AtomicBoolean(false);
        fodder.getConfidence().addEventListener(Threading.SAME_THREAD, new TransactionConfidence.Listener() {
            @Override
            public void onConfidenceChanged(TransactionConfidence confidence, ChangeReason reason) {
                fodderIsDead.set(confidence.getConfidenceType() == ConfidenceType.DEAD);
            }
        });

        // Fork like this:
        //
        // genesis -> b1 -> b2 -> b3 -> [...]
        // \-> b4 -> b5 -> b6 -> [...]
        //
        // The b4/ b5/ b6 is now the best chain
        Block b4 = BlockForTest.createNextBlock(b1, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        Block b5 = BlockForTest.createNextBlock(b4, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        Block b6 = BlockForTest.createNextBlock(b5, someOtherGuy, PARAMS.getGenesisBlock().getHash());

        log.debug("Adding block b4");
        assertTrue(chain.add(b4));
        log.debug("Adding block b5");
        assertTrue(chain.add(b5));
        log.debug("Adding block b6");
        assertTrue(chain.add(b6));

        Block secondTip = b6;
        for (int i = 0; i < PARAMS.getSpendableCoinbaseDepth() - 2; i++) {
            secondTip = BlockForTest.createNextBlock(secondTip, someOtherGuy, PARAMS.getGenesisBlock().getHash());
            chain.add(secondTip);
        }

        // Transaction 1 (in block b2) is now on a side chain and should have
        // confidence type of dead and be in
        // the dead pool only.
        assertEquals(TransactionConfidence.ConfidenceType.DEAD, coinbase.getConfidence().getConfidenceType());
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.PENDING, coinbase.getHash()));
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.UNSPENT, coinbase.getHash()));
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.SPENT, coinbase.getHash()));
        assertTrue(wallet.poolContainsTxHash(WalletTransaction.Pool.DEAD, coinbase.getHash()));
        assertTrue(fodderIsDead.get());

        // ... and back to the first chain.
        Block b7 = BlockForTest.createNextBlock(firstTip, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        Block b8 = BlockForTest.createNextBlock(b7, someOtherGuy, PARAMS.getGenesisBlock().getHash());

        log.debug("Adding block b7");
        assertTrue(chain.add(b7));
        log.debug("Adding block b8");
        assertTrue(chain.add(b8));

        //
        // genesis -> b1 -> b2 -> b3 -> [...] -> b7 -> b8
        // \-> b4 -> b5 -> b6 -> [...]
        //

        // The coinbase transaction should now have confidence type of building
        // once more and in the unspent pool only.
        assertEquals(TransactionConfidence.ConfidenceType.BUILDING, coinbase.getConfidence().getConfidenceType());
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.PENDING, coinbase.getHash()));
        assertTrue(wallet.poolContainsTxHash(WalletTransaction.Pool.UNSPENT, coinbase.getHash()));
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.SPENT, coinbase.getHash()));
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.DEAD, coinbase.getHash()));
        // However, fodder is still dead. Bitcoin Core doesn't keep killed
        // transactions around in case they become
        // valid again later. They are just deleted from the mempool for good.

        // ... make the side chain dominant again.
        Block b9 = BlockForTest.createNextBlock(secondTip, someOtherGuy, PARAMS.getGenesisBlock().getHash());
        Block b10 = BlockForTest.createNextBlock(b9, someOtherGuy, PARAMS.getGenesisBlock().getHash());

        log.debug("Adding block b9");
        assertTrue(chain.add(b9));
        log.debug("Adding block b10");
        assertTrue(chain.add(b10));

        //
        // genesis -> b1 -> b2 -> b3 -> [...] -> b7 -> b8
        // \-> b4 -> b5 -> b6 -> [...] -> b9 -> b10
        //

        // The coinbase transaction should now have the confidence type of dead
        // and be in the dead pool only.
        assertEquals(TransactionConfidence.ConfidenceType.DEAD, coinbase.getConfidence().getConfidenceType());
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.PENDING, coinbase.getHash()));
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.UNSPENT, coinbase.getHash()));
        assertTrue(!wallet.poolContainsTxHash(WalletTransaction.Pool.SPENT, coinbase.getHash()));
        assertTrue(wallet.poolContainsTxHash(WalletTransaction.Pool.DEAD, coinbase.getHash()));
    }
}
