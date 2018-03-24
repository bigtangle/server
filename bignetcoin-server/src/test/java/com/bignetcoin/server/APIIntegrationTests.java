/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockForTest;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PrunedException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.MapToBeanMapperUtil;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.MilestoneService;
import com.fasterxml.jackson.core.JsonProcessingException;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class APIIntegrationTests extends AbstractIntegrationTest {

    private int height = 1;
    
  // @Test
    public void createECKey() {
        ECKey ecKey = new ECKey();
        logger.info("pubKey : " + Utils.HEX.encode(ecKey.getPubKey()));
        logger.info("pubKeyHash : " + Utils.HEX.encode(ecKey.getPubKeyHash()));
        //pubKey = 032f46420523938d355d1a539e849cf2903a314dce13c32562c0dec456757c9dce
        ECKey toKey =ECKey.fromPublicOnly(ecKey.getPubKey());
        logger.info("pubKey : " + Utils.HEX.encode(ecKey.getPubKey()));
        logger.info("pubKeyHash : " + Utils.HEX.encode(toKey.getPubKeyHash()));
    }
    
    @Test
    public void testWalletWrapperECKey() {
        Wallet wallet = new Wallet(networkParameters, contextRoot);
        for (int i = 0; i < 10; i ++) {
            ECKey toKey = wallet.freshReceiveKey();
            logger.info("a->eckey pubKeyHash : " + Utils.HEX.encode(toKey.getPubKeyHash()));
            toKey = wallet.currentReceiveKey();
            logger.info("c->eckey pubKeyHash : " + Utils.HEX.encode(toKey.getPubKeyHash()));
        }
    }
    
//    @Before
    public Block getRollingBlock(ECKey outKey) throws Exception {
        Context.propagate(new Context(networkParameters));
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(),Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        for (int i = 1; i < networkParameters.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,networkParameters.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        return rollingBlock;
    }
    
    @Test
    public void testCreateTransaction() throws Exception {
        byte[] data = getAskTransactionBlock();
        
        System.out.println("len : " + data.length);
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        Block r1 = nextBlockSerializer(byteBuffer);
        Block r2 = nextBlockSerializer(byteBuffer);
        
        ECKey outKey = new ECKey();
        int height = 1;
        
        Block block = r2.createNextBlock(null, Block.BLOCK_VERSION_GENESIS, (TransactionOutPoint) null,
                Utils.currentTimeSeconds(), outKey.getPubKey(), Coin.ZERO, height, r1.getHash(), outKey.getPubKey());

        reqCmdSaveBlock(block);
    }

    private static final Logger logger = LoggerFactory.getLogger(APIIntegrationTests.class);
    
    @Autowired
    private MilestoneService milestoneService;
    
    @Autowired
    private BlockService blockService;
    
    @Test
    public void testUTXOProviderWithWallet() throws Exception {
        KeyParameter aesKey = null;
        WalletAppKit bitcoin = new WalletAppKit(PARAMS, new File("."), "bignetcoin");
        bitcoin.wallet().setServerURL(contextRoot);
        
        DecryptingKeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(bitcoin.wallet(), aesKey);
        List<ECKey> keys = new ArrayList<ECKey>();
        for (ECKey key : bitcoin.wallet().getImportedKeys()) {
            ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
            System.out.println("realKey, pubKey : " + ecKey.getPublicKeyAsHex() + ", prvKey : " + ecKey.getPrivateKeyAsHex());
            keys.add(ecKey);
        }
        for (DeterministicKeyChain chain : bitcoin.wallet().getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey key : chain.getLeafKeys()) {
                ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
                System.out.println("realKey, pubKey : " + ecKey.getPublicKeyAsHex() + ", priKey : " + ecKey.getPrivateKeyAsHex());
                keys.add(ecKey);
            }
        }
        ECKey outKey = new ECKey();
        int height = 1;

        // Add genesis block
        blockgraph.add(networkParameters.getGenesisBlock());
        BlockEvaluation genesisEvaluation = blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash());
        blockService.updateMilestone(genesisEvaluation, true);
        blockService.updateSolid(genesisEvaluation, true);

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);

        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        
        for (int i = 1; i < networkParameters.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        milestoneService.update();
        
        rollingBlock = BlockForTest.createNextBlock(rollingBlock, null, networkParameters.getGenesisBlock().getHash());
        System.out.println("rollingBlock : " + rollingBlock.getHashAsString());
        rollingBlock = networkParameters.getDefaultSerializer().makeBlock(rollingBlock.bitcoinSerialize());
        System.out.println("rollingBlock : " + rollingBlock.getHashAsString());

        // Create bitcoin spend of 1 BTC.
        Wallet wallet = bitcoin.wallet();
        ECKey myKey = keys.get(0);
        System.out.println("key " + myKey.getPublicKeyAsHex());
        
        Coin amount = Coin.valueOf(100000, NetworkParameters.BIGNETCOIN_TOKENID);
//        Address address = new Address(PARAMS, toKey.getPubKeyHash());

        Transaction t = new Transaction(networkParameters);
        t.addOutput(new TransactionOutput(networkParameters, t, amount, myKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);
        
        milestoneService.update(); //ADDED

//        byte[] data = getAskTransactionBlock();
//        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
//        Block r1 = nextBlockSerializer(byteBuffer);
//        Block r2 = nextBlockSerializer(byteBuffer);
        
        rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
//        rollingBlock = BlockForTest.createNextBlock(r1, null, r2.getHash());
        
        amount = Coin.valueOf(100, NetworkParameters.BIGNETCOIN_TOKENID);

        ECKey toKey = new ECKey();
        Address address = new Address(networkParameters, toKey.getPubKeyHash());
        SendRequest request = SendRequest.to(address, amount);
        request.changeAddress = new Address(networkParameters, myKey.getPubKeyHash());
        wallet.completeTx(request);

        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);
        
        // cal block update
        milestoneService.update();
//        logger.info("AVAILABLE : " + wallet.getBalance(Wallet.BalanceType.AVAILABLE) + ", ESTIMATED : " + wallet.getBalance(Wallet.BalanceType.ESTIMATED));
        
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name()).content(myKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + response);
    }
    
    @Test
    public void testTransactionAndGetBalances() throws Exception {
        ECKey toKey = createWalletAndAddCoin();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name()).content(toKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + response);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testTransactionAndGetOutputs() throws Exception {
        ECKey toKey = createWalletAndAddCoin();
        
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getOutputs.name()).content(toKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + response);
        
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> outputs0 = (List<Map<String, Object>>) data.get("outputs");
        List<UTXO> outputs = new ArrayList<UTXO>();
        for (Map<String, Object> map : outputs0) {
            UTXO utxo = MapToBeanMapperUtil.parseUTXO(map);
            outputs.add(utxo);
        }
        
        System.out.println(outputs.get(0).getValue());
    }

    public ECKey createWalletAndAddCoin() throws Exception, PrunedException {
        ECKey outKey = new ECKey();
        Block rollingBlock = this.getRollingBlock(outKey);
        
        rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,networkParameters.getGenesisBlock().getHash());

        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        
        Wallet wallet = new Wallet(networkParameters);
        wallet.setUTXOProvider(store);
        System.out.println(wallet.getBalance(Wallet.BalanceType.AVAILABLE));
        System.out.println(wallet.getBalance(Wallet.BalanceType.ESTIMATED));
        
        ECKey toKey = wallet.freshReceiveKey();
        Coin amount = Coin.valueOf(1000000, NetworkParameters.BIGNETCOIN_TOKENID);

        Transaction t = new Transaction(networkParameters);
        t.addOutput(new TransactionOutput(networkParameters, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);
        
        System.out.println(wallet.getBalance(Wallet.BalanceType.AVAILABLE));
        System.out.println(wallet.getBalance(Wallet.BalanceType.ESTIMATED));
        milestoneService.update();
        return toKey;
    }
    
    @Autowired
    private NetworkParameters networkParameters;
    
    @Test
    public void testSpringBootGetBalances() throws Exception {
        ECKey ecKey = new ECKey();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name()).content(ecKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + data);
    }
    
    @Test
    @SuppressWarnings("unused")
    public void testSpringBootAskTransaction() throws Exception {
        byte[] data = getAskTransactionBlock();
        
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        Block r1 = nextBlockSerializer(byteBuffer);
        Block r2 = nextBlockSerializer(byteBuffer);
    }
    
    @Test
    public void testSpringBootCreateGenesisBlock() throws Exception {
        ECKey outKey = new ECKey();
        byte[] pubKey = outKey.getPubKey();
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4 + pubKey.length);
        byteBuffer.putInt(100000000);
        byteBuffer.putInt(pubKey.length);
        byteBuffer.put(pubKey);
        
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.createGenesisBlock.name()).content(byteBuffer.array());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        byte[] data = mvcResult.getResponse().getContentAsByteArray();
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        
        logger.info("createGenesisBlock resp : " + block);
    }
    
    @Test
    public void testSpringBootSaveBlock() throws Exception {
        Block block = networkParameters.getGenesisBlock();
        reqCmdSaveBlock(block);
    }

    public Block nextBlockSerializer(ByteBuffer byteBuffer) {
        int len = byteBuffer.getInt();
        byte[] data = new byte[len];
        byteBuffer.get(data);
        Block r1 = networkParameters.getDefaultSerializer().makeBlock(data);
        System.out.println("block len : " + len + " conv : " + r1.getHashAsString());
        return r1;
    }

    public byte[] getAskTransactionBlock() throws JsonProcessingException, Exception {
        final Map<String, Object> request = new HashMap<String, Object>();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.askTransaction.name()).content(toJson(request));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        byte[] data = mvcResult.getResponse().getContentAsByteArray();
        return data;
    }


    public void reqCmdSaveBlock(Block block) throws Exception, UnsupportedEncodingException {
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.saveBlock.name()).content(block.bitcoinSerialize());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testSaveBlock resp : " + data);
    }
    
    
   
}
