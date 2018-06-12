/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
import net.bigtangle.core.Transaction.SigHash;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.server.service.BlockService;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
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
    // transfer the coin to address with multisign for spent
    public void testMultiSigOutputToString() throws Exception {

        Transaction multiSigTransaction = new Transaction(networkParameters);

        List<ECKey> wallet1Keys_ = new ArrayList<ECKey>();
        wallet1Keys_.add(wallet1Keys.get(0));
        wallet1Keys_.add(wallet1Keys.get(1));
        
        for (ECKey ecKey : wallet1Keys_) System.out.println(ecKey.getPublicKeyAsHex());
        
        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, wallet1Keys_);

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
       checkBalance(amount0, wallet1Keys_);
        
        //find the transaction as input
      
       ECKey receiverkey = walletAppKit.wallet().currentReceiveKey();
       List<UTXO> ulist0 = testTransactionAndGetBalances(true, receiverkey);
       List<UTXO> ulist = testTransactionAndGetBalances(false, wallet1Keys_);
       
       TransactionOutput multisigOutput = new FreeStandingTransactionOutput(this.networkParameters, ulist.get(0), 0);
        Script multisigScript1 = multisigOutput.getScriptPubKey();
       
        Coin amount1 = Coin.parseCoin("0.05", NetworkParameters.BIGNETCOIN_TOKENID);
        
        Coin amount2 = multisigOutput.getValue().subtract(amount1);
        
        Transaction transaction0 = new Transaction(networkParameters);
        transaction0.addOutput(amount1, receiverkey);
        //add remainder back
        transaction0.addOutput(amount2, scriptPubKey);
        TransactionInput input = transaction0.addInput(multisigOutput);
        
        Transaction transaction_ = networkParameters.getDefaultSerializer().makeTransaction(transaction0.bitcoinSerialize());
        transaction0 = transaction_;
        TransactionInput input2 = transaction0.getInput(0);
        
        Sha256Hash sighash = transaction0.hashForSignature(0, multisigScript1, Transaction.SigHash.ALL, false);
        TransactionSignature tsrecsig = new TransactionSignature(wallet1Keys.get(0).sign(sighash), Transaction.SigHash.ALL, false);
        TransactionSignature tsintsig = new TransactionSignature(wallet1Keys.get(1).sign(sighash), Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(tsrecsig, tsintsig));
        input2.setScriptSig(inputScript);

//        TransactionOutput transactionOutput = new FreeStandingTransactionOutput(this.networkParameters, ulist.get(0), 0);
       
        
    //    Transaction transaction1 = new Transaction(networkParameters);
    //    transaction1.addOutput(amount2, scriptPubKey);
     //   transaction1.addInput(candidates.get(1));
     //   transaction1.addInput(multisigOutput);
     //   SendRequest req = SendRequest.forTx(transaction1);
     //   walletAppKit.wallet().signTransaction(req);
        
//        Transaction multiSigTransaction_ = new Transaction(networkParameters);
//        Script scriptPubKey_ = ScriptBuilder.createMultiSigOutputScript(2, wallet1Keys_);
//        multiSigTransaction_.addOutput(amount2, scriptPubKey_);
//        request = SendRequest.forTx(multiSigTransaction_);
//        walletAppKit.wallet().completeTx(request);
        
        data = OkHttp3Util.post(contextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(transaction0);
     //   rollingBlock.addTransaction(transaction1);
//        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

       checkResponse( OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize()));
       
       List<TransactionOutput> candidates = walletAppKit.wallet().calculateAllSpendCandidates(true);
       
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