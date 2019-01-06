/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FullPrunedBlockGraphTest extends AbstractIntegrationTest {
    
    // TODO 4 + 5 checks implement fully in existing tests
    // TODO unconfirm dependents correctly!
    // TODO Tipsservice test

    @Test
    public void testConnectTransactionalUTXOs() throws Exception {
        store.resetStore();

        // A few blocks exist beforehand
        for (int i = 0; i < 5; i++) {
            Block rollingBlock1 = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
            blockgraph.add(rollingBlock1, true);
        }
        
        // Create block with UTXOs
        Transaction tx1 = makeTestTransaction();
        assertNull(transactionService.getUTXO(tx1.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx1.getOutput(1).getOutPointFor()));
        
        createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                networkParameters.getGenesisBlock(), tx1);
        
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

        // A few blocks exist beforehand
        for (int i = 0; i < 5; i++) {
            Block rollingBlock1 = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
            blockgraph.add(rollingBlock1, true);
        }

        // Generate blocks until passing first reward interval
        Block rollingBlock = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
        blockgraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + 2; i++) {
            rollingBlock1 = BlockForTest.createNextBlock(rollingBlock1, Block.BLOCK_VERSION_GENESIS, rollingBlock1);
            blockgraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        
        // Should exist now
        assertFalse(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));
    }

    @Test
    public void testConnectTokenUTXOs() throws Exception {
        store.resetStore();

        // A few blocks exist beforehand
        for (int i = 0; i < 5; i++) {
            Block rollingBlock1 = BlockForTest.createNextBlock(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, networkParameters.getGenesisBlock());
            blockgraph.add(rollingBlock1, true);
        }
     
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

        // This (saveBlock) calls milestoneUpdate currently, that's why we need other blocks beforehand.
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

        // Should exist now
        assertFalse(store.getTokenConfirmed(block1.getHash().toString()));
        assertFalse(store.getTokenSpent(block1.getHash().toString()));        
    }

    @Test
    public void testConfirmTransactionalUTXOs() throws Exception {
        store.resetStore();
        
        // Create block with UTXOs
        Transaction tx1 = makeTestTransaction();
        assertNull(transactionService.getUTXO(tx1.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx1.getOutput(1).getOutPointFor()));
        
        createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                networkParameters.getGenesisBlock(), tx1);
        
        // Confirm
        milestoneService.update();
        
        // Should be confirmed now
        final UTXO utxo1 = transactionService.getUTXO(tx1.getOutput(0).getOutPointFor());
        final UTXO utxo2 = transactionService.getUTXO(tx1.getOutput(0).getOutPointFor());
        assertNotNull(utxo1);
        assertNotNull(utxo2);
        assertTrue(utxo1.isConfirmed());
        assertTrue(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());
    }

    @Test
    public void testConfirmRewardUTXOs() throws Exception {
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

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        
        // Confirm
        milestoneService.update();

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));
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

        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(block1.getHash().toString()));
        assertFalse(store.getTokenSpent(block1.getHash().toString()));        
    }

    @Test
    public void testUnconfirmTransactionalUTXOs() throws Exception {
        store.resetStore();
        
        // Create block with UTXOs
        Transaction tx11 = makeTestTransaction();
        assertNull(transactionService.getUTXO(tx11.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx11.getOutput(1).getOutPointFor()));
        
        createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(),
                networkParameters.getGenesisBlock(), tx11);
        
        // Confirm
        milestoneService.update();
        
        // Should be confirmed now
        final UTXO utxo11 = transactionService.getUTXO(tx11.getOutput(0).getOutPointFor());
        final UTXO utxo21 = transactionService.getUTXO(tx11.getOutput(0).getOutPointFor());
        assertNotNull(utxo11);
        assertNotNull(utxo21);
        assertTrue(utxo11.isConfirmed());
        assertTrue(utxo21.isConfirmed());
        assertFalse(utxo11.isSpent());
        assertFalse(utxo21.isSpent());
        
        // Build alternate path
        Block rollingBlock2 = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            rollingBlock2 = BlockForTest.createNextBlock(rollingBlock2, Block.BLOCK_VERSION_GENESIS, rollingBlock2);
            blockgraph.add(rollingBlock2, true);     
        }
        
        // Unconfirm
        milestoneService.update();
        
        // Should be unconfirmed now
        final UTXO utxo1 = transactionService.getUTXO(tx11.getOutput(0).getOutPointFor());
        final UTXO utxo2 = transactionService.getUTXO(tx11.getOutput(0).getOutPointFor());
        assertNotNull(utxo1);
        assertNotNull(utxo2);
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());
    }

    @Test
    public void testUnconfirmRewardUTXOs() throws Exception {
        store.resetStore();
        
        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i1 = 0; i1 < NetworkParameters.REWARD_HEIGHT_INTERVAL + 2; i1++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blockgraph.add(rollingBlock, true);
        }
        
        // Generate mining reward block
        Block rewardBlock11 = transactionService.createMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        
        // Confirm
        milestoneService.update();
        
        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));

        // Build alternate path
        for (int i = 0; i < 5; i++) {
            rollingBlock = BlockForTest.createNextBlock(rollingBlock, Block.BLOCK_VERSION_GENESIS, rollingBlock);
            blockgraph.add(rollingBlock, true);     
        }
        
        // Unconfirm
        milestoneService.update();

        // Should be unconfirmed now
        assertFalse(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));
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
        
        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(block11.getHash().toString()));
        assertFalse(store.getTokenSpent(block11.getHash().toString()));

        // Build alternate path
        Block rollingBlock2 = networkParameters.getGenesisBlock();
        for (int i = 0; i < 3; i++) {
            rollingBlock2 = BlockForTest.createNextBlock(rollingBlock2, Block.BLOCK_VERSION_GENESIS, rollingBlock2);
            blockgraph.add(rollingBlock2, true);     
        }
        
        // Unconfirm
        milestoneService.update();

        // Should be unconfirmed now
        assertFalse(store.getTokenConfirmed(block11.getHash().toString()));
        assertFalse(store.getTokenSpent(block11.getHash().toString()));        
    }
    
}