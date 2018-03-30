/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockEvaluation;
import org.bitcoinj.core.BlockForTest;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bignetcoin.server.service.BlockService;
import com.bignetcoin.server.service.MilestoneService;
import com.bignetcoin.server.service.WalletService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransactionServiceTest extends AbstractIntegrationTest {
    
    @Test
    public void getaaaBalance() throws Exception {
        ECKey outKey = new ECKey();
        int height = 1;
        
        blockgraph.add(PARAMS.getGenesisBlock());
        BlockEvaluation genesisEvaluation = blockService.getBlockEvaluation(PARAMS.getGenesisBlock().getHash());
        blockService.updateMilestone(genesisEvaluation, true);
        blockService.updateSolid(genesisEvaluation, true);

        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        
        //get the coinbase to spend
        Transaction transaction = rollingBlock.getTransactions().get(0);
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());

        milestoneService.update();
        {
            MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name()).content(outKey.getPubKeyHash());
            MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
            String response = mvcResult.getResponse().getContentAsString();
            logger.info("outKey > testGetBalances resp : " + response);
        }
        
        ECKey toKey0 = new ECKey();
        ECKey toKey1 = new ECKey();
        Coin amount0 = Coin.valueOf(100, NetworkParameters.BIGNETCOIN_TOKENID);
        Coin amount1 = Coin.valueOf(10000, NetworkParameters.BIGNETCOIN_TOKENID);

        TransactionOutPoint spendableOutput0 = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        TransactionOutPoint spendableOutput1 = new TransactionOutPoint(PARAMS, 1, transaction.getHash());
        
//        ImmutableList<ECKey> keys = ImmutableList.of(outKey, toKey);
//        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, keys);
        
        Transaction t = new Transaction(PARAMS);
        t.addOutput(amount0, toKey0);
      //  t.addOutput(amount1, toKey1);
        
        t.addSignedInput(spendableOutput0, new Script(spendableOutputScriptPubKey), outKey);
        
    //    t.addSignedInput(spendableOutput1, new Script(spendableOutputScriptPubKey), outKey);
        
//        t.addOutput(new TransactionOutput(PARAMS, t, amount1, scriptPubKey.getProgram()));
//        t.addSignedInput(spendableOutput1, scriptPubKey, toKey);
        
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        
        blockgraph.add(rollingBlock);
        milestoneService.update();
        
        {
            MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name()).content(toKey0.getPubKeyHash());
            MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
            String response = mvcResult.getResponse().getContentAsString();
            logger.info("toKey > testGetBalances resp : " + response);
        }
        {
            MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name()).content(toKey1.getPubKeyHash());
            MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
            String response = mvcResult.getResponse().getContentAsString();
            logger.info("outKey > testGetBalances resp : " + response);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(TransactionServiceTest.class);

    @Autowired
    private WalletService walletService;

    @Autowired
    private MilestoneService milestoneService;

    @Autowired
    private BlockService blockService;

    @Test
    public void getBalance() throws Exception {
        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;
        logger.debug(outKey.getPublicKeyAsHex());

        // Add genesis block
        blockgraph.add(PARAMS.getGenesisBlock());
        BlockEvaluation genesisEvaluation = blockService.getBlockEvaluation(PARAMS.getGenesisBlock().getHash());
        blockService.updateMilestone(genesisEvaluation, true);
        blockService.updateSolid(genesisEvaluation, true);

        // Build some blocks on genesis block to create a spendable output
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        rollingBlock = BlockForTest.createNextBlock(rollingBlock, null, PARAMS.getGenesisBlock().getHash());

        // Create bitcoin spend of 1 BTC.
        ECKey toKey = new ECKey();
        Coin amount = Coin.valueOf(11123, NetworkParameters.BIGNETCOIN_TOKENID);
        Address address = new Address(PARAMS, toKey.getPubKeyHash());
        Coin totalAmount = Coin.ZERO;

        Transaction t = new Transaction(PARAMS);
        t.addOutput(new TransactionOutput(PARAMS, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);
        totalAmount = totalAmount.add(amount);

        milestoneService.update(); //ADDED
        List<UTXO> outputs = store.getOpenTransactionOutputs(Lists.newArrayList(address));
        assertNotNull(outputs);
        assertEquals("Wrong Number of Outputs", 1, outputs.size());
        UTXO output = outputs.get(0);
        assertEquals("The address is not equal", address.toString(), output.getAddress());
        assertEquals("The amount is not equal", totalAmount.getValue(), output.getValue().getValue());
        outputs = null; 
  
        try {
            store.close();
        } catch (Exception e) {
        }
    }

    @Test
    public void testUTXOProviderWithWallet() throws Exception {
        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;

        // Build some blocks on genesis block to create a spendable output.
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(PARAMS.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < PARAMS.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, PARAMS.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        rollingBlock = BlockForTest.createNextBlock(rollingBlock, null, PARAMS.getGenesisBlock().getHash());
    }

}