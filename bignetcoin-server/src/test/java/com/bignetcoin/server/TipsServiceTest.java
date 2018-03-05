package com.bignetcoin.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.FullPrunedBlockGraph;
import org.bitcoinj.core.MySQLFullPrunedBlockChainTest;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.bignetcoin.server.service.TipsService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TipsServiceTest extends MySQLFullPrunedBlockChainTest {

    @Mock
    TipsService tipsManager;
    
    @Test
    public void testECKey() {
        for (int i = 0; i < 100; i ++) {
            ECKey outKey = new ECKey();
            System.out.println("prK : " + outKey.getPrivateKeyAsHex() + ", " + outKey.getPublicKeyAsHex());
        }
    }

    @Test
    public void updateLinearRatingsTestWorks() throws Exception {
        final int UNDOABLE_BLOCKS_STORED = 10;
        store = createStore(PARAMS, UNDOABLE_BLOCKS_STORED);
        resetStore(store);
        blockgraph = new FullPrunedBlockGraph(PARAMS, store);

        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = PARAMS.getGenesisBlock().createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = rollingBlock.createNextBlockWithCoinbase(Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }

        Map<Sha256Hash, Set<Sha256Hash>> ratings = new HashMap<>();
        tipsManager.updateHashRatings(rollingBlock.getHash(), ratings, new HashSet<>());

    }

}