/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import com.google.protobuf.ByteString;

import net.bigtangle.core.*;
import net.bigtangle.core.Transaction.Purpose;
import net.bigtangle.core.TransactionConfidence.ConfidenceType;
import net.bigtangle.crypto.DeterministicKey;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.store.BlockGraph;
import net.bigtangle.store.MemoryBlockStore;
import net.bigtangle.testing.FakeTxBuilder;
import net.bigtangle.testing.FooWalletExtension;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.utils.Threading;
import net.bigtangle.wallet.DeterministicKeyChain;
import net.bigtangle.wallet.KeyChain;
import net.bigtangle.wallet.MarriedKeyChain;
import net.bigtangle.wallet.Protos;
import net.bigtangle.wallet.UnreadableWalletException;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.WalletExtension;
import net.bigtangle.wallet.WalletProtobufSerializer;
import net.bigtangle.wallet.WalletTransaction;
import net.bigtangle.wallet.WalletTransaction.Pool;
import net.bigtangle.wallet.listeners.WalletCoinsReceivedEventListener;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import static org.junit.Assert.*;
import static com.google.common.base.Preconditions.checkNotNull;
import static net.bigtangle.core.Coin.*;
import static net.bigtangle.testing.FakeTxBuilder.createFakeTx;

public class WalletProtobufSerializerTest {
    private static final NetworkParameters PARAMS = UnitTestParams.get();
    private ECKey myKey;
    private ECKey myWatchedKey;
    private Address myAddress;
    private Wallet myWallet;

    public static String WALLET_DESCRIPTION  = "The quick brown fox lives in \u4f26\u6566"; // Beijing in Chinese
    private long mScriptCreationTime;

    @Before
    public void setUp() throws Exception {
        BriefLogFormatter.initVerbose();
        Context ctx = new Context(PARAMS);
        myWatchedKey = new ECKey();
        myWallet = new Wallet(PARAMS);
        myKey = new ECKey();
        myKey.setCreationTimeSeconds(123456789L);
        myWallet.importKey(myKey);
        myAddress = myKey.toAddress(PARAMS);
        myWallet = new Wallet(PARAMS);
        myWallet.importKey(myKey);
        mScriptCreationTime = new Date().getTime() / 1000 - 1234;
        myWallet.addWatchedAddress(myWatchedKey.toAddress(PARAMS), mScriptCreationTime);
        myWallet.setDescription(WALLET_DESCRIPTION);
    }

