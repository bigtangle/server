package net.bigtangle.subtangle; /*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.bigtangle.core.*;
import net.bigtangle.core.Block.Type;
import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.response.*;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.config.ServerConfiguration;
import net.bigtangle.server.service.*;
import net.bigtangle.store.FullBlockGraph;
import net.bigtangle.store.FullBlockStore;
import net.bigtangle.utils.Json;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.UUIDUtil;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
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

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    public WalletAppKit walletAppKit;
    public WalletAppKit walletAppKit1;
    public WalletAppKit walletAppKit2;

    protected final KeyParameter aesKey = null;

    private HashMap<String, ECKey> walletKeyData = new HashMap<String, ECKey>();

    @Autowired
    protected FullBlockGraph blockGraph;
    @Autowired
    protected BlockService blockService;
    @Autowired
    protected MCMCService mcmcService;
    @Autowired
    protected RewardService rewardService;

    @Autowired
    protected NetworkParameters networkParameters;

    @Autowired
    protected StoreService storeService;

    @Autowired
    protected TipsService tipsService;
    @Autowired
    protected SyncBlockService syncBlockService;

    @Autowired
    protected ServerConfiguration serverConfiguration;
    
    @Autowired
    protected void prepareContextRoot(@Value("${local.server.port}") int port) {
        contextRoot = String.format(CONTEXT_ROOT_TEMPLATE, port);
    }

    protected static ECKey outKey = new ECKey();
    protected static ECKey outKey2 = new ECKey();
    public static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    public static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
    public static String yuanTokenPub = "02a717921ede2c066a4da05b9cdce203f1002b7e2abeee7546194498ef2fa9b13a";
    public static String yuanTokenPriv = "8db6bd17fa4a827619e165bfd4b0f551705ef2d549a799e7f07115e5c3abad55";

    protected static ObjectMapper objectMapper = new ObjectMapper();
    public FullBlockStore store;

    public void testCreateDomainToken() throws Exception {
        this.walletKeys();
        this.initWalletKeysMapper();

        String tokenid = walletKeys.get(1).getPublicKeyAsHex();
        int amount = 678900000;
        final String domainname = "de";
        this.createDomainToken(tokenid, "中央银行token - 000", "de", amount, this.walletKeys);
        this.checkTokenAssertTrue(tokenid, domainname);
    }

    protected Block addFixedBlocks(int num, Block startBlock, List<Block> blocksAddedAll) throws BlockStoreException {
        // add more blocks follow this startBlock
        Block rollingBlock1 = startBlock;
        for (int i = 0; i < num; i++) {
            rollingBlock1 = rollingBlock1.createNextBlock(rollingBlock1);
            blockGraph.add(rollingBlock1, true, store);
            blocksAddedAll.add(rollingBlock1);
        }
        return rollingBlock1;
    }

    public void checkTokenAssertTrue(String tokenid, String domainname) throws Exception {
        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("tokenid", tokenid);
       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenById.name(),
                Json.jsonmapper().writeValueAsString(requestParam0));

        GetTokensResponse getTokensResponse = Json.jsonmapper().readValue(resp, GetTokensResponse.class);
        Token token_ = getTokensResponse.getTokens().get(0);
        assertTrue(token_.getDomainName().equals(domainname));
    }

    public void initWalletKeysMapper() throws Exception {
        wallet1();
        wallet2();
        List<ECKey> tmpList = new ArrayList<ECKey>();
        tmpList.addAll(this.walletKeys);
        tmpList.addAll(this.wallet1Keys);
        tmpList.addAll(this.wallet2Keys);
        for (Iterator<ECKey> iterator = tmpList.iterator(); iterator.hasNext();) {
            ECKey outKey = iterator.next();
            walletKeyData.put(outKey.getPublicKeyAsHex(), outKey);
        }
    }

    @Before
    public void setUp() throws Exception {
        Utils.unsetMockClock();
        store = storeService.getStore();
        store.resetStore();

        this.walletKeys();
        this.initWalletKeysMapper();

    }

    @After
    public void close() throws Exception {
        store.close();
    }

    protected void payBigTo(ECKey beneficiary, long amount) throws Exception {
    	payBigTo(beneficiary, amount, new ArrayList<>());
    }

    protected void payTestTokenTo(ECKey beneficiary, ECKey testKey, long amount)
            throws Exception {
    	payTestTokenTo(beneficiary, testKey, amount, new ArrayList<>());
    }

    protected void payBig(long amount) throws Exception {
    	payBig(amount, new ArrayList<>());
    }

    protected void payTestToken(ECKey testKey, long amount)
            throws Exception {
    	payTestToken(testKey, amount, new ArrayList<>());
    }

    protected void payBigTo(ECKey beneficiary, long amount, List<Block> addedBlocks) throws Exception {
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        giveMoneyResult.put(beneficiary.toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, "payBig");
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        addedBlocks.add(b);
        makeRewardBlock(addedBlocks);
    }

    protected void payTestTokenTo(ECKey beneficiary, ECKey testKey, long amount, List<Block> addedBlocks)
            throws Exception {

        HashMap<String, Long> giveMoneyTestToken = new HashMap<String, Long>();

        giveMoneyTestToken.put(beneficiary.toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyTestToken, testKey.getPubKey(), "" );
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        addedBlocks.add(b);
        makeRewardBlock(addedBlocks);
        // Open sell order for test tokens
    }

    protected void payBig(long amount, List<Block> addedBlocks) throws Exception {
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();

        giveMoneyResult.put(wallet1Keys.get(0).toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, "payBig");
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        addedBlocks.add(b);
        makeRewardBlock(addedBlocks);
    }

    protected void payTestToken(ECKey testKey, long amount, List<Block> addedBlocks)
            throws Exception {

        HashMap<String, Long> giveMoneyTestToken = new HashMap<String, Long>();

        giveMoneyTestToken.put(wallet2Keys.get(0).toAddress(networkParameters).toString(), amount);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyTestToken, testKey.getPubKey(), "" );
        // log.debug("block " + (b == null ? "block is null" : b.toString()));

        addedBlocks.add(b);
        makeRewardBlock(addedBlocks);
        // Open sell order for test tokens
    }

    protected Block resetAndMakeTestToken(ECKey testKey, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        Block block = makeTestToken(testKey, BigInteger.valueOf(77777L), addedBlocks, 0);
		return block;
    }

    protected Block resetAndMakeTestTokenWithSpare(ECKey testKey, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        Block block = makeTestToken(testKey, BigInteger.valueOf(77777L), addedBlocks, 0);
        payTestTokenTo(testKey, testKey, 50000, addedBlocks);
        payTestTokenTo(testKey, testKey, 40000, addedBlocks);
        payTestTokenTo(testKey, testKey, 30000, addedBlocks);
        payTestTokenTo(testKey, testKey, 20000, addedBlocks);
        payTestTokenTo(testKey, testKey, 10000, addedBlocks);
		return block;
    }

    protected void generateSpareChange(ECKey beneficiary, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        payBigTo(beneficiary, 500000, addedBlocks);
        payBigTo(beneficiary, 400000, addedBlocks);
        payBigTo(beneficiary, 300000, addedBlocks);
        payBigTo(beneficiary, 200000, addedBlocks);
        payBigTo(beneficiary, 100000, addedBlocks);
    }

    protected Block resetAndMakeTestToken(ECKey testKey, BigInteger amount, List<Block> addedBlocks)
            throws JsonProcessingException, Exception, BlockStoreException {
        return makeTestToken(testKey, amount, addedBlocks, 0);
    }

    protected Block makeTestToken(ECKey testKey, BigInteger amount, List<Block> addedBlocks, int decimal)
            throws JsonProcessingException, Exception, BlockStoreException {
        // Make the "test" token
        Block block = null;
        TokenInfo tokenInfo = new TokenInfo();

        Coin coinbase = new Coin(amount, testKey.getPubKey());
        // BigInteger amount = coinbase.getValue();
        Token tokens = Token.buildSimpleTokenInfo(true, null, testKey.getPublicKeyAsHex(), testKey.getPublicKeyAsHex(),
                "", 1, 0, amount, true, decimal, networkParameters.getGenesisBlock().getHashAsString());

        tokenInfo.setToken(tokens);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", testKey.getPublicKeyAsHex()));

        block = saveTokenUnitTest(tokenInfo, coinbase, testKey, null);
        addedBlocks.add(block);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeAndConfirmTransaction(ECKey fromKey, ECKey beneficiary, String tokenId, long sellAmount,
            List<Block> addedBlocks) throws Exception {

        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
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
                .filter(out -> out.getValue().getValue().compareTo(amount.getValue()) > 0).collect(Collectors.toList());
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, beneficiary));
        tx.addOutput(
                new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount), fromKey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);

        // Sign
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);
        TransactionSignature sig = new TransactionSignature(fromKey.sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(sig);
        input.setScriptSig(inputScript);

        // Create block with tx
        block = predecessor.createNextBlock(predecessor);
        block.addTransaction(tx);
        block = adjustSolve(block);
        this.blockGraph.add(block, true, store);
        addedBlocks.add(block);

        // Confirm and return
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeAndConfirmBlock(List<Block> addedBlocks, Block predecessor) throws Exception {
        Block block = null;

        // Create and add block
        block = predecessor.createNextBlock(predecessor);
        block = adjustSolve(block);
        this.blockGraph.add(block, true, store);
        addedBlocks.add(block);

        // Confirm and return
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeAndAddBlock(Block predecessor) throws Exception {
        Block block = null;

        // Create and add block
        block = predecessor.createNextBlock(predecessor);
        block = adjustSolve(block);
        this.blockGraph.add(block, true, store);
        return block;
    }

    protected Block makeSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            List<Block> addedBlocks) throws Exception {

        return makeSellOrder(beneficiary, tokenId, sellPrice, sellAmount,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, addedBlocks);
    }

    protected Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            List<Block> addedBlocks) throws Exception {

        Block block = makeSellOrder(beneficiary, tokenId, sellPrice, sellAmount,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, addedBlocks);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
            String basetoken, List<Block> addedBlocks) throws Exception {
        Wallet w = Wallet.fromKeys(networkParameters, beneficiary);
        w.setServerURL(contextRoot);
        Block block = w.sellOrder(null, tokenId, sellPrice, sellAmount, null, null, basetoken, true);
        addedBlocks.add(block);
        return block;

    }

    protected Block makeAndConfirmSellOrder(ECKey beneficiary, String tokenId, long sellPrice, long sellAmount,
    		String basetoken, List<Block> addedBlocks) throws Exception {

        Block block = makeSellOrder(beneficiary, tokenId, sellPrice, sellAmount,
        		basetoken, addedBlocks);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeAndConfirmPayContract(ECKey beneficiary, String tokenId, BigInteger buyAmount,
            String contractTokenid, List<Block> addedBlocks) throws Exception {
        Wallet w = Wallet.fromKeys(networkParameters, beneficiary);
        w.setServerURL(contextRoot);
        Block block = w.payContract(null, tokenId, buyAmount, null, null, contractTokenid);
        addedBlocks.add(block);
        makeRewardBlock(addedBlocks);
        return block;

    }

    protected Block makeBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            List<Block> addedBlocks) throws Exception {

        Block block = makeBuyOrder(beneficiary, tokenId, buyPrice, buyAmount,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, addedBlocks);
        return block;
    }

    protected Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            List<Block> addedBlocks) throws Exception {

        Block block = makeBuyOrder(beneficiary, tokenId, buyPrice, buyAmount,
                NetworkParameters.BIGTANGLE_TOKENID_STRING, addedBlocks);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            String basetoken, List<Block> addedBlocks) throws Exception {
        Wallet w = Wallet.fromKeys(networkParameters, beneficiary);
        w.setServerURL(contextRoot);
        Block block = w.buyOrder(null, tokenId, buyPrice, buyAmount, null, null, basetoken,true);
        addedBlocks.add(block);
        return block;
    }

    protected Block makeAndConfirmBuyOrder(ECKey beneficiary, String tokenId, long buyPrice, long buyAmount,
            String basetoken, List<Block> addedBlocks) throws Exception {

        Block block = makeBuyOrder(beneficiary, tokenId, buyPrice, buyAmount,
        		basetoken, addedBlocks);
        makeRewardBlock(addedBlocks);
        return block;
    }

    protected Block makeCancelOp(Block order, ECKey legitimatingKey, List<Block> addedBlocks)
            throws Exception {

        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        return makeCancelOp(order, legitimatingKey, addedBlocks, predecessor);
    }

    protected Block makeCancelOp(Block order, ECKey legitimatingKey, List<Block> addedBlocks,
            Block predecessor) throws Exception {
        // Make an order op
        Transaction tx = new Transaction(networkParameters);
        OrderCancelInfo info = new OrderCancelInfo(order.getHash());
        tx.setData(info.toByteArray());

        // Legitimate it by signing
        Sha256Hash sighash1 = tx.getHash();
        ECKey.ECDSASignature party1Signature = legitimatingKey.sign(sighash1, null);
        byte[] buf1 = party1Signature.encodeToDER();
        tx.setDataSignature(buf1);

        // Create block with order
        Block block = predecessor.createNextBlock(predecessor);
        block.addTransaction(tx);
        block.setBlockType(Type.BLOCKTYPE_ORDER_CANCEL);
        block = adjustSolve(block);

        this.blockGraph.add(block, true, store);
        addedBlocks.add(block);

        return block;
    }

    protected Block makeRewardBlock() throws Exception {
        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        return makeRewardBlock(predecessor);
    }

    protected Block makeRewardBlock(List<Block> addedBlocks) throws Exception {
    	Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());
        Block block = makeRewardBlock(predecessor);
        addedBlocks.add(block);
		return block;
    }

    protected Block makeRewardBlock(Block predecessor) throws Exception {
        return makeRewardBlock(predecessor.getHash());
    }

    protected Block makeRewardBlock(Sha256Hash predecessor) throws Exception {
        Block block = makeRewardBlock(store.getMaxConfirmedReward().getBlockHash(),
                predecessor, predecessor);
        return block;
    }

    protected Block makeAndConfirmContractExecution(List<Block> addedBlocks) throws Exception {

        Block predecessor = store.get(tipsService.getValidatedBlockPair(store).getLeft());

        Block block = makeRewardBlock(store.getMaxConfirmedReward().getBlockHash(),
                predecessor.getHash(), predecessor.getHash());
        addedBlocks.add(block);

        // Confirm
        makeRewardBlock();

        return block;
    }

    protected void assertCurrentTokenAmountEquals(HashMap<String, Long> origTokenAmounts) throws BlockStoreException {
        assertCurrentTokenAmountEquals(origTokenAmounts, true);
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
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().getValue().longValue());
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
        List<OrderRecord> orders = store.getAllOpenOrdersSorted(null, null);
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
            hashMap.put(tokenId, hashMap.get(tokenId) + o.getValue().getValue().longValue());
        }
    }

    protected void showOrders() throws BlockStoreException {
        // Snapshot current state
        List<OrderRecord> allOrdersSorted = store.getAllOpenOrdersSorted(null, null);
        for (OrderRecord o : allOrdersSorted) {
            log.debug(o.toString());
        }
    }

    protected void checkOrders(int ordersize) throws BlockStoreException {
        // Snapshot current state
        assertTrue(store.getAllOpenOrdersSorted(null, null).size() == ordersize);

    }

    protected void readdConfirmedBlocksAndAssertDeterministicExecution(List<Block> addedBlocks)
            throws BlockStoreException, JsonParseException, JsonMappingException, IOException, InterruptedException,
            ExecutionException {
        // Snapshot current state

        List<OrderRecord> allOrdersSorted = store.getAllOpenOrdersSorted(null, null);
        List<UTXO> allUTXOsSorted = store.getAllAvailableUTXOsSorted();
        Map<Block, Boolean> blockConfirmed = new HashMap<>();
        for (Block b : addedBlocks) {
            blockConfirmed.put(b, blockService.getBlockEvaluation(b.getHash(), store).isConfirmed());
        }

        // Redo and assert snapshot equal to new state
        store.resetStore();
        for (Block b : addedBlocks) {
            blockGraph.add(b, true, true, store);
        }

        List<OrderRecord> allOrdersSorted2 = store.getAllOpenOrdersSorted(null, null);
        List<UTXO> allUTXOsSorted2 = store.getAllAvailableUTXOsSorted();
        assertEquals(allOrdersSorted.toString(), allOrdersSorted2.toString());
        assertEquals(allUTXOsSorted.toString(), allUTXOsSorted2.toString());
    }

    protected Sha256Hash getRandomSha256Hash() {
        byte[] rawHashBytes = new byte[32];
        new Random().nextBytes(rawHashBytes);
        Sha256Hash sha256Hash = Sha256Hash.wrap(rawHashBytes);
        return sha256Hash;
    }

    protected Block createAndAddNextBlock(Block b1, Block b2) throws VerificationException, BlockStoreException {
        Block block = b1.createNextBlock(b2);
        this.blockGraph.add(block, true, store);
        return block;
    }

    protected Block createAndAddNextBlockWithTransaction(Block b1, Block b2, Transaction prevOut)
            throws VerificationException, BlockStoreException, JsonParseException, JsonMappingException, IOException {
        Block block1 = b1.createNextBlock(b2);
        block1.addTransaction(prevOut);
        block1 = adjustSolve(block1);
        this.blockGraph.add(block1, true, store);
        return block1;
    }

    public Block adjustSolve(Block block) throws IOException, JsonParseException, JsonMappingException {
        // save block
       byte[] resp = OkHttp3Util.post(contextRoot + ReqCmd.adjustHeight.name(), block.bitcoinSerialize());
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
        String dataHex = (String) result.get("dataHex");

        Block adjust = networkParameters.getDefaultSerializer().makeBlock(Utils.HEX.decode(dataHex));
        adjust.solve();
        return adjust;
    }

    protected Transaction createTestTransaction() throws Exception {

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        List<UTXO> outputs = getBalance(false, genesiskey);
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, outputs.get(0));
        Coin amount = Coin.valueOf(2, NetworkParameters.BIGTANGLE_TOKENID);
        Transaction tx = new Transaction(networkParameters);
        tx.addOutput(new TransactionOutput(networkParameters, tx, amount, genesiskey));
        if (spendableOutput.getValue().subtract(amount).getValue().signum() != 0)
            tx.addOutput(new TransactionOutput(networkParameters, tx, spendableOutput.getValue().subtract(amount),
                    genesiskey));
        TransactionInput input = tx.addInput(outputs.get(0).getBlockHash(), spendableOutput);
        Sha256Hash sighash = tx.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL, false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);
        return tx;
    }

    protected void walletKeys() throws Exception {
        KeyParameter aesKey = null;
        File f = new File("./logs/", "bigtangle.wallet");
        if (f.exists())
            f.delete();
        walletAppKit = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle");
        walletAppKit.wallet().importKey(
                ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub)));
        // add ge
        walletAppKit.wallet().setServerURL(contextRoot);
        walletKeys = walletAppKit.wallet().walletKeys(aesKey);
    }

    protected void wallet1() throws Exception {
        KeyParameter aesKey = null;
        // delete first
        File f = new File("./logs/", "bigtangle1.wallet");
        if (f.exists())
            f.delete();
        walletAppKit1 = new WalletAppKit(networkParameters, new File("./logs/"), "bigtangle1");
        walletAppKit1.wallet().setServerURL(contextRoot);

        wallet1Keys = walletAppKit1.wallet().walletKeys(aesKey);
    }

    protected void wallet2() throws Exception {
        KeyParameter aesKey = null;
        // delete first
        File f = new File("./logs/", "bigtangle2.wallet");
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
       byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        //byte[] response = mvcResult.getResponse().getContentAsString();
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (withZero) {
                listUTXO.add(utxo);
            } else if (utxo.getValue().getValue().signum() > 0) {
                listUTXO.add(utxo);
            }
        }

        return listUTXO;
    }

    // get balance for the walletKeys
    protected List<UTXO> getBalance(String address) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();

        keyStrHex000.add(Utils.HEX.encode(Address.fromBase58(networkParameters, address).getHash160()));
       byte[] response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            listUTXO.add(utxo);
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
        // testCreateMarket();
        testInitTransferWallet();
        makeRewardBlock();

        // testInitTransferWalletPayToTestPub();
        List<UTXO> ux = getBalance();
        // assertTrue(!ux.isEmpty());
        for (UTXO u : ux) {
            log.debug(u.toString());
        }

    }

    // transfer the coin from protected testPub to address in wallet

    protected void testInitTransferWallet() throws Exception {
        ECKey fromkey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        giveMoneyResult.put(walletKeys.get(1).toAddress(networkParameters).toString(),
                MonetaryFormat.FIAT.noCode().parse("33333").getValue().longValue());
        walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, "testInitTransferWallet");
    }

    protected Block testCreateToken(ECKey outKey, String tokennameName) throws JsonProcessingException, Exception {
        return testCreateToken(outKey, tokennameName, networkParameters.getGenesisBlock().getHashAsString());
    }

    protected Block testCreateToken(ECKey outKey, String tokennameName, String domainpre)
            throws JsonProcessingException, Exception {
        // ECKey outKey = walletKeys.get(0);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        String tokenid = Utils.HEX.encode(pubKey);

        Coin basecoin = Coin.valueOf(77777L, pubKey);
        BigInteger amount = basecoin.getValue();

        Token token = Token.buildSimpleTokenInfo(true, null, tokenid, tokennameName, "", 1, 0, amount, true, 0,
                domainpre);

        tokenInfo.setToken(token);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(token.getTokenid(), "", outKey.getPublicKeyAsHex()));

        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex, 0));
            }
        }

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);

        MultiSignAddress multiSignAddress = permissionedAddressesResponse.getMultiSignAddresses().get(0);
        String pubKeyHex = multiSignAddress.getPubKeyHex();
        ECKey ecKey = this.walletKeyData.get(pubKeyHex);
        return this.pullBlockDoMultiSign(tokenid, ecKey, aesKey);

    }

    protected void testCreateMarket() throws JsonProcessingException, Exception {
        ECKey outKey = walletKeys.get(1);
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        String tokenid = Utils.HEX.encode(pubKey);
        Token tokens = Token.buildMarketTokenInfo(true, null, tokenid, "p2p", "", null);
        tokenInfo.setToken(tokens);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(0, pubKey);
        walletAppKit.wallet().saveToken(tokenInfo, basecoin, outKey, null);
    }

    protected void checkResponse(byte[]  resp) throws JsonParseException, JsonMappingException, IOException {
        checkResponse(resp, 0);
    }

    protected void checkResponse(byte[]  resp, int code) throws JsonParseException, JsonMappingException, IOException {
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
        List<UTXO> ulist = getBalance(false, a);
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId()) && coin.getValue().equals(u.getValue().getValue())) {
                myutxo = u;
                break;
            }
        }
        assertTrue(myutxo != null);
        assertTrue(myutxo.getAddress() != null && !myutxo.getAddress().isEmpty());
        log.debug(myutxo.toString());
    }

    protected void checkBalanceSum(Coin coin, List<ECKey> a) throws Exception {
        List<UTXO> ulist = getBalance(false, a);

        Coin sum = new Coin(0, coin.getTokenid());
        for (UTXO u : ulist) {
            if (coin.getTokenHex().equals(u.getTokenId())) {
                sum = sum.add(u.getValue());

            }
        }
        if (coin.getValue().compareTo(sum.getValue()) != 0) {
            log.error(" expected: " + coin + " got: " + sum);
        }
        assertTrue(coin.getValue().compareTo(sum.getValue()) == 0);

    }

    // create a token with multi sign
    protected void testCreateMultiSigToken(List<ECKey> keys, TokenInfo tokenInfo)
            throws JsonProcessingException, Exception {
        // First issuance cannot be multisign but instead needs the signature of
        // the token id
        // Hence we first create a normal token with multiple permissioned, then
        // we can issue via multisign

        String tokenid = createFirstMultisignToken(keys, tokenInfo);

        makeRewardBlock();

        BigInteger amount = new BigInteger("200000");
        Coin basecoin = new Coin(amount, tokenid);

        // TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
       byte[] resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        long tokenindex_ = tokenIndexResponse.getTokenindex();
        ;

        Token tokens = Token.buildSimpleTokenInfo(true, tokenIndexResponse.getBlockhash(), tokenid, "test", "test", 3,
                tokenindex_, amount, false, 0, networkParameters.getGenesisBlock().getHashAsString());
        KeyValue kv = new KeyValue();
        kv.setKey("testkey");
        kv.setKey("testvalue");
        tokens.addKeyvalue(kv);

        tokenInfo.setToken(tokens);

        ECKey key1 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex));
            }
        }

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Type.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(keys.get(2).getPubKey(), basecoin, tokenInfo, new MemoInfo("coinbase"));
        block = adjustSolve(block);

        log.debug("block hash : " + block.getHashAsString());

        // save block, but no signature and is not saved as block, but in a
        // table for signs
        OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block.bitcoinSerialize());

        List<ECKey> ecKeys = new ArrayList<ECKey>();
        ecKeys.add(key1);
        ecKeys.add(key2);

        MultiSignAddress multiSignAddress = permissionedAddressesResponse.getMultiSignAddresses().get(0);
        final String pubKeyHex = multiSignAddress.getPubKeyHex();
        ecKeys.add(this.walletKeyData.get(pubKeyHex));

        for (ECKey ecKey : ecKeys) {
            HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
            requestParam0.put("address", ecKey.toAddress(networkParameters).toBase58());
           byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenSignByAddress.name(),
                    Json.jsonmapper().writeValueAsString(requestParam0));
            System.out.println(resp);

            MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);

            if (multiSignResponse.getMultiSigns().isEmpty())
                continue;

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
            checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block0.bitcoinSerialize()));

        }

        checkBalance(basecoin, key1);
    }

    private String createFirstMultisignToken(List<ECKey> keys, TokenInfo tokenInfo)
            throws Exception, JsonProcessingException, IOException, JsonParseException, JsonMappingException {
        String tokenid = keys.get(1).getPublicKeyAsHex();

        Coin basecoin = MonetaryFormat.FIAT.noCode().parse("678900000");

        // TokenInfo tokenInfo = new TokenInfo();

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenid);
       byte[] resp2 = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2, TokenIndexResponse.class);
        long tokenindex_ = tokenIndexResponse.getTokenindex();
        Sha256Hash prevblockhash = tokenIndexResponse.getBlockhash();

        Token tokens = Token.buildSimpleTokenInfo(true, prevblockhash, tokenid, "test", "test", 3, tokenindex_,
                basecoin.getValue(), false, 0, networkParameters.getGenesisBlock().getHashAsString());
        tokenInfo.setToken(tokens);

        ECKey key1 = keys.get(1);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key1.getPublicKeyAsHex()));

        ECKey key2 = keys.get(2);
        tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenid, "", key2.getPublicKeyAsHex()));

        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex));
            }
        }

        walletAppKit.wallet().saveToken(tokenInfo, basecoin, keys.get(1), null);
        return tokenid;
    }

    // for unit tests
    public Block saveTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws Exception {
        tokenInfo.getToken().setTokenname(UUIDUtil.randomUUID());
        return saveTokenUnitTest(tokenInfo, basecoin, outKey, aesKey, null, null);
    }

    public Block saveTokenUnitTestWithTokenname(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws Exception {
        return saveTokenUnitTest(tokenInfo, basecoin, outKey, aesKey, null, null);
    }

    public Block saveTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey,
            Block overrideHash1, Block overrideHash2) throws IOException, Exception {

        Block block = makeTokenUnitTest(tokenInfo, basecoin, outKey, aesKey, overrideHash1, overrideHash2);
        OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block.bitcoinSerialize());

        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        MultiSignAddress multiSignAddress = permissionedAddressesResponse.getMultiSignAddresses().get(0);
        String pubKeyHex = multiSignAddress.getPubKeyHex();
        pullBlockDoMultiSign(tokenInfo.getToken().getTokenid(), this.walletKeyData.get(pubKeyHex), aesKey);
        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        pullBlockDoMultiSign(tokenInfo.getToken().getTokenid(), genesiskey, null);

        return block;
    }

    public Block makeTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws JsonProcessingException, IOException, Exception {
        return makeTokenUnitTest(tokenInfo, basecoin, outKey, aesKey, null, null);
    }

    public Block makeTokenUnitTest(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey,
            Block overrideHash1, Block overrideHash2) throws JsonProcessingException, IOException, Exception {

        final String tokenid = tokenInfo.getToken().getTokenid();
        List<MultiSignAddress> multiSignAddresses = tokenInfo.getMultiSignAddresses();
        PermissionedAddressesResponse permissionedAddressesResponse = this
                .getPrevTokenMultiSignAddressList(tokenInfo.getToken());
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            if (StringUtils.isBlank(tokenInfo.getToken().getDomainName())) {
                tokenInfo.getToken().setDomainName(permissionedAddressesResponse.getDomainName());
            }
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex, 0));
            }
        }

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Type.BLOCKTYPE_TOKEN_CREATION);

        if (overrideHash1 != null && overrideHash2 != null) {
            block.setPrevBlockHash(overrideHash1.getHash());

            block.setPrevBranchBlockHash(overrideHash2.getHash());

            block.setHeight(Math.max(overrideHash2.getHeight(), overrideHash1.getHeight()) + 1);
        }

        block.addCoinbaseTransaction(outKey.getPubKey(), basecoin, tokenInfo, new MemoInfo("coinbase"));

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();

        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        ECKey.ECDSASignature party2Signature = genesiskey.sign(sighash, aesKey);
        byte[] buf2 = party2Signature.encodeToDER();
        multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(genesiskey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(genesiskey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf2));
        multiSignBies.add(multiSignBy0);

        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        // save block
        block = adjustSolve(block);
        return block;
    }

    public void createDomainToken(String tokenid, String tokenname, String domainname, final int amount,
            List<ECKey> walletKeys) throws Exception {

        Coin basecoin = Coin.valueOf(amount, tokenid);
        TokenIndexResponse tokenIndexResponse = this.getServerCalTokenIndex(tokenid);

        long tokenindex_ = tokenIndexResponse.getTokenindex();
        Sha256Hash prevblockhash = tokenIndexResponse.getBlockhash();

        int signnumber = 3;

        // TODO domainname create token
        Token tokens = Token.buildDomainnameTokenInfo(true, prevblockhash, tokenid, tokenname, "de domain name",
                signnumber, tokenindex_, false, domainname, networkParameters.getGenesisBlock().getHashAsString());
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setToken(tokens);

        List<MultiSignAddress> multiSignAddresses = new ArrayList<MultiSignAddress>();
        tokenInfo.setMultiSignAddresses(multiSignAddresses);

        for (int i = 1; i <= 3; i++) {
            ECKey ecKey = walletKeys.get(i);
            multiSignAddresses.add(new MultiSignAddress(tokenid, "", ecKey.getPublicKeyAsHex()));
        }

        PermissionedAddressesResponse permissionedAddressesResponse = this.getPrevTokenMultiSignAddressList(tokens);
        if (permissionedAddressesResponse != null && permissionedAddressesResponse.getMultiSignAddresses() != null
                && !permissionedAddressesResponse.getMultiSignAddresses().isEmpty()) {
            for (MultiSignAddress multiSignAddress : permissionedAddressesResponse.getMultiSignAddresses()) {
                final String pubKeyHex = multiSignAddress.getPubKeyHex();
                multiSignAddresses.add(new MultiSignAddress(tokenid, "", pubKeyHex));
            }
        }

        signnumber++;
        tokens.setSignnumber(signnumber);

        upstreamToken2LocalServer(tokenInfo, basecoin, walletKeys.get(1), aesKey);

        for (int i = 2; i <= 3; i++) {
            ECKey outKey = walletKeys.get(i);
            pullBlockDoMultiSign(tokenid, outKey, aesKey);
        }

        MultiSignAddress multiSignAddress = permissionedAddressesResponse.getMultiSignAddresses().get(0);
        String pubKeyHex = multiSignAddress.getPubKeyHex();
        ECKey outKey = this.walletKeyData.get(pubKeyHex);
        this.pullBlockDoMultiSign(tokenid, outKey, aesKey);

        ECKey genesiskey = ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        this.pullBlockDoMultiSign(tokenid, genesiskey, null);
    }

    public TokenIndexResponse getServerCalTokenIndex(String tokenid) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("tokenid", tokenid);
       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp, TokenIndexResponse.class);
        return tokenIndexResponse;
    }

    public void upstreamToken2LocalServer(TokenInfo tokenInfo, Coin basecoin, ECKey outKey, KeyParameter aesKey)
            throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Type.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(outKey.getPubKey(), basecoin, tokenInfo, new MemoInfo("coinbase"));

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();
        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(tokenInfo.getToken().getTokenid().trim());
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        block = adjustSolve(block);
        OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block.bitcoinSerialize());
    }

    public Block pullBlockDoMultiSign(final String tokenid, ECKey outKey, KeyParameter aesKey) throws Exception {
        HashMap<String, Object> requestParam = new HashMap<String, Object>();

        String address = outKey.toAddress(networkParameters).toBase58();
        requestParam.put("address", address);
        requestParam.put("tokenid", tokenid);

       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenSignByAddress.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
        if (multiSignResponse.getMultiSigns().isEmpty())
            return null;
        MultiSign multiSign = multiSignResponse.getMultiSigns().get(0);

        byte[] payloadBytes = Utils.HEX.decode((String) multiSign.getBlockhashHex());
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
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash, aesKey);
        byte[] buf1 = party1Signature.encodeToDER();

        MultiSignBy multiSignBy0 = new MultiSignBy();

        multiSignBy0.setTokenid(multiSign.getTokenid());
        multiSignBy0.setTokenindex(multiSign.getTokenindex());
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
        OkHttp3Util.post(contextRoot + ReqCmd.signToken.name(), block0.bitcoinSerialize());
        return block0;
    }

    public PermissionedAddressesResponse getPrevTokenMultiSignAddressList(Token token) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        requestParam.put("domainNameBlockHash", token.getDomainNameBlockHash());
       byte[] resp = OkHttp3Util.postString(contextRoot + ReqCmd.getTokenPermissionedAddresses.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        PermissionedAddressesResponse permissionedAddressesResponse = Json.jsonmapper().readValue(resp,
                PermissionedAddressesResponse.class);
        return permissionedAddressesResponse;
    }

    public Block makeRewardBlock(Sha256Hash prevHash, Sha256Hash prevTrunk, Sha256Hash prevBranch)
            throws Exception {
        Block block = rewardService.createMiningRewardBlock(prevHash, prevTrunk, prevBranch, store);
        if (block != null) {
            blockService.saveBlock(block, store);
            blockGraph.updateChain();
        }
        return block;
    }

    public void sendEmpty() throws JsonProcessingException, Exception {
        int c = needEmptyBlocks();
        if (c > 0) {
            sendEmpty(c);
        }
    }

    public void sendEmpty(int c) throws JsonProcessingException {

        for (int i = 0; i < c; i++) {
            try {
                send();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }

    public void send() throws JsonProcessingException, Exception {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

    }

    private int needEmptyBlocks() throws Exception {
        try {
            List<BlockEvaluationDisplay> a = getBlockInfos();
            // only parallel blocks with rating < 70 need empty to resolve
            // conflicts
            int res = 0;
            for (BlockEvaluationDisplay b : a) {
                if (b.getMcmc().getRating() < 70) {
                    res += 1;
                }
            }

            return res;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<BlockEvaluationDisplay> getBlockInfos() throws Exception {

        String lastestAmount = "200";
        Map<String, Object> requestParam = new HashMap<String, Object>();

        requestParam.put("lastestAmount", lastestAmount);
       byte[] response = OkHttp3Util.postString(contextRoot + "/" + ReqCmd.findBlockEvaluation.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        GetBlockEvaluationsResponse getBlockEvaluationsResponse = Json.jsonmapper().readValue(response,
                GetBlockEvaluationsResponse.class);
        return getBlockEvaluationsResponse.getEvaluations();
    }

    public Block createToken(ECKey key, String tokename, int decimals, String domainname, String description,
            BigInteger amount, boolean increment, TokenKeyValues tokenKeyValues, int tokentype, String tokenid,
            Wallet w) throws Exception {

        Token token = Token.buildSimpleTokenInfo(true, Sha256Hash.ZERO_HASH, tokenid, tokename, description, 1, 0,
                amount, !increment, decimals, "");
        token.setTokenKeyValues(tokenKeyValues);
        token.setTokentype(tokentype);
        List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();
        addresses.add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));
        return w.createToken(key, domainname, increment, token, addresses);

    }

    public Block createToken(ECKey key, String tokename, int decimals, String domainname, String description,
            BigInteger amount, boolean increment, TokenKeyValues tokenKeyValues, int tokentype, String tokenid,
            Wallet w, byte[] pubkeyTo, MemoInfo memoInfo) throws Exception {

        Token token = Token.buildSimpleTokenInfo(true, Sha256Hash.ZERO_HASH, tokenid, tokename, description, 1, 0,
                amount, !increment, decimals, "");
        token.setTokenKeyValues(tokenKeyValues);
        token.setTokentype(tokentype);
        List<MultiSignAddress> addresses = new ArrayList<MultiSignAddress>();
        addresses.add(new MultiSignAddress(tokenid, "", key.getPublicKeyAsHex()));
        return w.createToken(key, domainname, increment, token, addresses, pubkeyTo, memoInfo);

    }

    public void mcmcServiceUpdate() throws InterruptedException, ExecutionException, BlockStoreException {
        mcmcService.update(store);
//        blockGraph.updateConfirmed();
    }

    public void mcmc() throws JsonProcessingException, InterruptedException, ExecutionException, BlockStoreException {
        sendEmpty(5);
        mcmcServiceUpdate();

    }
}