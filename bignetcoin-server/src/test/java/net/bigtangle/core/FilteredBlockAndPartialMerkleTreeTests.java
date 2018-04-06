/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Utils.HEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.google.common.collect.Lists;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BloomFilter;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.FilteredBlock;
import net.bigtangle.core.PartialMerkleTree;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.StoredBlock;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.TransactionConfidence.ConfidenceType;
import net.bigtangle.store.MemoryBlockStore;
import net.bigtangle.testing.FakeTxBuilder;
import net.bigtangle.testing.TestWithPeerGroup;
import net.bigtangle.wallet.KeyChainGroup;
import net.bigtangle.wallet.Wallet;

@RunWith(value = Parameterized.class)
@Ignore
public class FilteredBlockAndPartialMerkleTreeTests extends TestWithPeerGroup {
    @Parameterized.Parameters
    public static Collection<ClientType[]> parameters() {
        return Arrays.asList(new ClientType[] { ClientType.NIO_CLIENT_MANAGER },
                new ClientType[] { ClientType.BLOCKING_CLIENT_MANAGER });
    }

    public FilteredBlockAndPartialMerkleTreeTests(ClientType clientType) {
        super(clientType);
    }

    @Before
    public void setUp() throws Exception {
        context = new Context(PARAMS);
        MemoryBlockStore store = new MemoryBlockStore(PARAMS);

        // Cheat and place the previous block (block 100000) at the head of the
        // block store without supporting blocks
       // Block b = new Block(PARAMS, HEX.decode(
        //        "0100000050120119172a610421a6c3011dd330d9df07b63616c2cc1f1cd00200000000006657a9252aacd5c0b2940996ecff952228c3067cc38d4885efb5a4ac4247e9f337221b4d4c86041b0f2b5710"));

        Block   b = PARAMS.getDefaultSerializer().makeBlock(HEX.decode(
                "0100000050120119172a610421a6c3011dd330d9df07b63616c2cc1f1cd00200000000006657a9252aacd5c0b2940996ecff952228c3067cc38d4885efb5a4ac4247e9f337221b4d4c86041b0f2b5710"));
        store.put(new StoredBlock(b,  100000));
        store.setChainHead(
                store.get(Sha256Hash.wrap("000000000003ba27aa200b1cecaad478d2b00432346c3f1f3986da1afd33e506")));

        KeyChainGroup group = new KeyChainGroup(PARAMS);
        group.importKeys(ECKey.fromPublicOnly(HEX.decode(
                "04b27f7e9475ccf5d9a431cb86d665b8302c140144ec2397fce792f4a4e7765fecf8128534eaa71df04f93c74676ae8279195128a1506ebf7379d23dab8fca0f63")),
                ECKey.fromPublicOnly(HEX.decode(
                        "04732012cb962afa90d31b25d8fb0e32c94e513ab7a17805c14ca4c3423e18b4fb5d0e676841733cb83abaf975845c9f6f2a8097b7d04f4908b18368d6fc2d68ec")),
                ECKey.fromPublicOnly(HEX.decode(
                        "04cfb4113b3387637131ebec76871fd2760fc430dd16de0110f0eb07bb31ffac85e2607c189cb8582ea1ccaeb64ffd655409106589778f3000fdfe3263440b0350")),
                ECKey.fromPublicOnly(HEX.decode(
                        "04b2f30018908a59e829c1534bfa5010d7ef7f79994159bba0f534d863ef9e4e973af6a8de20dc41dbea50bc622263ec8a770b2c9406599d39e4c9afe61f8b1613")));
        wallet = new Wallet(PARAMS, group);

        super.setUp(store);
    }

    @After
    public void tearDown() {
        super.tearDown();
    }

       @Test
    public void deserializeFilteredBlock() throws Exception {
        // Random real block
        // (000000000000dab0130bbcc991d3d7ae6b81aa6f50a798888dfe62337458dc45)
        // With one tx
        FilteredBlock block = new FilteredBlock(PARAMS, HEX.decode(
                "0100000079cda856b143d9db2c1caff01d1aecc8630d30625d10e8b4b8b0000000000000b50cc069d6a3e33e3ff84a5c41d9d3febe7c770fdcc96b2c3ff60abe184f196367291b4d4c86041b8fa45d630100000001b50cc069d6a3e33e3ff84a5c41d9d3febe7c770fdcc96b2c3ff60abe184f19630101"));

        // Check that the header was properly deserialized
        assertTrue(block.getBlockHeader().getHash()
                .equals(Sha256Hash.wrap("000000000000dab0130bbcc991d3d7ae6b81aa6f50a798888dfe62337458dc45")));

        // Check that the partial merkle tree is correct
        List<Sha256Hash> txesMatched = block.getTransactionHashes();
        assertTrue(txesMatched.size() == 1);
        assertTrue(txesMatched
                .contains(Sha256Hash.wrap("63194f18be0af63f2c6bc9dc0f777cbefed3d9415c4af83f3ee3a3d669c00cb5")));

        // Check round tripping.
        assertEquals(block, new FilteredBlock(PARAMS, block.bitcoinSerialize()));
    }

    // Binary is incompatible @Test
    public void createFilteredBlock() throws Exception {
        ECKey key1 = new ECKey();
        ECKey key2 = new ECKey();
        Transaction tx1 = FakeTxBuilder.createFakeTx(PARAMS, Coin.COIN, key1);
        Transaction tx2 = FakeTxBuilder.createFakeTx(PARAMS, Coin.FIFTY_COINS, key2.toAddress(PARAMS));
        Block block = FakeTxBuilder.makeSolvedTestBlock(PARAMS.getGenesisBlock(),
                Address.fromBase58(PARAMS, "msg2t2V2sWNd85LccoddtWysBTR8oPnkzW"), tx1, tx2);
        BloomFilter filter = new BloomFilter(4, 0.1, 1);
        filter.insert(key1);
        filter.insert(key2);
        FilteredBlock filteredBlock = filter.applyAndUpdate(block);
        assertEquals(4, filteredBlock.getTransactionCount());
        // This call triggers verification of the just created data.
        List<Sha256Hash> txns = filteredBlock.getTransactionHashes();
        assertTrue(txns.contains(tx1.getHash()));
        assertTrue(txns.contains(tx2.getHash()));
    }

    private Sha256Hash numAsHash(int num) {
        byte[] bits = new byte[32];
        bits[0] = (byte) num;
        return Sha256Hash.wrap(bits);
    }

    // Binary is incompatible @Test  @Test(expected = VerificationException.class)
    public void merkleTreeMalleability() throws Exception {
        List<Sha256Hash> hashes = Lists.newArrayList();
        for (byte i = 1; i <= 10; i++)
            hashes.add(numAsHash(i));
        hashes.add(numAsHash(9));
        hashes.add(numAsHash(10));
        byte[] includeBits = new byte[2];
        Utils.setBitLE(includeBits, 9);
        Utils.setBitLE(includeBits, 10);
        PartialMerkleTree pmt = PartialMerkleTree.buildFromLeaves(PARAMS, includeBits, hashes);
        List<Sha256Hash> matchedHashes = Lists.newArrayList();
        pmt.getTxnHashAndMerkleRoot(matchedHashes);
    }

    
}