    @Test
    public void empty() throws Exception {
        // Check the base case of a wallet with one key and no transactions.
        Wallet wallet1 = roundTrip(myWallet);
        assertEquals(0, wallet1.getTransactions(true).size());
       
        assertArrayEquals(myKey.getPubKey(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPubKey());
        assertArrayEquals(myKey.getPrivKeyBytes(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPrivKeyBytes());
        assertEquals(myKey.getCreationTimeSeconds(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getCreationTimeSeconds());
/*        assertEquals(mScriptCreationTime,
                wallet1.getWatchedScripts().get(0).getCreationTimeSeconds());
        assertEquals(1, wallet1.getWatchedScripts().size());
        assertEquals(ScriptBuilder.createOutputScript(myWatchedKey.toAddress(PARAMS)),
                wallet1.getWatchedScripts().get(0));*/
        assertEquals(WALLET_DESCRIPTION, wallet1.getDescription());
    }

   // @Test
    public void oneTx() throws Exception {
        // Check basic tx serialization.
        Coin v1 = COIN;
        Transaction t1 = createFakeTx(PARAMS, v1, myAddress);
//        t1.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByName("1.2.3.4")));
//        t1.getConfidence().markBroadcastBy(new PeerAddress(PARAMS, InetAddress.getByName("5.6.7.8")));
//        t1.getConfidence().setSource(TransactionConfidence.Source.NETWORK);
//       
        Wallet wallet1 = roundTrip(myWallet);
        assertEquals(1, wallet1.getTransactions(true).size());
      
        Transaction t1copy = wallet1.getTransaction(t1.getHash());
        assertArrayEquals(t1.unsafeBitcoinSerialize(), t1copy.unsafeBitcoinSerialize());
//        assertEquals(2, t1copy.getConfidence().numBroadcastPeers());
//        assertNotNull(t1copy.getConfidence().getLastBroadcastedAt());
//        assertEquals(TransactionConfidence.Source.NETWORK, t1copy.getConfidence().getSource());
//        
        Protos.Wallet walletProto = new WalletProtobufSerializer().walletToProto(myWallet);
        assertEquals(Protos.Key.Type.ORIGINAL, walletProto.getKey(0).getType());
        assertEquals(0, walletProto.getExtensionCount());
        assertEquals(1, walletProto.getTransactionCount());
        assertEquals(6, walletProto.getKeyCount());
        
        Protos.Transaction t1p = walletProto.getTransaction(0);
        assertEquals(0, t1p.getBlockHashCount());
        assertArrayEquals(t1.getHash().getBytes(), t1p.getHash().toByteArray());
        assertEquals(Protos.Transaction.Pool.PENDING, t1p.getPool());
        assertFalse(t1p.hasLockTime());
        assertFalse(t1p.getTransactionInput(0).hasSequence());
        assertArrayEquals(t1.getInputs().get(0).getOutpoint().getHash().getBytes(),
                t1p.getTransactionInput(0).getTransactionOutPointHash().toByteArray());
        assertEquals(0, t1p.getTransactionInput(0).getTransactionOutPointIndex());
        assertEquals(t1p.getTransactionOutput(0).getValue(), v1.value);
    }

    //@Test
    public void raiseFeeTx() throws Exception {
        // Check basic tx serialization.
        Coin v1 = COIN;
        Transaction t1 = createFakeTx(PARAMS, v1, myAddress);
        t1.setPurpose(Purpose.RAISE_FEE);
     
        Wallet wallet1 = roundTrip(myWallet);
        Transaction t1copy = wallet1.getTransaction(t1.getHash());
        assertEquals(Purpose.RAISE_FEE, t1copy.getPurpose());
    }

   //TODO no need  @Test
    public void doubleSpend() throws Exception {
        // Check that we can serialize double spends correctly, as this is a slightly tricky case.
        FakeTxBuilder.DoubleSpends doubleSpends = FakeTxBuilder.createFakeDoubleSpendTxns(PARAMS, myAddress);
        // t1 spends to our wallet.
        
        // t2 rolls back t1 and spends somewhere else.
       // myWallet.receiveFromBlock(doubleSpends.t2, null, BlockGraph.NewBlockType.BEST_CHAIN, 0);
        Wallet wallet1 = roundTrip(myWallet);
        assertEquals(1, wallet1.getTransactions(true).size());
        Transaction t1 = wallet1.getTransaction(doubleSpends.t1.getHash());
       // assertEquals(ConfidenceType.DEAD, t1.getConfidence().getConfidenceType());
      

        // TODO: Wallet should store overriding transactions even if they are not wallet-relevant.
        // assertEquals(doubleSpends.t2, t1.getConfidence().getOverridingTransaction());
    }
    
    @Test
    public void testKeys() throws Exception {
        for (int i = 0 ; i < 20 ; i++) {
            myKey = new ECKey();
            myAddress = myKey.toAddress(PARAMS);
            myWallet = new Wallet(PARAMS);
            myWallet.importKey(myKey);
            Wallet wallet1 = roundTrip(myWallet);
            assertArrayEquals(myKey.getPubKey(), wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPubKey());
            assertArrayEquals(myKey.getPrivKeyBytes(), wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPrivKeyBytes());
        }
    }

    

  //  @Test
    public void testSequenceNumber() throws Exception {
        Wallet wallet = new Wallet(PARAMS);
        Transaction tx1 = createFakeTx(PARAMS, Coin.COIN, wallet.currentReceiveAddress());
        tx1.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE);
       
        Transaction tx2 = createFakeTx(PARAMS, Coin.COIN, wallet.currentReceiveAddress());
        tx2.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE - 1);
       
        Wallet walletCopy = roundTrip(wallet);
        Transaction tx1copy = checkNotNull(walletCopy.getTransaction(tx1.getHash()));
        assertEquals(TransactionInput.NO_SEQUENCE, tx1copy.getInput(0).getSequenceNumber());
        Transaction tx2copy = checkNotNull(walletCopy.getTransaction(tx2.getHash()));
        assertEquals(TransactionInput.NO_SEQUENCE - 1, tx2copy.getInput(0).getSequenceNumber());
    }

    //@Test
    public void testAppearedAtChainHeightDepthAndWorkDone() throws Exception {
        // Test the TransactionConfidence appearedAtChainHeight, depth and workDone field are stored.

        BlockGraph chain = new BlockGraph(PARAMS,  new MemoryBlockStore(PARAMS));

        final ArrayList<Transaction> txns = new ArrayList<Transaction>(2);
        myWallet.addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                txns.add(tx);
            }
        });

        // Start by building two blocks on top of the genesis block.
        Block b1 = BlockForTest.createNextBlock(PARAMS.getGenesisBlock(),myAddress, PARAMS.getGenesisBlock().getHash());
        BigInteger work1 = b1.getWork();
        assertTrue(work1.signum() > 0);

        Block b2 = BlockForTest.createNextBlock(b1,myAddress, PARAMS.getGenesisBlock().getHash());
        BigInteger work2 = b2.getWork();
        assertTrue(work2.signum() > 0);

        assertTrue(chain.add(b1));
        assertTrue(chain.add(b2));

        // We now have the following chain:
        //     genesis -> b1 -> b2

        // Check the transaction confidence levels are correct before wallet roundtrip.
        Threading.waitForUserCode();
        assertEquals(2, txns.size());

