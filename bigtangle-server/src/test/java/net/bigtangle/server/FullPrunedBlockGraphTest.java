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

import java.math.BigInteger;
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
        Transaction tx1 = createTestTransaction();
        Block block1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(), networkParameters.getGenesisBlock(),
                tx1);

        // Should exist now
        final UTXO utxo1 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(block1.getHash()));
        final UTXO utxo2 = blockService.getUTXO(tx1.getOutput(1).getOutPointFor(block1.getHash()));
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
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate mining reward block
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());
        milestoneService.update();

        // Should exist now
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));
    }

    @Test
    public void testConnectTokenUTXOs() throws Exception {
        store.resetStore();
        ;
        byte[] pubKey = walletKeys.get(1).getPubKey();
        System.out.println(Utils.HEX.encode(pubKey));

        // Generate an eligible issuance
        Sha256Hash firstIssuance;
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, pubKey);
            BigInteger amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                    false, 0, networkParameters.getGenesisBlock().getHashAsString());

            tokenInfo.setToken(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", walletKeys.get(1).getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we
            // need other blocks beforehand.
            Block block1 =  saveTokenUnitTest(tokenInfo, coinbase, walletKeys.get(1),null);
            firstIssuance = block1.getHash();

            // Should exist now
            store.getTokenConfirmed(block1.getHash() ); // Fine as
                                                                  // long as it
                                                                  // does not
                                                                  // throw
            assertFalse(store.getTokenSpent(block1.getHash() ));
        }

        // Generate a subsequent issuance
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, pubKey);
            BigInteger amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, firstIssuance, Utils.HEX.encode(pubKey), "Test",
                    "Test", 1, 1, amount, true, 0, networkParameters.getGenesisBlock().getHashAsString());

            tokenInfo.setToken(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", walletKeys.get(8).getPublicKeyAsHex()));
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", walletKeys.get(9).getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we
            // need other blocks beforehand.
            Block block1 = saveTokenUnitTest(tokenInfo, coinbase, walletKeys.get(8), null);

            block1 = pullBlockDoMultiSign(tokens.getTokenid(), walletKeys.get(1), null);
            // Should exist now
            store.getTokenConfirmed(block1.getHash()); // Fine as
                                                                  // long as it
                                                                  // does not
                                                                  // throw
            assertFalse(store.getTokenSpent(block1.getHash()));
        }
    }

    @Test
    public void testConnectOrderOpenUTXOs() throws Exception {
        store.resetStore();
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Set the order
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.SELL,
                testKey.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());
        tx.setDataClassName("OrderOpen");

        // Give it the legitimation of an order opening tx by finally signing
        // the hash
        ECKey.ECDSASignature party1Signature = walletKeys.get(8).sign(tx.getHash(), null);
        byte[] buf1 = party1Signature.encodeToDER();
        tx.setDataSignature(buf1);

        // Create burning 2 BIG
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
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
        assertEquals(order.getSpenderBlockHash(), null);
        assertEquals(order.getTargetTokenid(), "test");
        assertEquals(order.getTargetValue(), 2);
        // assertEquals(order.getTtl(), NetworkParameters.INITIAL_ORDER_TTL);
        assertEquals(order.getBlockHash(), block1.getHash());
        assertFalse(order.isConfirmed());
        assertFalse(order.isSpent());
    }

    @Test
    public void testConfirmTransactionalUTXOs() throws Exception {
        store.resetStore();

        // Create block with UTXOs
        Transaction tx1 = createTestTransaction();
        Block spenderBlock = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx1);

        // Confirm
        blockGraph.confirm(spenderBlock.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Should be confirmed now
        final UTXO utxo1 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(spenderBlock.getHash()));
        final UTXO utxo2 = blockService.getUTXO(tx1.getOutput(1).getOutPointFor(spenderBlock.getHash()));
        assertTrue(utxo1.isConfirmed());
        assertTrue(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());

        // Further manipulations on prev UTXOs
        final UTXO origUTXO = blockService
                .getUTXO(networkParameters.getGenesisBlock().getTransactions().get(0).getOutput(0).getOutPointFor(networkParameters.getGenesisBlock().getHash()));
        assertTrue(origUTXO.isConfirmed());
        assertTrue(origUTXO.isSpent());
        assertEquals(store.getTransactionOutputSpender(origUTXO.getBlockHash(), origUTXO.getTxHash(), origUTXO.getIndex()).getBlockHash(),
                spenderBlock.getHash());
    }

    @Test
    public void testConfirmRewardUTXOs() throws Exception {
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

        // Generate mining reward block
        Block rewardBlock1 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        milestoneService.update();

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Further manipulations on prev UTXOs
        assertTrue(store.getRewardConfirmed(networkParameters.getGenesisBlock().getHash()));
        assertTrue(store.getRewardSpent(networkParameters.getGenesisBlock().getHash()));
        assertEquals(store.getRewardSpender(networkParameters.getGenesisBlock().getHash()), rewardBlock1.getHash());

        // Check the virtual txs too
        Transaction virtualTX = blockGraph.generateVirtualMiningRewardTX(rewardBlock1);
        final UTXO utxo1 = blockService.getUTXO(virtualTX.getOutput(0).getOutPointFor(rewardBlock1.getHash()));
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
        BigInteger amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", walletKeys.get(0).getPublicKeyAsHex()));

        // This (saveBlock) calls milestoneUpdate currently
        Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);
        blockGraph.confirm(block1.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(block1.getHash()));
        assertFalse(store.getTokenSpent(block1.getHash()));
    }

    @Test
    public void testConfirmOrderOpenUTXOs() throws Exception {
        store.resetStore();
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Set the order
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                testKey.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());
        tx.setDataClassName("OrderOpen");

        // Create burning 2 BIG
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
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

        blockGraph.confirm(block1.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Ensure the order is confirmed now
        OrderRecord order = store.getOrder(block1.getHash(), Sha256Hash.ZERO_HASH);
        assertNotNull(order);
        assertTrue(order.isConfirmed());
        assertFalse(order.isSpent());
    }

    @Test
    public void testConfirmOrderReclaimUTXOs() throws Exception {
        store.resetStore();
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

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
        Block rewardBlock1 =createAndAddOrderMatchingBlock(
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
        milestoneService.update();
        blockGraph.confirm(block2.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Should be confirmed
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Order should be confirmed and spent now
        assertTrue(store.getOrderConfirmed(block1.getHash(), Sha256Hash.ZERO_HASH));
        assertTrue(store.getOrderSpent(block1.getHash(), Sha256Hash.ZERO_HASH));

        // Since the order matching did not collect the confirmed block, the
        // reclaim works
        Transaction tx = blockGraph.generateReclaimTX(block2);
        final UTXO utxo1 = blockService.getUTXO(tx.getOutput(0).getOutPointFor(block2.getHash()));
        assertTrue(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testConfirmOrderMatchUTXOs1() throws Exception {
        store.resetStore();
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

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
        Block rollingBlock1 = block1;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate matching block
        Block rewardBlock1 =createAndAddOrderMatchingBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        long cutoffHeight = blockService.getCutoffHeight();
        
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>(), cutoffHeight);

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
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Make a buy order for testKey.getPubKey()s
        Block block1 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, Utils.HEX.encode(testKey.getPubKey()), testKey.getPubKey(), null,
                    null, Side.BUY, testKey.toAddress(networkParameters).toBase58());
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

        // Make the "test" token
        Block block2 = null;
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
            BigInteger amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
                    1, 0, amount, true, 0, networkParameters.getGenesisBlock().getHashAsString());

            tokenInfo.setToken(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently
            block2 = saveTokenUnitTest(tokenInfo, coinbase, testKey, null);
            blockGraph.confirm(block2.getHash(), new HashSet<>(),    blockService.getCutoffHeight());
        }

        // Make a sell order for testKey.getPubKey()s
        Block block3 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, NetworkParameters.BIGTANGLE_TOKENID_STRING, testKey.getPubKey(),
                    null, null, Side.SELL, testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());
            tx.setDataClassName("OrderOpen");

            // Create burning 2 "test"
            List<UTXO> outputs = getBalance(false, testKey).stream().filter(
                    out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
                    .collect(Collectors.toList());
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0));
            Coin amount = Coin.valueOf(2, testKey.getPubKey());
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
            block3 = block2.createNextBlock(block2);
            block3.addTransaction(tx);
            block3.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
            block3.solve();
            this.blockGraph.add(block3, true);
        }

        // Generate blocks until passing first reward interval
        Block rollingBlock1 = block3;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate matching block
        Block rewardBlock1 =createAndAddOrderMatchingBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

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
        Transaction tx = blockGraph.generateOrderMatching(rewardBlock1).getOutputTx();
        final UTXO utxo1 = blockService.getUTXO(tx.getOutput(0).getOutPointFor(rewardBlock1.getHash()));
        assertTrue(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testUnconfirmTransactionalUTXOs() throws Exception {
        store.resetStore();

        // Create block with UTXOs
        Transaction tx11 = createTestTransaction();
        Block block = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx11);

        // Confirm
        blockGraph.confirm(block.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Should be confirmed now
        final UTXO utxo11 = blockService.getUTXO(tx11.getOutput(0).getOutPointFor(block.getHash()));
        final UTXO utxo21 = blockService.getUTXO(tx11.getOutput(1).getOutPointFor(block.getHash()));
        assertNotNull(utxo11);
        assertNotNull(utxo21);
        assertTrue(utxo11.isConfirmed());
        assertTrue(utxo21.isConfirmed());
        assertFalse(utxo11.isSpent());
        assertFalse(utxo21.isSpent());

        // Unconfirm
        blockGraph.unconfirm(block.getHash(), new HashSet<>());

        // Should be unconfirmed now
        final UTXO utxo1 = blockService.getUTXO(tx11.getOutput(0).getOutPointFor(block.getHash()));
        final UTXO utxo2 = blockService.getUTXO(tx11.getOutput(1).getOutPointFor(block.getHash()));
        assertNotNull(utxo1);
        assertNotNull(utxo2);
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());

        // Further manipulations on prev UTXOs
        final UTXO origUTXO = blockService
                .getUTXO(networkParameters.getGenesisBlock().getTransactions().get(0).getOutput(0).getOutPointFor(networkParameters.getGenesisBlock().getHash()));
        assertTrue(origUTXO.isConfirmed());
        assertFalse(origUTXO.isSpent());
        assertNull(store.getTransactionOutputSpender(origUTXO.getBlockHash(), origUTXO.getTxHash(), origUTXO.getIndex()));
    }

    @Test
    public void testUnconfirmRewardUTXOs() throws Exception {
        store.resetStore();

        // Generate blocks until passing first reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i1 = 0; i1 < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }

        // Generate mining reward block
        Block rewardBlock11 = rewardService.createAndAddMiningRewardBlock(networkParameters.getGenesisBlock().getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());

        // Confirm
        milestoneService.update();

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
        final UTXO utxo1 = blockService.getUTXO(virtualTX.getOutput(0).getOutPointFor(rewardBlock11.getHash()));
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
        BigInteger amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                true, 0, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        // This (saveBlock) calls milestoneUpdate currently
        Block block11 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);
        blockGraph.confirm(block11.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(block11.getHash()));
        assertFalse(store.getTokenSpent(block11.getHash()));

        // Unconfirm
        blockGraph.unconfirm(block11.getHash(), new HashSet<>());

        // Should be unconfirmed now
        assertFalse(store.getTokenConfirmed(block11.getHash()));
        assertFalse(store.getTokenSpent(block11.getHash()));
    }

    @Test
    public void testUnconfirmOrderOpenUTXOs() throws Exception {
        store.resetStore();
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Set the order
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo(2, "test", testKey.getPubKey(), null, null, Side.BUY,
                testKey.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());
        tx.setDataClassName("OrderOpen");

        // Create burning 2 BIG
        List<UTXO> outputs = getBalance(false, testKey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
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

        blockGraph.confirm(block1.getHash(), new HashSet<>(),  blockService.getCutoffHeight());
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
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

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
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate matching block
        Block rewardBlock1 =createAndAddOrderMatchingBlock(
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
        milestoneService.update();
        blockGraph.confirm(block2.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Unconfirm
        blockGraph.unconfirm(block2.getHash(), new HashSet<>());

        // Should be confirmed
        assertTrue(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));

        // Order should be confirmed and unspent now
        assertTrue(store.getOrderConfirmed(block1.getHash(), Sha256Hash.ZERO_HASH));
        assertFalse(store.getOrderSpent(block1.getHash(), Sha256Hash.ZERO_HASH));

        // The virtual tx is in the db but unconfirmed
        Transaction tx = blockGraph.generateReclaimTX(block2);
        final UTXO utxo1 = blockService.getUTXO(tx.getOutput(0).getOutPointFor(block2.getHash()));
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testUnconfirmOrderMatchUTXOs1() throws Exception {
        store.resetStore();
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

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
        Block rollingBlock1 = block1;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }
       
        // Generate matching block
        Block rewardBlock1 =createAndAddOrderMatchingBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Unconfirm
        blockGraph.unconfirm(rewardBlock1.getHash(), new HashSet<>());

        // Should be unconfirmed now
        assertFalse(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));
        assertNull(store.getRewardSpender(rewardBlock1.getHash()));

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
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));

        // Make a buy order for testKey.getPubKey()s
        Block block1 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, Utils.HEX.encode(testKey.getPubKey()), testKey.getPubKey(), null,
                    null, Side.BUY, testKey.toAddress(networkParameters).toBase58());
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

        // Make the "test" token
        Block block2 = null;
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
            BigInteger amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(testKey.getPubKey()), "Test", "Test",
                    1, 0, amount, true, 0, networkParameters.getGenesisBlock().getHashAsString());

            tokenInfo.setToken(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently
            block2 = saveTokenUnitTest(tokenInfo, coinbase, testKey, null);
            blockGraph.confirm(block2.getHash(), new HashSet<>(),  blockService.getCutoffHeight());
        }

        // Make a sell order for testKey.getPubKey()s
        Block block3 = null;
        {
            Transaction tx = new Transaction(networkParameters);
            OrderOpenInfo info = new OrderOpenInfo(2, NetworkParameters.BIGTANGLE_TOKENID_STRING, testKey.getPubKey(),
                    null, null, Side.SELL, testKey.toAddress(networkParameters).toBase58());
            tx.setData(info.toByteArray());
            tx.setDataClassName("OrderOpen");

            // Create burning 2 "test"
            List<UTXO> outputs = getBalance(false, testKey).stream().filter(
                    out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(Utils.HEX.encode(testKey.getPubKey())))
                    .collect(Collectors.toList());
            TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters,
                    outputs.get(0));
            Coin amount = Coin.valueOf(2, testKey.getPubKey());
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
            block3 = block2.createNextBlock(block2);
            block3.addTransaction(tx);
            block3.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
            block3.solve();
            this.blockGraph.add(block3, true);
        }

        // Generate blocks until passing first reward interval
        Block rollingBlock1 = block3;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true);
        }

        // Generate matching block
        Block rewardBlock1 =createAndAddOrderMatchingBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock1.getHash(), rollingBlock1.getHash());

        // Confirm
        blockGraph.confirm(rewardBlock1.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Unconfirm
        blockGraph.unconfirm(rewardBlock1.getHash(), new HashSet<>());

        // Should be unconfirmed now
        assertFalse(store.getRewardConfirmed(rewardBlock1.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock1.getHash()));
        assertNull(store.getRewardSpender(rewardBlock1.getHash()));

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
        Transaction tx = blockGraph.generateOrderMatching(rewardBlock1).getOutputTx();
        final UTXO utxo1 = blockService.getUTXO(tx.getOutput(0).getOutPointFor(rewardBlock1.getHash()));
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testUnconfirmDependentsTransactional() throws Exception {
        store.resetStore();

        // Create blocks with UTXOs
        Transaction tx1 = createTestTransaction();
        Block block1 = createAndAddNextBlockWithTransaction(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock(), tx1);
        blockGraph.confirm(block1.getHash(), new HashSet<>(),  blockService.getCutoffHeight());
        Block betweenBlock = createAndAddNextBlock(networkParameters.getGenesisBlock(),
                networkParameters.getGenesisBlock());
        Transaction tx2 = createTestTransaction();
        Block block2 = createAndAddNextBlockWithTransaction(betweenBlock, betweenBlock, tx2);
        blockGraph.confirm(block2.getHash(), new HashSet<>(),  blockService.getCutoffHeight());

        // Should be confirmed now
        assertTrue(store.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertTrue(store.getBlockEvaluation(block2.getHash()).isConfirmed());
        UTXO utxo11 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(block1.getHash()));
        UTXO utxo21 = blockService.getUTXO(tx1.getOutput(1).getOutPointFor(block1.getHash()));
        assertNotNull(utxo11);
        assertNotNull(utxo21);
        assertTrue(utxo11.isConfirmed());
        assertTrue(utxo21.isConfirmed());
        assertTrue(utxo11.isSpent());
        assertFalse(utxo21.isSpent());
        utxo11 = blockService.getUTXO(tx2.getOutput(0).getOutPointFor(block2.getHash()));
        assertNotNull(utxo11);
        assertTrue(utxo11.isConfirmed());
        assertFalse(utxo11.isSpent());

        // Unconfirm first block
        blockGraph.unconfirm(block1.getHash(), new HashSet<>());

        // Both should be unconfirmed now
        assertFalse(store.getBlockEvaluation(block1.getHash()).isConfirmed());
        assertFalse(store.getBlockEvaluation(block2.getHash()).isConfirmed());

        final UTXO utxo1 = blockService.getUTXO(tx1.getOutput(0).getOutPointFor(block1.getHash()));
        assertNotNull(utxo1);
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());

        final UTXO utxo2 = blockService.getUTXO(tx2.getOutput(0).getOutPointFor(block2.getHash()));
        assertNotNull(utxo2);
        assertFalse(utxo2.isConfirmed());
        assertFalse(utxo2.isSpent());
    }

    @Test
    public void testUnconfirmDependentsRewardOtherRewards() throws Exception {
        store.resetStore();

        // Generate blocks until passing second reward interval
        // Generate mining reward block
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i1 = 0; i1 < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        Block rewardBlock11 = rewardService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(), rollingBlock.getHash());

        // Confirm
        milestoneService.update();

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock11.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock11.getHash()));

        // Generate second mining reward block
        rollingBlock = rewardBlock11;
        for (int i1 = 0; i1 < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i1++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        Block rewardBlock2 = rewardService.createAndAddMiningRewardBlock(rewardBlock11.getHash(),
                rollingBlock.getHash(), rollingBlock.getHash());

        // Confirm
        milestoneService.update();

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
        UTXO utxo1 = blockService.getUTXO(virtualTX.getOutput(0).getOutPointFor(rewardBlock11.getHash()));
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
        virtualTX = blockGraph.generateVirtualMiningRewardTX(rewardBlock2);
        utxo1 = blockService.getUTXO(virtualTX.getOutput(0).getOutPointFor(rewardBlock2.getHash()));
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo1.isSpent());
    }

    @Test
    public void testUnconfirmDependentsRewardVirtualSpenders() throws Exception {
        store.resetStore();
        
        ECKey testKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        // Generate blocks until passing second reward interval
        Block rollingBlock = networkParameters.getGenesisBlock();
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock,NetworkParameters.BLOCK_VERSION_GENESIS, testKey.getPubKeyHash());
            rollingBlock=adjustSolve(rollingBlock);
          
            blockGraph.add(rollingBlock, true);
        }

        // Generate mining reward block
        Block rewardBlock = rewardService.createAndAddMiningRewardBlock(
                networkParameters.getGenesisBlock().getHash(), rollingBlock.getHash(), rollingBlock.getHash());

        // Confirm
        milestoneService.update();

        // Should be confirmed now
        assertTrue(store.getRewardConfirmed(rewardBlock.getHash()));
        assertFalse(store.getRewardSpent(rewardBlock.getHash()));

        // Generate spending block
        Block betweenBlock = createAndAddNextBlock(rollingBlock, rollingBlock);
    
        List<UTXO> outputs = getBalance(false, testKey);
        outputs.removeIf(o -> o.getValue().getValue() == NetworkParameters.BigtangleCoinTotal);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig, testKey);
        input.setScriptSig(inputScript);
        Block spenderBlock = createAndAddNextBlockWithTransaction(betweenBlock, betweenBlock, tx);

        // Confirm
        milestoneService.update();
        blockGraph.confirm(spenderBlock.getHash(), new HashSet<Sha256Hash>(),  blockService.getCutoffHeight());

        // Should be confirmed now
        final UTXO utxo11 = blockService.getUTXO(tx.getOutput(0).getOutPointFor(spenderBlock.getHash()));
        final UTXO utxo21 = blockService.getUTXO(tx.getOutput(1).getOutPointFor(spenderBlock.getHash()));
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
        final UTXO utxo1 = blockService.getUTXO(tx.getOutput(0).getOutPointFor(spenderBlock.getHash()));
        final UTXO utxo2 = blockService.getUTXO(tx.getOutput(1).getOutPointFor(spenderBlock.getHash()));
        assertNotNull(utxo1);
        assertNotNull(utxo2);
        assertFalse(utxo1.isConfirmed());
        assertFalse(utxo2.isConfirmed());
        assertFalse(utxo1.isSpent());
        assertFalse(utxo2.isSpent());

        // Check the virtual txs too
        Transaction virtualTX = blockGraph.generateVirtualMiningRewardTX(rewardBlock);
        UTXO utxo3 = blockService.getUTXO(virtualTX.getOutput(0).getOutPointFor(rewardBlock.getHash()));
        assertFalse(utxo3.isConfirmed());
        assertFalse(utxo3.isSpent());
        assertNull(store.getTransactionOutputSpender(utxo3.getBlockHash(), utxo3.getTxHash(), utxo3.getIndex()));
    }

    @Test
    public void testUnconfirmDependentsToken() throws Exception {
        store.resetStore();
        ECKey outKey = walletKeys.get(8);
        byte[] pubKey = outKey.getPubKey();

        // Generate an eligible issuance
        Sha256Hash firstIssuance;
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, pubKey);
            BigInteger amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, null, Utils.HEX.encode(pubKey), "Test", "Test", 1, 0, amount,
                    false, 0, networkParameters.getGenesisBlock().getHashAsString());

            tokenInfo.setToken(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we
            // need other blocks beforehand.
            Block block1 =saveTokenUnitTest(tokenInfo, coinbase, outKey, null);
            firstIssuance = block1.getHash();
        }

        // Generate a subsequent issuance
        Sha256Hash subseqIssuance;
        {
            TokenInfo tokenInfo = new TokenInfo();

            Coin coinbase = Coin.valueOf(77777L, pubKey);
            BigInteger amount = coinbase.getValue();
            Token tokens = Token.buildSimpleTokenInfo(true, firstIssuance, Utils.HEX.encode(pubKey), "Test",
                    "Test", 1, 1, amount, true, 0, networkParameters.getGenesisBlock().getHashAsString());

            tokenInfo.setToken(tokens);
            tokenInfo.getMultiSignAddresses()
                    .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

            // This (saveBlock) calls milestoneUpdate currently, that's why we
            // need other blocks beforehand.
            Block block1 = saveTokenUnitTest(tokenInfo, coinbase, outKey, null);
            subseqIssuance = block1.getHash();
        }

        // Confirm
        blockGraph.confirm(firstIssuance, new HashSet<>(),  blockService.getCutoffHeight());
        blockGraph.confirm(subseqIssuance, new HashSet<>(),  blockService.getCutoffHeight());

        // Should be confirmed now
        assertTrue(store.getTokenConfirmed(subseqIssuance));
        assertTrue(store.getTokenConfirmed(subseqIssuance));

        // Unconfirm
        blockGraph.unconfirm(firstIssuance, new HashSet<>());

        // Should be unconfirmed now
        assertFalse(store.getTokenConfirmed(subseqIssuance));
        assertFalse(store.getTokenConfirmed(subseqIssuance));
    }

    @Test
    public void testUnconfirmDependentsOrderVirtualUTXOSpenders() throws Exception {
        
        ECKey genesisKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(8);
 
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
        Block betweenBlock = makeAndConfirmBlock(addedBlocks, addedBlocks.get(addedBlocks.size() - 2));
        Block utxoSpendingBlock = makeAndConfirmTransaction(genesisKey, walletKeys.get(8), testTokenId, 50, addedBlocks,
                betweenBlock);

        // Unconfirm order matching
        blockGraph.unconfirm(rewardBlock.getHash(), new HashSet<>());

        // Verify the dependent spending block is unconfirmed too
        assertFalse(store.getBlockEvaluation(utxoSpendingBlock.getHash()).isConfirmed());

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);
    }

    @Test
    public void testUnconfirmDependentsOrderMatchingDependentReclaim() throws Exception {
        @SuppressWarnings({ "unused" })
        ECKey genesisKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
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
        Block betweenBlock = makeAndConfirmBlock(addedBlocks, addedBlocks.get(addedBlocks.size() - 2));
        Block reclaimBlock = makeAndConfirmReclaim(order.getHash(), rewardBlock.getHash(), addedBlocks, betweenBlock);

        // Unconfirm order matching
        blockGraph.unconfirm(rewardBlock.getHash(), new HashSet<>());

        // Verify the dependent reclaim block is unconfirmed too
        assertFalse(store.getBlockEvaluation(reclaimBlock.getHash()).isConfirmed());

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Repeat
        blockGraph.confirm(rewardBlock.getHash(), new HashSet<>(),  blockService.getCutoffHeight());
        blockGraph.confirm(reclaimBlock.getHash(), new HashSet<>(),  blockService.getCutoffHeight());
        blockGraph.unconfirm(rewardBlock.getHash(), new HashSet<>());
        assertFalse(store.getBlockEvaluation(reclaimBlock.getHash()).isConfirmed());
    }

    @Test
    public void testUnconfirmDependentsOrderReclaimDependent() throws Exception {
        @SuppressWarnings({ "unused" })
        ECKey genesisKey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(0);
        ;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        Block token = resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Open sell order for test tokens
        Block order = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 7, addedBlocks);

        // Execute order matching
        Block rewardBlock = makeAndConfirmOrderMatching(addedBlocks, token);

        // Generate reclaim block
        Block betweenBlock = makeAndConfirmBlock(addedBlocks, addedBlocks.get(addedBlocks.size() - 2));
        Block reclaimBlock = makeAndConfirmReclaim(order.getHash(), rewardBlock.getHash(), addedBlocks, betweenBlock);

        // Generate reclaim-dependent transaction
        Block reclaimSpender = makeAndConfirmTransaction(testKey, testKey, testTokenId, 6, addedBlocks,
                reclaimBlock);

        // Unconfirm reclaim
        blockGraph.unconfirm(reclaimBlock.getHash(), new HashSet<>());

        // Verify the dependent spender block is unconfirmed too
        assertFalse(store.getBlockEvaluation(reclaimSpender.getHash()).isConfirmed());

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

}