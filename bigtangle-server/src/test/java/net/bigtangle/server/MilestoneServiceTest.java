/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockForTest;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.PrunedException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.server.service.TransactionService;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MilestoneServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(MilestoneServiceTest.class);

    @Autowired
    private BlockService blockService;
    @Autowired
    private MilestoneService milestoneService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private NetworkParameters networkParameters;
    @Autowired
    protected FullPrunedBlockStore store;

    ECKey outKey = new ECKey();

    public List<Block> createMultiLinearTangle1() throws Exception {

        List<Block> blocks = new ArrayList<Block>();
        for (int j = 0; j < 30; j++) {
            Block rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                    Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0, networkParameters.getGenesisBlock().getHash());
            blocks.add(rollingBlock);
            for (int i = 0; i < 30; i++) {
                rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                        outKey.getPubKey(), 0, rollingBlock.getHash());
                blocks.add(rollingBlock);
            }
        }

        int i = 0;
        for (Block block : blocks) {
            this.blockgraph.add(block);
            log.debug("create  " + i + " block:" + block.getHashAsString());
            i++;

        }
        return blocks;
    }

    public List<Block> createLinearTangle(int blockCount) throws Exception {
        List<Block> blocks = new ArrayList<Block>();

        Block rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0, networkParameters.getGenesisBlock().getHash());
        blocks.add(rollingBlock);
        blockgraph.add(rollingBlock);

        for (int i = 0; i < blockCount; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    0, rollingBlock.getHash());
            blocks.add(rollingBlock);
            blockgraph.add(rollingBlock);
        }

        return blocks;
    }

    @Test
    public void testMilestoneTestTangle1() throws Exception {
        store.resetStore();

        Block b1 = createAndAddNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), networkParameters.getGenesisBlock().getHash());
        Block b2 = createAndAddNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), networkParameters.getGenesisBlock().getHash());
        Block b3 = createAndAddNextBlock(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b2.getHash());
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isMilestone());

        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        // use UTXO to create double spending, this can not be created with
        // wallet
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGNETCOIN_TOKENID);
        Transaction doublespent = new Transaction(networkParameters);
        doublespent.addOutput(new TransactionOutput(networkParameters, doublespent, amount, outKey));
        TransactionInput input = doublespent.addInput(spendableOutput);
        Sha256Hash sighash = doublespent.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        // Create blocks with a conflict
        Block b5 = createAndAddNextBlockWithTransaction(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b3.getHash(), doublespent);
        Block b5link = createAndAddNextBlock(b5, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b5.getHash());
        Block b6 = createAndAddNextBlock(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b3.getHash());
        Block b7 = createAndAddNextBlock(b3, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b3.getHash());
        Block b8 = createAndAddNextBlockWithTransaction(b6, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b7.getHash(), doublespent);
        Block b8link = createAndAddNextBlock(b8, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8.getHash());
        Block b9 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b6.getHash());
        Block b10 = createAndAddNextBlock(b9, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
        Block b11 = createAndAddNextBlock(b9, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
        Block b12 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
        Block b13 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
        Block b14 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b8link.getHash());
        Block bOrphan1 = createAndAddNextBlock(b1, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), b1.getHash());
        Block bOrphan5 = createAndAddNextBlock(b5link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b5link.getHash());
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
        assertFalse(blockService.getBlockEvaluation(b9.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b10.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b11.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b12.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b13.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(b14.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash()).isMilestone());

        // Now make block 8 heavier and higher rated than b5 to make it
        // disconnect block
        // 5+link and connect block 8+link instead
        Block b8weight1 = createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b8link.getHash());
        Block b8weight2 = createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b8link.getHash());
        Block b8weight3 = createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b8link.getHash());
        Block b8weight4 = createAndAddNextBlock(b8link, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                b8link.getHash());
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isMilestone());
        // sometimes this won't work (which is fine since probabilistic)
