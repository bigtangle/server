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
import net.bigtangle.core.VerificationException.IncorrectTransactionCountException;
import net.bigtangle.core.VerificationException.InvalidDependencyException;
import net.bigtangle.core.VerificationException.InvalidTokenOutputException;
import net.bigtangle.core.VerificationException.InvalidTransactionDataException;
import net.bigtangle.core.VerificationException.InvalidTransactionException;
import net.bigtangle.core.VerificationException.MalformedTransactionDataException;
import net.bigtangle.core.VerificationException.MissingDependencyException;
import net.bigtangle.core.VerificationException.MissingTransactionDataException;
import net.bigtangle.core.VerificationException.NotCoinbaseException;
import net.bigtangle.core.VerificationException.TimeReversionException;
import net.bigtangle.core.VerificationException.TransactionInputsDisallowedException;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ValidatorServiceTest extends AbstractIntegrationTest {

    // TODO drop string from everywhere, stop using sha256hash.wrap, stop using jsonserialization!
    
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

        // Make another conflicting issuance that goes through
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

        // Make another conflicting issuance that goes through
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
        tokenInfo2.getMultiSignAddresses().add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

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
    
    @Test(expected=VerificationException.class)
    public void testVerificationFutureTimestamp() throws Exception {
        store.resetStore();
        
        Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
        Block r1 = blockService.getBlock(tipsToApprove.getLeft());
        Block r2 = blockService.getBlock(tipsToApprove.getRight());
        Block b = r2.createNextBlock(r1);
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
        Block b = r2.createNextBlock(r1);
        b.setTime(1377836800);
        blockService.saveBlock(b);
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
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();

        // Should be out
        assertFalse(blockService.getBlockEvaluation(block1.getHash()).isMilestone());
        assertFalse(store.getTransactionOutput(tx1.getHash(), 0).isConfirmed());
        assertFalse(store.getTokenConfirmed(block1.getHashAsString()));
    }

    @Test
    public void testReorgMiningReward() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
            blockGraph.add(rollingBlock2, true);
        }

        Block fusingBlock = rollingBlock1.createNextBlock(rollingBlock2);
        blockGraph.add(fusingBlock, true);
        

        // Generate ineligible mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        milestoneService.update();

        // Mining reward block should usually not go through since not sufficiently approved
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());

        // Generate eligible mining reward blocks
        Block rewardBlock2 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        Block rewardBlock3 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        milestoneService.update();

        // Second mining reward block should now go through since everything is
        // updated
        rollingBlock = rewardBlock2;
        for (int i = 1; i < 30; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isMilestone());

        // Third mining reward block should now instead go through since
        // everything is
        // updated
        rollingBlock = rewardBlock3;
        for (int i = 1; i < 60; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
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
            Block b = r2.createNextBlock(r1);
            blockGraph.add(b, true);
        }
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isMilestone());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isMilestone());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isMilestone());
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
            tx2.getOutput(0).getValue().value = tx2.getOutput(0).getValue().value + 1;
            createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx2);
            fail();
        } catch (InvalidTransactionException e) {
        }
    }
    
    // TODO check sigops
    
    @Test
    public void testSolidityRewardTxWithTransfers() throws Exception {
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
    public void testSolidityRewardTxWithMalformedRewardInfo() throws Exception {
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
    public void testSolidityRewardMutatedRewardInfo() throws Exception {
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

    
    /* TODO mutate all fields of tokeninfo too
    -> Token CHECK: well-formed tx data TokenInfo
    -> Token CHECK: required fields of TokenInfo exist, i.e. not null
    -> Token CHECK: fields may not be oversize
    -> Token CHECK: issued tokens in txouts must be the same as the issued token
    -> Token CHECK: number of permissioned addresses ok
    -> Token CHECK: predecessor is TOKEN 
    -> Token CHECK: predecessor is of same tokenid and of one lower tokenindex 
    -> Token CHECK: predecessor is of same name and type
    -> Token CHECK: predecessor allows further issuances
    -> Token CHECK: signatures exist
    -> Token CHECK: signatures well-formed
    -> Token CHECK: signatures valid, unique and for permissioned addresses
    -> Token CHECK: enough signatures 
    */
//    
//    @Test
//    public void testSolidityTokenMalformedData() throws Exception {
//        
//        store.resetStore();
//        
//        // Generate an eligible issuance tokenInfo
//        ECKey outKey = walletKeys.get(0);
//        byte[] pubKey = outKey.getPubKey();
//        TokenInfo tokenInfo0 = new TokenInfo();
//        Coin coinbase = Coin.valueOf(77777L, pubKey);
//        long amount = coinbase.getValue();
//        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount, false, true);
//        tokenInfo0.setTokens(tokens);
//        tokenInfo0.getMultiSignAddresses()
//                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
//        
//        // Make mutated versions of the data
//        TokenInfo tokenInfo3 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo4 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo5 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo6 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo7 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo8 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo9 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo10 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo11 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo12 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo13 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo14 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo15 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo16 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo17 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo18 = TokenInfo.parse(tokenInfo0.toByteArray());
//        TokenInfo tokenInfo19 = TokenInfo.parse(tokenInfo0.toByteArray());
////        tokenInfo3.setTokens(null);
////        tokenInfo4.setMultiSignAddresses(null);
////        tokenInfo5.getTokens().setAmount(amount);
////        tokenInfo5.getTokens().setBlockhash(blockhash);
////        tokenInfo5.getTokens().setDescription(description);
////        tokenInfo5.getTokens().setMultiserial(multiserial);
////        tokenInfo5.getTokens().setPrevblockhash(prevblockhash);
////        tokenInfo5.getTokens().setSignnumber(signnumber);
////        tokenInfo5.getTokens().setTokenid(tokenid);
////        tokenInfo5.getTokens().setTokenindex(tokenindex);
////        tokenInfo5.getTokens().setTokenname(tokenname);
////        tokenInfo5.getTokens().setTokenstop(tokenstop);
////        tokenInfo5.getTokens().setTokentype(tokentype);
////        tokenInfo5.getTokens().setUrl(url);
//        
//        
//        // make blocks
//
//        // Make block including it
//        Block block = createNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
//        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
//        
//        // Coinbase with signatures
//        block.addCoinbaseTransaction(outKey.getPubKey(), coinbase, tokenInfo0);
//        Transaction transaction = block.getTransactions().get(0);
//        Sha256Hash sighash1 = transaction.getHash();
//        ECKey.ECDSASignature party1Signature = outKey.sign(sighash1, null);
//        byte[] buf1 = party1Signature.encodeToDER();
//        
////        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
////        MultiSignBy multiSignBy0 = new MultiSignBy();
////        multiSignBy0.setTokenid(tokenInfo.getTokens().getTokenid().trim());
////        multiSignBy0.setTokenindex(0);
////        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
////        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
////        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
////        multiSignBies.add(multiSignBy0);
////        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
////        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
//        
//        
//        
//        
//        
//        
//        
//        
//        // save block
//        block.solve();
//        
//        // Should not go through
//        try {
//            blockGraph.add(block, false);
//            
//            fail();
//        } catch (InvalidTransactionDataException e) {
//        }
//    }
    
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
    public void testSolidityTokenWrongTokenCoinbase() throws Exception {
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