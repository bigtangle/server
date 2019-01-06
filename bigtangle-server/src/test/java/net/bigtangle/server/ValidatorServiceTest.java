/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
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
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ValidatorServiceTest extends AbstractIntegrationTest {
    
    // TODO manual creation of tests via summary consensus rules.md
    // TODO code coverage
    // TODO refactor abstractintegrationtest, other tests etc. 
    
    @Test(expected=VerificationException.class)
    public void testVerificationFutureTimestamp() throws Exception {
        store.resetStore();
        
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        Block b = BlockForTest.createNextBlock(r2, Block.BLOCK_VERSION_GENESIS,  r1);
        b.setTime(1577836800); // 01/01/2020 @ 12:00am (UTC)
        b.solve();
        blockService.saveBlock(b);
    }

    @Test(expected=VerificationException.class)
    public void testVerificationIncorrectPoW() throws Exception {
        store.resetStore();
        
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        Block b = BlockForTest.createNextBlock(r2, Block.BLOCK_VERSION_GENESIS,  r1);
        b.setTime(1377836800);
        blockService.saveBlock(b);
    }

    @Test
    public void testUnsolidBlockAllowed() throws Exception {
        store.resetStore();
        
        Sha256Hash sha256Hash1 = getRandomSha256Hash();
        Sha256Hash sha256Hash2 = getRandomSha256Hash();
        Block block = new Block(this.networkParameters, sha256Hash1, sha256Hash2, Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
        block.solve();
        System.out.println(block.getHashAsString());

        // Send over kafka method to allow unsolids
        transactionService.addConnected(block.bitcoinSerialize(), true, false);
    }
    
    @Test
    public void testUnsolidBlockDisallowed() throws Exception {
        store.resetStore();
        
        Sha256Hash sha256Hash1 = getRandomSha256Hash();
        Sha256Hash sha256Hash2 = getRandomSha256Hash();
        Block block = new Block(this.networkParameters, sha256Hash1, sha256Hash2, Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
        block.solve();
        System.out.println(block.getHashAsString());

        // Send over API method to disallow unsolids
        blockService.saveBlock(block);
        
        // Should not be added since insolid
        assertNull(store.get(block.getHash()));
    }

    @Test
    public void testUnsolidBlockReconnectBlock() throws Exception {
        store.resetStore();
        
        Block depBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = new Block(this.networkParameters, sha256Hash, sha256Hash, Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
        block.solve();
        System.out.println(block.getHashAsString());
        transactionService.addConnected(block.bitcoinSerialize(), true, false);
        
        // Should not be added since insolid
        assertNull(store.get(block.getHash()));

        // Add missing dependency
        blockService.saveBlock(depBlock);

        // After adding the missing dependency, should be added 
        assertNotNull(store.get(block.getHash()));
        assertNotNull(store.get(depBlock.getHash()));
    }

    @Test
    public void testUnsolidMissingPredecessor1() throws Exception {
        store.resetStore();
        
        Block depBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = new Block(this.networkParameters, sha256Hash, networkParameters.getGenesisBlock().getHash(), Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
        block.solve();
        System.out.println(block.getHashAsString());
        transactionService.addConnected(block.bitcoinSerialize(), true, false);
        
        // Should not be added since insolid
        assertNull(store.get(block.getHash()));

        // Add missing dependency
        blockService.saveBlock(depBlock);

        // After adding the missing dependency, should be added 
        assertNotNull(store.get(block.getHash()));
        assertNotNull(store.get(depBlock.getHash()));
    }

    @Test
    public void testUnsolidMissingPredecessor2() throws Exception {
        store.resetStore();
        
        Block depBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS,
                networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = new Block(this.networkParameters, networkParameters.getGenesisBlock().getHash(), sha256Hash, Block.Type.BLOCKTYPE_TRANSFER,
                System.currentTimeMillis() / 1000, 0, Block.EASIEST_DIFFICULTY_TARGET);
        block.solve();
        System.out.println(block.getHashAsString());
        transactionService.addConnected(block.bitcoinSerialize(), true, false);
        
        // Should not be added since insolid
        assertNull(store.get(block.getHash()));

        // Add missing dependency
        blockService.saveBlock(depBlock);

        // After adding the missing dependency, should be added 
        assertNotNull(store.get(block.getHash()));
        assertNotNull(store.get(depBlock.getHash()));
    }

    @Test
    public void testUnsolidMissingUTXO() throws Exception {
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
        
        // Add block allowing unsolids
        transactionService.addConnected(block.bitcoinSerialize(), true, false);
        
        // Should not be added since insolid
        assertNull(store.get(block.getHash()));

        // Add missing dependency
        blockService.saveBlock(depBlock);

        // After adding the missing dependency, should be added 
        assertNotNull(store.get(block.getHash()));
        assertNotNull(store.get(depBlock.getHash()));
    }

    // TODO reward min distance to height
    @Test
    public void testUnsolidMissingReward() throws Exception {
        store.resetStore();
        List<Block> blocks1 = new ArrayList<>();
        List<Block> blocks2 = new ArrayList<>();

        // Generate blocks until passing first reward interval and second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + 2; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blocks1.add(rollingBlock);
        }
        for (Block b : blocks1) {
            blockgraph.add(b, true);            
        }
        milestoneService.update();
        
        // Generate eligible mining reward block
        Block rewardBlock1 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        milestoneService.update();

        // Mining reward block should go through
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());
        
        // Make more for next reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + 2; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blocks2.add(rollingBlock);
        }
        for (Block b : blocks2) {
            blockgraph.add(b, true);            
        }
        milestoneService.update();
        
        // Generate eligible second mining reward block
        Block rewardBlock2 = transactionService.createMiningRewardBlock(rewardBlock1.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        milestoneService.update();
        
        store.resetStore();
        for (Block b : blocks1) {
            blockgraph.add(b, true);            
        }
        for (Block b : blocks2) {
            blockgraph.add(b, true);            
        }
        milestoneService.update();
        
        // Add block allowing unsolids
        transactionService.addConnected(rewardBlock2.bitcoinSerialize(), true, false);
        
        // Should not be added since insolid
        assertNull(store.get(rewardBlock2.getHash()));

        // Add missing dependency
        blockService.saveBlock(rewardBlock1);

        // After adding the missing dependency, should be added 
        assertNotNull(store.get(rewardBlock1.getHash()));
        assertNotNull(store.get(rewardBlock2.getHash()));
    }

    @Test
    public void testUnsolidMissingToken() throws Exception {
        store.resetStore();
        
        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();

        TokenInfo tokenInfo = new TokenInfo();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, false);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block depBlock = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate second eligible issuance
        TokenInfo tokenInfo2 = new TokenInfo();        
        Token tokens2 = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 1, amount, false, false);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
        
        store.resetStore();
        
        // Add block allowing unsolids
        transactionService.addConnected(block.bitcoinSerialize(), true, false);
        
        // Should not be added since insolid
        assertNull(store.get(block.getHash()));

        // Add missing dependency
        blockService.saveBlock(depBlock);

        // After adding the missing dependency, should be added 
        assertNotNull(store.get(block.getHash()));
        assertNotNull(store.get(depBlock.getHash()));
    }

    @Test
    public void testReorgToken() throws Exception {
        store.resetStore();

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
        milestoneService.update();

        // Should go through
        assertTrue(blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        Transaction tx1 = block1.getTransactions().get(0);
        assertTrue(store.getTransactionOutput(tx1.getHash(), 0).isConfirmed());
        assertTrue(store.getTokenConfirmed(block1.getHashAsString()));

        // Remove it from the milestone
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 1; i < 5; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blockgraph.add(rollingBlock, true);
        }
        milestoneService.update();

        // Should be out
        assertFalse(blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        assertFalse(store.getTransactionOutput(tx1.getHash(), 0).isConfirmed());
        assertFalse(store.getTokenConfirmed(block1.getHashAsString()));
    }

    @Test
    public void testConflictToken() throws Exception {
        store.resetStore();

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, true);

        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Make another conflicting one that goes through
        Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block block2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, genHash, genHash);
        Block rollingBlock = BlockForTest.createNextBlock(block2, Block.BLOCK_VERSION_GENESIS, block1);
        blockgraph.add(rollingBlock, true);
        milestoneService.update();

        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                && blockService.getBlockEvaluation(block1.getHash()).isMilestone());

        // TODO Generate differing ineligible issuances
        // TODO Generate issuance continuation
    }

    @Test
    public void testReorgMiningReward() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
        blockgraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + 2; i++) {
            rollingBlock1 = BlockForTest.createNextBlock(rollingBlock1, Block.BLOCK_VERSION_GENESIS, rollingBlock1);
            blockgraph.add(rollingBlock1, true);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + 2; i++) {
            rollingBlock2 = BlockForTest.createNextBlock(rollingBlock2, Block.BLOCK_VERSION_GENESIS, rollingBlock2);
            blockgraph.add(rollingBlock2, true);
        }

        Block fusingBlock = BlockForTest.createNextBlock(rollingBlock1,
                Block.BLOCK_VERSION_GENESIS, rollingBlock2);
        blockgraph.add(fusingBlock, true);
        

        // Generate ineligible mining reward block
        Block rewardBlock1 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        milestoneService.update();

        // Mining reward block should usually not go through since not sufficiently approved
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());

        // Generate eligible mining reward blocks (different prevblocks for
        // different
        // coinbases for the sake of testing)
        Block rewardBlock2 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        Block rewardBlock3 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock2.getHash());
        milestoneService.update();

        // Second mining reward block should now go through since everything is
        // updated
        rollingBlock = rewardBlock2;
        for (int i = 1; i < 20; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blockgraph.add(rollingBlock, true);
        }
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isMilestone());

        // Third mining reward block should now instead go through since
        // everything is
        // updated
        rollingBlock = rewardBlock3;
        for (int i = 1; i < 40; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blockgraph.add(rollingBlock, true);
        }
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isMilestone());

        // Check that not both mining blocks get approved
        for (int i = 1; i < 10; i++) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            Block b = BlockForTest.createNextBlock(r2, Block.BLOCK_VERSION_GENESIS, r1);
            blockgraph.add(b, true);
        }
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isMilestone());
    }

    //TODO this doesn't work because of the current difficulty maximum @Test
    public void testSolidityPredecessorDifficultyInheritance() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval and second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            Block rollingBlockNew = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            
            // The difficulty should be equal to the previous difficulty
            assertEquals(rollingBlock.getDifficultyTarget(), rollingBlockNew.getDifficultyTarget());
            
            rollingBlock = rollingBlockNew;
            blockgraph.add(rollingBlock, true);     
        }
        milestoneService.update();
        
        // Generate eligible mining reward block
        Block rewardBlock1 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());

        // The difficulty should now not be equal to the previous difficulty
        assertNotEquals(rollingBlock.getDifficultyTarget(), rewardBlock1.getDifficultyTarget());

        for (int i = 0; i < 3; i++) {
            Block rollingBlockNew = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            
            // The difficulty should be equal to the previous difficulty
            assertEquals(rollingBlock.getDifficultyTarget(), rollingBlockNew.getDifficultyTarget());
            
            rollingBlock = rollingBlockNew;
            blockgraph.add(rollingBlock, true);     
        }
    }

    @Test
    public void testSolidityPredecessorConsensusInheritance() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval and second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            Block rollingBlockNew = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            
            // The difficulty should be equal to the previous difficulty
            assertEquals(rollingBlock.getLastMiningRewardBlock(), rollingBlockNew.getLastMiningRewardBlock());
            
            rollingBlock = rollingBlockNew;
            blockgraph.add(rollingBlock, true);     
        }
        milestoneService.update();
        
        // Generate eligible mining reward block
        Block rewardBlock1 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());

        // The difficulty should now not be equal to the previous difficulty
        assertNotEquals(rollingBlock.getLastMiningRewardBlock(), rewardBlock1.getLastMiningRewardBlock());

        for (int i = 0; i < 3; i++) {
            Block rollingBlockNew = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            
            // The difficulty should be equal to the previous difficulty
            assertEquals(rollingBlock.getLastMiningRewardBlock(), rollingBlockNew.getLastMiningRewardBlock());
            
            rollingBlock = rollingBlockNew;
            blockgraph.add(rollingBlock, true);     
        }
    }

    @Test
    public void testSolidityPredecessorTimeInheritance() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval and second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            Block rollingBlockNew = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            
            // The time should not be moving backwards
            assertTrue(rollingBlock.getTimeSeconds() <= rollingBlockNew.getTimeSeconds());
            
            rollingBlock = rollingBlockNew;
            blockgraph.add(rollingBlock, true);     
        }
        milestoneService.update();

        // The time is allowed to stay the same
        Block b = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
        b.setTime(rollingBlock.getTimeSeconds()); // 01/01/2000 @ 12:00am (UTC)
        b.solve();
        blockgraph.add(b, true);
        
        // The time is not allowed to move backwards
        try {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            rollingBlock.setTime(946684800); // 01/01/2000 @ 12:00am (UTC)
            b.solve();
            blockgraph.add(rollingBlock, true);     
            fail();
        } catch (VerificationException e) {
        }
    }

    @Test
    public void testSolidityCoinbase() throws Exception {
        store.resetStore();

        // TODO test all cases (with working if possible and) not working coinbases
    }

    @Test
    public void testSolidityTXInputScriptsCorrect() throws Exception {
        store.resetStore();
        
        // Create block with UTXO
        Transaction tx1 = makeTestTransaction();
        createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                networkParameters.getGenesisBlock(), tx1);
        
        store.resetStore();

        // Again but with incorrect input script
        try {
            tx1.getInput(0).setScriptSig(new Script(new byte[0]));
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    networkParameters.getGenesisBlock(), tx1);
            fail();
        } catch (VerificationException e) {
        }
    }

    @Test
    public void testSolidityTXOutputSumCorrect() throws Exception {
        store.resetStore();

        // Create block with UTXO
        {
            Transaction tx1 = makeTestTransaction();
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    networkParameters.getGenesisBlock(), tx1);
        }
        
        store.resetStore();

        // Again but with less output coins
        {
            @SuppressWarnings("deprecation")
            ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
            List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                    0);
            Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
            Transaction tx2 = new Transaction(networkParameters);
            tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, genesiskey));
            tx2.addOutput(new TransactionOutput(networkParameters, tx2, spendableOutput.getValue().subtract(amount).subtract(amount), genesiskey));
            TransactionInput input = tx2.addInput(spendableOutput);
            Sha256Hash sighash = tx2.hashForSignature(0, spendableOutput.getScriptBytes(),
                    Transaction.SigHash.ALL, false);
            TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                    false);
            Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
            input.setScriptSig(inputScript);
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    networkParameters.getGenesisBlock(), tx2);
        }
        
        store.resetStore();

        // Again but with more output coins
        try {
            @SuppressWarnings("deprecation")
            ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
            List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                    0);
            Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
            Transaction tx2 = new Transaction(networkParameters);
            tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount.add(amount), genesiskey));
            tx2.addOutput(new TransactionOutput(networkParameters, tx2, spendableOutput.getValue(), genesiskey));
            TransactionInput input = tx2.addInput(spendableOutput);
            Sha256Hash sighash = tx2.hashForSignature(0, spendableOutput.getScriptBytes(),
                    Transaction.SigHash.ALL, false);
            TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                    false);
            Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
            input.setScriptSig(inputScript);
            tx2.getOutput(0).getValue().value = tx2.getOutput(0).getValue().value + 1;
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    networkParameters.getGenesisBlock(), tx2);
            fail();
        } catch (VerificationException e) {
        }
    }

    @Test
    public void testSolidityTXOutputNonNegative() throws Exception {
        store.resetStore();

        // Create block with negative outputs
        try {
            @SuppressWarnings("deprecation")
            ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
            List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                    0);
            Coin amount = Coin.valueOf(-1, NetworkParameters.BIGTANGLE_TOKENID);
            Transaction tx2 = new Transaction(networkParameters);
            tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, genesiskey));
            tx2.addOutput(new TransactionOutput(networkParameters, tx2, spendableOutput.getValue().minus(amount), genesiskey));
            TransactionInput input = tx2.addInput(spendableOutput);
            Sha256Hash sighash = tx2.hashForSignature(0, spendableOutput.getScriptBytes(),
                    Transaction.SigHash.ALL, false);
            TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                    false);
            Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
            input.setScriptSig(inputScript);
            tx2.getOutput(0).getValue().value = tx2.getOutput(0).getValue().value + 1;
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                    networkParameters.getGenesisBlock(), tx2);
            fail();
        } catch (VerificationException e) {
        }
    }
    
    // TODO check too many sigops in tx

}