package net.bigtangle.tools.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import net.bigtangle.core.Token;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.core.http.server.resp.MultiSignResponse;
import net.bigtangle.core.http.server.resp.TokenIndexResponse;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

public class GiveMoneyUtils {

    public static void createTokenMultiSign(String tokenId, List<ECKey> ecKeys, int amount)
            throws JsonProcessingException, Exception {
        if (ecKeys.isEmpty()) {
            return;
        }
        TokenInfo tokenInfo = new TokenInfo();
        for (ECKey ecKey : ecKeys) {
            tokenInfo.getMultiSignAddresses().add(new MultiSignAddress(tokenId, "", ecKey.getPublicKeyAsHex()));
        }
        Coin basecoin = Coin.valueOf(amount, tokenId);

        HashMap<String, String> requestParam00 = new HashMap<String, String>();
        requestParam00.put("tokenid", tokenId);
        String resp2 = OkHttp3Util.postString(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getCalTokenIndex.name(),
                Json.jsonmapper().writeValueAsString(requestParam00));

        TokenIndexResponse tokenIndexResponse = Json.jsonmapper().readValue(resp2,
                TokenIndexResponse.class);
        long tokenindex = tokenIndexResponse.getTokenindex();
        String prevblockhash = tokenIndexResponse.getBlockhash();
        
        Token tokens = Token.buildSimpleTokenInfo(false, prevblockhash, tokenId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), ecKeys.size(), tokenindex, amount,  false);
        tokenInfo.setToken(tokens);
        
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Configure.PARAMS.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(ecKeys.get(0).getPubKey(), basecoin, tokenInfo);
        block.solve();

        OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.multiSign.name(), block.bitcoinSerialize());

        for (ECKey ecKey : ecKeys) {
            HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
            requestParam0.put("address", ecKey.toAddress(Configure.PARAMS).toBase58());
            String resp = OkHttp3Util.postString(
                    Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getMultiSignWithAddress.name(),
                    Json.jsonmapper().writeValueAsString(requestParam0));
            System.out.println(resp);

            MultiSignResponse multiSignResponse = Json.jsonmapper().readValue(resp, MultiSignResponse.class);
            String blockhashHex = multiSignResponse.getMultiSigns().get((int) tokenindex - 1).getBlockhashHex();
            byte[] payloadBytes = Utils.HEX.decode(blockhashHex);

            Block block0 = Configure.PARAMS.getDefaultSerializer().makeBlock(payloadBytes);
            Transaction transaction = block0.getTransactions().get(0);

            List<MultiSignBy> multiSignBies = null;
            if (transaction.getDataSignature() == null) {
                multiSignBies = new ArrayList<MultiSignBy>();
            } else {
                MultiSignByRequest multiSignByRequest = Json.jsonmapper().readValue(transaction.getDataSignature(),
                        MultiSignByRequest.class);
                multiSignBies = multiSignByRequest.getMultiSignBies();
            }

            Sha256Hash sighash = transaction.getHash();
            ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
            byte[] buf1 = party1Signature.encodeToDER();

            MultiSignBy multiSignBy0 = new MultiSignBy();
            multiSignBy0.setTokenid(tokenId);
            multiSignBy0.setTokenindex(tokenindex);
            multiSignBy0.setAddress(ecKey.toAddress(Configure.PARAMS).toBase58());
            multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
            multiSignBy0.setSignature(Utils.HEX.encode(buf1));
            multiSignBies.add(multiSignBy0);
            MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
            transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));
            OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.multiSign.name(), block0.bitcoinSerialize());
        }
    }

    public static void give(ECKey ecKey) throws Exception {
        Block block = getAskTransactionBlock();

        @SuppressWarnings("deprecation")
        ECKey genesiskey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        List<UTXO> outputs = getTransactionAndGetBalances(genesiskey);

        System.out.println(outputs.size());

        Coin coinbase = Coin.valueOf(9999L, NetworkParameters.BIGTANGLE_TOKENID);

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
            OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<UTXO> getTransactionAndGetBalances(ECKey ecKey) throws Exception {
        List<UTXO> listUTXO = new ArrayList<UTXO>();
        List<String> keyStrHex000 = new ArrayList<String>();
        keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        String response = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        for (UTXO utxo : getBalancesResponse.getOutputs()) {
            if (utxo.getValue().getValue() > 0) {
                listUTXO.add(utxo);
            }
        }
        return listUTXO;
    }

    public static Block createTokenBlock(ECKey outKey) throws Exception {
        byte[] pubKey = outKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();

        String tokenId = Utils.HEX.encode(pubKey);
        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokenId, "", outKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(10000000L, pubKey);
        long amount = basecoin.getValue();
        
        Token tokens = Token.buildSimpleTokenInfo(false, tokenId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "", 1, 0, amount, true);
        tokenInfo.setToken(tokens);
        
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Configure.PARAMS.getDefaultSerializer().makeBlock(data);
        block.setBlockType(Block.Type.BLOCKTYPE_TOKEN_CREATION);
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

        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        block.solve();
        return block;
    }

    public static Block getAskTransactionBlock() throws JsonProcessingException, Exception {
        final Map<String, Object> requestParam = new HashMap<String, Object>();
        byte[] data = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        return Configure.PARAMS.getDefaultSerializer().makeBlock(data);
    }
}