//        TransactionConfidence confidence0 = txns.get(0).getConfidence();
//        TransactionConfidence confidence1 = txns.get(1).getConfidence();

//        assertEquals(1, confidence0.getAppearedAtChainHeight());
//        assertEquals(2, confidence1.getAppearedAtChainHeight());
//
//        assertEquals(2, confidence0.getDepthInBlocks());
//        assertEquals(1, confidence1.getDepthInBlocks());

        // Roundtrip the wallet and check it has stored the depth and workDone.
        Wallet rebornWallet = roundTrip(myWallet);

        Set<Transaction> rebornTxns = rebornWallet.getTransactions(false);
        assertEquals(2, rebornTxns.size());

        // The transactions are not guaranteed to be in the same order so sort them to be in chain height order if required.
        Iterator<Transaction> it = rebornTxns.iterator();
        Transaction txA = it.next();
        Transaction txB = it.next();

        Transaction rebornTx0, rebornTx1;
  
        
 
    }

    private static Wallet roundTrip(Wallet wallet) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new WalletProtobufSerializer().writeWallet(wallet, output);
        ByteArrayInputStream test = new ByteArrayInputStream(output.toByteArray());
        assertTrue(WalletProtobufSerializer.isWallet(test));
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        return new WalletProtobufSerializer().readWallet(input);
    }

    @Test
    public void testRoundTripNormalWallet() throws Exception {
        Wallet wallet1 = roundTrip(myWallet);
        assertEquals(0, wallet1.getTransactions(true).size());
        
        assertArrayEquals(myKey.getPubKey(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPubKey());
        assertArrayEquals(myKey.getPrivKeyBytes(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPrivKeyBytes());
        assertEquals(myKey.getCreationTimeSeconds(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getCreationTimeSeconds());
    }

    @Test
    public void testRoundTripWatchingWallet() throws Exception {
        final String xpub = "tpubD9LrDvFDrB6wYNhbR2XcRRaT4yCa37TjBR3YthBQvrtEwEq6CKeEXUs3TppQd38rfxmxD1qLkC99iP3vKcKwLESSSYdFAftbrpuhSnsw6XM";
        final long creationTimeSeconds = 1457019819;
        Wallet wallet = Wallet.fromWatchingKeyB58(PARAMS, xpub, creationTimeSeconds);
        Wallet wallet2 = roundTrip(wallet);
        Wallet wallet3 = roundTrip(wallet2);
        assertEquals(xpub, wallet.getWatchingKey().serializePubB58(PARAMS));
        assertEquals(creationTimeSeconds, wallet.getWatchingKey().getCreationTimeSeconds());
        assertEquals(creationTimeSeconds, wallet2.getWatchingKey().getCreationTimeSeconds());
        assertEquals(creationTimeSeconds, wallet3.getWatchingKey().getCreationTimeSeconds());
        assertEquals(creationTimeSeconds, wallet.getEarliestKeyCreationTime());
        assertEquals(creationTimeSeconds, wallet2.getEarliestKeyCreationTime());
        assertEquals(creationTimeSeconds, wallet3.getEarliestKeyCreationTime());
    }

    @Test
    public void testRoundTripMarriedWallet() throws Exception {
        // create 2-of-2 married wallet
        myWallet = new Wallet(PARAMS);
        final DeterministicKeyChain partnerChain = new DeterministicKeyChain(new SecureRandom());
        DeterministicKey partnerKey = DeterministicKey.deserializeB58(null, partnerChain.getWatchingKey().serializePubB58(PARAMS), PARAMS);
        MarriedKeyChain chain = MarriedKeyChain.builder()
                .random(new SecureRandom())
                .followingKeys(partnerKey)
                .threshold(2).build();
        myWallet.addAndActivateHDChain(chain);

        myAddress = myWallet.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        Wallet wallet1 = roundTrip(myWallet);
        assertEquals(0, wallet1.getTransactions(true).size());
       
        assertEquals(2, wallet1.getActiveKeyChain().getSigsRequiredToSpend());
        assertEquals(myAddress, wallet1.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS));
    }

   // @Test
    public void roundtripVersionTwoTransaction() throws Exception {
        Transaction tx = new Transaction(PARAMS, Utils.HEX.decode(
                "0200000001d7902864af9310420c6e606b814c8f89f7902d40c130594e85df2e757a7cc301070000006b483045022100ca1757afa1af85c2bb014382d9ce411e1628d2b3d478df9d5d3e9e93cb25dcdd02206c5d272b31a23baf64e82793ee5c816e2bbef251e733a638b630ff2331fc83ba0121026ac2316508287761befbd0f7495ea794b396dbc5b556bf276639f56c0bd08911feffffff0274730700000000001976a91456da2d038a098c42390c77ef163e1cc23aedf24088ac91062300000000001976a9148ebf3467b9a8d7ae7b290da719e61142793392c188ac22e00600"));
        assertEquals(tx.getVersion(), 2);
        assertEquals(tx.getHashAsString(), "0321b1413ed9048199815bd6bc2650cab1a9e8d543f109a42c769b1f18df4174");
        myWallet.addWalletTransaction(new WalletTransaction(Pool.UNSPENT, tx));
        Wallet wallet1 = roundTrip(myWallet);
        Transaction tx2 = wallet1.getTransaction(tx.getHash());
        assertEquals(checkNotNull(tx2).getVersion(), 2);
    }

 
    @Test
    public void tags() throws Exception {
        myWallet.setTag("foo", ByteString.copyFromUtf8("bar"));
        assertEquals("bar", myWallet.getTag("foo").toStringUtf8());
        myWallet = roundTrip(myWallet);
        assertEquals("bar", myWallet.getTag("foo").toStringUtf8());
    }

  //  @Test
    public void extensions() throws Exception {
        myWallet.addExtension(new FooWalletExtension("com.whatever.required", true));
        Protos.Wallet proto = new WalletProtobufSerializer().walletToProto(myWallet);
        // Initial extension is mandatory: try to read it back into a wallet that doesn't know about it.
        try {
            new WalletProtobufSerializer().readWallet(PARAMS, null, proto);
            fail();
        } catch (UnreadableWalletException e) {
            assertTrue(e.getMessage().contains("mandatory"));
        }
        Wallet wallet = new WalletProtobufSerializer().readWallet(PARAMS,
                new WalletExtension[]{ new FooWalletExtension("com.whatever.required", true) },
                proto);
        assertTrue(wallet.getExtensions().containsKey("com.whatever.required"));

        // Non-mandatory extensions are ignored if the wallet doesn't know how to read them.
        Wallet wallet2 = new Wallet(PARAMS);
        wallet2.addExtension(new FooWalletExtension("com.whatever.optional", false));
        Protos.Wallet proto2 = new WalletProtobufSerializer().walletToProto(wallet2);
        Wallet wallet5 = new WalletProtobufSerializer().readWallet(PARAMS, null, proto2);
        assertEquals(0, wallet5.getExtensions().size());
    }

    @Test
    public void extensionsWithError() throws Exception {
        WalletExtension extension = new WalletExtension() {
            @Override
            public String getWalletExtensionID() {
                return "test";
            }

            @Override
            public boolean isWalletExtensionMandatory() {
                return false;
            }

            @Override
            public byte[] serializeWalletExtension() {
                return new byte[0];
            }

            @Override
            public void deserializeWalletExtension(Wallet containingWallet, byte[] data) throws Exception {
                throw new NullPointerException();  // Something went wrong!
            }
        };
        myWallet.addExtension(extension);
        Protos.Wallet proto = new WalletProtobufSerializer().walletToProto(myWallet);
        Wallet wallet = new WalletProtobufSerializer().readWallet(PARAMS, new WalletExtension[]{extension}, proto);
        assertEquals(0, wallet.getExtensions().size());
    }

    @Test(expected = UnreadableWalletException.FutureVersion.class)
    public void versions() throws Exception {
        Protos.Wallet.Builder proto = Protos.Wallet.newBuilder(new WalletProtobufSerializer().walletToProto(myWallet));
        proto.setVersion(2);
        new WalletProtobufSerializer().readWallet(PARAMS, null, proto.build());
    }
}
