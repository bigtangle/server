/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

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
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FullPrunedBlockGraphTest extends AbstractIntegrationTest {

    @Test
    public void testConnectTransactionalUTXOs() throws Exception {
        store.resetStore();
        
        // Create block with UTXOs
        Transaction tx1 = createTestGenesisTransaction();
        assertNull(transactionService.getUTXO(tx1.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx1.getOutput(1).getOutPointFor()));
        
        createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx1);
        
        // Should exist now
        final UTXO utxo1 = transactionService.getUTXO(tx1.getOutput(0).getOutPointFor());
        final UTXO utxo2 = transactionService.getUTXO(tx1.getOutput(1).getOutPointFor());
        assertNotNull(utxo1);
        assertNotNull(utxo2);
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());
    }

    @Test
    public void testConnectRewardUTXOs() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        
        // Should exist now
        assertFalse(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));
    }

    @Test
    public void testConnectTokenUTXOs() throws Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
     
        // Generate an eligible issuance
        Sha256Hash firstIssuance;
        {
            TokenInfo tokenInfo = new TokenInfo();
            
            Coin coinbase = Coin.valueOf(77777L, pubKey);
            long amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                    amount, false, false);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we need other blocks beforehand.
            Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
            firstIssuance = block1.getHash();

            // Should exist now
            store.getTokenConfirmed(block1.getHash().toString()); // Fine as long as it does not throw
            assertFalse(store.getTokenSpent(block1.getHash().toString()));        
        }
     
        // Generate a subsequent issuance
        {
            TokenInfo tokenInfo = new TokenInfo();
            
            Coin coinbase = Coin.valueOf(77777L, pubKey);
            long amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, firstIssuance.toString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1,
                    amount, false, true);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we need other blocks beforehand.
            Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

            // Should exist now
            store.getTokenConfirmed(block1.getHash().toString()); // Fine as long as it does not throw
            assertFalse(store.getTokenSpent(block1.getHash().toString()));        
        }
    }

    @Test
    public void testConfirmTransactionalUTXOs() throws Exception {
        store.resetStore();
        
        // Create block with UTXOs
        Transaction tx1 = createTestGenesisTransaction();
        assertNull(transactionService.getUTXO(tx1.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx1.getOutput(1).getOutPointFor()));
        
        Block spenderBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx1);
        
        // Confirm
        blockGraph.confirm(spenderBlock.getHash());
        
        // Should be confirmed now
        final UTXO utxo1 = transactionService.getUTXO(tx1.getOutput(0).getOutPointFor());
        final UTXO utxo2 = transactionService.getUTXO(tx1.getOutput(1).getOutPointFor());
        assertTrue(utxo1.isConfirmed());
        assertTrue(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());
        
        // Extra for transactional UTXOs
        assertEquals(store.getTransactionOutputConfirmingBlock(utxo1.getHash(), utxo1.getIndex()), spenderBlock.getHash());
        assertEquals(store.getTransactionOutputConfirmingBlock(utxo2.getHash(), utxo2.getIndex()), spenderBlock.getHash());
        
        // Further manipulations on prev UTXOs
        final UTXO origUTXO = transactionService.getUTXO(networkParameters.getGenesisBlock().getTransactions().get(0).getOutput(0).getOutPointFor());
        assertTrue(origUTXO.isConfirmed());
        assertTrue(origUTXO.isSpent());
        assertEquals(store.getTransactionOutputSpender(origUTXO.getHash(), origUTXO.getIndex()).getBlockHash(), spenderBlock.getHash());
    }

    @Test
    public void testConfirmRewardUTXOs() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        
        // Confirm
        blockGraph.confirm(rewardBlock1.getHash());

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));
        
        // Further manipulations on prev UTXOs
        assertTrue(store.getRewardConfirmed(networkParameters.getGenesisBlock().getHash()));
        assertTrue(store.getRewardSpent(networkParameters.getGenesisBlock().getHash()));
        assertEquals(store.getRewardSpender(networkParameters.getGenesisBlock().getHash()), rewardBlock1.getHash());
        
        // Check the virtual txs too
        Transaction virtualTX = blockGraph.generateVirtualMiningRewardTX(rewardBlock1);
        final UTXO utxo1 = transactionService.getUTXO(virtualTX.getOutput(0).getOutPointFor());
        assertTrue(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testConfirmTokenUTXOs() throws Exception {
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

        // This (saveBlock) calls milestoneUpdate currently
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
        blockGraph.confirm(block1.getHash());

        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(block1.getHash().toString()));
        assertFalse(store.getTokenSpent(block1.getHash().toString()));   
    }

    @Test
    public void testUnconfirmTransactionalUTXOs() throws Exception {
        store.resetStore();
        
        // Create block with UTXOs
        Transaction tx11 = createTestGenesisTransaction();
        assertNull(transactionService.getUTXO(tx11.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx11.getOutput(1).getOutPointFor()));
        
        Block block = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx11);
        
        // Confirm
        blockGraph.confirm(block.getHash());
        
        // Should be confirmed now
        final UTXO utxo11 = transactionService.getUTXO(tx11.getOutput(0).getOutPointFor());
        final UTXO utxo21 = transactionService.getUTXO(tx11.getOutput(1).getOutPointFor());
        assertNotNull(utxo11);
        assertNotNull(utxo21);
        assertTrue(utxo11.isConfirmed());
        assertTrue(utxo21.isConfirmed());
        assertFalse(utxo11.isSpent());
        assertFalse(utxo21.isSpent());
        
        // Unconfirm
        blockGraph.unconfirm(block.getHash());
        
        // Should be unconfirmed now
        final UTXO utxo1 = transactionService.getUTXO(tx11.getOutput(0).getOutPointFor());
        final UTXO utxo2 = transactionService.getUTXO(tx11.getOutput(1).getOutPointFor());
        assertNotNull(utxo1);
        assertNotNull(utxo2);
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());
        
        // Extra for transactional UTXOs
        assertNull(store.getTransactionOutputConfirmingBlock(utxo1.getHash(), utxo1.getIndex()));
        assertNull(store.getTransactionOutputConfirmingBlock(utxo2.getHash(), utxo2.getIndex()));
        
        // Further manipulations on prev UTXOs
        final UTXO origUTXO = transactionService.getUTXO(networkParameters.getGenesisBlock().getTransactions().get(0).getOutput(0).getOutPointFor());
        assertTrue(origUTXO.isConfirmed());
        assertFalse(origUTXO.isSpent());
        assertNull(store.getTransactionOutputSpender(origUTXO.getHash(), origUTXO.getIndex()));
    }

    @Test
    public void testUnconfirmRewardUTXOs() throws Exception {
        store.resetStore();
        
        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i1 = 0; i1 < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        
        // Generate mining reward block
        Block rewardBlock11 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        
        // Confirm
        blockGraph.confirm(rewardBlock11.getHash());
        
        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));
        
        // Unconfirm
        blockGraph.unconfirm(rewardBlock11.getHash());

        // Should be unconfirmed now
        assertFalse(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));
        
        // Further manipulations on prev UTXOs
        assertTrue(store.getRewardConfirmed(networkParameters.getGenesisBlock().getHash()));
        assertFalse(store.getRewardSpent(networkParameters.getGenesisBlock().getHash()));
        assertNull(store.getRewardSpender(networkParameters.getGenesisBlock().getHash()));
        
        // Check the virtual txs too
        Transaction virtualTX = blockGraph.generateVirtualMiningRewardTX(rewardBlock11);
        final UTXO utxo1 = transactionService.getUTXO(virtualTX.getOutput(0).getOutPointFor());
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testUnconfirmTokenUTXOs() throws Exception {
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
        
        // This (saveBlock) calls milestoneUpdate currently
        Block block11 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
        blockGraph.confirm(block11.getHash());
        
        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(block11.getHash().toString()));
        assertFalse(store.getTokenSpent(block11.getHash().toString()));
        
        // Unconfirm
        blockGraph.unconfirm(block11.getHash());

        // Should be unconfirmed now
        assertFalse(store.getTokenConfirmed(block11.getHash().toString()));
        assertFalse(store.getTokenSpent(block11.getHash().toString()));        
    }

    @Test
    public void testUnconfirmDependentsTransactional() throws Exception {
        store.resetStore();
        
        // Create blocks with UTXOs
        Transaction tx1 = createTestGenesisTransaction();
        Block block1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx1);
        blockGraph.confirm(block1.getHash());
        Transaction tx2 = createTestGenesisTransaction();
        Block block2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx2);
        blockGraph.confirm(block2.getHash());
        
        // Should be confirmed now
        assertTrue(store.getBlockEvaluation(block1.getHash()).isMilestone());
        assertTrue(store.getBlockEvaluation(block2.getHash()).isMilestone());
        UTXO utxo11 = transactionService.getUTXO(tx1.getOutput(0).getOutPointFor());
        UTXO utxo21 = transactionService.getUTXO(tx1.getOutput(1).getOutPointFor());
        assertNotNull(utxo11);
        assertNotNull(utxo21);
        assertTrue(utxo11.isConfirmed());
        assertTrue(utxo21.isConfirmed());
        assertTrue(utxo11.isSpent());
        assertFalse(utxo21.isSpent());        
        utxo11 = transactionService.getUTXO(tx2.getOutput(0).getOutPointFor());
        utxo21 = transactionService.getUTXO(tx2.getOutput(1).getOutPointFor());
        assertNotNull(utxo11);
        assertNotNull(utxo21);
        assertTrue(utxo11.isConfirmed());
        assertTrue(utxo21.isConfirmed());
        assertFalse(utxo11.isSpent());
        assertFalse(utxo21.isSpent());
        
        // Unconfirm first block
        blockGraph.unconfirm(block1.getHash());
        
        // Both should be unconfirmed now
        assertFalse(store.getBlockEvaluation(block1.getHash()).isMilestone());
        assertFalse(store.getBlockEvaluation(block2.getHash()).isMilestone());
        for (Transaction tx : new Transaction[] {tx1, tx2}) {
            final UTXO utxo1 = transactionService.getUTXO(tx.getOutput(0).getOutPointFor());
            final UTXO utxo2 = transactionService.getUTXO(tx.getOutput(1).getOutPointFor());
            assertNotNull(utxo1);
            assertNotNull(utxo2);
            assertFalse(utxo1.isConfirmed());
            assertFalse(utxo2.isConfirmed());
            assertFalse(utxo1.isSpent());
            assertFalse(utxo2.isSpent());
            
            // Extra for transactional UTXOs
            assertNull(store.getTransactionOutputConfirmingBlock(utxo1.getHash(), utxo1.getIndex()));
            assertNull(store.getTransactionOutputConfirmingBlock(utxo2.getHash(), utxo2.getIndex()));
        }
    }

    @Test
    public void testUnconfirmDependentsRewardOtherRewards() throws Exception {
        store.resetStore();
        
        // Generate blocks until passing second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i1 = 0; i1 < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        for (int i1 = 0; i1 < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        
        // Generate mining reward block
        Block rewardBlock11 = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        
        // Confirm
        blockGraph.confirm(rewardBlock11.getHash());
        
        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));
        
        // Generate second mining reward block
        Block rewardBlock2 = transactionService.createAndAddMiningRewardBlock(rewardBlock11.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        
        // Confirm
        blockGraph.confirm(rewardBlock2.getHash());
        
        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertTrue(store.getRewardSpent(rewardBlock11.getHash()));
        assertTrue(store.getRewardConfirmed(rewardBlock2.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock2.getHash()));
        
        // Unconfirm
        blockGraph.unconfirm(rewardBlock11.getHash());

        // Both should be unconfirmed now
        assertFalse(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));
        assertFalse(store.getRewardConfirmed(rewardBlock2.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock2.getHash()));
        
        // Further manipulations on prev UTXOs
        assertTrue(store.getRewardConfirmed(networkParameters.getGenesisBlock().getHash()));
        assertFalse(store.getRewardSpent(networkParameters.getGenesisBlock().getHash()));
        assertNull(store.getRewardSpender(networkParameters.getGenesisBlock().getHash()));
        assertFalse(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));
        assertNull(store.getRewardSpender(rewardBlock11.getHash()));
        
        // Check the virtual txs too
        Transaction virtualTX = blockGraph.generateVirtualMiningRewardTX(rewardBlock11);
        UTXO utxo1 = transactionService.getUTXO(virtualTX.getOutput(0).getOutPointFor());
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
        virtualTX = blockGraph.generateVirtualMiningRewardTX(rewardBlock2);
        utxo1 = transactionService.getUTXO(virtualTX.getOutput(0).getOutPointFor());
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }
    
    @Test
    public void testUnconfirmDependentsRewardVirtualSpenders() throws Exception {
        store.resetStore();
        
        // Generate blocks until passing second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        
        // Generate mining reward block
        Block rewardBlock = transactionService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        
        // Confirm
        blockGraph.confirm(rewardBlock.getHash());
        
        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock.getHash()));
        
        // Generate spending block
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        outputs.removeIf(o -> o.getValue().value == NetworkParameters.testCoin);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, genesiskey));
        tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), genesiskey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);
        
        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig, genesiskey);
        input.setScriptSig(inputScript);
        assertNull(transactionService.getUTXO(tx.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx.getOutput(1).getOutPointFor()));
        
        Block spenderBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx);
        
        // Confirm
        blockGraph.confirm(spenderBlock.getHash());
     
        // Should be confirmed now
        final UTXO utxo11 = transactionService.getUTXO(tx.getOutput(0).getOutPointFor());
        final UTXO utxo21 = transactionService.getUTXO(tx.getOutput(1).getOutPointFor());
        assertNotNull(utxo11);
        assertNotNull(utxo21);
        assertTrue(utxo11.isConfirmed());
        assertTrue(utxo21.isConfirmed());
        assertFalse(utxo11.isSpent());
        assertFalse(utxo21.isSpent());
        
        // Unconfirm reward block
        blockGraph.unconfirm(rewardBlock.getHash());

        // Both should be unconfirmed now
        assertFalse(store.getRewardConfirmed(rewardBlock.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock.getHash()));
        final UTXO utxo1 = transactionService.getUTXO(tx.getOutput(0).getOutPointFor());
        final UTXO utxo2 = transactionService.getUTXO(tx.getOutput(1).getOutPointFor());
        assertNotNull(utxo1);
        assertNotNull(utxo2);
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());
        
        // Extra for transactional UTXOs
        assertNull(store.getTransactionOutputConfirmingBlock(utxo1.getHash(), utxo1.getIndex()));
        assertNull(store.getTransactionOutputConfirmingBlock(utxo2.getHash(), utxo2.getIndex()));
        
        // Check the virtual txs too
        Transaction virtualTX = blockGraph.generateVirtualMiningRewardTX(rewardBlock);
        UTXO utxo3 = transactionService.getUTXO(virtualTX.getOutput(0).getOutPointFor());
        assertFalse(utxo3.isConfirmed());
        assertFalse(utxo3.isSpent());
        assertNull(store.getTransactionOutputSpender(utxo3.getHash(), utxo3.getIndex()));
    }

    @Test
    public void testUnconfirmDependentsToken() throws Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        
        // Generate an eligible issuance
        Sha256Hash firstIssuance;
        {
            TokenInfo tokenInfo = new TokenInfo();
            
            Coin coinbase = Coin.valueOf(77777L, pubKey);
            long amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0,
                    amount, false, false);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we need other blocks beforehand.
            Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
            firstIssuance = block1.getHash();  
        }
     
        // Generate a subsequent issuance
        Sha256Hash subseqIssuance;
        {
            TokenInfo tokenInfo = new TokenInfo();
            
            Coin coinbase = Coin.valueOf(77777L, pubKey);
            long amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, firstIssuance.toString(), Utils.HEX.encode(pubKey), "Test", "Test", 1, 1,
                    amount, false, true);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we need other blocks beforehand.
            Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
            subseqIssuance = block1.getHash();
        }

        // Confirm
        blockGraph.confirm(firstIssuance);
        blockGraph.confirm(subseqIssuance);
        
        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(subseqIssuance.toString()));
        assertTrue(store.getTokenConfirmed(subseqIssuance.toString()));
        
        // Unconfirm
        blockGraph.unconfirm(firstIssuance);

        // Should be unconfirmed now
        assertFalse(store.getTokenConfirmed(subseqIssuance.toString()));
        assertFalse(store.getTokenConfirmed(subseqIssuance.toString()));
    }
    
}