/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import net.bigtangle.core.VerificationException;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TipsServiceTest extends AbstractIntegrationTest {
    /*
     *  TODO Tipsservice test
        -> CHECK: no conflicts with milestone: used "generalized UTXOs" are confirmed + unspent for approved non-milestone blocks
        -> CHECK: type-specific selection conditions (see below)
        -> Reward CHECK: eligibility==eligible or (eligibility==ineligible and overruled)
     */
    
    // TODO test allow unconfirmed but non-conflicting

    @Test
    public void testPrototypeTransactional() throws Exception {
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

        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair(b1);
            assertTrue(tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash()));
        }

        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair(b2);
            assertTrue(tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash()));
        }
        
        // After confirming one of them into the milestone, only that one block is now available
        blockGraph.confirm(b1.getHash());

        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair(b1);
            assertTrue(tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash()));
        }

        try {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair(b2);
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

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
        
        milestoneService.update();

        hit1 = false;
        hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
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

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
        
        milestoneService.update();

        hit1 = false;
        hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
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
        Block b1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());
        Block b2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
        
        milestoneService.update();

        hit1 = false;
        hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
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
        Block b1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1.getHash(), block1.getHash());

        TokenInfo tokenInfo3 = new TokenInfo();
        Coin coinbase3 = Coin.valueOf(666, pubKey);
        long amount3 = coinbase3.getValue();
        Token tokens3 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1,
                amount3, false, true);
        tokenInfo3.setTokens(tokens3);
        tokenInfo3.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens3.getTokenid(), "", outKey.getPublicKeyAsHex()));        
        Block b2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo3, coinbase3, outKey, null, block1.getHash(), block1.getHash());

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
        
        milestoneService.update();

        hit1 = false;
        hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
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

        Block b1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Make another conflicting issuance that goes through
        Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block b2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, genHash, genHash);

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
        
        milestoneService.update();

        hit1 = false;
        hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
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

        Block b1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
        
        // Generate another issuance slightly different
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(6666, pubKey);
        long amount2 = coinbase2.getValue();
        Token tokens2 = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test2", "Test2", 1, 0,
                amount2, false, false);
        tokenInfo2.setTokens(tokens2);
        tokenInfo2.getMultiSignAddresses().add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block b2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, genHash, genHash);

        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
        
        milestoneService.update();

        hit1 = false;
        hit2 = false;
        for (int i = 0; i < 25; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair();
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertTrue((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b1.getHash())) 
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b2.getHash())));
        }
        assertTrue(hit1);
        assertTrue(hit2);
    }
    
}