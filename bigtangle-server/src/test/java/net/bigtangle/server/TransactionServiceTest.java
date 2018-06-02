/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockForTest;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransactionServiceTest extends AbstractIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(TransactionServiceTest.class);

    @Autowired
    private NetworkParameters networkParameters;

    @Autowired
    private MilestoneService milestoneService;

    @Autowired
    private BlockService blockService;

    @Test
    public void getaaaBalance() throws Exception {
        ECKey outKey = new ECKey();
        int height = 1;

        BlockEvaluation genesisEvaluation = blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash());
        store.updateBlockEvaluationMilestone(genesisEvaluation.getBlockhash(), true);
        blockService.updateSolid(genesisEvaluation, true);

        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(networkParameters.getGenesisBlock(),
                Block.BLOCK_VERSION_GENESIS, outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
        blockgraph.add(rollingBlock);

        // get the coinbase to spend
        Transaction transaction = rollingBlock.getTransactions().get(0);
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        for (int i = 1; i < networkParameters.getSpendableCoinbaseDepth(); i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
            blockgraph.add(rollingBlock);
        }
        rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());

        milestoneService.update();
        {
            MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name())
                    .content(outKey.getPubKeyHash());
            MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk())
                    .andReturn();
            String response = mvcResult.getResponse().getContentAsString();
            logger.info("outKey > testGetBalances resp : " + response);
        }

        ECKey toKey = new ECKey();
        Coin amount0 = Coin.valueOf(3, NetworkParameters.BIGNETCOIN_TOKENID);
        Coin amount1 = Coin.valueOf(2, NetworkParameters.BIGNETCOIN_TOKENID);

        TransactionOutPoint spendableOutput0 = new TransactionOutPoint(networkParameters, 0, transaction.getHash());
        TransactionOutPoint spendableOutput1 = new TransactionOutPoint(networkParameters, 1, transaction.getHash());

        // ImmutableList<ECKey> keys = ImmutableList.of(outKey, toKey);
        // Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2,
        // keys);

        Transaction t = new Transaction(networkParameters);
        t.addOutput(amount1, toKey);
        t.addSignedInput(spendableOutput0, new Script(spendableOutputScriptPubKey), outKey);

        // t.addOutput(amount0, outKey);
        // t.addSignedInput(spendableOutput1, new
        // Script(spendableOutputScriptPubKey), toKey);

        // t.addOutput(new TransactionOutput(networkParameters, t, amount1,
        // scriptPubKey.getProgram()));
        // t.addSignedInput(spendableOutput1, scriptPubKey, toKey);

        rollingBlock.addTransaction(t);
        rollingBlock.solve();

        blockgraph.add(rollingBlock);
        milestoneService.update();

        rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                outKey.getPubKey(), height++, networkParameters.getGenesisBlock().getHash());
        Transaction t0 = new Transaction(networkParameters);
        t0.addOutput(amount0, outKey);
        t0.addSignedInput(spendableOutput1, new Script(spendableOutputScriptPubKey), toKey);
        rollingBlock.addTransaction(t0);
        rollingBlock.solve();
        try {
            blockgraph.add(rollingBlock);
        } catch (Exception e) {
            // TODO: handle exception
        }
        milestoneService.update();

        {
            MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name())
                    .content(toKey.getPubKeyHash());
            MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk())
                    .andReturn();
            String response = mvcResult.getResponse().getContentAsString();
            logger.info("toKey > testGetBalances resp : " + response);
        }
        {
            MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getBalances.name())
                    .content(outKey.getPubKeyHash());
            MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk())
                    .andReturn();
            String response = mvcResult.getResponse().getContentAsString();
            logger.info("outKey > testGetBalances resp : " + response);
        }
    }

    @Test
    public void getBalance() throws Exception {
        // Check that we aren't accidentally leaving any references
        // to the full StoredUndoableBlock's lying around (ie memory leaks)
        ECKey outKey = new ECKey();
        int height = 1;
        logger.debug(outKey.getPublicKeyAsHex());

        
        BlockEvaluation genesisEvaluation = blockService.getBlockEvaluation(networkParameters.getGenesisBlock().getHash());
        store.updateBlockEvaluationMilestone(genesisEvaluation.getBlockhash(), true);
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
        rollingBlock = BlockForTest.createNextBlock(rollingBlock, null, networkParameters.getGenesisBlock().getHash());

        // Create bitcoin spend of 1 BTA.
        ECKey toKey = new ECKey();
        Coin amount = Coin.valueOf(11123, NetworkParameters.BIGNETCOIN_TOKENID);
        Address address = new Address(networkParameters, toKey.getPubKeyHash());
        Coin totalAmount = Coin.ZERO;

        Transaction t = new Transaction(networkParameters);
        t.addOutput(new TransactionOutput(networkParameters, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        blockgraph.add(rollingBlock);
        totalAmount = totalAmount.add(amount);

        milestoneService.update(); // ADDED
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
        rollingBlock = BlockForTest.createNextBlock(rollingBlock, null, networkParameters.getGenesisBlock().getHash());
    }

    @Test
    // transfer the coin to address with multisign for spent
    public void testMultiSigOutputToString() throws Exception {

        Transaction multiSigTransaction = new Transaction(networkParameters);

        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, wallet1Keys);

        Coin amount0 = Coin.parseCoin("0.15", NetworkParameters.BIGNETCOIN_TOKENID);
        multiSigTransaction.addOutput(amount0, scriptPubKey);
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        SendRequest request = SendRequest.forTx(multiSigTransaction);
        walletAppKit.wallet().completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        //tis is failed, becuase the address is empty for createMultiSigOutputScript
        //TODO add new table for UTXO as outputsmulti _> 
        //UTXO write 
        //join and make this correct
       // checkBalance(NetworkParameters.BIGNETCOIN_TOKENID_STRING, wallet1Keys);
        
        //find the transaction as input
      
        TransactionOutput multisigOutput = request.tx.getOutput(1);
        TransactionOutput o = request.tx.getOutput(0);
        
        Script multisigScript = o.getScriptPubKey();
        Script multisigScript1 = multisigOutput.getScriptPubKey();
       
        
       if( multisigScript.isSentToMultiSig()) multisigOutput=o;
 

        Coin amount = multisigOutput.getValue();
        
        
        ECKey receiverkey = walletKeys.get(1);
        
        Transaction spendtx = new Transaction(networkParameters);
        spendtx.addOutput(amount, receiverkey);
       
        TransactionInput input = spendtx.addInput(multisigOutput);
        
        Sha256Hash sighash = spendtx.hashForSignature(0, multisigScript, Transaction.SigHash.ALL, false);
       // split steps for sign and use table to sign
        TransactionSignature tsrecsig = new TransactionSignature(wallet1Keys.get(0).sign(sighash), Transaction.SigHash.ALL, false);
        TransactionSignature tsintsig = new TransactionSignature(wallet1Keys.get(1).sign(sighash), Transaction.SigHash.ALL, false);
        
        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(tsrecsig, tsintsig));
        
        input.setScriptSig(inputScript);
      
         data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
         rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

 
        rollingBlock.addTransaction(spendtx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());

        //TODO remainder
    }

    @Test
    // transfer the coin to address 
    public void testTransferWallet() throws Exception {

        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        Coin amount = Coin.parseCoin("0.001", NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(walletKeys.get(1).toAddress(networkParameters), amount);
        walletAppKit.wallet().completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
    }
}