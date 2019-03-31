/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.common.collect.ImmutableList;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.SendRequest;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransactionServiceTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(TransactionServiceTest.class);

    @Test
    public void testRewardVoting() throws Exception {
        store.resetStore();
        Block rollingBlock = networkParameters.getGenesisBlock();

        // Generate blocks until passing first reward interval
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }

        // Generate mining reward blocks
        Block rewardBlock = transactionService.performRewardVoting();

        // Assert that voting will not generate a new block since we have an
        // eligible alternative already
        assertEquals(rewardBlock, transactionService.performRewardVoting());

        // After updating, we shall make new blocks now
        rollingBlock = rewardBlock;
        for (int i = 0; i < NetworkParameters.REWARD_MIN_HEIGHT_INTERVAL
                + NetworkParameters.REWARD_MIN_HEIGHT_DIFFERENCE + 1; i++) {
            rollingBlock = rollingBlock.createNextBlock(rollingBlock);
            blockGraph.add(rollingBlock, true);
        }
        milestoneService.update();
        Block rewardBlock2 = transactionService.performRewardVoting();
        assertNotEquals(rewardBlock, rewardBlock2);
        assertNotNull(rewardBlock2);
    }

    // pay to mutilsigns keys wallet1Keys_part
    public void createMultiSigns(List<ECKey> wallet1Keys_part) throws Exception {

        Transaction multiSigTransaction = new Transaction(networkParameters);

        for (ECKey ecKey : wallet1Keys_part)
            log.debug(ecKey.getPublicKeyAsHex());

        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, wallet1Keys_part);

        Coin amount0 = Coin.parseCoin("0.15", NetworkParameters.BIGTANGLE_TOKENID);
        multiSigTransaction.addOutput(amount0, scriptPubKey);
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        SendRequest request = SendRequest.forTx(multiSigTransaction);
        walletAppKit.wallet().completeTx(request, null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());

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
        wallet1Keys_part.add(wallet1Keys.get(1));
        createMultiSigns(wallet1Keys_part);
        multiSigns(walletAppKit.wallet().currentReceiveKey(), wallet1Keys_part);
        multiSigns(walletAppKit2.wallet().currentReceiveKey(), wallet1Keys_part);
        multiSigns(walletAppKit1.wallet().currentReceiveKey(), wallet1Keys_part);
    }

    public void multiSigns(ECKey receiverkey, List<ECKey> wallet1Keys_part) throws Exception {

        List<UTXO> ulist = getBalance(false, wallet1Keys_part);

        TransactionOutput multisigOutput = new FreeStandingTransactionOutput(this.networkParameters, ulist.get(0), 0);
        Script multisigScript1 = multisigOutput.getScriptPubKey();

        Coin amount1 = Coin.parseCoin("0.03", NetworkParameters.BIGTANGLE_TOKENID);

        Coin amount2 = multisigOutput.getValue().subtract(amount1);

        Transaction transaction0 = new Transaction(networkParameters);
        transaction0.addOutput(amount1, receiverkey);
        // add remainder back
        Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(2, wallet1Keys_part);
        transaction0.addOutput(amount2, scriptPubKey);

        transaction0.addInput(multisigOutput);

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
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
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
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);

        Coin amount = Coin.parseCoin("0.01", NetworkParameters.BIGTANGLE_TOKENID);
        SendRequest request = SendRequest.to(walletKeys.get(1).toAddress(networkParameters), amount);
        walletAppKit.wallet().completeTx(request, null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
    }
}