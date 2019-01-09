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

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.RewardInfo;
import net.bigtangle.core.ScriptException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VerificationException;
import net.bigtangle.core.VerificationException.CoinbaseDisallowedException;
import net.bigtangle.core.VerificationException.DifficultyConsensusInheritanceException;
import net.bigtangle.core.VerificationException.GenesisBlockDisallowedException;
import net.bigtangle.core.VerificationException.IncorrectTransactionCountException;
import net.bigtangle.core.VerificationException.InsufficientSignaturesException;
import net.bigtangle.core.VerificationException.InvalidDependencyException;
import net.bigtangle.core.VerificationException.InvalidSignatureException;
import net.bigtangle.core.VerificationException.InvalidTokenOutputException;
import net.bigtangle.core.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.VerificationException.InvalidTransactionException;
import net.bigtangle.core.VerificationException.MalformedTransactionDataException;
import net.bigtangle.core.VerificationException.MissingDependencyException;
import net.bigtangle.core.VerificationException.MissingTransactionDataException;
import net.bigtangle.core.VerificationException.NegativeValueOutput;
import net.bigtangle.core.VerificationException.NotCoinbaseException;
import net.bigtangle.core.VerificationException.PreviousTokenDisallowsException;
import net.bigtangle.core.VerificationException.ProofOfWorkException;
import net.bigtangle.core.VerificationException.SigOpsException;
import net.bigtangle.core.VerificationException.TimeReversionException;
import net.bigtangle.core.VerificationException.TimeTravelerException;
import net.bigtangle.core.VerificationException.TransactionInputsDisallowedException;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ValidatorServiceTest extends AbstractIntegrationTest {
    
    @Test
    public void testConflictTransactionalUTXO() throws Exception {
        store.resetStore();

        // Generate two conflicting blocks
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
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), doublespendTX);
        Block b2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), doublespendTX);

        blockGraph.add(b1, true);
        blockGraph.add(b2, true);

        createAndAddNextBlock(b1, b2);
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isMilestone()
                && blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone()
                || blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isMilestone()
                && blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone()
                || blockService.getBlockEvaluation(b2.getHash()).isMilestone());
    }

    @Test
    public void testConflictReward() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate eligible mining reward blocks 
        Block b1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        Block b2 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        createAndAddNextBlock(b2, b1);
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isMilestone()
                && blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone()
                || blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isMilestone()
                && blockService.getBlockEvaluation(b2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isMilestone()
                || blockService.getBlockEvaluation(b2.getHash()).isMilestone());
    }
    
    @Test
    public void testConflictSameTokenSubsequentIssuance() throws Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, false);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate two subsequent issuances
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1,
                amount2, false, true);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block conflictBlock1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
        Block conflictBlock2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());

        // Make a fusing block
        Block rollingBlock = conflictBlock1.createNextBlock(conflictBlock2);
        blockGraph.add(rollingBlock, true);
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash()).isMilestone()
                && blockService.getBlockEvaluation(conflictBlock2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(conflictBlock1.getHash()).isMilestone()
                || blockService.getBlockEvaluation(conflictBlock2.getHash()).isMilestone());
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash()).isMilestone()
                && blockService.getBlockEvaluation(conflictBlock2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(conflictBlock1.getHash()).isMilestone()
                || blockService.getBlockEvaluation(conflictBlock2.getHash()).isMilestone());
    }

    @Test
    public void testConflictSameTokenidSubsequentIssuance() throws Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, false);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate two subsequent issuances
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1,
                amount2, false, true);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block conflictBlock1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());

        TokenInfo tokenInfo3 = new TokenInfo();
        Coin coinbase3 = Coin.valueOf(666, pubKey);
        long amount3 = coinbase3.getValue();
        Token tokens3 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1,
                amount3, false, true);
        tokenInfo3.setTokens(tokens3);
        tokenInfo3.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens3.getTokenid(), "", outKey.getPublicKeyAsHex()));        
        Block conflictBlock2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo3, coinbase3, outKey, null, block1.getHash(), block1.getHash());

        // Make a fusing block
        Block rollingBlock = conflictBlock1.createNextBlock(conflictBlock2);
        blockGraph.add(rollingBlock, true);
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash()).isMilestone()
                && blockService.getBlockEvaluation(conflictBlock2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(conflictBlock1.getHash()).isMilestone()
                || blockService.getBlockEvaluation(conflictBlock2.getHash()).isMilestone());
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash()).isMilestone()
                && blockService.getBlockEvaluation(conflictBlock2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(conflictBlock1.getHash()).isMilestone()
                || blockService.getBlockEvaluation(conflictBlock2.getHash()).isMilestone());
    }

    @Test
    public void testConflictSameTokenFirstIssuance() throws Exception {
        store.resetStore();

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, true);

        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Make another conflicting issuance that goes through
        Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block block2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, genHash, genHash);
        Block rollingBlock = block2.createNextBlock(block1);
        blockGraph.add(rollingBlock, true);
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                && blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                || blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                && blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                || blockService.getBlockEvaluation(block1.getHash()).isMilestone());
    }

    @Test
    public void testConflictSameTokenidFirstIssuance() throws Exception {
        store.resetStore();

        // Generate an issuance
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
        
        // Generate another issuance slightly different
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(6666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test2", "Test2", 1, 0,
                amount2, false, false);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses().add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block block2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, genHash, genHash);
        Block rollingBlock = block2.createNextBlock(block1);
        blockGraph.add(rollingBlock, true);
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                && blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                || blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                && blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isMilestone()
                || blockService.getBlockEvaluation(block1.getHash()).isMilestone());
    }
    
    @Test
    public void testVerificationFutureTimestamp() throws Exception {
        store.resetStore();
        
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        Block b = r2.createNextBlock(r1);
        b.setTime(1577836800); // 01/01/2020 @ 12:00am (UTC)
        b.solve();
        try {
            blockService.saveBlock(b);
            fail();
        } catch (TimeTravelerException e) {
        }
    }

    @Test
    public void testVerificationIncorrectPoW() throws Exception {
        store.resetStore();
        
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        Block b = r2.createNextBlock(r1);
        b.setNonce(1377836800);
        try {
            blockService.saveBlock(b);
            fail();
        } catch (ProofOfWorkException e) {
        }
    }

    @Test
    public void testUnsolidBlockAllowed() throws Exception {
        store.resetStore();
        
        Sha256Hash sha256Hash1 = getRandomSha256Hash();
        Sha256Hash sha256Hash2 = getRandomSha256Hash();
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setPrevBlockHash(sha256Hash1);
        block.setPrevBranchBlockHash(sha256Hash2);
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
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setPrevBlockHash(sha256Hash1);
        block.setPrevBranchBlockHash(sha256Hash2);
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
        
        Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setPrevBlockHash(sha256Hash);
        block.setPrevBranchBlockHash(sha256Hash);
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
        
        Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setPrevBlockHash(sha256Hash);
        block.setPrevBranchBlockHash(networkParameters.getGenesisBlock().getHash());
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
        
        Block depBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        
        Sha256Hash sha256Hash = depBlock.getHash();
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setPrevBlockHash(networkParameters.getGenesisBlock().getHash());
        block.setPrevBranchBlockHash(sha256Hash);
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
        Transaction tx1 = createTestGenesisTransaction();
        Block depBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx1);
        
        milestoneService.update();

        // Create block with dependency
        Transaction tx2 = createTestGenesisTransaction();
        Block block = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx2);
        
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
    public void testUnsolidMissingReward() throws Exception {
        store.resetStore();
        List<Block> blocks1 = new ArrayList<>();
        List<Block> blocks2 = new ArrayList<>();

        // Generate blocks until passing first reward interval and second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blocks1.add(rollingBlock);
        }
        for (Block b : blocks1) {
            blockGraph.add(b, true);            
        }
        milestoneService.update();
        
        // Generate eligible mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        milestoneService.update();

        // Mining reward block should go through
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());
        
        // Make more for next reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blocks2.add(rollingBlock);
        }
        for (Block b : blocks2) {
            blockGraph.add(b, true);            
        }
        milestoneService.update();
        
        // Generate eligible second mining reward block
        Block rewardBlock2 = transactionService.createAndAddMiningRewardBlock(rewardBlock1.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        milestoneService.update();
        
        store.resetStore();
        for (Block b : blocks1) {
            blockGraph.add(b, true);            
        }
        for (Block b : blocks2) {
            blockGraph.add(b, true);            
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
        Token tokens2 = Token.buildSimpleTokenInfo(true, depBlock.getHashAsString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1, amount, false, false);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase, outKey, null, networkParameters.getGenesisBlock().getHash(), networkParameters.getGenesisBlock().getHash());
        
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
    public void testSolidityPredecessorDifficultyInheritance() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval and second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);
            
            // The difficulty should be equal to the previous difficulty
            assertEquals(rollingBlock.getDifficultyTarget(), rollingBlockNew.getDifficultyTarget());
            
            rollingBlock = rollingBlockNew;
            blockGraph.add(rollingBlock, true);     
        }
        milestoneService.update();
        
        // Generate eligible mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());

        // The difficulty should now not be equal to the previous difficulty
        assertNotEquals(rollingBlock.getDifficultyTarget(), rewardBlock1.getDifficultyTarget());

        rollingBlock = rewardBlock1;
        for (int i = 0; i < 3; i++) {
            Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);
            
            // The difficulty should be equal to the previous difficulty
            assertEquals(rollingBlock.getDifficultyTarget(), rollingBlockNew.getDifficultyTarget());
            
            rollingBlock = rollingBlockNew;
            blockGraph.add(rollingBlock, true);     
        }
        
        try {
            Block failingBlock = rollingBlock.createNextBlock(networkParameters.getGenesisBlock());
            failingBlock.setDifficultyTarget(networkParameters.getGenesisBlock().getDifficultyTarget());
            failingBlock.solve();
            blockGraph.add(failingBlock, false);
            fail();
        } catch (DifficultyConsensusInheritanceException e) {
            // Expected
        }
        
        try {
            Block failingBlock = networkParameters.getGenesisBlock().createNextBlock(rollingBlock);
            failingBlock.setDifficultyTarget(networkParameters.getGenesisBlock().getDifficultyTarget());
            failingBlock.solve();
            blockGraph.add(failingBlock, false);
            fail();
        } catch (DifficultyConsensusInheritanceException e) {
            // Expected
        }
    }

    @Test
    public void testSolidityPredecessorConsensusInheritance() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval and second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);
            
            // The difficulty should be equal to the previous difficulty
            assertEquals(rollingBlock.getLastMiningRewardBlock(), rollingBlockNew.getLastMiningRewardBlock());
            
            rollingBlock = rollingBlockNew;
            blockGraph.add(rollingBlock, true);     
        }
        milestoneService.update();
        
        // Generate eligible mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());

        // The consensus number should now be equal to the previous number + 1
        assertEquals(rollingBlock.getLastMiningRewardBlock() + 1, rewardBlock1.getLastMiningRewardBlock());

        for (int i = 0; i < 3; i++) {
            Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);
            
            // The difficulty should be equal to the previous difficulty
            assertEquals(rollingBlock.getLastMiningRewardBlock(), rollingBlockNew.getLastMiningRewardBlock());
            
            rollingBlock = rollingBlockNew;
            blockGraph.add(rollingBlock, true);     
        }
        
        try {
            Block failingBlock = rollingBlock.createNextBlock(networkParameters.getGenesisBlock());
            failingBlock.setLastMiningRewardBlock(2);
            failingBlock.solve();
            blockGraph.add(failingBlock, false);
            fail();
        } catch (DifficultyConsensusInheritanceException e) {
            // Expected
        }
        
        try {
            Block failingBlock = networkParameters.getGenesisBlock().createNextBlock(rollingBlock);
            failingBlock.setLastMiningRewardBlock(2);
            failingBlock.solve();
            blockGraph.add(failingBlock, false);
            fail();
        } catch (DifficultyConsensusInheritanceException e) {
            // Expected
        }
        
        try {
            Block failingBlock = transactionService.createMiningRewardBlock(rewardBlock1.getHash(), rollingBlock.getHash(), rollingBlock.getHash(), true);
            failingBlock.setLastMiningRewardBlock(123);
            failingBlock.solve();
            blockGraph.add(failingBlock, false);
            fail();
        } catch (DifficultyConsensusInheritanceException e) {
            // Expected
        }
    }

    @Test
    public void testSolidityPredecessorTimeInheritance() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval and second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            Block rollingBlockNew = rollingBlock.createNextBlock(rollingBlock);
            
            // The time should not be moving backwards
            assertTrue(rollingBlock.getTimeSeconds() <= rollingBlockNew.getTimeSeconds());
            
            rollingBlock = rollingBlockNew;
            blockGraph.add(rollingBlock, true);     
        }
        milestoneService.update();

        // The time is allowed to stay the same
        rollingBlock = rollingBlock.createNextBlock(rollingBlock);
        rollingBlock.setTime(rollingBlock.getTimeSeconds()); // 01/01/2000 @ 12:00am (UTC)
        rollingBlock.solve();
        blockGraph.add(rollingBlock, true);
        
        // The time is not allowed to move backwards
        try {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            rollingBlock.setTime(946684800); // 01/01/2000 @ 12:00am (UTC)
            rollingBlock.solve();
            blockGraph.add(rollingBlock, true);     
            fail();
        } catch (TimeReversionException e) {
        }
    }

    @Test
    public void testSolidityCoinbaseDisallowed() throws Exception {
        store.resetStore();
        final Block genesisBlock = networkParameters.getGenesisBlock();
        
        // For disallowed types: coinbases are not allowed
        for (Type type : Block.Type.values()) {
            if (!type.allowCoinbaseTransaction())
                try {
                    // Build transaction
                    Transaction tx = new Transaction(networkParameters);
                    tx.addOutput(Coin.SATOSHI.times(2), outKey.toAddress(networkParameters));
                    
                    // The input does not really need to be a valid signature, as long
                    // as it has the right general form and is slightly different for
                    // different tx
                    TransactionInput input = new TransactionInput(networkParameters, tx, Script.createInputScript(
                            genesisBlock.getHash().getBytes(), genesisBlock.getHash().getBytes()));
                    tx.addInput(input);
                    
                    // Check it fails
                    Block rollingBlock = genesisBlock.createNextBlock(genesisBlock);
                    rollingBlock.setBlockType(type);
                    rollingBlock.addTransaction(tx);
                    rollingBlock.solve();
                    blockGraph.add(rollingBlock, false);
                    
                    fail();
                } catch (CoinbaseDisallowedException e) {
                }
        }
    }

    @Test
    public void testSolidityTXInputScriptsCorrect() throws Exception {
        store.resetStore();
        
        // Create block with UTXO
        Transaction tx1 = createTestGenesisTransaction();
        createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx1);
        
        store.resetStore();

        // Again but with incorrect input script
        try {
            tx1.getInput(0).setScriptSig(new Script(new byte[0]));
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx1);
            fail();
        } catch (ScriptException e) {
        }
    }

    @Test
    public void testSolidityTXOutputSumCorrect() throws Exception {
        store.resetStore();

        // Create block with UTXO
        {
            Transaction tx1 = createTestGenesisTransaction();
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx1);
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
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx2);
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
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx2);
            fail();
        } catch (InvalidTransactionException e) {
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
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx2);
            fail();
        } catch (NegativeValueOutput e) {
            //Expected
        }
    }

    @Test
    public void testSolidityNewGenesis() throws Exception {
        store.resetStore();

        // Create genesis block 
        try {
            Block b = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
            b.setBlockType(Type.BLOCKTYPE_INITIAL);
            b.solve();
            blockGraph.add(b, false);
            fail();
        } catch (GenesisBlockDisallowedException e) {
        }
    }

    @Test
    public void testSoliditySigOps() throws Exception {
        store.resetStore();

        // Create block with outputs
        try {
            @SuppressWarnings("deprecation")
            ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
            List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                    0);
            Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
            Transaction tx2 = new Transaction(networkParameters);
            tx2.addOutput(new TransactionOutput(networkParameters, tx2, amount, genesiskey));
            tx2.addOutput(new TransactionOutput(networkParameters, tx2, spendableOutput.getValue().minus(amount), genesiskey));
            TransactionInput input = tx2.addInput(spendableOutput);
            
            ScriptBuilder scriptBuilder = new ScriptBuilder();
            for (int i = 0; i < NetworkParameters.MAX_BLOCK_SIGOPS + 1; i++)
                scriptBuilder.op(0xac);
            
            Script inputScript = scriptBuilder.build();
            input.setScriptSig(inputScript);
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx2);
            fail();
        } catch (SigOpsException e) {
        }
    }
    
    @Test
    public void testSolidityRewardTxTooClose() throws Exception {
        store.resetStore();
        Block rollingBlock = networkParameters.getGenesisBlock();

        // Generate blocks until passing first reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }        

        // Generate mining reward block with spending inputs
        Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        
        // Should not go through
        try {
            blockGraph.add(rewardBlock, false);
            
            fail();
        } catch (InvalidTransactionDataException e) {
        }
    }
    
    @Test
    public void testSolidityRewardTxWrongDifficulty() throws Exception {
        store.resetStore();
        Block rollingBlock = networkParameters.getGenesisBlock();

        // Generate blocks until passing first reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }        

        // Generate mining reward block with spending inputs
        Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        rewardBlock.setDifficultyTarget(rollingBlock.getDifficultyTarget());
        rewardBlock.solve();
        
        // Should not go through
        try {
            blockGraph.add(rewardBlock, false);
            fail();
        } catch (InvalidTransactionDataException e) {
        }
    }
    
    @Test
    public void testSolidityRewardTxWithTransfers1() throws Exception {
        store.resetStore();
        Block rollingBlock = networkParameters.getGenesisBlock();

        // Generate blocks until passing first reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }        

        // Generate mining reward block with spending inputs
        Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        Transaction tx = rewardBlock.getTransactions().get(0);
        
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, genesiskey));
        tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), genesiskey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);
    
        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);
        rewardBlock.solve();
        
        // Should not go through
        try {
            blockGraph.add(rewardBlock, false);
            
            fail();
        } catch (TransactionInputsDisallowedException e) {
        }
    }
    
    @Test
    public void testSolidityRewardTxWithTransfers2() throws Exception {
        store.resetStore();
        Block rollingBlock = networkParameters.getGenesisBlock();

        // Generate blocks until passing first reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }        

        // Generate mining reward block with additional tx
        Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        Transaction tx = createTestGenesisTransaction();
        rewardBlock.addTransaction(tx);
        rewardBlock.solve();
        
        // Should not go through
        try {
            blockGraph.add(rewardBlock, false);
            
            fail();
        } catch (IncorrectTransactionCountException e) {
        }
    }

    @Test
    public void testSolidityRewardTxWithMissingRewardInfo() throws Exception {
        store.resetStore();
        Block rollingBlock = networkParameters.getGenesisBlock();

        // Generate blocks until passing first reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }        

        // Generate mining reward block with malformed tx data
        Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        rewardBlock.getTransactions().get(0).setData(null);
        rewardBlock.solve();
        
        // Should not go through
        try {
            blockGraph.add(rewardBlock, false);
            
            fail();
        } catch (MissingTransactionDataException e) {
        }
    }

    @Test
    public void testSolidityRewardTxMalformedData1() throws Exception {
        store.resetStore();
        Block rollingBlock = networkParameters.getGenesisBlock();

        // Generate blocks until passing first reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }        

        // Generate mining reward block with malformed tx data
        Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        rewardBlock.getTransactions().get(0).setData(new byte[] {2, 3, 4});;
        rewardBlock.solve();
        
        // Should not go through
        try {
            blockGraph.add(rewardBlock, false);
            
            fail();
        } catch (MalformedTransactionDataException e) {
        }
    }

    @Test
    public void testSolidityRewardTxMalformedData2() throws Exception {
        store.resetStore();
        Block rollingBlock = networkParameters.getGenesisBlock();

        // Generate blocks until passing first reward interval
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }        

        // Generate mining reward block with malformed fields
        Block rewardBlock = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        Block testBlock1 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
        Block testBlock2 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
        Block testBlock3 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
        Block testBlock4 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
        Block testBlock5 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
        Block testBlock6 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
        Block testBlock7 = networkParameters.getDefaultSerializer().makeBlock(rewardBlock.bitcoinSerialize());
        RewardInfo rewardInfo1 = RewardInfo.parse(testBlock1.getTransactions().get(0).getData());
        RewardInfo rewardInfo2 = RewardInfo.parse(testBlock2.getTransactions().get(0).getData());
        RewardInfo rewardInfo3 = RewardInfo.parse(testBlock3.getTransactions().get(0).getData());
        RewardInfo rewardInfo4 = RewardInfo.parse(testBlock4.getTransactions().get(0).getData());
        RewardInfo rewardInfo5 = RewardInfo.parse(testBlock5.getTransactions().get(0).getData());
        RewardInfo rewardInfo6 = RewardInfo.parse(testBlock6.getTransactions().get(0).getData());
        RewardInfo rewardInfo7 = RewardInfo.parse(testBlock7.getTransactions().get(0).getData());
        rewardInfo1.setFromHeight(-1);
        rewardInfo2.setPrevRewardHash(null);
        rewardInfo3.setPrevRewardHash(getRandomSha256Hash());
        rewardInfo4.setPrevRewardHash(rollingBlock.getHash());
        rewardInfo5.setPrevRewardHash(rollingBlock.getHash());
        rewardInfo6.setToHeight(12341324);
        rewardInfo7.setToHeight(-1);
        testBlock1.getTransactions().get(0).setData(rewardInfo1.toByteArray());
        testBlock2.getTransactions().get(0).setData(rewardInfo2.toByteArray());
        testBlock3.getTransactions().get(0).setData(rewardInfo3.toByteArray());
        testBlock4.getTransactions().get(0).setData(rewardInfo4.toByteArray());
        testBlock5.getTransactions().get(0).setData(rewardInfo5.toByteArray());
        testBlock6.getTransactions().get(0).setData(rewardInfo6.toByteArray());
        testBlock7.getTransactions().get(0).setData(rewardInfo7.toByteArray());
        testBlock1.solve();
        testBlock2.solve();
        testBlock3.solve();
        testBlock4.solve();
        testBlock5.solve();
        testBlock6.solve();
        testBlock7.solve();
        
        // Should not go through
        try {
            blockGraph.add(testBlock1, false);
            fail();
        } catch (InvalidTransactionDataException e) {
        }
        try {
            blockGraph.add(testBlock2, false);
            fail();
        } catch (MissingDependencyException e) {
        }
        if (blockGraph.add(testBlock3, false))
            fail();
        try {
            blockGraph.add(testBlock4, false);
            fail();
        } catch (InvalidDependencyException e) {
        }
        try {
            blockGraph.add(testBlock5, false);
            fail();
        } catch (InvalidDependencyException e) {
        }
        try {
            blockGraph.add(testBlock6, false);
            fail();
        } catch (InvalidTransactionDataException e) {
        }
        try {
            blockGraph.add(testBlock7, false);
            fail();
        } catch (InvalidTransactionDataException e) {
        }
    }

    interface TestCase {
        public boolean expectsException();
        public void preApply(TokenInfo info);
    }

    @Test
    public void testSolidityTokenMalformedData1() throws Exception {
        store.resetStore();
        
        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // Coinbase without data
        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
        block.getTransactions().get(0).setData(null);
        
        // solve block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            fail();
        } catch (MissingTransactionDataException e) {
        }
    }
    
    @Test
    public void testSolidityTokenMalformedData2() throws Exception {
        store.resetStore();
        
        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // Coinbase without data
        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
        block.getTransactions().get(0).setData(new byte[] {1, 2});
        
        // solve block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            fail();
        } catch (MalformedTransactionDataException e) {
        }
    }
    
    @Test
    public void testSolidityTokenMalformedDataSignature1() throws Exception {
        store.resetStore();
        
        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // Coinbase without data
        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
        block.getTransactions().get(0).setDataSignature(null);
        
        // solve block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            fail();
        } catch (MissingTransactionDataException e) {
        }
    }
    
    @Test
    public void testSolidityTokenMalformedDataSignature2() throws Exception {
        store.resetStore();
        
        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // Coinbase without data
        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
        block.getTransactions().get(0).setDataSignature(new byte[] {1, 2});
        
        // solve block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            fail();
        } catch (MalformedTransactionDataException e) {
        }
    }
    
    @Test
    public void testSolidityTokenMutatedData() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        
        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo0 = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo0.setTokens(tokens);
        tokenInfo0.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // TODO try too few signatures for signnumber? split output multisig and token multisig definitions
        // Make mutated versions of the data
        // TODO setting blockhash is useless, drop it
        // TODO amount can be inferred, drop it
        // TODO multiserial is useless?
        // TODO type is useless
        // TODO MultiSignAddress.address useless
        // TODO MultiSignAddress.blockhash useless
        // TODO MultiSignAddress.tokenid does not matter, must be inferred
        TestCase[] executors = new TestCase[] { new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {
                tokenInfo5.setTokens(null);
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {
                tokenInfo5.setMultiSignAddresses(null);
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {
                tokenInfo5.getTokens().setAmount(-1);
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setBlockhash(null);
            }

            @Override
            public boolean expectsException() {
                return false;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setDescription(null);
            }

            @Override
            public boolean expectsException() {
                return false;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setDescription(
                        new String(new char[NetworkParameters.TOKEN_MAX_DESC_LENGTH]).replace("\0", "A"));
            }

            @Override
            public boolean expectsException() {
                return false;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setDescription(
                        new String(new char[NetworkParameters.TOKEN_MAX_DESC_LENGTH + 1]).replace("\0", "A"));
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setMultiserial(false);
            }

            @Override
            public boolean expectsException() {
                return false;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {
                tokenInfo5.getTokens().setPrevblockhash(null);
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setPrevblockhash("test");
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setPrevblockhash(getRandomSha256Hash().toString());
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setPrevblockhash(networkParameters.getGenesisBlock().getHashAsString());
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setSignnumber(-1);
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() { //13
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setSignnumber(0);
            }

            @Override
            public boolean expectsException() {
                return false;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenid(null);
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenid("");
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenid("test");
            }

            @Override
            public boolean expectsException() {
                return true;
            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenid(Utils.HEX.encode(genesiskey.getPubKey()));
            }

            @Override
            public boolean expectsException() {
                return true;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenindex(-1);
            }

            @Override
            public boolean expectsException() {
                return true;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenindex(5);
            }

            @Override
            public boolean expectsException() {
                return true;

            }
        }, new TestCase() { //20
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenindex(NetworkParameters.TOKEN_MAX_ISSUANCE_NUMBER + 1);
            }

            @Override
            public boolean expectsException() {
                return true;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenname(null);
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenname("");
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens()
                        .setTokenname(new String(new char[NetworkParameters.TOKEN_MAX_NAME_LENGTH]).replace("\0", "A"));
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenname(
                        new String(new char[NetworkParameters.TOKEN_MAX_NAME_LENGTH + 1]).replace("\0", "A"));
            }

            @Override
            public boolean expectsException() {
                return true;

            }
        }, new TestCase() { //25
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokenstop(false);
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setTokentype(-1);
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {
                tokenInfo5.getTokens().setUrl(null);
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens().setUrl("");
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getTokens()
                        .setUrl(new String(new char[NetworkParameters.TOKEN_MAX_URL_LENGTH]).replace("\0", "A"));
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) { //30

                tokenInfo5.getTokens()
                        .setUrl(new String(new char[NetworkParameters.TOKEN_MAX_URL_LENGTH + 1]).replace("\0", "A"));
            }

            @Override
            public boolean expectsException() {
                return true;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().remove(0);
            }

            @Override
            public boolean expectsException() {
                return true;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setAddress(null);
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setAddress("");
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setAddress(new String(new char[222]).replace("\0", "A"));
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {//35

                tokenInfo5.getMultiSignAddresses().get(0).setBlockhash(null);
            }

            @Override
            public boolean expectsException() {
                return false; // these do not matter, they are overwritten

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setBlockhash("test");
            }

            @Override
            public boolean expectsException() {
                return false; // these do not matter, they are overwritten

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setBlockhash(getRandomSha256Hash().toString());
            }

            @Override
            public boolean expectsException() {
                return false; // these do not matter, they are overwritten

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0)
                        .setBlockhash(networkParameters.getGenesisBlock().getHashAsString());
            }

            @Override
            public boolean expectsException() {
                return false; // these do not matter, they are overwritten

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(-1);
            }

            @Override
            public boolean expectsException() {
                return false; // these do not matter, they are overwritten

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) { //40

                tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(0);
            }

            @Override
            public boolean expectsException() {
                return false; // these do not matter, they are overwritten

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setPosIndex(4);
            }

            @Override
            public boolean expectsException() {
                return false; // these do not matter, they are overwritten

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setPubKeyHex(Utils.HEX.encode(outKey2.getPubKey()));
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setTokenid(null);
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setTokenid("");
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setTokenid("test");
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        }, new TestCase() {
            @Override
            public void preApply(TokenInfo tokenInfo5) {

                tokenInfo5.getMultiSignAddresses().get(0).setTokenid(Utils.HEX.encode(genesiskey.getPubKey()));
            }

            @Override
            public boolean expectsException() {
                return false;

            }
        } };
        
        for (int i = 0; i < executors.length; i++) {
            // Modify the tokenInfo
            TokenInfo tokenInfo = TokenInfo.parse(tokenInfo0.toByteArray());
            executors[i].preApply(tokenInfo);
            
            // Make block including it
            Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
            block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
            
            // Coinbase with signatures
            if (tokenInfo.getMultiSignAddresses() != null) {
                
                block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
                Transaction transaction = block.getTransactions().get(0);
                Sha256Hash sighash1 = transaction.getHash();
                ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
                byte[] buf1 = party1Signature.encodeToDER();
                
                List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
                MultiSignBy multiSignBy0 = new MultiSignBy();
                if (tokenInfo.getTokens() != null && tokenInfo.getTokens().getTokenid() != null)
                    multiSignBy0.setTokenid(tokenInfo.getTokens().getTokenid().trim());
                else
                    multiSignBy0.setTokenid(Utils.HEX.encode(outKey.getPubKey()));
                multiSignBy0.setTokenindex(0);
                multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
                multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
                multiSignBy0.setSignature(Utils.HEX.encode(buf1));
                multiSignBies.add(multiSignBy0);
                MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
                transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
            }
            
            // solve block
            block.solve();
            
            // Should not go through
            if (executors[i].expectsException()) {
                try {
                    blockGraph.add(block, false);
                    fail("Number " + i + " failed");
                } catch (VerificationException e) {
                }
            } else {
                if (!blockGraph.add(block, false))
                    fail("Number " + i + " failed");
            }
        }
    }
    
    @Test
    public void testSolidityTokenMutatedDataSignatures() throws Exception {
        store.resetStore();
        
        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // Coinbase with signatures
        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
        Transaction transaction = block.getTransactions().get(0);
        Sha256Hash sighash1 = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
        byte[] buf1 = party1Signature.encodeToDER();
        
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getTokens().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        
        // Mutate signatures
        Block block1 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block2 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block3 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block4 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block5 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block6 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block7 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block8 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block9 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block10 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block11 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block12 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block13 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block14 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block15 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block16 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block17 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());
        Block block18 = networkParameters.getDefaultSerializer().makeBlock(block.bitcoinSerialize());

        MultiSignByRequest multiSignByRequest1 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest2 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest3 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest4 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest5 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest6 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest7 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest8 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest9 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest10 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest11 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest12 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest13 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest14 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest15 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest16 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest17 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        MultiSignByRequest multiSignByRequest18 = Json.jsonmapper().readValue(transaction.getDataSignature(), MultiSignByRequest.class);
        
        multiSignByRequest1.setMultiSignBies(null);
        multiSignByRequest2.setMultiSignBies(new ArrayList<>());
        multiSignByRequest3.getMultiSignBies().get(0).setAddress(null);
        multiSignByRequest4.getMultiSignBies().get(0).setAddress("");
        multiSignByRequest5.getMultiSignBies().get(0).setAddress("test");
        multiSignByRequest6.getMultiSignBies().get(0).setPublickey(null);
        multiSignByRequest7.getMultiSignBies().get(0).setPublickey("");
        multiSignByRequest8.getMultiSignBies().get(0).setPublickey("test");
        multiSignByRequest9.getMultiSignBies().get(0).setPublickey(Utils.HEX.encode(outKey2.getPubKey()));
        multiSignByRequest10.getMultiSignBies().get(0).setSignature(null);
        multiSignByRequest11.getMultiSignBies().get(0).setSignature("");
        multiSignByRequest12.getMultiSignBies().get(0).setSignature("test");
        multiSignByRequest13.getMultiSignBies().get(0).setSignature(Utils.HEX.encode(outKey2.getPubKey()));
        multiSignByRequest14.getMultiSignBies().get(0).setTokenid(null);
        multiSignByRequest15.getMultiSignBies().get(0).setTokenid("");
        multiSignByRequest16.getMultiSignBies().get(0).setTokenid("test");
        multiSignByRequest17.getMultiSignBies().get(0).setTokenindex(-1);
        multiSignByRequest18.getMultiSignBies().get(0).setTokenindex(1);

        block1.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest1));
        block2.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest2));
        block3.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest3));
        block4.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest4));
        block5.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest5));
        block6.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest6));
        block7.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest7));
        block8.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest8));
        block9.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest9));
        block10.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest10));
        block11.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest11));
        block12.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest12));
        block13.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest13));
        block14.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest14));
        block15.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest15));
        block16.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest16));
        block17.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest17));
        block18.getTransactions().get(0).setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest18));
        
        block1.solve();
        block2.solve();
        block3.solve();
        block4.solve();
        block5.solve();
        block6.solve();
        block7.solve();
        block8.solve();
        block9.solve();
        block10.solve();
        block11.solve();
        block12.solve();
        block13.solve();
        block14.solve();
        block15.solve();
        block16.solve();
        block17.solve();
        block18.solve();
        
        // Test
        try {
            blockGraph.add(block1, false);
            fail();
        } catch (InsufficientSignaturesException e) {
        }
        try {
            blockGraph.add(block2, false);
            fail();
        } catch (InsufficientSignaturesException e) {
        }
        
        // TODO MultiSignByRequest.setAddress is useless field, remove!
        try {
            blockGraph.add(block3, false);
        } catch (VerificationException e) {
            fail();
        }
        try {
            blockGraph.add(block4, false);
        } catch (VerificationException e) {
            fail();
        }
        try {
            blockGraph.add(block5, false);
        } catch (VerificationException e) {
            fail();
        }
        
        try {
            blockGraph.add(block6, false);
            fail();
        } catch (VerificationException e) {
        }
        try {
            blockGraph.add(block7, false);
            fail();
        } catch (VerificationException e) {
        }
        try {
            blockGraph.add(block8, false);
            fail();
        } catch (VerificationException e) {
        }
        try {
            blockGraph.add(block9, false);
            fail();
        } catch (InvalidSignatureException e) {
        }
        try {
            blockGraph.add(block10, false);
            fail();
        } catch (VerificationException e) {
        }
        try {
            blockGraph.add(block11, false);
            fail();
        } catch (VerificationException e) {
        }
        try {
            blockGraph.add(block12, false);
            fail();
        } catch (VerificationException e) {
        }
        try {
            blockGraph.add(block13, false);
            fail();
        } catch (VerificationException e) {
        }
        
        // TODO MultiSignByRequest.tokenid is useless field, remove!
        try {
            blockGraph.add(block14, false);
        } catch (VerificationException e) {
            fail();
        }
        try {
            blockGraph.add(block15, false);
        } catch (VerificationException e) {
            fail();
        }
        try {
            blockGraph.add(block16, false);
        } catch (VerificationException e) {
            fail();
        }
        // TODO MultiSignByRequest.tokenindex is useless field, remove!
        try {
            blockGraph.add(block17, false);
        } catch (VerificationException e) {
            fail();
        }
        try {
            blockGraph.add(block18, false);
        } catch (VerificationException e) {
            fail();
        }
    }
    
    @Test
    public void testSolidityTokenNoTransaction() throws Exception {
        store.resetStore();

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // save block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            fail();
        } catch (IncorrectTransactionCountException e) {
        }
    }
    
    @Test
    public void testSolidityTokenMultipleTransactions1() throws Exception {
        store.resetStore();
        
        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // Coinbase with signatures
        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
        Transaction transaction = block.getTransactions().get(0);
        Sha256Hash sighash1 = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
        byte[] buf1 = party1Signature.encodeToDER();
        
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getTokens().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        
        // Add another transfer transaction
        Transaction tx = createTestGenesisTransaction();
        block.addTransaction(tx);
        
        // save block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            
            fail();
        } catch (IncorrectTransactionCountException e) {
        }
    }
    
    @Test
    public void testSolidityTokenMultipleTransactions2() throws Exception {
        store.resetStore();
        
        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // Coinbase with signatures
        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
        Transaction transaction = block.getTransactions().get(0);
        Sha256Hash sighash1 = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
        byte[] buf1 = party1Signature.encodeToDER();
        
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getTokens().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        
        // Add transaction again
        block.addTransaction(transaction);
        
        // save block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            
            fail();
        } catch (IncorrectTransactionCountException e) {
        }
    }
    
    @Test
    public void testSolidityTokenTransferTransaction() throws Exception {
        store.resetStore();

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);

        // Add transfer transaction
        Transaction tx = createTestGenesisTransaction();
        block.addTransaction(tx);
        
        // save block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            
            fail();
        } catch (NotCoinbaseException e) {
        }
    }
    
    @Test 
    public void testSolidityTokenPredecessorWrongTokenid() throws JsonProcessingException, Exception {
        store.resetStore();

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, false);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate a subsequent issuance that does not work
        byte[] pubKey2 = outKey2.getPubKey();
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey2);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey2), "Test", "Test", 1, 1,
                amount2, false, true);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey2.getPublicKeyAsHex()));
        try {
            Wallet r = walletAppKit.wallet();
            Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
            blockGraph.add(block, false);
            fail();
        } catch (InvalidDependencyException e) {
        }
    }
    
    @Test 
    public void testSolidityTokenWrongTokenindex() throws JsonProcessingException, Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, false);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate a subsequent issuance that does not work
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 2,
                amount2, false, true);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        try {
            Wallet r = walletAppKit.wallet();
            Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
            blockGraph.add(block, false);
            fail();
        } catch (InvalidDependencyException e) {
        }
    }
    
    @Test 
    public void testSolidityTokenPredecessorStopped() throws JsonProcessingException, Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate a subsequent issuance that does not work
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1,
                amount2, false, true);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        try {
            Wallet r = walletAppKit.wallet();
            Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
            blockGraph.add(block, false);
            fail();
        } catch (PreviousTokenDisallowsException e) {
        }
    }
    
    @Test 
    public void testSolidityTokenPredecessorConflictingType() throws JsonProcessingException, Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, false);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate a subsequent issuance that does not work
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1,
                amount2, false, true);
        tokens2.setTokentype(123);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        try {
            Wallet r = walletAppKit.wallet();
            Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
            blockGraph.add(block, false);
            fail();
        } catch (PreviousTokenDisallowsException e) {
        }
    }
    
    @Test 
    public void testSolidityTokenPredecessorConflictingName() throws JsonProcessingException, Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                amount, false, false);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate a subsequent issuance that does not work
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test2", "Test", 1, 1,
                amount2, false, true);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        try {
            Wallet r = walletAppKit.wallet();
            Block block = r.makeTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
            blockGraph.add(block, false);
            fail();
        } catch (PreviousTokenDisallowsException e) {
        }
    }
    
    @Test
    public void testSolidityTokenWrongTokenCoinbase() throws Exception {
        store.resetStore();

        // Generate an eligible issuance tokenInfo
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
        long amount = coinbase.getValue() + 2;
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Make block including it
        Block block = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        
        // Coinbase with signatures
        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo);
        Transaction transaction = block.getTransactions().get(0);
        
        // Add another output for other tokens
        block.getTransactions().get(0).addOutput(Coin.SATOSHI.times(2), outKey.toAddress(networkParameters));
        
        Sha256Hash sighash1 = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
        byte[] buf1 = party1Signature.encodeToDER();
        
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getTokens().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        
        // save block
        block.solve();
        
        // Should not go through
        try {
            blockGraph.add(block, false);
            
            fail();
        } catch (InvalidTokenOutputException e) {
        }
    }

}