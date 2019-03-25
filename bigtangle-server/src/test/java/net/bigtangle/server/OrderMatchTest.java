package net.bigtangle.server;

import static org.junit.Assert.assertFalse;
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
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
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
public class OrderMatchTest extends AbstractIntegrationTest {

    @Test
    public void testOrderReclaim() throws Exception {
        @SuppressWarnings({ "deprecation", "unused" })
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open lost sell order for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        
        // Execute order matching
        Block match = makeAndConfirmOrderMatching(addedBlocks, networkParameters.getGenesisBlock());
        
        // Fuse and let all be confirmed
        Block fuse = createAndAddNextBlock(sell, match);
        addedBlocks.add(fuse);
        
        // Pass far enough to lose order
        addedBlocks.add(makeAndConfirmOrderMatching(addedBlocks, fuse));
        milestoneService.update();
        
        // Try reclaiming
        addedBlocks.addAll(transactionService.performOrderReclaimMaintenance());
        milestoneService.update();
        
        // Verify the tokens returned possession
        assertHasAvailableToken(testKey, testKey.getPublicKeyAsHex(), 77777l);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void buy() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        showOrders();
        
        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        showOrders();
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        showOrders();
        
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void sell() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Open sell order for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void multiLevelBuy() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1001, 100, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 999, 50, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 99950l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void multiLevelSell() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 999, 100, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1001, 50, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100050l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialBuy() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 50000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 50l);

        // Verify token amount invariance
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialSell() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 50000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 50l);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialBidFill() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void partialAskFill() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey = walletKeys.get(8);
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 100000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 100l);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void cancel() throws Exception {
        @SuppressWarnings({ "deprecation", "unused" })
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Cancel
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void cancelTwoStep() throws Exception {
        @SuppressWarnings({ "deprecation", "unused" })
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Cancel
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        
        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void effectiveCancel() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell orders for test tokens
        Block sell = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);

        // Open buy order for test tokens
        Block buy = makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Cancel all
        makeAndConfirmCancelOp(sell, testKey, addedBlocks);
        makeAndConfirmCancelOp(buy, genesisKey, addedBlocks);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify all tokens did not change possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, null);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), null);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testValidFromTime() throws Exception {
        final int waitTime = 5000;
        
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens with timeout
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        long sellAmount = (long) 100;
        Block block = null;
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo((long) 1000 * sellAmount, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                testKey.getPubKey(), null, System.currentTimeMillis() + waitTime, Side.SELL, testKey.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());
        
        // Burn tokens to sell
        Coin amount = Coin.valueOf(sellAmount, testTokenId);
        List<UTXO> outputs = getBalance(false, testKey).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(testTokenId))
                .filter(out -> out.getValue().getValue() >= amount.getValue()).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
        
        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);
        
        // Create block with order
        block = predecessor.createNextBlock();
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block.solve();
        this.blockGraph.add(block, true);
        addedBlocks.add(block);
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());

        // Open buy order for test tokens
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);

        // Execute order matching
        Block matcherBlock1 = makeAndConfirmOrderMatching(addedBlocks);
        
        // Verify the order is still open
        // NOTE: Can fail if the test takes longer than 5 seconds. In that case, increase the wait time variable
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));
        assertFalse(store.getOrderSpent(block.getHash(), matcherBlock1.getHash()));
        
        // Wait until valid
        Thread.sleep(waitTime);

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        
        // Verify the order is now closed
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));
        assertTrue(store.getOrderSpent(block.getHash(), matcherBlock1.getHash()));
        
        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testValidToTime() throws Exception {
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open sell order for test tokens with timeout
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        long sellAmount = (long) 100;
        Block block = null;
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo((long) 1000 * sellAmount, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                testKey.getPubKey(), System.currentTimeMillis() - 10000, null, Side.SELL, testKey.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());
        
        // Burn tokens to sell
        Coin amount = Coin.valueOf(sellAmount, testTokenId);
        List<UTXO> outputs = getBalance(false, testKey).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(testTokenId))
                .filter(out -> out.getValue().getValue() >= amount.getValue()).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), testKey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
        
        TransactionSignature sig = new TransactionSignature(testKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);
        
        // Create block with order
        block = predecessor.createNextBlock();
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block.solve();
        this.blockGraph.add(block, true);
        addedBlocks.add(block);
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());

        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        
        // Verify there is no open order left
        assertTrue(store.getOrderSpent(block.getHash(), Sha256Hash.ZERO_HASH));
        
        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testAllOrdersSpent() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        Block b1 = makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        Block b2 = makeAndConfirmBuyOrder(genesisKey, testTokenId, 999, 50, addedBlocks);
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        makeAndConfirmOrderMatching(addedBlocks);
        makeAndConfirmOrderMatching(addedBlocks);

        // Cancel orders
        makeAndConfirmCancelOp(b1, testKey, addedBlocks);
        makeAndConfirmCancelOp(b2, genesisKey, addedBlocks);
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        makeAndConfirmOrderMatching(addedBlocks);
        
        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testMultiMatching1() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 225, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 75, addedBlocks);
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 450000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 450l);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testMultiMatching2() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 100, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 100, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 1000, 50, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 1000, 150, addedBlocks);
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);
        
        // Verify the tokens changed possession
        assertHasAvailableToken(testKey, NetworkParameters.BIGTANGLE_TOKENID_STRING, 400000l);
        assertHasAvailableToken(genesisKey, testKey.getPublicKeyAsHex(), 400l);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);
        
        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testMultiMatching3() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 456, 20, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 789, 3, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 987, 10, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 654, 8, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 321, 5, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 159, 2, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 951, 25, addedBlocks);
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 753, 12, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 357, 23, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 456, 45, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }

    @Test
    public void testMultiMatching4() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesisKey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        ECKey testKey =  walletKeys.get(8);;
        List<Block> addedBlocks = new ArrayList<>();

        // Make test token
        resetAndMakeTestToken(testKey, addedBlocks);
        String testTokenId = testKey.getPublicKeyAsHex();

        // Get current existing token amount
        HashMap<String, Long> origTokenAmounts = getCurrentTokenAmounts();

        // Open orders
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 456, 20, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 789, 3, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 987, 10, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 654, 8, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);        
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);        
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 150, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 456, 20, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 789, 3, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 987, 10, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 654, 8, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);        
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);        
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 654, 78, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 258, 58, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 852, 69, addedBlocks);
        makeAndConfirmSellOrder(testKey, testTokenId, 123, 23, addedBlocks);
        makeAndConfirmBuyOrder(genesisKey, testTokenId, 789, 15, addedBlocks);
        
        // Execute order matching
        makeAndConfirmOrderMatching(addedBlocks);

        // Verify token amount invariance 
        assertCurrentTokenAmountEquals(origTokenAmounts);

        // Verify deterministic overall execution
        readdConfirmedBlocksAndAssertDeterministicExecution(addedBlocks);
    }
}
