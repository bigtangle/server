/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.bitcoinj.core.Coin.FIFTY_COINS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.jni.Local;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockStoreException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
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

   //TODO @Test
    public void testCreateTransaction() throws Exception {
        ECKey outKey = new ECKey();
        final Map<String, Object> reqParam0 = new HashMap<>();
        reqParam0.put("command", "askTransaction");
        reqParam0.put("pubkey", Utils.HEX.encode(outKey.getPubKey()));
        reqParam0.put("toaddressPubkey", Utils.HEX.encode(outKey.getPubKey()));
        reqParam0.put("amount", String.valueOf(100));
        reqParam0.put("tokenid", networkParameters.BIGNETCOIN_TOKENID);
        
        MockHttpServletRequestBuilder httpRequest0 = post(contextRoot).content(toJson(reqParam0));
        MvcResult result0 = getMockMvc().perform(httpRequest0).andExpect(status().isOk()).andReturn();
        String jsonString0 = result0.getResponse().getContentAsString();
        logger.debug("resp askTransaction result : " + jsonString0);
        
        JSONObject jsonObject = new JSONObject(jsonString0);
        String r1Hex = jsonObject.getString("r1");
        String r2Hex = jsonObject.getString("r2");
        logger.debug("r1Hex : " + r1Hex);
        logger.debug("r2Hex : " + r2Hex);

        Block r1 = (Block) networkParameters.getDefaultSerializer().makeBlock(Utils.HEX.decode(r1Hex));
        Block r2 = (Block) networkParameters.getDefaultSerializer().makeBlock(Utils.HEX.decode(r2Hex));
        int height = 1;
        Block block = r2.createNextBlock(null, Block.BLOCK_VERSION_GENESIS, (TransactionOutPoint) null,
                Utils.currentTimeSeconds(), outKey.getPubKey(), FIFTY_COINS, height, r1.getHash(), outKey.getPubKey());

        Transaction transaction = block.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(networkParameters, 0, transaction.getHash());

        Coin coin = Coin.parseCoin(String.valueOf(100), NetworkParameters.BIGNETCOIN_TOKENID);
        Transaction t = new Transaction(networkParameters);
        t.addOutput(new TransactionOutput(networkParameters, t, coin, outKey));
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        // 交易签名
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        block.addTransaction(t);
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
