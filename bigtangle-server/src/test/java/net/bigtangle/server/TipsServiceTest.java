/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    public void testPrototypeTransactional() throws Exception {
        

        // Generate two conflicting blocks
      
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount,  new ECKey()));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
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

        blockGraph.add(b1, true,store);
        blockGraph.add(b2, true,store);
     
        // After confirming one of them into the milestone, only that one block
        // is now available
       //  blockGraph.confirm(b1.getHash(), new HashSet<>(), (long) -1,store);
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(b1, b1);
        }
        mcmcServiceUpdate();
 

        try {
            tipsService.getValidatedBlockPairCompatibleWithExisting(b2,store);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    @Test
    public void testConflictTransactionalUTXO() throws Exception {
        

        // Generate two conflicting blocks
 
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount,  new ECKey()));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
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

        blockGraph.add(b1, true,store);
        blockGraph.add(b2, true,store);

        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        mcmcServiceUpdate();
        
        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair( store);
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
        blockGraph.confirm(b1.getHash(), new HashSet<>(), (long) -1,store);

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithExisting(b1,store);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithExisting(b2,store);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    // Deprecated @Test
    public void testConflictEligibleReward() throws Exception {
        

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true,store);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < 1
                + 1 + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true,store);
        }

        // Generate eligible mining reward blocks
        Block b1 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash(),store);
        Block b2 = rewardService.createReward(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash(),store);

        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        mcmcServiceUpdate();
        
        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair( store);
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
        mcmcServiceUpdate();
        
        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithExisting(b1,store);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithExisting(b2,store);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    @Test
    public void testConflictSameTokenSubsequentIssuance() throws Exception {
        
        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();
        
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
 
        Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = saveTokenUnitTestWithTokenname(tokenInfo, coinbase, outKey, null);

        // Generate two subsequent issuances
        Block b1, b2;
        {
            TokenInfo tokenInfo2 = new TokenInfo();
            Coin coinbase2 = Coin.valueOf(666, pubKey);
          
            Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test",
                    "Test", 1, 1, coinbase2.getValue(), true, 0,networkParameters.getGenesisBlock().getHashAsString());
            tokenInfo2.setToken(tokens2);
            tokenInfo2.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
            b1 = saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1, block1);
        }
        {
            TokenInfo tokenInfo2 = new TokenInfo();
            Coin coinbase2 = Coin.valueOf(666, pubKey);
           
            Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test",
                    "Test", 1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
            tokenInfo2.setToken(tokens2);
            tokenInfo2.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
            b2 = saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null, block1, block1);
        }
        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(block1, block1);
        }
        mcmcServiceUpdate();
        
        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair( store);
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
        blockGraph.confirm(b1.getHash(), new HashSet<>(), (long) -1,store);

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithExisting(b1,store);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithExisting(b2,store);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    @Test
    public void testConflictSameTokenidSubsequentIssuance() throws Exception {
        
        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
       
        Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = saveTokenUnitTestWithTokenname(tokenInfo, coinbase, outKey, null);
     
        mcmcServiceUpdate();
        // Generate two subsequent issuances
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
 
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test",
                "Test", 1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block b1 = saveTokenUnitTestWithTokenname (tokenInfo2, coinbase2, outKey, null);

        mcmcServiceUpdate();
        
        TokenInfo tokenInfo3 = new TokenInfo();
        Coin coinbase3 = Coin.valueOf(666, pubKey);
 
        Token tokens3 = Token.buildSimpleTokenInfo(false, block1.getHash(), Utils.HEX.encode(pubKey), "Test",
                "Test", 1, 1, coinbase3.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo3.setToken(tokens3);
        tokenInfo3.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens3.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block b2 = saveTokenUnitTestWithTokenname(tokenInfo3, coinbase3, outKey, null);

         
        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            mcmcServiceUpdate();
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair( store);
            hit1 |= tips.getLeft().equals(b1.getHash()) || tips.getRight().equals(b1.getHash());
            hit2 |= tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash());
            assertFalse((tips.getLeft().equals(b1.getHash()) && tips.getRight().equals(b2.getHash()))
                    || (tips.getLeft().equals(b2.getHash()) && tips.getRight().equals(b1.getHash())));
            if (hit1 && hit2)
                break;
        }
        assertTrue(hit1);
      //  assertTrue(hit2);

        // After confirming one of them into the milestone, only that one block
        // is now available
        mcmcServiceUpdate();
      //  blockGraph.confirm(b1.getHash(), new HashSet<>(), (long) -1,store);
        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithExisting(b1,store);
             assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithExisting(b2,store);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    @Test
    public void testConflictSameTokenFirstIssuance() throws Exception {
        

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);
 
        Token tokens = Token.buildSimpleTokenInfo(false, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block b1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Make another conflicting issuance that goes through
        Block genHash = networkParameters.getGenesisBlock();
        Block b2 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null, genHash, genHash);

        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        mcmcServiceUpdate();
        
        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair( store);
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
        blockGraph.confirm(b1.getHash(), new HashSet<>(), (long) -1,store);

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithExisting(b1,store);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithExisting(b2,store);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

    @Test
    public void testConflictSameTokenidFirstIssuance() throws Exception {
        

        // Generate an issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);
 
        Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block b1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Generate another issuance slightly different
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(6666, pubKey);
 
        Token tokens2 = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test2", "Test2", 1, 0, coinbase2.getValue(),
                false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block genHash = networkParameters.getGenesisBlock() ;
        Block b2 = saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null,genHash,genHash);

        for (int i = 0; i < 5; i++) {
            createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        }
        mcmcServiceUpdate();
        
        boolean hit1 = false;
        boolean hit2 = false;
        for (int i = 0; i < 150; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPair( store);
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
        blockGraph.confirm(b1.getHash(), new HashSet<>(), (long) -1,store);

        for (int i = 0; i < 20; i++) {
            Pair<Sha256Hash, Sha256Hash> tips = tipsService.getValidatedBlockPairCompatibleWithExisting(b1,store);
            assertFalse(tips.getLeft().equals(b2.getHash()) || tips.getRight().equals(b2.getHash()));
        }

        try {
            tipsService.getValidatedBlockPairCompatibleWithExisting(b2,store);
            fail();
        } catch (VerificationException e) {
            // Expected
        }
    }

}