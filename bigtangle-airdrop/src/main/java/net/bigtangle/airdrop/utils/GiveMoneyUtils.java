package net.bigtangle.airdrop.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bigtangle.airdrop.config.ServerConfiguration;
import net.bigtangle.core.Address;
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
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;
import net.bigtangle.wallet.Wallet;

@Component
public class GiveMoneyUtils {

    @PostConstruct
    @SuppressWarnings("deprecation")
    public void init() {
        String contextRoot = serverConfiguration.getServerURL();
        coinbaseWallet = new Wallet(networkParameters, contextRoot);
        coinbaseWallet.importKey(
                new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub)));
        coinbaseWallet.setServerURL(contextRoot);
    }

    private Wallet coinbaseWallet;

    @Autowired
    private NetworkParameters networkParameters;

    @Autowired
    private ServerConfiguration serverConfiguration;
    
    private List<UTXO> getTransactionAndGetBalances(ECKey ecKey) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();
        keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        String contextRoot = serverConfiguration.getServerURL();
        
        String response = OkHttp3Util.post(contextRoot + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }
        return listUTXO;
    }

    public void batchGiveMoneyToECKeyList(HashMap<String, Integer> giveMoneyResult) throws Exception {
        String contextRoot = serverConfiguration.getServerURL();
        if (giveMoneyResult.isEmpty()) {
            return;
        }
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));

        Coin coinbase = Coin.ZERO;
        Transaction doublespent = new Transaction(networkParameters);

        for (Map.Entry<String, Integer> entry : giveMoneyResult.entrySet()) {
            Coin amount = Coin.valueOf(entry.getValue() * 1000, NetworkParameters.BIGTANGLE_TOKENID);
            Address address = Address.fromBase58(networkParameters, entry.getKey());
            doublespent.addOutput(amount, address);
            coinbase = coinbase.add(amount);
        }

        UTXO findOutput = null;
        for (UTXO output : getTransactionAndGetBalances(genesiskey)) {
            if (Arrays.equals(coinbase.getTokenid(), output.getValue().getTokenid())) {
                findOutput = output;
            }
        }

        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(networkParameters, findOutput, 0);
        Coin amount = spendableOutput.getValue().subtract(coinbase);

        doublespent.addOutput(amount, genesiskey);
        TransactionInput input = doublespent.addInput(spendableOutput);
        Sha256Hash sighash = doublespent.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip,
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(doublespent);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        
        for (TransactionOutput transactionOutput : doublespent.getOutputs()) {
            LOGGER.info("give mount output value : " + transactionOutput.getValue());
        }
    }
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GiveMoneyUtils.class);
}
