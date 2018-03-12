package com.bignetcoin.server;

import static org.bitcoinj.core.Coin.FIFTY_COINS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.jni.Local;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.store.BlockStoreException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import ch.qos.logback.classic.pattern.Util;

import com.bignetcoin.server.service.TransactionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bignetcoin.server.service.BlockService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(TipsServiceTest.class);
    
    @Autowired
    private BlockService blockService;
    
    @Autowired
    private TransactionService transactionService;
    
    @Test
    public void testBlockSerializer000() {
        ECKey outKey = new ECKey();
        Coin coin = Coin.parseCoin("100", -1);
        
        Block r1 = networkParameters.getGenesisBlock();
        Block r2 = networkParameters.getGenesisBlock();
        Block rollingBlock = 
                r2.createNextBlock(null, Block.BLOCK_VERSION_GENESIS, (TransactionOutPoint) null, Utils.currentTimeSeconds(), outKey.getPubKey(),
                        FIFTY_COINS, 1, r1.getHash(),  outKey.getPubKey() 
                        );
        
//        Transaction transaction = rollingBlock.getTransactions().get(0);
//        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
//       
//        Transaction t = new Transaction(networkParameters);
//        t.addOutput(new TransactionOutput(networkParameters, t, coin, outKey));
//        TransactionInput input = new TransactionInput(networkParameters, t, new byte[] {}, spendableOutput);
//
//        t.addInput(input);
//        rollingBlock.addTransaction(t);
        
        String hex = Utils.HEX.encode(rollingBlock.bitcoinSerialize());
        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(Utils.HEX.decode(hex));
    }
    
    @Test
    public void testBlockSerializer() throws BlockStoreException, Exception {
        ECKey outKey = new ECKey();
        String amount = "100";
        
        Coin coin = Coin.parseCoin(amount, -1);
        
        Block rollingBlock = networkParameters.getGenesisBlock();
               
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
       
        Transaction t = new Transaction(networkParameters);
        t.addOutput(new TransactionOutput(networkParameters, t, coin, outKey));
        TransactionInput input = new TransactionInput(networkParameters, t, new byte[] {}, spendableOutput);

        t.addInput(input);
        rollingBlock.addTransaction(t);
        
        System.out.println(Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
        
//        byte[] bytes = rollingBlock.bitcoinSerialize();
        byte[] bytes = Utils.HEX.decode(Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
        // 交易签名
        for (Transaction t0 : block.getTransactions()) {
            t0.addSigned(outKey);
        }
        // 解谜
        block.solve();
    }
    
    @Autowired
    private NetworkParameters networkParameters;

    @Test
    public void testCreateTransaction() throws Exception {
        ECKey outKey = new ECKey();
        final Map<String, Object> reqParam0 = new HashMap<>();
        reqParam0.put("command", "askTransaction");
        reqParam0.put("pubkey", Utils.HEX.encode(outKey.getPubKey()));
        reqParam0.put("toaddressPubkey", Utils.HEX.encode(outKey.getPubKey()));
        reqParam0.put("amount", String.valueOf(100));
        reqParam0.put("tokenid", -1);
        
        MockHttpServletRequestBuilder httpRequest0 = post(contextRoot).content(toJson(reqParam0));
        MvcResult result0 = getMockMvc().perform(httpRequest0).andExpect(status().isOk()).andReturn();
        String jsonString0 = result0.getResponse().getContentAsString();
        logger.debug("接收askTransaction数据 : " + jsonString0);
        
        JSONObject jsonObject = new JSONObject(jsonString0);
        String blockHex = jsonObject.getString("blockHex");
        logger.debug("blockHex : " + blockHex);
        
        blockHex = "010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000050ce07c376a79786c69e8faddc238fcd973d0ea3fb5b07442b16769e4bf26dab29ab5f4902000000000000000000000000000000000000000000000001000000010000000201000000010000000000000000000000000000000000000000000000000000000000000000ffffffff4d04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73ffffffff0100f2052a010000000100000000000000434104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac00000000010000000145c321ad6cc4d96d37ac8d81f085b670c308b179630e9650254eb74233b1b6b80000000000ffffffff0100e40b5402000000ffffffffffffffff232102d05d62ee5ba407f2a7a4f38e19bec17966e24186be61ab271c3c387874c3f82cac00000000";
        byte[] bytes = Utils.HEX.decode(blockHex);
//        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
        
//        byte[] bytes = rollingBlock.bitcoinSerialize();
        Block block = (Block) networkParameters.getDefaultSerializer().makeBlock(bytes);
        
        // 交易签名
        for (Transaction t : block.getTransactions()) {
            t.addSigned(outKey);
        }
        // 解谜
        block.solve();
        
        final Map<String, Object> reqParam1 = new HashMap<>();
        reqParam1.put("command", "saveBlock");
        reqParam1.put("blockString", Utils.HEX.encode(block.bitcoinSerialize()));
        MockHttpServletRequestBuilder httpRequest1 = post(contextRoot).content(toJson(reqParam1));
        MvcResult result1 = getMockMvc().perform(httpRequest1).andExpect(status().isOk()).andReturn();
        String jsonString1 = result1.getResponse().getContentAsString();
        logger.debug("接收saveBlock数据 : " + jsonString1);
    }
}
