/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.store;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

import com.google.protobuf.ByteString;

import net.bigtangle.core.Address;
import net.bigtangle.core.Context;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.crypto.DeterministicKey;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.testing.FooWalletExtension;
import net.bigtangle.utils.BriefLogFormatter;
import net.bigtangle.wallet.DeterministicKeyChain;
import net.bigtangle.wallet.KeyChain;
import net.bigtangle.wallet.MarriedKeyChain;
import net.bigtangle.wallet.Protos;
import net.bigtangle.wallet.UnreadableWalletException;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.WalletExtension;
import net.bigtangle.wallet.WalletProtobufSerializer;

public class WalletProtobufSerializerTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
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
 
    }

    @Test
    public void empty() throws Exception {
        // Check the base case of a wallet with one key and no transactions.
        Wallet wallet1 = roundTrip(myWallet);
 
       
        assertArrayEquals(myKey.getPubKey(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPubKey());
        assertArrayEquals(myKey.getPrivKeyBytes(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPrivKeyBytes());
        assertEquals(myKey.getCreationTimeSeconds(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getCreationTimeSeconds());

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

 
    //@Test
    public void testAppearedAtChainHeightDepthAndWorkDone() throws Exception { }

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
             
        assertArrayEquals(myKey.getPubKey(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPubKey());
        assertArrayEquals(myKey.getPrivKeyBytes(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getPrivKeyBytes());
        assertEquals(myKey.getCreationTimeSeconds(),
                wallet1.findKeyFromPubHash(myKey.getPubKeyHash()).getCreationTimeSeconds());
    }

   // @Test
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
  
        assertEquals(2, wallet1.getActiveKeyChain().getSigsRequiredToSpend());
        assertEquals(myAddress, wallet1.currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS));
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
