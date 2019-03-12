/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.OrderRecord;
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

        createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(),
                tx1);

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
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

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
            Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                    false, false);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we
            // need other blocks beforehand.
            Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
            firstIssuance = block1.getHash();

            // Should exist now
            store.getTokenConfirmed(block1.getHash().toString()); // Fine as
                                                                  // long as it
                                                                  // does not
                                                                  // throw
            assertFalse(store.getTokenSpent(block1.getHash().toString()));
        }

        // Generate a subsequent issuance
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, pubKey);
            long amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, firstIssuance.toString(), Utils.HEX.encode(pubKey), "Test",
                    "Test", 1, 1, amount, false, true);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we
            // need other blocks beforehand.
            Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);

            // Should exist now
            store.getTokenConfirmed(block1.getHash().toString()); // Fine as
                                                                  // long as it
                                                                  // does not
                                                                  // throw
            assertFalse(store.getTokenSpent(block1.getHash().toString()));
        }
    }

    @Test
    public void testConnectOrderOpenUTXOs() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Set the order
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.SELL,
                testKey.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());

        // Give it the legitimation of an order opening tx by finally signing
        // the hash
        ECKey.ECDSASignature party1Signature = outKey.sign(tx.getHash(), null);
        byte[] buf1 = party1Signature.encodeToDER();
        tx.setDataSignature(buf1);

        // Create burning 2 BIG
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with UTXOs
        Block block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block1.addTransaction(tx);
        block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block1.solve();
        Block block = block1;
        this.blockGraph.add(block, true);

        // Ensure the order is added now
        OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order);
        assertArrayEquals(order.getBeneficiaryPubKey(), testKey.getPubKey());
        assertEquals(order.getIssuingMatcherBlockHash(), Sha256Hash.ZERO_HASH);
        assertEquals(order.getOfferTokenid(), NetworkParameters.BIGTANGLE_TOKENID_STRING);
        assertEquals(order.getOfferValue(), 2);
        assertEquals(order.getOpIndex(), 0);
        assertEquals(order.getSpenderBlockHash(), null);
        assertEquals(order.getTargetTokenid(), "test");
        assertEquals(order.getTargetValue(), 2);
        // assertEquals(order.getTtl(), NetworkParameters.INITIAL_ORDER_TTL);
        assertEquals(order.getInitialBlockHash(), block1.getHash());
        assertFalse(order.isConfirmed());
        assertFalse(order.isSpent());
    }

    @Test
    public void testConfirmTransactionalUTXOs() throws Exception {
        store.resetStore();

        // Create block with UTXOs
        Transaction tx1 = createTestGenesisTransaction();
        assertNull(transactionService.getUTXO(tx1.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx1.getOutput(1).getOutPointFor()));

        Block spenderBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx1);

        // Confirm
        blockGraph.confirm(spenderBlock.getHash(), new HashSet<>());

        // Should be confirmed now
        final UTXO utxo1 = transactionService.getUTXO(tx1.getOutput(0).getOutPointFor());
        final UTXO utxo2 = transactionService.getUTXO(tx1.getOutput(1).getOutPointFor());
        assertTrue(utxo1.isConfirmed());
        assertTrue(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());

        // Extra for transactional UTXOs
        assertEquals(store.getTransactionOutputConfirmingBlock(utxo1.getHash(), utxo1.getIndex()),
                spenderBlock.getHash());
        assertEquals(store.getTransactionOutputConfirmingBlock(utxo2.getHash(), utxo2.getIndex()),
                spenderBlock.getHash());

        // Further manipulations on prev UTXOs
        final UTXO origUTXO = transactionService
                .getUTXO(networkParameters.getGenesisBlock().getTransactions().get(0).getOutput(0).getOutPointFor());
        assertTrue(origUTXO.isConfirmed());
        assertTrue(origUTXO.isSpent());
        assertEquals(store.getTransactionOutputSpender(origUTXO.getHash(), origUTXO.getIndex()).getBlockHash(),
                spenderBlock.getHash());
    }

    @Test
    public void testConfirmRewardUTXOs() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        blockGraph.add(rollingBlock, true);

        Block rollingBlock1 = rollingBlock;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>());

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
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                false, true);

        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // This (saveBlock) calls milestoneUpdate currently
        Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
        blockGraph.confirm(block1.getHash(), new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(block1.getHash().toString()));
        assertFalse(store.getTokenSpent(block1.getHash().toString()));
    }

    @Test
    public void testConfirmOrderOpenUTXOs() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Set the order
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                testKey.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());

        // Create burning 2 BIG
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with UTXOs
        Block block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block1.addTransaction(tx);
        block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block1.solve();
        this.blockGraph.add(block1, true);

        blockGraph.confirm(block1.getHash(), new HashSet<>());

        // Ensure the order is confirmed now
        OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order);
        assertTrue(order.isConfirmed());
        assertFalse(order.isSpent());
    }

    @Test
    public void testConfirmOrderReclaimUTXOs() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        Block block1 = null;
        {
            // Make a buy order for "test"s
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                    testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());

            // Create burning 2 BIG
            List<UTXO> outputs = getBalance(false, testKey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0), 0);
            Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(spendableOutput);
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
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
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
            block2 = fusingBlock.createNextBlock(fusingBlock);
            block2.addTransaction(tx);
            block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
            block2.solve();
            this.blockGraph.add(block2, false);
        }

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>());
        blockGraph.confirm(block2.getHash(), new HashSet<>());

        // Reward should be confirmed
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Order should be confirmed and spent now
        assertTrue(store.getOrderConfirmed(block1.getHash(), Sha256Hash.ZERO_HASH));
        assertTrue(store.getOrderSpent(block1.getHash(), Sha256Hash.ZERO_HASH));

        // Since the order matching did not collect the confirmed block, the
        // reclaim works
        Transaction tx = blockGraph.generateReclaimTX(block2);
        final UTXO utxo1 = transactionService.getUTXO(tx.getOutput(0).getOutPointFor());
        assertTrue(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testConfirmOrderMatchUTXOs1() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        Block block1 = null;
        {
            // Make a buy order for "test"s
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                    testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());

            // Create burning 2 BIG
            List<UTXO> outputs = getBalance(false, testKey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0), 0);
            Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(spendableOutput);
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
        Block rollingBlock1 = block1;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Ensure consumed order record is now spent
        OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order);
        assertTrue(order.isConfirmed());
        assertTrue(order.isSpent());

        // Ensure remaining orders are confirmed now
        OrderRecord order2 = store.getOrder(block1.getHash(), rewardBlock1.getHash());
        assertNotNull(order2);
        assertTrue(order2.isConfirmed());
        assertFalse(order2.isSpent());
    }

    @Test
    public void testConfirmOrderMatchUTXOs2() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Make a buy order for testKey.getPubKey()s
        Block block1 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, Utils.HEX.encode(testKey.getPubKey()), testKey.getPubKey(), null,
                    null, Side.BUY, testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());

            // Create burning 2 BIG
            List<UTXO> outputs = getBalance(false, testKey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0), 0);
            Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(spendableOutput);
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

        // Make the "test" token
        Block block2 = null;
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
            long amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
                    1, 0, amount, false, true);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently
            block2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
            blockGraph.confirm(block2.getHash(), new HashSet<>());
        }

        // Make a sell order for testKey.getPubKey()s
        Block block3 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, NetworkParameters.BIGTANGLE_TOKENID_STRING, testKey.getPubKey(),
                    null, null, Side.SELL, testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());

            // Create burning 2 "test"
            List<UTXO> outputs = getBalance(false, testKey).stream().filter(
                    out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
                    .collect(Collectors.toList());
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0), 0);
            Coin amount = Coin.valueOf(2, testKey.getPubKey());
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(spendableOutput);
            Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                    false);

            TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
            Script inputScript = ScriptBuilder.createInputScript(sig);
            input.setScriptSig(inputScript);

            // Create block with order
            block3 = block2.createNextBlock(block2);
            block3.addTransaction(tx);
            block3.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
            block3.solve();
            this.blockGraph.add(block3, true);
        }

        // Generate blocks until passing first reward interval
        Block rollingBlock1 = block3;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Ensure all consumed order records are now spent
        OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order);
        assertTrue(order.isConfirmed());
        assertTrue(order.isSpent());

        OrderRecord order2 = store.getOrder(block3.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order2);
        assertTrue(order2.isConfirmed());
        assertTrue(order2.isSpent());

        // Ensure virtual UTXOs are now confirmed
        Transaction tx = blockGraph.generateOrderMatching(rewardBlock1).getMiddle();
        final UTXO utxo1 = transactionService.getUTXO(tx.getOutput(0).getOutPointFor());
        assertTrue(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testUnconfirmTransactionalUTXOs() throws Exception {
        store.resetStore();

        // Create block with UTXOs
        Transaction tx11 = createTestGenesisTransaction();
        assertNull(transactionService.getUTXO(tx11.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx11.getOutput(1).getOutPointFor()));

        Block block = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx11);

        // Confirm
        blockGraph.confirm(block.getHash(), new HashSet<>());

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
        blockGraph.unconfirm(block.getHash(), new HashSet<>());

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
        final UTXO origUTXO = transactionService
                .getUTXO(networkParameters.getGenesisBlock().getTransactions().get(0).getOutput(0).getOutPointFor());
        assertTrue(origUTXO.isConfirmed());
        assertFalse(origUTXO.isSpent());
        assertNull(store.getTransactionOutputSpender(origUTXO.getHash(), origUTXO.getIndex()));
    }

    @Test
    public void testUnconfirmRewardUTXOs() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i1 = 0; i1 < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }

        // Generate mining reward block
        Block rewardBlock11 = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(), rollingBlock.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock11.getHash(), new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));

        // Unconfirm
        blockGraph.unconfirm(rewardBlock11.getHash(), new HashSet<>());

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
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                false, true);

        tokenInfo.setTokens(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // This (saveBlock) calls milestoneUpdate currently
        Block block11 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
        blockGraph.confirm(block11.getHash(), new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(block11.getHash().toString()));
        assertFalse(store.getTokenSpent(block11.getHash().toString()));

        // Unconfirm
        blockGraph.unconfirm(block11.getHash(), new HashSet<>());

        // Should be unconfirmed now
        assertFalse(store.getTokenConfirmed(block11.getHash().toString()));
        assertFalse(store.getTokenSpent(block11.getHash().toString()));
    }

    @Test
    public void testUnconfirmOrderOpenUTXOs() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Set the order
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                testKey.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());

        // Create burning 2 BIG
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with UTXOs
        Block block1 = networkParameters.getGenesisBlock().createNextBlock(networkParameters.getGenesisBlock());
        block1.addTransaction(tx);
        block1.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block1.solve();
        this.blockGraph.add(block1, true);

        blockGraph.confirm(block1.getHash(), new HashSet<>());
        blockGraph.unconfirm(block1.getHash(), new HashSet<>());

        // Ensure the order is confirmed now
        OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order);
        assertFalse(order.isConfirmed());
        assertFalse(order.isSpent());
    }

    @Test
    public void testUnconfirmOrderReclaimUTXOs() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        Block block1 = null;
        {
            // Make a buy order for "test"s
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                    testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());

            // Create burning 2 BIG
            List<UTXO> outputs = getBalance(false, testKey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0), 0);
            Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(spendableOutput);
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
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
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
            block2 = fusingBlock.createNextBlock(fusingBlock);
            block2.addTransaction(tx);
            block2.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
            block2.solve();
            this.blockGraph.add(block2, false);
        }

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>());
        blockGraph.confirm(block2.getHash(), new HashSet<>());

        // Unconfirm
        blockGraph.unconfirm(block2.getHash(), new HashSet<>());

        // Reward should be confirmed
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Order should be confirmed and unspent now
        assertTrue(store.getOrderConfirmed(block1.getHash(), Sha256Hash.ZERO_HASH));
        assertFalse(store.getOrderSpent(block1.getHash(), Sha256Hash.ZERO_HASH));

        // The virtual tx is in the db but unconfirmed
        Transaction tx = blockGraph.generateReclaimTX(block2);
        final UTXO utxo1 = transactionService.getUTXO(tx.getOutput(0).getOutPointFor());
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testUnconfirmOrderMatchUTXOs1() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        Block block1 = null;
        {
            // Make a buy order for "test"s
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                    testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());

            // Create burning 2 BIG
            List<UTXO> outputs = getBalance(false, testKey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0), 0);
            Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(spendableOutput);
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
        Block rollingBlock1 = block1;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>());

        // Unconfirm
        blockGraph.unconfirm(rewardBlock1.getHash(), new HashSet<>());

        // Should be unconfirmed now
        assertFalse(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Ensure consumed order record is now unspent
        OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order);
        assertTrue(order.isConfirmed());
        assertFalse(order.isSpent());

        // Ensure remaining orders are unconfirmed now
        OrderRecord order2 = store.getOrder(block1.getHash(), rewardBlock1.getHash());
        assertNotNull(order2);
        assertFalse(order2.isConfirmed());
        assertFalse(order2.isSpent());
    }

    @Test
    public void testUnconfirmOrderMatchUTXOs2() throws Exception {
        store.resetStore();
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Make a buy order for testKey.getPubKey()s
        Block block1 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, Utils.HEX.encode(testKey.getPubKey()), testKey.getPubKey(), null,
                    null, Side.BUY, testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());

            // Create burning 2 BIG
            List<UTXO> outputs = getBalance(false, testKey);
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0), 0);
            Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(spendableOutput);
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

        // Make the "test" token
        Block block2 = null;
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
            long amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
                    1, 0, amount, false, true);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently
            block2 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
            blockGraph.confirm(block2.getHash(), new HashSet<>());
        }

        // Make a sell order for testKey.getPubKey()s
        Block block3 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, NetworkParameters.BIGTANGLE_TOKENID_STRING, testKey.getPubKey(),
                    null, null, Side.SELL, testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());

            // Create burning 2 "test"
            List<UTXO> outputs = getBalance(false, testKey).stream().filter(
                    out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
                    .collect(Collectors.toList());
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0), 0);
            Coin amount = Coin.valueOf(2, testKey.getPubKey());
            // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
            // amount, testKey));
            tx.addOutput(
                    new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
            TransactionInput input = tx.addInput(spendableOutput);
            Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                    false);

            TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
            Script inputScript = ScriptBuilder.createInputScript(sig);
            input.setScriptSig(inputScript);

            // Create block with order
            block3 = block2.createNextBlock(block2);
            block3.addTransaction(tx);
            block3.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
            block3.solve();
            this.blockGraph.add(block3, true);
        }

        // Generate blocks until passing first reward interval
        Block rollingBlock1 = block3;
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>());

        // Unconfirm
        blockGraph.unconfirm(rewardBlock1.getHash(), new HashSet<>());

        // Should be unconfirmed now
        assertFalse(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Ensure all consumed order records are now unspent
        OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order);
        assertTrue(order.isConfirmed());
        assertFalse(order.isSpent());

        OrderRecord order2 = store.getOrder(block3.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order2);
        assertTrue(order2.isConfirmed());
        assertFalse(order2.isSpent());

        // Ensure virtual UTXOs are now confirmed
        Transaction tx = blockGraph.generateOrderMatching(rewardBlock1).getMiddle();
        final UTXO utxo1 = transactionService.getUTXO(tx.getOutput(0).getOutPointFor());
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testUnconfirmDependentsTransactional() throws Exception {
        store.resetStore();

        // Create blocks with UTXOs
        Transaction tx1 = createTestGenesisTransaction();
        Block block1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx1);
        blockGraph.confirm(block1.getHash(), new HashSet<>());
        Transaction tx2 = createTestGenesisTransaction();
        Block block2 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx2);
        blockGraph.confirm(block2.getHash(), new HashSet<>());

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
        assertNotNull(utxo11);
        assertTrue(utxo11.isConfirmed());
        assertFalse(utxo11.isSpent());

        // Unconfirm first block
        blockGraph.unconfirm(block1.getHash(), new HashSet<>());

        // Both should be unconfirmed now
        assertFalse(store.getBlockEvaluation(block1.getHash()).isMilestone());
        assertFalse(store.getBlockEvaluation(block2.getHash()).isMilestone());
        for (Transaction tx : new Transaction[] { tx1, tx2 }) {
            final UTXO utxo1 = transactionService.getUTXO(tx.getOutput(0).getOutPointFor());
            assertNotNull(utxo1);
            assertFalse(utxo1.isConfirmed());
            assertFalse(utxo1.isSpent());

            // Extra for transactional UTXOs
            assertNull(store.getTransactionOutputConfirmingBlock(utxo1.getHash(), utxo1.getIndex()));
        }
    }

    @Test
    public void testUnconfirmDependentsRewardOtherRewards() throws Exception {
        store.resetStore();

        // Generate blocks until passing second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i1 = 0; i1 < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        for (int i1 = 0; i1 < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }

        // Generate mining reward block
        Block rewardBlock11 = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(), rollingBlock.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock11.getHash(), new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));

        // Generate second mining reward block
        Block rewardBlock2 = transactionService.createAndAddMiningRewardBlock(rewardBlock11.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock2.getHash(), new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertTrue(store.getRewardSpent(rewardBlock11.getHash()));
        assertTrue(store.getRewardConfirmed(rewardBlock2.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock2.getHash()));

        // Unconfirm
        blockGraph.unconfirm(rewardBlock11.getHash(), new HashSet<>());

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
        for (int i = 0; i < NetworkParameters.REWARD_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE
                + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }

        // Generate mining reward block
        Block rewardBlock = transactionService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(), rollingBlock.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock.getHash(), new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock.getHash()));

        // Generate spending block
        @SuppressWarnings("deprecation")
        ECKey testKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, testKey);
        outputs.removeIf(o -> o.getValue().getValue() == NetworkParameters.testCoin);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig, testKey);
        input.setScriptSig(inputScript);
        assertNull(transactionService.getUTXO(tx.getOutput(0).getOutPointFor()));
        assertNull(transactionService.getUTXO(tx.getOutput(1).getOutPointFor()));

        Block spenderBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx);

        // Confirm
        blockGraph.confirm(spenderBlock.getHash(), new HashSet<>());

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
        blockGraph.unconfirm(rewardBlock.getHash(), new HashSet<>());

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
            Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                    false, false);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we
            // need other blocks beforehand.
            Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
            firstIssuance = block1.getHash();
        }

        // Generate a subsequent issuance
        Sha256Hash subseqIssuance;
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, pubKey);
            long amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, firstIssuance.toString(), Utils.HEX.encode(pubKey), "Test",
                    "Test", 1, 1, amount, false, true);

            tokenInfo.setTokens(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we
            // need other blocks beforehand.
            Block block1 = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, outKey, null, null, null);
            subseqIssuance = block1.getHash();
        }

        // Confirm
        blockGraph.confirm(firstIssuance, new HashSet<>());
        blockGraph.confirm(subseqIssuance, new HashSet<>());

        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(subseqIssuance.toString()));
        assertTrue(store.getTokenConfirmed(subseqIssuance.toString()));

        // Unconfirm
        blockGraph.unconfirm(firstIssuance, new HashSet<>());

        // Should be unconfirmed now
        assertFalse(store.getTokenConfirmed(subseqIssuance.toString()));
        assertFalse(store.getTokenConfirmed(subseqIssuance.toString()));
    }

    @Test
    public void testUnconfirmDependentsOrderVirtualUTXOSpenders() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        Block rewardBlock = makeAndConfirmOrderMatching(addedBlocks);

        // Generate spending block
        Block utxoSpendingBlock = makeAndConfirmTransaction(genesisKey, outKey2, testTokenId, 50, addedBlocks,
                addedBlocks.get(addedBlocks.size() - 2));

        // Unconfirm order matching
        blockGraph.unconfirm(rewardBlock.getHash(), new HashSet<>());

        // Verify the dependent spending block is unconfirmed too
        assertFalse(store.getBlockEvaluation(utxoSpendingBlock.getHash()).isMilestone());

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testUnconfirmDependentsOrderMatchingDependentReclaim() throws Exception {
        @SuppressWarnings({ "deprecation", "unused" })
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        Block token = resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        Block order = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        Block rewardBlock = makeAndConfirmOrderMatching(addedBlocks, token);

        // Generate reclaim block
        Block reclaimBlock = makeAndConfirmReclaim(order.getHash(), rewardBlock.getHash(), addedBlocks, order);

        // Unconfirm order matching
        blockGraph.unconfirm(rewardBlock.getHash(), new HashSet<>());

        // Verify the dependent reclaim block is unconfirmed too
        assertFalse(store.getBlockEvaluation(reclaimBlock.getHash()).isMilestone());

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Repeat
        blockGraph.confirm(rewardBlock.getHash(), new HashSet<>());
        blockGraph.confirm(reclaimBlock.getHash(), new HashSet<>());
        blockGraph.unconfirm(rewardBlock.getHash(), new HashSet<>());
        assertFalse(store.getBlockEvaluation(reclaimBlock.getHash()).isMilestone());

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testUnconfirmDependentsOrderReclaimDependent() throws Exception {
        @SuppressWarnings({ "deprecation", "unused" })
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = outKey;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        Block token = resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Open sell order for test tokens
        Block order = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 77777, addedBlocks);

        // Execute order matching
        Block rewardBlock = makeAndConfirmOrderMatching(addedBlocks, token);

        // Generate reclaim block
        Block reclaimBlock = makeAndConfirmReclaim(order.getHash(), rewardBlock.getHash(), addedBlocks, order);

        // Generate reclaim-dependent transaction
        Block reclaimSpender = makeAndConfirmTransaction(testKey, testKey, testTokenId, 77776, addedBlocks,
                rewardBlock);

        // Unconfirm reclaim
        blockGraph.unconfirm(reclaimBlock.getHash(), new HashSet<>());

        // Verify the dependent spender block is unconfirmed too
        assertFalse(store.getBlockEvaluation(reclaimSpender.getHash()).isMilestone());

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

}