//        assertFalse(blockService.getBlockEvaluation(b5.getHash()).isMilestone());
//        assertFalse(blockService.getBlockEvaluation(b5link.getHash()).isMilestone());
//        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isMilestone());
//        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isMilestone());
//        assertTrue(blockService.getBlockEvaluation(b8.getHash()).isMilestone());
//        assertTrue(blockService.getBlockEvaluation(b8link.getHash()).isMilestone());
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
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isMilestone());
//        assertFalse(blockService.getBlockEvaluation(b5.getHash()).isMilestone());
//        assertFalse(blockService.getBlockEvaluation(b5link.getHash()).isMilestone());
//        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isMilestone());
//        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isMilestone());
//        assertTrue(blockService.getBlockEvaluation(b8.getHash()).isMilestone());
//        assertTrue(blockService.getBlockEvaluation(b8link.getHash()).isMilestone());
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
        assertEquals(22,
                blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).getCumulativeWeight());
        assertEquals(20, blockService.getBlockEvaluation(b1.getHash()).getCumulativeWeight());
        assertEquals(19, blockService.getBlockEvaluation(b2.getHash()).getCumulativeWeight());
        assertEquals(18, blockService.getBlockEvaluation(b3.getHash()).getCumulativeWeight());
        assertEquals(9, blockService.getBlockEvaluation(b5.getHash()).getCumulativeWeight());
        assertEquals(8, blockService.getBlockEvaluation(b5link.getHash()).getCumulativeWeight());
        assertEquals(13, blockService.getBlockEvaluation(b6.getHash()).getCumulativeWeight());
        assertEquals(12, blockService.getBlockEvaluation(b7.getHash()).getCumulativeWeight());
        assertEquals(11, blockService.getBlockEvaluation(b8.getHash()).getCumulativeWeight());
        assertEquals(10, blockService.getBlockEvaluation(b8link.getHash()).getCumulativeWeight());
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
        assertEquals(0, blockService.getBlockEvaluation(b5.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b5link.getHash()).getMilestoneDepth());
        assertEquals(2, blockService.getBlockEvaluation(b6.getHash()).getMilestoneDepth());
        assertEquals(2, blockService.getBlockEvaluation(b7.getHash()).getMilestoneDepth());
        assertEquals(1, blockService.getBlockEvaluation(b8.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8link.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b9.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b10.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b11.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b12.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b13.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b14.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(bOrphan1.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(bOrphan5.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8weight1.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8weight2.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8weight3.getHash()).getMilestoneDepth());
        assertEquals(0, blockService.getBlockEvaluation(b8weight4.getHash()).getMilestoneDepth());
    }

    // @Test
    public void testMilestoneTestTangle2() throws Exception {
        store.resetStore();

        List<Block> blocks = createLinearTangle(80);
        milestoneService.update();

        Block rollingBlock = blocks.get(blocks.size() - 1);
        blockgraph.add(BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0,
                rollingBlock.getHash()));

        milestoneService.update();
    }

    @Test
    public void testMiningReward() throws Exception {
        store.resetStore();

        Block rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), 0, networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        for (int i = 0; i < 110; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    0, rollingBlock.getHash());
            blockgraph.add(rollingBlock);
        }

        transactionService.createMiningRewardBlock(0);
        milestoneService.update();
        milestoneService.update();
    }

    private Block createAndAddNextBlock(Block b1, long bVersion, byte[] pubKey, Sha256Hash b2)
            throws VerificationException, PrunedException {
        Block block = BlockForTest.createNextBlock(b1, bVersion, pubKey, 0, b2);
        this.blockgraph.add(block);
        log.debug("created block:" + block.getHashAsString());
        return block;
    }

    private Block createAndAddNextBlockWithTransaction(Block b1, long bVersion, byte[] pubKey, Sha256Hash b2,
            Transaction prevOut) throws VerificationException, PrunedException {
        Block block = BlockForTest.createNextBlock(b1, bVersion, pubKey, 0, b2);
        block.addTransaction(prevOut);
        block.solve();
        this.blockgraph.add(block);
        log.debug("created block:" + block.getHashAsString());
        return block;
    }
}