/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
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
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TipsServiceTest extends AbstractIntegrationTest {

    @Test
    public void testIneligibleRewards() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        Block rollingBlock1 = rollingBlock;
        blockGraph.add(rollingBlock, false);

        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, false);
        }
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, false);
        }
        blockGraph.confirm(rollingBlock.getHash(), new HashSet<>());
        blockGraph.confirm(rollingBlock1.getHash(), new HashSet<>());

        // Generate ineligible mining reward blocks
        Block b1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash(), true);
        transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash(), true);

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();

            // Do not hit the ineligible mining reward blocks
            assertTrue((tips.getLeft().equals(rollingBlock.getHash()) || tips.getLeft().equals(rollingBlock1.getHash()))
                    && (tips.getRight().equals(rollingBlock.getHash())
                            || tips.getRight().equals(rollingBlock1.getHash())));
        }

        // After overruling one of them, that one should be eligible
        rollingBlock = b1;
        for (int i = 0; i < 150; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, false);
        }

        // Wait until the lock time ends. It should enter the milestone
        Thread.sleep(NetworkParameters.REWARD_OVERRULE_TIME_MS);
        milestoneService.update();
        assertTrue(store.getBlockEvaluation(b1.getHash()).isMilestone());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();

            // Do not hit the ineligible mining reward block b2 but do go
            // through b1
            assertTrue((tips.getLeft().equals(rollingBlock.getHash()) && tips.getRight().equals(rollingBlock.getHash()))
                    || (tips.getLeft().equals(rollingBlock1.getHash())
                            && tips.getRight().equals(rollingBlock1.getHash())));
        }

    }

    @Test
    public void testPrototypeTransactional() throws Exception {
        store.resetStore();

        // Generate two conflicting blocks
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, walletKeys.get(8)));
        TransactionInput input = doublespendTX.addInput(spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with conflict
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        Block b2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);

        blockGraph.add(b1, true);
        blockGraph.add(b2, true);

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertTrue(tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash()));
        }

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            assertTrue(tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash()));
        }

        // After confirming one of them into the milestone, only that one block
        // is now available
        blockGraph.confirm(b1.getHash(), new HashSet<>());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertTrue(tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    @Test
    public void testConflictTransactionalUTXO() throws Exception {
        store.resetStore();

        // Generate two conflicting blocks
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, walletKeys.get(8)));
        TransactionInput input = doublespendTX.addInput(spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with conflict
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        Block b2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);

        blockGraph.add(b1, true);
        blockGraph.add(b2, true);
        
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        milestoneService.update();

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertFalse((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b2.getHash()))
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b1.getHash())));
            if (hit1 && hit2)
                break;
        }
        assertTrue(hit1);
        assertTrue(hit2);

        // After confirming one of them into the milestone, only that one block
        // is now available
        blockGraph.confirm(b1.getHash(), new HashSet<>());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    @Test
    public void testConflictEligibleReward() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate eligible mining reward blocks
        Block b1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        Block b2 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        milestoneService.update();

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertFalse((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b2.getHash()))
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b1.getHash())));
            if (hit1 && hit2)
                break;
        }
        assertTrue(hit1);
        assertTrue(hit2);

        // After confirming one of them into the milestone, only that one block
        // is now available
        blockGraph.confirm(b1.getHash(), new HashSet<>());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
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
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                false);
        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate two subsequent issuances
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
                "Test", 1, 1, amount2, true);
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block b1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(),
                block1.getHash());
        Block b2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(),
                block1.getHash());
        
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(block1, block1);
        }
        milestoneService.update();

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertFalse((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b2.getHash()))
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b1.getHash())));
            if (hit1 && hit2)
                break;
        }
        assertTrue(hit1);
        assertTrue(hit2);

        // After confirming one of them into the milestone, only that one block
        // is now available
        blockGraph.confirm(b1.getHash(), new HashSet<>());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
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
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                false);
        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate two subsequent issuances
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
                "Test", 1, 1, amount2, true);
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block b1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(),
                block1.getHash());

        TokenInfo tokenInfo3 = new TokenInfo();
        Coin coinbase3 = Coin.valueOf(666, pubKey);
        long amount3 = coinbase3.getValue();
        Token tokens3 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
                "Test", 1, 1, amount3, true);
        tokenInfo3.setToken(tokens3);
        tokenInfo3.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens3.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block b2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo3, coinbase3, outKey, null, block1.getHash(),
                block1.getHash());
        
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(block1, block1);
        }
        milestoneService.update();

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertFalse((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b2.getHash()))
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b1.getHash())));
            if (hit1 && hit2)
                break;
        }
        assertTrue(hit1);
        assertTrue(hit2);

        // After confirming one of them into the milestone, only that one block
        // is now available
        blockGraph.confirm(b1.getHash(), new HashSet<>());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
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
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                true);

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block b1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Make another conflicting issuance that goes through
        Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block b2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, genHash, genHash);
        
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        milestoneService.update();

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertFalse((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b2.getHash()))
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b1.getHash())));
            if (hit1 && hit2)
                break;
        }
        assertTrue(hit1);
        assertTrue(hit2);

        // After confirming one of them into the milestone, only that one block
        // is now available
        blockGraph.confirm(b1.getHash(), new HashSet<>());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
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
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                true);

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block b1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Generate another issuance slightly different
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(6666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test2", "Test2", 1, 0, amount2,
                false);
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block b2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, genHash, genHash);
        
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        milestoneService.update();

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertFalse((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b2.getHash()))
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b1.getHash())));
            if (hit1 && hit2)
                break;
        }
        assertTrue(hit1);
        assertTrue(hit2);

        // After confirming one of them into the milestone, only that one block
        // is now available
        blockGraph.confirm(b1.getHash(), new HashSet<>());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    @Test
    public void testConflictOrderReclaim() throws Exception {
        @SuppressWarnings({ "deprecation", "unused" })
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(8);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        Block token = resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Open sell order for test tokens
        Block order = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        Block rewardBlock = makeAndConfirmOrderMatching(addedBlocks, token);

        // Generate reclaim blocks
        Block b1 = makeReclaim(order.getHash(), rewardBlock.getHash(), addedBlocks, order, rewardBlock);
        Block b2 = makeReclaim(order.getHash(), rewardBlock.getHash(), addedBlocks, order, rewardBlock);
        
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        milestoneService.update();

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertFalse((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b2.getHash()))
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b1.getHash())));
            if (hit1 && hit2)
                break;
        }
        assertTrue(hit1);
        assertTrue(hit2);

        // After confirming one of them into the milestone, only that one block
        // is now available
        blockGraph.confirm(b1.getHash(), new HashSet<>());

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithPrototype(b1);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithPrototype(b2);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }
}