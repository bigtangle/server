/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockForTest;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class APIIntegrationTests extends AbstractIntegrationTest {

    private int height = 1;
    
//    @Before
    public Block getRollingBlock(ECKey outKey) throws Exception {
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++,PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        return rollingBlock;
    }

    @Test
    public void testCreateTransaction() throws Exception {
        byte[] data = getAskTransactionBlock();
        
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
    
    @Test
    public void testGetBalances() throws Exception {
        ECKey outKey = new ECKey();
        Block rollingBlock = this.getRollingBlock(outKey);
        rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock,Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
                
        Wallet wallet = new Wallet(PARAMS);
        assertEquals("Available balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.AVAILABLE));
        assertEquals("Estimated balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.ESTIMATED));

        wallet.setUTXOProvider(store);
        ECKey toKey = wallet.freshReceiveKey();
        Coin amount = Coin.valueOf(1000, NetworkParameters.BIGNETCOIN_TOKENID);

        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        
        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);
        
        assertEquals("Available balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.AVAILABLE));
        assertEquals("Estimated balance is incorrect", Coin.ZERO, wallet.getBalance(Wallet.BalanceType.ESTIMATED));
        
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name()).content(toKey.getPubKey());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + data);
    }
    
    @Autowired
    private NetworkParameters networkParameters;
    
    @Test
    @SuppressWarnings("unused")
    public void testReqAskTransaction() throws Exception {
        byte[] data = getAskTransactionBlock();
        
        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        Block r1 = nextBlockSerializer(byteBuffer);
        Block r2 = nextBlockSerializer(byteBuffer);
    }

    public Block nextBlockSerializer(ByteBuffer byteBuffer) {
        byte[] data = new byte[byteBuffer.getInt()];
        byteBuffer.get(data);
        Block r1 = (Block) networkParameters.getDefaultSerializer().makeBlock(data);
        return r1;
    }

    public byte[] getAskTransactionBlock() throws JsonProcessingException, Exception {
        final Map<String, Object> request = new HashMap<String, Object>();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.askTransaction.name()).content(toJson(request));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        byte[] data = mvcResult.getResponse().getContentAsByteArray();
        return data;
    }
    
    @Test
    public void testReqSaveBlock() throws Exception {
        Block block = networkParameters.getGenesisBlock();
        reqCmdSaveBlock(block);
    }

    public void reqCmdSaveBlock(Block block) throws Exception, UnsupportedEncodingException {
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.saveBlock.name()).content(block.bitcoinSerialize());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testSaveBlock resp : " + data);
    }
}
