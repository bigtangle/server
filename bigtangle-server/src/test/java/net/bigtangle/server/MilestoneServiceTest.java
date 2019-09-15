/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.http.server.resp.GetBlockListResponse;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MilestoneServiceTest extends AbstractIntegrationTest {

    @Test
    public void testUnconfirmedOutput() throws Exception {
        store.resetStore();

        // Create block with UTXO
        Transaction tx1 = createTestGenesisTransaction();
        Block depBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx1);

        milestoneService.update();

        // Create block with dependency
        Block betweenBlock = createAndAddNextBlock(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock());
        Transaction tx2 = createTestGenesisTransaction();
        Block block = createAndAddNextBlockWithTransaction(betweenBlock, betweenBlock, tx2);

        store.resetStore();

        blockGraph.add(depBlock, false);
        blockGraph.add(betweenBlock, false);
        blockGraph.add(block, false);

        Block b1 = createAndAddNextBlock(depBlock, block);

        milestoneService.update();

        // Update cycle should allow all through
        assertTrue(blockService.getBlockEvaluation(depBlock.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
    }

    @Test
    public void testConflictTransactionalUTXO() throws Exception {
        store.resetStore();

        // Generate two conflicting blocks
        ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, walletKeys.get(8)));
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

        blockGraph.add(b1, true);
        blockGraph.add(b2, true);

        createAndAddNextBlock(b1, b2);

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(b2.getHash()).isConfirmed());

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
    }

    @Test
    public void testConflictReward() throws Exception {
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
        Block b1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        Block b2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        createAndAddNextBlock(b2, b1);

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(b2.getHash()).isConfirmed());

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
    }

    @Test
    public void testConflictSameTokenSubsequentIssuance() throws Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
 
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Generate two subsequent issuances

        Block conflictBlock1, conflictBlock2;
        {
            TokenInfo tokenInfo2 = new TokenInfo();
            Coin coinbase2 = Coin.valueOf(666, pubKey);
        
            Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey),
                    "Test", "Test", 1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
            tokenInfo2.setToken(tokens2);
            tokenInfo2.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
            conflictBlock1 = saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
        }
        {
            TokenInfo tokenInfo2 = new TokenInfo();
            Coin coinbase2 = Coin.valueOf(666, pubKey);
       
            Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey),
                    "Test", "Test", 1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
            tokenInfo2.setToken(tokens2);
            tokenInfo2.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
            conflictBlock2 = saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
        }
        // Make a fusing block
        Block rollingBlock = conflictBlock1.createNextBlock(conflictBlock2);
        blockGraph.add(rollingBlock, true);

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(conflictBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(conflictBlock1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(conflictBlock2.getHash()).isConfirmed());

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(conflictBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(conflictBlock1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(conflictBlock2.getHash()).isConfirmed());
    }

    @Test
    public void testConflictSameTokenidSubsequentIssuance() throws Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        TokenInfo tokenInfo = new TokenInfo();
        Coin coinbase = Coin.valueOf(77777L, pubKey);
     
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Generate two subsequent issuances
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(666, pubKey);
  
        Token tokens2 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
                "Test", 1, 1, coinbase2.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block conflictBlock1 = saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null);

        TokenInfo tokenInfo3 = new TokenInfo();
        Coin coinbase3 = Coin.valueOf(666, pubKey);
 
        Token tokens3 = Token.buildSimpleTokenInfo(false, block1.getHashAsString(), Utils.HEX.encode(pubKey), "Test",
                "Test", 1, 1, coinbase3.getValue(), true, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo3.setToken(tokens3);
        tokenInfo3.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens3.getTokenid(), "", outKey.getPublicKeyAsHex()));
        Block conflictBlock2 = saveTokenUnitTest(tokenInfo3, coinbase3, outKey, null);

        // Make a fusing block
        Block rollingBlock = conflictBlock1.createNextBlock(conflictBlock2);
        blockGraph.add(rollingBlock, true);

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(conflictBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(conflictBlock1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(conflictBlock2.getHash()).isConfirmed());

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(conflictBlock1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(conflictBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(conflictBlock1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(conflictBlock2.getHash()).isConfirmed());
    }

    @Test
    public void testConflictSameTokenFirstIssuance() throws Exception {
        store.resetStore();

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);
 
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Make another conflicting issuance that goes through
        // Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block block2 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);
        Block rollingBlock = block2.createNextBlock(block1);
        blockGraph.add(rollingBlock, true);

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(block1.getHash()).isConfirmed());

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
    }

    @Test
    public void testConflictSameTokenidFirstIssuance() throws Exception {
        store.resetStore();

        // Generate an issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);
 
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Generate another issuance slightly different
        TokenInfo tokenInfo2 = new TokenInfo();
        Coin coinbase2 = Coin.valueOf(6666, pubKey);
 
        Token tokens2 = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test2", "Test2", 1, 0, coinbase2.getValue(),
                false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo2.setToken(tokens2);
        tokenInfo2.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens2.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // Sha256Hash genHash = networkParameters.getGenesisBlock().getHash();
        Block block2 = saveTokenUnitTest(tokenInfo2, coinbase2, outKey, null);
        Block rollingBlock = block2.createNextBlock(block1);
        blockGraph.add(rollingBlock, true);

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(block1.getHash()).isConfirmed());

        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
    }

    @Test
    public void testPrunedConflict() throws Exception {
        store.resetStore();

        // Create block with UTXO
        Transaction tx1 = createTestGenesisTransaction();
        Block txBlock1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx1);

        // Generate blocks 
        Block rollingBlock = txBlock1.createNextBlock(txBlock1);
        blockGraph.add(rollingBlock, true);

        for (int i = 0; i < NetworkParameters.ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF + 5; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        
        // Generate mining reward block
        rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        milestoneService.update();

        // First block is no longer maintained, while newest one is maintained
        assertFalse(blockService.getBlockEvaluation(txBlock1.getHash()).isMaintained());
        assertTrue(blockService.getBlockEvaluation(rollingBlock.getHash()).isMaintained());

        // All confirmed
        assertTrue(blockService.getBlockEvaluation(txBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rollingBlock.getHash()).isConfirmed());

        // Create conflicting block with UTXO
        Block txBlock2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(), tx1);
        rollingBlock = rollingBlock.createNextBlock(txBlock2);
        blockGraph.add(rollingBlock, true);

        milestoneService.update();

        // Confirmation should stay true except for conflict
        assertTrue(blockService.getBlockEvaluation(txBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(txBlock2.getHash()).isConfirmed());
    }

    @Test
    public void testUpdateConflictingTransactionalMilestoneCandidates() throws Exception {
        store.resetStore();

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        // use UTXO to create double spending, this can not be created with
        // wallet
        List<UTXO> outputs = getBalance(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, walletKeys.get(8)));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with a conflict
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
        Block b2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        Block b3 = createAndAddNextBlock(b1, b2);
        for (int i = 0; i < 15; i++) {
            createAndAddNextBlock(b3, b3);
        }
        createAndAddNextBlock(b2, b2);

        milestoneService.update();

        assertFalse(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
    }

    @Test
    public void testUpdateConflictingTokenMilestoneCandidates() throws Exception {
        store.resetStore();

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);
 
        Token tokens = Token.buildSimpleTokenInfo(false, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);

        // Make another conflicting issuance that goes through
        Block genHash = networkParameters.getGenesisBlock();
        Block block2 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null, genHash, genHash);
        Block rollingBlock = block2.createNextBlock(block1);
        blockGraph.add(rollingBlock, true);

        // Let block 1 win
        createAndAddNextBlock(block1, block1);
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(block2.getHash()).isConfirmed());

        // Reorg to block 2
        for (int i = 0; i < 25; i++) {
            createAndAddNextBlock(block2, block2);
        }
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(block2.getHash()).isConfirmed());
    }

    @Test
    public void testUpdateConflictingConsensusMilestoneCandidates() throws Exception {
        store.resetStore();

        // Generate blocks until passing second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < 2 * NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }

        // Generate mining reward blocks
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        createAndAddNextBlock(rewardBlock1, rewardBlock2);

        // One of them shall win
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed()
                && blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed()
                || blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
    }

    @Test
    public void testUpdateConflictingReclaimMilestoneCandidates() throws Exception {
        store.resetStore();

        ECKey testKey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        Block block1 = null;
        {
            // Make a buy order for "test"s
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                    testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());
            tx.setDataClassName("OrderOpen");

            // Create burning 2 BIG
            List<UTXO> outputs = getBalance(false, testKey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0));
            Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
            Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                    false);

            TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
            Script inputScript = ScriptBuilder.createInputScript(sig);
            input.setScriptSig(inputScript);

            // Create block with order
            block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
            block1.addTransaction(tx);
            block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
            block1.solve();
            this.blockGraph.add(block1, true);
        }

        // Generate blocks until passing first reward interval
        Block rollingBlock1 = networkParameters.getGenesisBlock();
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate matching block
        Block rewardBlock1 = ordermatchService.createAndAddOrderMatchingBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());
        Block fusingBlock = rewardBlock1.createNextBlock(block1);
        blockGraph.add(fusingBlock, false);

        // Try order reclaim
        Block block2 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderReclaimInfo info = new OrderReclaimInfo(0, block1.getHash(), rewardBlock1.getHash());
            tx.setData(info.toByteArray());

            // Create block with order reclaim
            block2 = fusingBlock.createNextBlock( fusingBlock);
            block2.addTransaction(tx);
            block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
            block2.solve();
        }

        // Should go through
        assertTrue(this.blockGraph.add(block2, false));

        // Try order reclaim 2
        Block block3 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderReclaimInfo info = new OrderReclaimInfo(0, block1.getHash(), rewardBlock1.getHash());
            tx.setData(info.toByteArray());

            // Create block with order reclaim
            block3 = block2.createNextBlock(block2);
            block3.addTransaction(tx);
            block3.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
            block3.solve();
        }

        // Should go through
        assertTrue(this.blockGraph.add(block3, false));

        // But only the first shall win
        milestoneService.update();
        assertTrue(store.getBlockEvaluation(block2.getHash()).isConfirmed());
        assertFalse(store.getBlockEvaluation(block3.getHash()).isConfirmed());
    }

    @Test
    public void testUpdate() throws Exception {
        store.resetStore();

        Block b1 = createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        Block b2 = createAndAddNextBlock(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock());
        Block b3 = createAndAddNextBlock(b1, b2);
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isConfirmed());

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        // use UTXO to create double spending, this can not be created with
        // wallet
        List<UTXO> outputs = getBalance(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, walletKeys.get(8)));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with a conflict
        Block b5 = createAndAddNextBlockWithTransaction(b3, b3, doublespendTX);
        Block b5link = createAndAddNextBlock(b5, b5);
        Block b6 = createAndAddNextBlock(b3, b3);
        Block b7 = createAndAddNextBlock(b3, b3);
        Block b8 = createAndAddNextBlockWithTransaction(b6, b7, doublespendTX);
        Block b8link = createAndAddNextBlock(b8, b8);
        Block b9 = createAndAddNextBlock(b5link, b6);
        Block b10 = createAndAddNextBlock(b9, b8link);
        Block b11 = createAndAddNextBlock(b9, b8link);
        Block b12 = createAndAddNextBlock(b5link, b8link);
        Block b13 = createAndAddNextBlock(b5link, b8link);
        Block b14 = createAndAddNextBlock(b5link, b8link);
        Block bOrphan1 = createAndAddNextBlock(b1, b1);
        Block bOrphan5 = createAndAddNextBlock(b5link, b5link);
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b5link.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8link.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b9.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b10.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b11.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b12.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b13.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b14.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash()).isConfirmed());

        // Now make block 8 heavier and higher rated than b5 to make it
        // disconnect block
        // 5+link and connect block 8+link instead
        Block b8weight1 = createAndAddNextBlock(b8link, b8link);
        Block b8weight2 = createAndAddNextBlock(b8link, b8link);
        Block b8weight3 = createAndAddNextBlock(b8link, b8link);
        Block b8weight4 = createAndAddNextBlock(b8link, b8link);

        // extra weights to ensure this works
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);
        createAndAddNextBlock(b8link, b8link);

        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isConfirmed());
        // sometimes this won't work since probabilistic. this is tested later
        // with additional weights
        assertFalse(blockService.getBlockEvaluation(b5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b5link.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b8.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b8link.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b9.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b10.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b11.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b12.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b13.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b14.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8weight1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8weight2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8weight3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8weight4.getHash()).isConfirmed());

        // Lastly, there will be a milestone-candidate conflict in the last
        // update that
        // should not change anything
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b5link.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b8.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b8link.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b9.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b10.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b11.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b12.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b13.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b14.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(bOrphan1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(bOrphan5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8weight1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8weight2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8weight3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b8weight4.getHash()).isConfirmed());

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

        // Make consensus block
        Block rollingBlock = b8link;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock = createAndAddNextBlock(rollingBlock, rollingBlock);
        }
        rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(b5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b5link.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b8.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b8link.getHash()).isConfirmed());

        // Check milestone depths (handmade tests)
        assertEquals(16,
                blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash()).getMilestoneDepth());
        assertEquals(15, blockService.getBlockEvaluation(b1.getHash()).getMilestoneDepth());
        assertEquals(15, blockService.getBlockEvaluation(b2.getHash()).getMilestoneDepth());
        assertEquals(1, blockService.getBlockEvaluation(rollingBlock.getHash()).getMilestoneDepth());
    }

    @Test
    public void testReorgToken() throws Exception {
        store.resetStore();

        // Generate an eligible issuance
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, pubKey);
 

        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, coinbase.getValue(),
                true, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);
        milestoneService.update();

        // Should go through
        assertTrue(blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
        Transaction tx1 = block1.getTransactions().get(0);
        assertTrue(store.getTransactionOutput(block1.getHash(), tx1.getHash(), 0).isConfirmed());
        assertTrue(store.getTokenConfirmed(block1.getHashAsString()));

        // Remove it from the milestone
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 1; i < 5; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();

        // Should be out
        assertFalse(blockService.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertFalse(store.getTransactionOutput(block1.getHash(), tx1.getHash(), 0).isConfirmed());
        assertFalse(store.getTokenConfirmed(block1.getHashAsString()));
    }

    @Test
    public void testReorgMiningReward() throws Exception {
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

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
            blockGraph.add(rollingBlock2, true);
        }

        Block fusingBlock = rollingBlock1.createNextBlock(rollingBlock2);
        blockGraph.add(fusingBlock, true);

        // Generate mining reward block
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        milestoneService.update();

        // Mining reward block should go through
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        Block rewardBlock3 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                fusingBlock.getHash(), rollingBlock1.getHash());
        milestoneService.update();

        // No change
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // Third mining reward block should now instead go through since longer
        rollingBlock = rewardBlock3;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        rewardService.createAndAddMiningRewardBlock(rewardBlock3.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // Check that not both mining blocks get approved
        for (int i = 1; i < 10; i++) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            Block b = r2.createNextBlock(r1);
            blockGraph.add(b, true);
        }
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
    }
    

    @Test
    public void blocksFromChainlenght() throws Exception {
        // create some blocks
        testReorgMiningReward();

        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("start", "0");
        request.put("end", "0");
        String response = OkHttp3Util.post(contextRoot + ReqCmd.blocksFromChainLength.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());

        GetBlockListResponse blockListResponse = Json.jsonmapper().readValue(response, GetBlockListResponse.class);

   
        // log.info("searchBlock resp : " + response);
        assertTrue(blockListResponse.getBlockbytelist().size() > 0);

        for (byte[] data : blockListResponse.getBlockbytelist()) {
            transactionService.addConnected(data, false, false);
        }
    }

    @Test
    public void testReorgMiningReward2() throws Exception {
        store.resetStore();
        
        List<Block> blocksAdded = new ArrayList<>();

        // Generate blocks until passing first reward interval parallel
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);
        blocksAdded.add(rollingBlock);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
            blocksAdded.add(rollingBlock1);
        }

        Block rollingBlock2 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock2 = rollingBlock2.createNextBlock(rollingBlock2);
            blockGraph.add(rollingBlock2, true);
            blocksAdded.add(rollingBlock2);
        }

        // Generate mining reward block on chain 1
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        blocksAdded.add(rewardBlock1);

        // Mining reward block should go through
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());

        // Generate more mining reward blocks
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock2.getHash(), rollingBlock1.getHash());
        Block rewardBlock3 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock2.getHash(), rollingBlock2.getHash());
        blocksAdded.add(rewardBlock2);
        blocksAdded.add(rewardBlock3);

        // No change
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        milestoneService.update();
        assertTrue(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());

        // Third mining reward block should now instead go through since longer
        Block rollingBlock3 = rewardBlock3;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock3 = rollingBlock3.createNextBlock(rollingBlock3);
            blockGraph.add(rollingBlock3, true);
            blocksAdded.add(rollingBlock3);
        }
        Block rewardBlock4 = rewardService.createAndAddMiningRewardBlock(rewardBlock3.getHash(),
                rollingBlock3.getHash(), rollingBlock3.getHash());
        blocksAdded.add(rewardBlock4);

        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        
        // The same state should be reached if getting the blocks in a different order
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        // Now try switching to second
        Block rollingBlock4 = rewardBlock2;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock4 = rollingBlock4.createNextBlock(rollingBlock4);
            blockGraph.add(rollingBlock4, true);
            blocksAdded.add(rollingBlock4);
        }
        Block rewardBlock5 = rewardService.createAndAddMiningRewardBlock(rewardBlock2.getHash(),
                rollingBlock4.getHash(), rollingBlock4.getHash());
        blocksAdded.add(rewardBlock5);

        Block rollingBlock5 = rewardBlock5;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock5 = rollingBlock5.createNextBlock(rollingBlock5);
            blockGraph.add(rollingBlock5, true);
            blocksAdded.add(rollingBlock5);
        }
        Block rewardBlock6 = rewardService.createAndAddMiningRewardBlock(rewardBlock5.getHash(),
                rollingBlock5.getHash(), rollingBlock5.getHash());
        blocksAdded.add(rewardBlock6);

        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        // The same state should be reached if getting the blocks in a different order
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        
        // Now try switching to third again
        Block rollingBlock6 = rewardBlock4;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock6 = rollingBlock6.createNextBlock(rollingBlock6);
            blockGraph.add(rollingBlock6, true);
            blocksAdded.add(rollingBlock6);
        }
        Block rewardBlock7 = rewardService.createAndAddMiningRewardBlock(rewardBlock4.getHash(),
                rollingBlock6.getHash(), rollingBlock6.getHash());
        blocksAdded.add(rewardBlock7);

        Block rollingBlock7 = rewardBlock7;
        for (int i = 1; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock7 = rollingBlock7.createNextBlock(rollingBlock7);
            blockGraph.add(rollingBlock7, true);
            blocksAdded.add(rollingBlock7);
        }
        Block rewardBlock8 = rewardService.createAndAddMiningRewardBlock(rewardBlock7.getHash(),
                rollingBlock7.getHash(), rollingBlock7.getHash());
        blocksAdded.add(rewardBlock8);

        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        // The same state should be reached if getting the blocks in a different order
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        Collections.reverse(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        
        Collections.shuffle(blocksAdded);
        
        store.resetStore();
        for (Block b : blocksAdded) 
            blockGraph.add(b, true);
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
        milestoneService.update();
        assertFalse(blockService.getBlockEvaluation(rewardBlock1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock3.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock4.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock5.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(rewardBlock6.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock7.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(rewardBlock8.getHash()).isConfirmed());
    }

    @Test
    public void testReorgMaintained() throws Exception {
        store.resetStore();

        // Generate two conflicting blocks where the second block approves the
        // first

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction doublespendTX = new Transaction(networkParameters);
        doublespendTX.addOutput(new TransactionOutput(networkParameters, doublespendTX, amount, walletKeys.get(8)));
        TransactionInput input = doublespendTX.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = doublespendTX.hashForSignature(0, spendableOutput.getScriptBytes(),
                Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create blocks with conflict
        Block b1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), doublespendTX);
        blockGraph.add(b1, true);
        Block b2 = createAndAddNextBlockWithTransaction(b1, b1, doublespendTX);
        blockGraph.add(b2, true);

        // Approve these blocks by adding linear tangle onto them
        Block rollingBlock = b2;
        for (int i = 0; i < 10; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();

        // Second block may not be added, only first one
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b2.getHash()).isConfirmed());

        // Add blocks via tip selection
        for (int i = 1; i < 30; i++) {
            Pair<Sha256Hash, Sha256Hash> tipsToApprove = tipsService.getValidatedBlockPair();
            Block r1 = blockService.getBlock(tipsToApprove.getLeft());
            Block r2 = blockService.getBlock(tipsToApprove.getRight());
            Block b = r2.createNextBlock(r1);
            blockGraph.add(b, true);
        }
        milestoneService.update();

        // Ensure the second block eventually loses and is not
        assertTrue(blockService.getBlockEvaluation(b1.getHash()).isConfirmed());
        assertFalse(blockService.getBlockEvaluation(b2.getHash()).isConfirmed());
        assertTrue(blockService.getBlockEvaluation(b2.getHash()).getRating() < 50);
    }
}