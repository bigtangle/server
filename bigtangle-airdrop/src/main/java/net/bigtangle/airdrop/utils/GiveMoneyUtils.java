package net.bigtangle.airdrop.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import net.bigtangle.airdrop.config.ServerConfiguration;
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

@Component
public class GiveMoneyUtils {

    @Autowired
    private NetworkParameters networkParameters;

    @Autowired
    private ServerConfiguration serverConfiguration;

    public void batchGiveMoneyToECKeyList(List<ECKey> ecKeys) throws Exception {
        final Map<String, Object> requestParam = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(serverConfiguration.getServerURL() + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);

        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        List<UTXO> outputs = getTransactionAndGetBalances(genesiskey);

        Coin coinbase = Coin.valueOf(9999L, NetworkParameters.BIGNETCOIN_TOKENID);

        Transaction doublespent = new Transaction(networkParameters);
        for (ECKey ecKey : ecKeys) {
            doublespent.addOutput(new TransactionOutput(networkParameters, doublespent, coinbase, ecKey));
        }

        UTXO output_ = null;
        for (UTXO output : outputs) {
            if (Arrays.equals(coinbase.getTokenid(), output.getValue().getTokenid())) {
                output_ = output;
            }
        }

        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(networkParameters, output_, 0);
        Coin amount2 = spendableOutput.getValue().subtract(coinbase);
        doublespent.addOutput(amount2, genesiskey);
        TransactionInput input = doublespent.addInput(spendableOutput);
        Sha256Hash sighash = doublespent.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);
        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        block.addTransaction(doublespent);
        block.solve();
        try {
            OkHttp3Util.post(serverConfiguration.getServerURL() + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<UTXO> getTransactionAndGetBalances(ECKey ecKey) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();
        keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        String response = OkHttp3Util.post(serverConfiguration.getServerURL() + ReqCmd.getBalances.name(),
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
        if (giveMoneyResult.isEmpty()) {
            return;
        }
        
        final Map<String, Object> requestParam = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(serverConfiguration.getServerURL() + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);

        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        List<UTXO> outputs = getTransactionAndGetBalances(genesiskey);

        Coin coinbase000 = Coin.ZERO;
        Transaction doublespent = new Transaction(networkParameters);
        for (Map.Entry<String, Integer> entry : giveMoneyResult.entrySet()) {
            ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(entry.getKey()));
            Coin coinbase = Coin.valueOf(100 * entry.getValue(), NetworkParameters.BIGNETCOIN_TOKENID);
            doublespent.addOutput(new TransactionOutput(networkParameters, doublespent, coinbase, ecKey));
            coinbase000 = coinbase000.add(coinbase);
        }

        UTXO output_ = null;
        for (UTXO output : outputs) {
            if (Arrays.equals(NetworkParameters.BIGNETCOIN_TOKENID, output.getValue().getTokenid())) {
                output_ = output;
            }
        }

        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(networkParameters, output_, 0);
        Coin amount2 = spendableOutput.getValue().subtract(coinbase000);
        doublespent.addOutput(amount2, genesiskey);
        TransactionInput input = doublespent.addInput(spendableOutput);
        Sha256Hash sighash = doublespent.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);
        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        block.addTransaction(doublespent);
        block.solve();
        try {
            OkHttp3Util.post(serverConfiguration.getServerURL() + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
