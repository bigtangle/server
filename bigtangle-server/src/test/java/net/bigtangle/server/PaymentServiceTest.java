/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.collect.ImmutableList;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.core.response.GetTXRewardListResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.SendRequest;
import static org.junit.Assert.fail;
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PaymentServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(PaymentServiceTest.class);

    @Test
    public void testAllTXReward() throws Exception {

        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        String response = OkHttp3Util.postString(contextRoot + ReqCmd.getAllConfirmedReward.name(),
                Json.jsonmapper().writeValueAsString(requestParam));

        GetTXRewardListResponse getBalancesResponse = Json.jsonmapper().readValue(response,
                GetTXRewardListResponse.class);
        assertTrue(getBalancesResponse.getTxReward().size() > 0);
    }

    // pay to mutilsigns keys wallet1Keys_part
    public void createMultiSigns(List<ECKey> wallet1Keys_part) throws Exception {

        Transaction multiSigTransaction = new Transaction(networkParameters);

        for (ECKey ecKey : wallet1Keys_part)
            log.debug(ecKey.getPublicKeyAsHex());

        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, wallet1Keys_part);

        Coin amount0 = Coin.valueOf(15, NetworkParameters.BIGTANGLE_TOKENID);
        multiSigTransaction.addOutput(amount0, scriptPubKey);
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        SendRequest request = SendRequest.forTx(multiSigTransaction);
        walletAppKit.wallet().completeTx(request, null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock= adjustSolve(rollingBlock);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        mcmcService.update();
        confirmationService.update(store);
        checkBalance(amount0, wallet1Keys_part);
    }

    @Test
    // transfer the coin to address with multisign for spent
    public void testMultiSigns() throws Exception {
        testInitWallet();
        wallet1();
        wallet2();

        List<ECKey> wallet1Keys_part = new ArrayList<ECKey>();
        wallet1Keys_part.add(wallet1Keys.get(0));
        wallet1Keys_part.add(new ECKey());
        createMultiSigns(wallet1Keys_part);

    }

    public void multiSigns(ECKey receiverkey, List<ECKey> wallet1Keys_part) throws Exception {

        List<UTXO> ulist = getBalance(false, wallet1Keys_part);

        TransactionOutput multisigOutput = new FreeStandingTransactionOutput(this.networkParameters, ulist.get(0));
        Script multisigScript1 = multisigOutput.getScriptPubKey();

        Coin amount1 = Coin.valueOf(3, NetworkParameters.BIGTANGLE_TOKENID);

        Coin amount2 = multisigOutput.getValue().subtract(amount1);

        Transaction transaction0 = new Transaction(networkParameters);
        transaction0.addOutput(amount1, receiverkey);
        // add remainder back
        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, wallet1Keys_part);
        transaction0.addOutput(amount2, scriptPubKey);

        transaction0.addInput(ulist.get(0).getBlockHash(), multisigOutput);

        Transaction transaction_ = networkParameters.getDefaultSerializer()
                .makeTransaction(transaction0.bitcoinSerialize());
        transaction0 = transaction_;
        TransactionInput input2 = transaction0.getInput(0);

        Sha256Hash sighash = transaction0.hashForSignature(0, multisigScript1, Transaction.SigHash.ALL, false);
        TransactionSignature tsrecsig = new TransactionSignature(wallet1Keys_part.get(0).sign(sighash),
                Transaction.SigHash.ALL, false);
        TransactionSignature tsintsig = new TransactionSignature(wallet1Keys_part.get(1).sign(sighash),
                Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createMultiSigInputScript(ImmutableList.of(tsrecsig, tsintsig));
        input2.setScriptSig(inputScript);

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(transaction0);

        rollingBlock.solve();

        checkResponse(OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize()));

        checkBalance(amount1, receiverkey);
    }

    @Test
    // transfer the coin to address
    public void testTransferWallet() throws Exception {
        testInitWallet();
        wallet1();
        wallet2();

        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
        Address address = walletKeys.get(0).toAddress(networkParameters);
        SendRequest request = SendRequest.to(address, amount);
       // request.tx.setMemo(new MemoInfo(createDataSize(5000)));
        walletAppKit.wallet().completeTx(request, null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        sendEmpty(5);
        mcmcService.update();
        confirmationService.update(store);
        //check the output histoty
        historyUTXOList(address.toBase58(),amount);
    }

    @Test
    // transfer the coin to address
    public void testPossibleConflict() throws Exception {
        testInitWallet();
        wallet1();
        wallet2();

        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        Coin amount = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
        Address address = walletKeys.get(0).toAddress(networkParameters);
        SendRequest request = SendRequest.to(address, amount);
       // request.tx.setMemo(new MemoInfo(createDataSize(5000)));
        walletAppKit.wallet().completeTx(request, null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        sendEmpty(5);
        mcmcService.update();
        confirmationService.update(store);
        //check the output histoty
        historyUTXOList(address.toBase58(),amount);
        //retry the block throw possible conflict
        try {
        walletAppKit.wallet().retryBlocks(rollingBlock);
        fail();
        }catch (RuntimeException e) {
            
        }
        
    }

    public void historyUTXOList(String addressString, Coin amount) throws Exception {
        Map<String, String> param = new HashMap<String, String>();
       
        param.put("toaddress", addressString);
        
        String response = OkHttp3Util.postString(contextRoot + ReqCmd.getOutputsHistory.name(),
                Json.jsonmapper().writeValueAsString(param));
        
       
        GetBalancesResponse balancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        Map<String, Token> tokennames = balancesResponse.getTokennames();
        int h=0;
        int my=0;
        for (UTXO utxo : balancesResponse.getOutputs()) {
            if(utxo.isSpent()) {h++;}
            if(amount.compareTo(utxo.getValue())==0) my=1;
   
        }
      //  assertTrue(h>0);
        assertTrue(my==1);
    }
    @Test
    // coins in wallet to one coin to address
    public void testPartsToOne() throws Exception {

        ECKey to =  new ECKey();
        walletAppKit1.wallet().importKey(to);
        Coin aCoin = Coin.valueOf(1, NetworkParameters.BIGTANGLE_TOKENID);
        testPartsToOne(aCoin, to);
        checkBalance(aCoin, to);

        testPartsToOne(aCoin, to);
        testPartsToOne(aCoin, to);
        List<TransactionOutput> uspent = walletAppKit1.wallet().calculateAllSpendCandidates(null, false);
        assertTrue(uspent.size() == 3);
        walletAppKit1.wallet().payPartsToOne(null, to.toAddress(networkParameters), NetworkParameters.BIGTANGLE_TOKENID,
                "0,3");
        mcmcService.update();
        confirmationService.update(store);
        ArrayList<ECKey> a = new ArrayList<ECKey>();
        a.add(to);
        List<UTXO> ulist = getBalance(false, a);
        assertTrue(ulist.size() == 1);

    }

    public void testPartsToOne(Coin amount, ECKey to) throws Exception {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.postAndGetBlock(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        SendRequest request = SendRequest.to(to.toAddress(networkParameters), amount);

        walletAppKit.wallet().completeTx(request, null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

        mcmcService.update();
        confirmationService.update(store);
    }

    private static String createDataSize(int msgSize) {
        StringBuilder sb = new StringBuilder(msgSize);
        for (int i = 0; i < msgSize; i++) {
            sb.append('a');
        }
        return sb.toString();
    }
}