/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.bigtangle.core.Block;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderOpInfo;
import net.bigtangle.core.OrderOpInfo.OrderOp;
import net.bigtangle.core.OrderOpenInfo;
import net.bigtangle.core.OrderReclaimInfo;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.PrunedException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Side;
import net.bigtangle.core.Token;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.core.http.server.resp.MultiSignResponse;
import net.bigtangle.core.http.server.resp.TokenIndexResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.server.service.OrderReclaimService;
import net.bigtangle.server.service.OrdermatchService;
import net.bigtangle.server.service.RewardService;
import net.bigtangle.server.service.TipsService;
import net.bigtangle.server.service.TransactionService;
import net.bigtangle.store.FullPrunedBlockGraph;
import net.bigtangle.store.FullPrunedBlockStore;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {})

@TestExecutionListeners(value = { DependencyInjectionTestExecutionListener.class, MockitoTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class

})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    private static final String CONTEXT_ROOT_TEMPLATE = "http://localhost:%s/";
    protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);
    public String contextRoot;
    public List<ECKey> walletKeys;
    public List<ECKey> wallet1Keys;
    public List<ECKey> wallet2Keys;

    WalletAppKit walletAppKit;
    WalletAppKit walletAppKit1;
    WalletAppKit walletAppKit2;

    @Autowired
    protected FullPrunedBlockGraph blockGraph;
    @Autowired
    protected BlockService blockService;
    @Autowired
    protected MilestoneService milestoneService;
    @Autowired
    protected TransactionService transactionService;
    @Autowired
    protected RewardService rewardService;
    
    @Autowired
    protected OrdermatchService ordermatchService;
    @Autowired
    protected OrderReclaimService ordeReclaimService;
    
    @Autowired
    protected NetworkParameters networkParameters;
    @Autowired
    protected FullPrunedBlockStore store;
    @Autowired
    protected TipsService tipsService;

    @Autowired
    protected void prepareContextRoot(@Value("${local.server.port}") int port) {
        contextRoot = String.format(CONTEXT_ROOT_TEMPLATE, port);
    }

    protected static ECKey outKey = new ECKey();
    protected static ECKey outKey2 = new ECKey();
    protected static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    protected static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
    protected static ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() throws Exception {
        Utils.unsetMockClock();

        store.resetStore();
        walletKeys();
    }

    protected Block resetAndMakeTestToken(ECKey testKey, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        store.resetStore();

        // Make the "test" token
        Block block = null;
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = Coin.valueOf(77777L, testKey.getPubKey());
        long amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, "", Utils.HEX.encode(testKey.getPubKey()), "Test", "Test", 1, 0,
                amount, true);

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

        block = walletAppKit.wallet().saveTokenUnitTest(tokenInfo, coinbase, testKey, null, null, null);
        addedBlocks.add(block);
        blockGraph.confirm(block.getHash(), new HashSet<>());
        milestoneService.update();
        return block;
    }

    protected Block makeAndConfirmReclaim(Sha256Hash reclaimedOrder, Sha256Hash missingOrderMatching,
            List<Block> addedBlocks) throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        return makeAndConfirmReclaim(reclaimedOrder, missingOrderMatching, addedBlocks, predecessor);
    }

    protected Block makeAndConfirmReclaim(Sha256Hash reclaimedOrder, Sha256Hash missingOrderMatching,
            List<Block> addedBlocks, Block predecessor) throws Exception {
        Block block = makeReclaim(reclaimedOrder, missingOrderMatching, addedBlocks, predecessor);

        // Confirm and return
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());
        return block;
    }

    protected Block makeReclaim(Sha256Hash reclaimedOrder, Sha256Hash missingOrderMatching, List<Block> addedBlocks)
            throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        return makeReclaim(reclaimedOrder, missingOrderMatching, addedBlocks, predecessor);
    }

    protected Block makeReclaim(Sha256Hash reclaimedOrder, Sha256Hash missingOrderMatching, List<Block> addedBlocks,
            Block predecessor) throws Exception {
        return makeReclaim(reclaimedOrder, missingOrderMatching, addedBlocks, predecessor, predecessor);
    }

    protected Block makeReclaim(Sha256Hash reclaimedOrder, Sha256Hash missingOrderMatching, List<Block> addedBlocks,
            Block predecessor, Block branchPredecessor) throws Exception {
        Block block = null;

        // Make transaction
        Transaction tx = new Transaction(networkParameters);
        OrderReclaimInfo info = new OrderReclaimInfo(0, reclaimedOrder, missingOrderMatching);
        tx.setData(info.toByteArray());

        // Create block with order reclaim
        block = predecessor.createNextBlock(branchPredecessor);
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_RECLAIM);
        block.solve();
        this.blockGraph.add(block, true);
        addedBlocks.add(block);
        return block;
    }

    protected Block makeAndConfirmTransaction(ECKey fromKey, ECKey beneficiary, String tokenId, long sellAmount,
            List<Block> addedBlocks) throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        return makeAndConfirmTransaction(fromKey, beneficiary, tokenId, sellAmount, addedBlocks, predecessor);
    }

    protected Block makeAndConfirmTransaction(ECKey fromKey, ECKey beneficiary, String tokenId, long sellAmount,
            List<Block> addedBlocks, Block predecessor) throws Exception {
        Block block = null;

        // Make transaction
        Transaction tx = new Transaction(networkParameters);
        Coin amount = Coin.valueOf(sellAmount, tokenId);
        List<UTXO> outputs = getBalance(false, fromKey).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(tokenId))
                .filter(out -> out.getValue().getValue() >= amount.getValue()).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, beneficiary));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), fromKey));
        TransactionInput input = tx.addInput(spendableOutput);

        // Sign
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
        TransactionSignature sig = new TransactionSignature(fromKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with tx
        block = predecessor.createNextBlock();
        block.addTransaction(tx);
        block.solve();
        this.blockGraph.add(block, true);
        addedBlocks.add(block);

        // Confirm and return
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());
        return block;
    }

    protected Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            List<Block> addedBlocks) throws Exception {

        Block block = walletAppKit.wallet().sellOrder(null,  tokenId, sellPrice, sellAmount,
                null, null);
        addedBlocks.add(block);
        milestoneService.update();
        return block;

    }

    protected Block makeAndConfirmSellOrder1(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            List<Block> addedBlocks) throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        return makeAndConfirmSellOrder(beneficiary, tokenId, sellPrice, sellAmount, addedBlocks, predecessor);
    }

    protected Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            List<Block> addedBlocks, Block predecessor) throws Exception {
        Block block = null;
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo(sellPrice * sellAmount, NetworkParameters.BIGTANGLE_TOKENID_STRING,
                beneficiary.getPubKey(), null, null, Side.SELL, beneficiary.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());

        // Burn tokens to sell
        Coin amount = Coin.valueOf(sellAmount, tokenId);
        List<UTXO> outputs = getBalance(false, beneficiary).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid()).equals(tokenId))
                .filter(out -> out.getValue().getValue() >= amount.getValue()).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), beneficiary));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(beneficiary.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with order
        block = predecessor.createNextBlock();
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block.solve();
        this.blockGraph.add(block, true);
        addedBlocks.add(block);
        milestoneService.update();
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());
        return block;
    }

    protected Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            List<Block> addedBlocks) throws Exception {

        Block block = walletAppKit.wallet().buyOrder(null,  tokenId, buyPrice, buyAmount,
                null, null);
        addedBlocks.add(block);
        milestoneService.update();
        return block;

    }

    protected Block makeAndConfirmBuyOrder1(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            List<Block> addedBlocks) throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        
        return makeAndConfirmBuyOrder(beneficiary, tokenId, buyPrice, buyAmount, addedBlocks, predecessor);
    }

    protected Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            List<Block> addedBlocks, Block predecessor) throws Exception {
        Block block = null;
        Transaction tx = new Transaction(networkParameters);
        OrderOpenInfo info = new OrderOpenInfo(buyAmount, tokenId, beneficiary.getPubKey(), null, null, Side.BUY,
                beneficiary.toAddress(networkParameters).toBase58());
        tx.setData(info.toByteArray());

        // Burn BIG to buy
        Coin amount = Coin.valueOf(buyAmount * buyPrice, NetworkParameters.BIGTANGLE_TOKENID);
        List<UTXO> outputs = getBalance(false, beneficiary).stream()
                .filter(out -> Utils.HEX.encode(out.getValue().getTokenid())
                        .equals(Utils.HEX.encode(NetworkParameters.BIGTANGLE_TOKENID)))
                .filter(out -> out.getValue().getValue() >= amount.getValue()).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        // BURN: tx.addOutput(new TransactionOutput(networkParameters, tx,
        // amount, testKey));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), beneficiary));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature sig = new TransactionSignature(beneficiary.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with order
        block = predecessor.createNextBlock();
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_OPEN);
        block.solve();
        this.blockGraph.add(block, true);
        milestoneService.update();
        addedBlocks.add(block);
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());
        milestoneService.update();
        return block;
        

    }

    protected Block makeAndConfirmCancelOp(Block order, ECKey legitimatingKey, List<Block> addedBlocks)
            throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        return makeAndConfirmCancelOp(order, legitimatingKey, addedBlocks, predecessor);
    }

    protected Block makeAndConfirmCancelOp(Block order, ECKey legitimatingKey, List<Block> addedBlocks,
            Block predecessor) throws Exception {
        // Make an order op
        Transaction tx = new Transaction(networkParameters);
        OrderOpInfo info = new OrderOpInfo(OrderOp.CANCEL, 0, order.getHash());
        tx.setData(info.toByteArray());

        // Legitimate it by signing
        Sha256Hash sighash1 = tx.getHash();
        ECKey.ECDSASignature party1Signature = legitimatingKey.sign(sighash1, null);
        byte[] buf1 = party1Signature.encodeToDER();
        tx.setDataSignature(buf1);

        // Create block with order
        Block block = predecessor.createNextBlock();
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_OP);
        block.solve();

        this.blockGraph.add(block, true);
        addedBlocks.add(block);
        milestoneService.update();
        this.blockGraph.confirm(block.getHash(), new HashSet<Sha256Hash>());
        milestoneService.update();
        return block;
    }

    protected Block makeAndConfirmOrderMatching(List<Block> addedBlocks) throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair().getLeft()).getHeader();
        return makeAndConfirmOrderMatching(addedBlocks, predecessor);
    }

    protected Block makeAndConfirmOrderMatching(List<Block> addedBlocks, Block predecessor) throws Exception {
        // Generate blocks until passing interval
        Block rollingBlock = predecessor;
        long currHeight = store.getBlockEvaluation(predecessor.getHash()).getHeight();
        long currMilestoneHeight = store.getOrderMatchingToHeight(store.getMaxConfirmedOrderMatchingBlockHash());
        long targetHeight = currMilestoneHeight + NetworkParameters.ORDER_MATCHING_MIN_HEIGHT_INTERVAL;
        for (int i = 0; i < targetHeight - currHeight; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
            addedBlocks.add(rollingBlock);
        }

        // Generate matching block
        Block block = ordermatchService.createAndAddOrderMatchingBlock(store.getMaxConfirmedOrderMatchingBlockHash(),
                rollingBlock.getHash(), rollingBlock.getHash());
        addedBlocks.add(block);

        // Confirm
        blockGraph.confirm(block.getHash(), new HashSet<>());
        milestoneService.update();
        return block;
    }

    protected void assertCurrentTokenAmountEquals(HashMap<String, Long> origTokenAmounts) throws BlockStoreException {
        assertCurrentTokenAmountEquals(origTokenAmounts, false);
    }

    protected void assertCurrentTokenAmountEquals(HashMap<String, Long> origTokenAmounts, boolean skipBig)
            throws BlockStoreException {
        // Asserts that the current token amounts are equal to the given token
        // amounts
        HashMap<String, Long> currTokenAmounts = getCurrentTokenAmounts();
        for (Entry<String, Long> origTokenAmount : origTokenAmounts.entrySet()) {
            if (skipBig && origTokenAmount.getKey().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING))
                continue;

            assertTrue(currTokenAmounts.containsKey(origTokenAmount.getKey()));
            assertEquals(origTokenAmount.getValue(), currTokenAmounts.get(origTokenAmount.getKey()));
        }
        for (Entry<String, Long> currTokenAmount : currTokenAmounts.entrySet()) {
            if (skipBig && currTokenAmount.getKey().equals(NetworkParameters.BIGTANGLE_TOKENID_STRING))
                continue;

            assertTrue(origTokenAmounts.containsKey(currTokenAmount.getKey()));
            assertEquals(origTokenAmounts.get(currTokenAmount.getKey()), currTokenAmount.getValue());
        }
    }

    protected void assertHasAvailableToken(ECKey testKey, String tokenId_, Long amount) throws Exception {
        // Asserts that the given ECKey possesses the given amount of tokens
        List<UTXO> balance = getBalance(false, testKey);
        HashMap<String, Long> hashMap = new HashMap<>();
        for (UTXO o : balance) {
            String tokenId = Utils.HEX.encode(o.getValue().getTokenid());
            if (!hashMap.containsKey(tokenId))
                hashMap.put(tokenId, 0L);
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().getValue());
        }

        assertEquals(amount == 0 ? null : amount, hashMap.get(tokenId_));
    }

    protected HashMap<String, Long> getCurrentTokenAmounts() throws BlockStoreException {
        // Adds the token values of open orders and UTXOs to a hashMap
        HashMap<String, Long> hashMap = new HashMap<>();
        addCurrentUTXOTokens(hashMap);
        addCurrentOrderTokens(hashMap);
        return hashMap;
    }

    protected void addCurrentOrderTokens(HashMap<String, Long> hashMap) throws BlockStoreException {
        // Adds the token values of open orders to the hashMap
        List<OrderRecord> orders = store.getAllAvailableOrdersSorted(false);
        for (OrderRecord o : orders) {
            String tokenId = o.getOfferTokenid();
            if (!hashMap.containsKey(tokenId))
                hashMap.put(tokenId, 0L);
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getOfferValue());
        }
    }

    protected void addCurrentUTXOTokens(HashMap<String, Long> hashMap) throws BlockStoreException {
        // Adds the token values of open UTXOs to the hashMap
        List<UTXO> utxos = store.getAllAvailableUTXOsSorted();
        for (UTXO o : utxos) {
            String tokenId = Utils.HEX.encode(o.getValue().getTokenid());
            if (!hashMap.containsKey(tokenId))
                hashMap.put(tokenId, 0L);
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().getValue());
        }
    }

    protected void showOrders() throws BlockStoreException {
        // Snapshot current state
        List<OrderRecord> allOrdersSorted = store.getAllAvailableOrdersSorted(false);
        for (OrderRecord o : allOrdersSorted) {
            log.debug(o.toString());
        }
    }

    protected void readdConfirmedBlocksAndAssertDeterministicExecution(List<Block> addedBlocks)
            throws BlockStoreException {
        // Snapshot current state
        List<OrderRecord> allOrdersSorted = store.getAllAvailableOrdersSorted(false);
        List<UTXO> allUTXOsSorted = store.getAllAvailableUTXOsSorted();
        Map<Block, Boolean> blockConfirmed = new HashMap<>();
        for (Block b : addedBlocks) {
            blockConfirmed.put(b, store.getBlockEvaluation(b.getHash()).isMilestone());
        }

        // Redo and assert snapshot equal to new state
        store.resetStore();
        for (Block b : addedBlocks) {
            blockGraph.add(b, false);
            if (blockConfirmed.get(b))
                blockGraph.confirm(b.getHash(), new HashSet<>());
        }
        List<OrderRecord> allOrdersSorted2 = store.getAllAvailableOrdersSorted(false);
        List<UTXO> allUTXOsSorted2 = store.getAllAvailableUTXOsSorted();
        assertEquals(allOrdersSorted2.toString(), allOrdersSorted.toString()); // Works
                                                                               // for
                                                                               // now
        assertEquals(allUTXOsSorted2.toString(), allUTXOsSorted.toString());
    }

    protected Sha256Hash getRandomSha256Hash() {
        byte[] rawHashBytes = new byte[32];
        new Random().nextBytes(rawHashBytes);
        Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
        return sha256Hash;
    }

    protected Block createAndAddNextBlock(Block b1, Block b2) throws VerificationException, PrunedException {
        Block block = b1.createNextBlock(b2);
        this.blockGraph.add(block, true);
        return block;
    }

    protected Block createAndAddNextBlockWithTransaction(Block b1, Block b2, Transaction prevOut)
            throws VerificationException, PrunedException {
        Block block1 = b1.createNextBlock(b2);
        block1.addTransaction(prevOut);
        block1.solve();
        Block block = block1;
        this.blockGraph.add(block, true);
        return block;
    }

    protected Transaction createTestGenesisTransaction() throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0),
                0);
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, genesiskey));
        if (spendableOutput.getValue().subtract(amount).getValue() != 0)
            tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount),
                    genesiskey));
        TransactionInput input = tx.addInput(spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);
        return tx;
    }

    @SuppressWarnings("deprecation")
    protected void walletKeys() throws Exception {
        KeyParameter aesKey = null;
        File f = new File("./logs/", "bigtangle");
        if (f.exists())
            f.delete();
        walletAppKit = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle");
        walletAppKit.wallet().importKey(new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub)));
        // add ge
        walletAppKit.wallet().setServerURL(contextRoot);
        walletKeys = walletAppKit.wallet().walletKeys(aesKey);
    }

    protected void wallet1() throws Exception {
        KeyParameter aesKey = null;
        // delete first
        File f = new File("./logs/", "bigtangle1");
        if (f.exists())
            f.delete();
        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle1");
        walletAppKit1.wallet().setServerURL(contextRoot);

        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);
    }

    protected void wallet2() throws Exception {
        KeyParameter aesKey = null;
        // delete first
        File f = new File("./logs/", "bigtangle2");
        if (f.exists())
            f.delete();
        walletAppKit2 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle2");
        walletAppKit2.wallet().setServerURL(contextRoot);

        wallet2Keys = walletAppKit2.wallet().walletKeys(aesKey);
    }

    protected List<UTXO> getBalance() throws Exception {
        return getBalance(false);
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(boolean withZero) throws Exception {
        return getBalance(withZero, walletKeys);
    }

    protected UTXO getBalance(String tokenid, boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> ulist = getBalance(withZero, keys);

        for (UTXO u : ulist) {
            if (tokenid.equals(u.getTokenId())) {
                return u;
            }
        }

        throw new RuntimeException();
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(boolean withZero, List<ECKey> keys) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : keys) {
            // keyStrHex000.add(ecKey.toAddress(networkParameters).toString());
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
        String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        // String response = mvcResult.getResponse().getContentAsString();
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (withZero) {
                listUTXO.add(utxo);
            } else if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }

        return listUTXO;
    }

    protected List<UTXO> getBalance(boolean withZero, ECKey ecKey) throws Exception {
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(ecKey);
        return getBalance(withZero, keys);
    }

    protected void testInitWallet() throws Exception {

        // testCreateMultiSig();
        testCreateMarket();
        testInitTransferWallet();
        milestoneService.update();
        // testInitTransferWalletPayToTestPub();
        List<UTXO> ux = getBalance();
        // assertTrue(!ux.isEmpty());
        for (UTXO u : ux) {
            log.debug(u.toString());
        }

    }

    // transfer the coin from protected testPub to address in wallet
    @SuppressWarnings("deprecation")
    protected void testInitTransferWallet() throws Exception {
        ECKey fromkey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        giveMoneyResult.put(walletKeys.get(1).toAddress(networkParameters).toString(), 3333333l);
        walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
    }

    protected void testCreateToken() throws JsonProcessingException, Exception {
        ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        String tokenid = Utils.HEX.encode(pubKey);

        Coin basecoin = Coin.valueOf(77777L, pubKey);
        long amount = basecoin.getValue();

        Token tokens = Token.buildSimpleTokenInfo(true, "", tokenid, "test", "", 1, 0, amount, true);
        tokenInfo.setToken(tokens);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
    }

    protected void testCreateMarket() throws JsonProcessingException, Exception {
        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        String tokenid = Utils.HEX.encode(pubKey);
        Token tokens = Token.buildMarketTokenInfo(true, "", tokenid, "p2p", "", "http://localhost:8089");
        tokenInfo.setToken(tokens);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(0, pubKey);
        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
    }

    protected void checkResponse(String resp) throws JsonParseException, JsonMappingException, IOException {
        checkResponse(resp, 0);
    }

    protected void checkResponse(String resp, int code) throws JsonParseException, JsonMappingException, IOException {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        int error = (Integer) result2.get("errorcode");
        assertTrue(error == code);
    }

    protected void checkBalance(Coin coin, ECKey ecKey) throws Exception {
        ArrayList<ECKey> a = new ArrayList<ECKey>();
        a.add(ecKey);
        checkBalance(coin, a);
    }

    protected void checkBalance(Coin coin, List<ECKey> a) throws Exception {
        milestoneService.update();
        List<UTXO> ulist = getBalance(false, a);
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue() == u.getValue().getValue()) {
                myutxo = u;
                break;
            }
        }
        assertTrue(myutxo != null);
        assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
        log.debug(myutxo.toString());
    }

    // create a token with multi sign
    protected void testCreateMultiSigToken(List<ECKey> keys, TokenInfo tokenInfo)
            throws JsonProcessingException, Exception {
        // First issuance cannot be multisign but instead needs the signature of
        // the token id
        // Hence we first create a normal token with multiple permissioned, then
        // we can issue via multisign

        String tokenid = createFirstMultisignToken(keys, tokenInfo);

        milestoneService.update();

        int amount = 2000000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        // TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        long tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 2, tokenindex_, amount, false);
        KeyValue kv = new KeyValue();
        kv.setKey("testkey");
        kv.setKey("testvalue");
        tokens.addKeyvalue(kv);

        tokenInfo.setToken(tokens);

        ECKey key1 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(keys.get(2).getPubKey(), basecoin, tokenInfo);
        block.solve();

        log.debug("block hash : " + block.getHashAsString());

        // save block, but no signature and is not saved as block, but in a
        // table for signs
        OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());

        List<ECKey> ecKeys = new ArrayList<ECKey>();
        ecKeys.add(key1);
        ecKeys.add(key2);

        for (ECKey ecKey : ecKeys) {
            HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
            requestParam0.put("address", ecKey.toAddress(networkParameters).toBase58());
            String resp = OkHttp3Util.postString(contextRoot + ReqCmd.getMultiSignWithAddress.name(),
                    Json.jsonmapper().writeValueAsString(requestParam0));
            System.out.println(resp);

            MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
            String blockhashHex = multiSignResponse.getMultiSigns().get((int) tokenindex_).getBlockhashHex();
            byte[] payloadBytes = Utils.HEX.decode(blockhashHex);

            Block block0 = networkParameters.getDefaultSerializer().makeBlock(payloadBytes);
            Transaction transaction = block0.getTransactions().get(0);

            List<MultiSignBy> multiSignBies = null;
            if (transaction.getDataSignature() == null) {
                multiSignBies = new ArrayList<MultiSignBy>();
            } else {
                MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                        MultiSignByRequest.class);
                multiSignBies = multiSignByRequest.getMultiSignBies();
            }
            Sha256Hash sighash = transaction.getHash();
            ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
            byte[] buf1 = party1Signature.encodeToDER();

            MultiSignBy multiSignBy0 = new MultiSignBy();
            multiSignBy0.setTokenid(tokenid);
            multiSignBy0.setTokenindex(tokenindex_);
            multiSignBy0.setAddress(ecKey.toAddress(networkParameters).toBase58());
            multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
            multiSignBy0.setSignature(Utils.HEX.encode(buf1));
            multiSignBies.add(multiSignBy0);
            MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
            transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
            checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block0.bitcoinSerialize()));

        }

        checkBalance(basecoin, key1);
    }

    private String createFirstMultisignToken(List<ECKey> keys, TokenInfo tokenInfo)
            throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
        String tokenid = keys.get(1).getPublicKeyAsHex();

        int amount = 678900000;
        Coin basecoin = Coin.valueOf(amount, tokenid);

        // TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
        String resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        long tokenindex_ = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), 2, tokenindex_, amount, false);
        tokenInfo.setToken(tokens);

        ECKey key1 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, keys.get(1), null);
        return tokenid;
    }

}
