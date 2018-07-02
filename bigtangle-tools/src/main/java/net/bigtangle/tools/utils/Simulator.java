package net.bigtangle.tools.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TokenInfo;
import net.bigtangle.core.TokenSerial;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

public class Simulator {

    public static void give(ECKey ecKey) throws Exception {
        //Thread.sleep(20000);

        Block block = getAskTransactionBlock();

        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPiv),
                Utils.HEX.decode(NetworkParameters.testPub));
        List<UTXO> outputs = getTransactionAndGetBalances(genesiskey);

        System.out.println(outputs.size());

        Coin coinbase = Coin.valueOf(9999L, NetworkParameters.BIGNETCOIN_TOKENID);

        Transaction doublespent = new Transaction(Configure.PARAMS);
        doublespent.addOutput(new TransactionOutput(Configure.PARAMS, doublespent, coinbase, ecKey));
        
        UTXO output_ = null;
        for (UTXO output : outputs) {
            if (Arrays.equals(coinbase.getTokenid(), output.getValue().getTokenid())) {
                output_ = output;
            }
        }

        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(Configure.PARAMS, output_, 0);
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
            OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<UTXO> getTransactionAndGetBalances(ECKey ecKey) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();
        keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        String response = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "batchGetBalances",
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("outputs");
        for (Map<String, Object> object : list) {
            UTXO u = MapToBeanMapperUtil.parseUTXO(object);
            if (u.getValue().getValue() > 0)
                listUTXO.add(u);
        }
        return listUTXO;
    }

    public static Block createTokenBlock(ECKey outKey) throws Exception {

        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(Utils.HEX.encode(pubKey), "test", "", "", 1, false, false, false);
        tokenInfo.setTokens(tokens);

        // add MultiSignAddress item
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", outKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(100000L, pubKey);

        long amount = basecoin.getValue();
        tokenInfo.setTokenSerial(new TokenSerial(tokens.getTokenid(), 0, amount));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Configure.PARAMS.getDefaultSerializer().makeBlock(data);
        block.setBlocktype(NetworkParameters.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(outKey.getPubKey(), basecoin, tokenInfo);

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(Utils.HEX.encode(pubKey));
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(Configure.PARAMS).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        transaction.setDatasignature(Json.jsonmapper().writeValueAsBytes(multiSignBies));

        // save block
        block.solve();

        return block;
    }

    public static Block getAskTransactionBlock() throws JsonProcessingException, Exception {
        final Map<String, Object> requestParam = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        return Configure.PARAMS.getDefaultSerializer().makeBlock(data);
    }
}
