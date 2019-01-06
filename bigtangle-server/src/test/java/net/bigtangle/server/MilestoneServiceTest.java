/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockForTest;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MilestoneServiceTest extends AbstractIntegrationTest {

    @Test
    public void testUnconfirmedOutput() throws Exception {
        store.resetStore();
        
        // Create block with UTXO
        Transaction tx1 = makeTestTransaction();
        Block depBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                networkParameters.getGenesisBlock(), tx1);
        
        milestoneService.update();

        // Create block with dependency
        Transaction tx2 = makeTestTransaction();
        Block block = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                networkParameters.getGenesisBlock(), tx2);
        
        store.resetStore();

        blockGraph.add(depBlock, false);
        blockGraph.add(block, false);
        
        Block b1 = createAndAddNextBlock(depBlock, Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), block);
        
        milestoneService.update(1);

        // One update cycle should not allow the second transaction to go through since it was not confirmed yet
        assertTrue(blockService.getBlockEvaluation(depBlock.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(block.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        
        milestoneService.update(1);

        // Second update cycle should allow all through
        assertTrue(blockService.getBlockEvaluation(depBlock.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(block.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
    }

    @Test
    public void testPrunedConflict() throws Exception {
        store.resetStore();
        
        // Create block with UTXO
        Transaction tx1 = makeTestTransaction();
        Block txBlock1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                networkParameters.getGenesisBlock(), tx1);
     
        // Generate blocks until first ones become unmaintained
        Block rollingBlock = BlockForTest.createNextBlock(txBlock1,
                Block.BLOCK_VERSION_GENESIS, txBlock1);
        blockGraph.add(rollingBlock, true);

        for (int i = 0; i < NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF + 5; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS,rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();
        milestoneService.update();

        // First block is no longer maintained, while newest one is maintained
        assertFalse(blockService.getBlockEvaluation(txBlock1.getHash()).isMaintained());
        assertTrue(blockService.getBlockEvaluation(rollingBlock.getHash()).isMaintained());
        
        // All confirmed
        assertTrue(blockService.getBlockEvaluation(txBlock1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(rollingBlock.getHash()).isMilestone());
        
        // Create conflicting block with UTXO
        Block txBlock2 = createAndAddNextBlockWithTransaction(rollingBlock, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                rollingBlock, tx1);

        milestoneService.update();
        milestoneService.update();

        // No change in maintenance
        assertFalse(blockService.getBlockEvaluation(txBlock1.getHash()).isMaintained());
        assertTrue(blockService.getBlockEvaluation(rollingBlock.getHash()).isMaintained());
        assertTrue(blockService.getBlockEvaluation(txBlock2.getHash()).isMaintained());
        
        // Confirmation should stay true except for conflict
        assertTrue(blockService.getBlockEvaluation(txBlock1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(rollingBlock.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(txBlock2.getHash()).isMilestone());
    }

    @Test
    public void testUpdateWithConflict() throws Exception {
        store.resetStore();

        Block b1 = createAndAddNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), networkParameters.getGenesisBlock());
        Block b2 = createAndAddNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), networkParameters.getGenesisBlock());
        Block b3 = createAndAddNextBlock(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b2);
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isMilestone());

        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        // use UTXO to create double spending, this can not be created with
        // wallet
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, outKey));
        TransactionInput input = doublespendTX.addInput(spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        // Create blocks with a conflict
        Block b5 = createAndAddNextBlockWithTransaction(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b3, doublespendTX);
        Block b5link = createAndAddNextBlock(b5, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b5);
        Block b6 = createAndAddNextBlock(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b3);
        Block b7 = createAndAddNextBlock(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b3);
        Block b8 = createAndAddNextBlockWithTransaction(b6, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b7, doublespendTX);
        Block b8link = createAndAddNextBlock(b8, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8);
        Block b9 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b6);
        Block b10 = createAndAddNextBlock(b9, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        Block b11 = createAndAddNextBlock(b9, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        Block b12 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        Block b13 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        Block b14 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        Block bOrphan1 = createAndAddNextBlock(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b1);
        Block bOrphan5 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b5link);
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b5.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b5link.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8link.getHash()).isMilestone());
        // assertFalse(blockService.getBlockEvaluation(b9.getHash()).isMilestone());
        // assertFalse(blockService.getBlockEvaluation(b10.getHash()).isMilestone());
        // assertFalse(blockService.getBlockEvaluation(b11.getHash()).isMilestone());
        // assertFalse(blockService.getBlockEvaluation(b12.getHash()).isMilestone());
        // assertFalse(blockService.getBlockEvaluation(b13.getHash()).isMilestone());
        // assertFalse(blockService.getBlockEvaluation(b14.getHash()).isMilestone());
        // assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash()).isMilestone());
        // assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash()).isMilestone());

        // Now make block 8 heavier and higher rated than b5 to make it
        // disconnect block
        // 5+link and connect block 8+link instead
        Block b8weight1 = createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b8link);
        Block b8weight2 = createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b8link);
        Block b8weight3 = createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b8link);
        Block b8weight4 = createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b8link);

        //extra weights to ensure this works
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isMilestone());
        // sometimes this won't work since probabilistic. this is tested later with additional weights
        assertFalse(blockService.getBlockEvaluation(b5.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b5link.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b8.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b8link.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b9.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b10.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b11.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b12.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b13.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b14.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8weight1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8weight2.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8weight3.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8weight4.getHash()).isMilestone());

        // Lastly, there will be a milestone-candidate conflict in the last
        // update that
        // should not change anything
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b5.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b5link.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b8.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b8link.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b9.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b10.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b11.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b12.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b13.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b14.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8weight1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8weight2.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8weight3.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b8weight4.getHash()).isMilestone());

        // Check heights (handmade tests)
        assertEquals(0, blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).getHeight());
        assertEquals(1, blockService.getBlockEvaluation(b1.getHash()).getHeight());
        assertEquals(1, blockService.getBlockEvaluation(b2.getHash()).getHeight());
        assertEquals(2, blockService.getBlockEvaluation(b3.getHash()).getHeight());
        assertEquals(3, blockService.getBlockEvaluation(b5.getHash()).getHeight());
        assertEquals(4, blockService.getBlockEvaluation(b5link.getHash()).getHeight());
        assertEquals(3, blockService.getBlockEvaluation(b6.getHash()).getHeight());
        assertEquals(3, blockService.getBlockEvaluation(b7.getHash()).getHeight());
        assertEquals(4, blockService.getBlockEvaluation(b8.getHash()).getHeight());
        assertEquals(5, blockService.getBlockEvaluation(b8link.getHash()).getHeight());
        assertEquals(5, blockService.getBlockEvaluation(b9.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b10.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b11.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b12.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b13.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b14.getHash()).getHeight());
        assertEquals(2, blockService.getBlockEvaluation(bOrphan1.getHash()).getHeight());
        assertEquals(5, blockService.getBlockEvaluation(bOrphan5.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b8weight1.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b8weight2.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b8weight3.getHash()).getHeight());
        assertEquals(6, blockService.getBlockEvaluation(b8weight4.getHash()).getHeight());

        // Check depths (handmade tests)
        assertEquals(6, blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).getDepth());
        assertEquals(5, blockService.getBlockEvaluation(b1.getHash()).getDepth());
        assertEquals(5, blockService.getBlockEvaluation(b2.getHash()).getDepth());
        assertEquals(4, blockService.getBlockEvaluation(b3.getHash()).getDepth());
        assertEquals(3, blockService.getBlockEvaluation(b5.getHash()).getDepth());
        assertEquals(2, blockService.getBlockEvaluation(b5link.getHash()).getDepth());
        assertEquals(3, blockService.getBlockEvaluation(b6.getHash()).getDepth());
        assertEquals(3, blockService.getBlockEvaluation(b7.getHash()).getDepth());
        assertEquals(2, blockService.getBlockEvaluation(b8.getHash()).getDepth());
        assertEquals(1, blockService.getBlockEvaluation(b8link.getHash()).getDepth());
        assertEquals(1, blockService.getBlockEvaluation(b9.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b10.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b11.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b12.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b13.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b14.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(bOrphan1.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(bOrphan5.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8weight1.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8weight2.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8weight3.getHash()).getDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8weight4.getHash()).getDepth());

        // Check cumulative weights (handmade tests)
        assertEquals(30,
                blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).getCumulativeWeight());
        assertEquals(28, blockService.getBlockEvaluation(b1.getHash()).getCumulativeWeight());
        assertEquals(27, blockService.getBlockEvaluation(b2.getHash()).getCumulativeWeight());
        assertEquals(26, blockService.getBlockEvaluation(b3.getHash()).getCumulativeWeight());
        assertEquals(9, blockService.getBlockEvaluation(b5.getHash()).getCumulativeWeight());
        assertEquals(8, blockService.getBlockEvaluation(b5link.getHash()).getCumulativeWeight());
        assertEquals(21, blockService.getBlockEvaluation(b6.getHash()).getCumulativeWeight());
        assertEquals(20, blockService.getBlockEvaluation(b7.getHash()).getCumulativeWeight());
        assertEquals(19, blockService.getBlockEvaluation(b8.getHash()).getCumulativeWeight());
        assertEquals(18, blockService.getBlockEvaluation(b8link.getHash()).getCumulativeWeight());
        assertEquals(3, blockService.getBlockEvaluation(b9.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b10.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b11.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b12.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b13.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b14.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(bOrphan1.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(bOrphan5.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b8weight1.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b8weight2.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b8weight3.getHash()).getCumulativeWeight());
        assertEquals(1, blockService.getBlockEvaluation(b8weight4.getHash()).getCumulativeWeight());

        // Check milestone depths (handmade tests)
        assertEquals(5,
                blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).getMilestoneDepth());
        assertEquals(4, blockService.getBlockEvaluation(b1.getHash()).getMilestoneDepth());
        assertEquals(4, blockService.getBlockEvaluation(b2.getHash()).getMilestoneDepth());
        assertEquals(3, blockService.getBlockEvaluation(b3.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b5.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b5link.getHash()).getMilestoneDepth());
        assertEquals(2, blockService.getBlockEvaluation(b6.getHash()).getMilestoneDepth());
        assertEquals(2, blockService.getBlockEvaluation(b7.getHash()).getMilestoneDepth());
        assertEquals(1, blockService.getBlockEvaluation(b8.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8link.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b9.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b10.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b11.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b12.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b13.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b14.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(bOrphan1.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(bOrphan5.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b8weight1.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b8weight2.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b8weight3.getHash()).getMilestoneDepth());
        assertEquals(-1, blockService.getBlockEvaluation(b8weight4.getHash()).getMilestoneDepth());

        // Added: extra weights to ensure this works
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link);
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b5.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b5link.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b8.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b8link.getHash()).isMilestone());
    }

    // TODO feature not implemented @Test
    public void testReorgUnmaintained() throws Exception {
        store.resetStore();

        // Generate blocks until first ones become unmaintained
        Block rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        for (int i = 0; i < NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF + 5; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS,rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();
        milestoneService.update();
        
        Block oldTangleBlock = rollingBlock;

        // Genesis block is no longer maintained, while newest one is maintained
        assertFalse(blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).isMaintained());
        assertTrue(blockService.getBlockEvaluation(oldTangleBlock.getHash()).isMaintained());
        assertTrue(blockService.getBlockEvaluation(oldTangleBlock.getHash()).isMilestone());

        // Generate longer new Tangle
        rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        for (int i = 0; i < NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF + 25; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();
        Block newTangleBlock = rollingBlock;

        // New Tangle should now be in Milestone?
        assertFalse(blockService.getBlockEvaluation(oldTangleBlock.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(newTangleBlock.getHash()).isMilestone());
    }

    @Test
    public void testReorgMaintained() throws Exception {
        store.resetStore();

        // Generate two conflicting blocks where the second block approves the
        // first
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, outKey));
        TransactionInput input = doublespendTX.addInput(spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        // Create blocks with conflict
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), networkParameters.getGenesisBlock(),
                doublespendTX);
        blockGraph.add(b1, true);
        Block b2 = createAndAddNextBlockWithTransaction(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b1, doublespendTX);
        blockGraph.add(b2, true);

        // Approve these blocks by adding linear tangle onto them
        Block rollingBlock = b2;
        for (int i = 0; i < 10; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();

        // Second block may not be added, only first one
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b2.getHash()).isMilestone());

        // Add blocks via tip selection
        for (int i = 1; i < 30; i++) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            Block b = BlockForTest.createNextBlock(r2, Block.BLOCK_VERSION_GENESIS,
                    r1);
            blockGraph.add(b, true);
        }
        milestoneService.update();

        // Ensure the second block eventually loses and is not
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).getRating() < 50);
    }
}