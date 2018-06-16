package net.bigtangle.tools.utils;

import java.util.ArrayList;
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
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

public class Simulator {

    /*
     * public static void give(ECKey ecKey) throws Exception { int height = 1; Block
     * ref = getAskTransactionBlock(); Block ref1 = getAskTransactionBlock(); Block
     * rollingBlock = BlockForTest.createNextBlockWithCoinbase(ref,
     * Block.BLOCK_VERSION_GENESIS, Configure.OUT_KEY.getPubKey(), height++,
     * ref1.getHash());
     * 
     * OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "saveBlock",
     * rollingBlock.bitcoinSerialize());
     * 
     * Transaction transaction = rollingBlock.getTransactions().get(0);
     * TransactionOutPoint spendableOutput = new
     * TransactionOutPoint(Configure.PARAMS, 0, transaction.getHash());
     * 
     * for (int i = 1; i < 3; i++) { rollingBlock =
     * BlockForTest.createNextBlockWithCoinbase(rollingBlock,
     * Block.BLOCK_VERSION_GENESIS, Configure.OUT_KEY.getPubKey(), height++,
     * ref.getHash()); OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT +
     * "saveBlock", rollingBlock.bitcoinSerialize()); }
     * 
     * Block b = createTokenBlock(ecKey); rollingBlock =
     * BlockForTest.createNextBlock(b, null, ref1.getHash()); Coin amount =
     * Coin.valueOf(999999999999L, NetworkParameters.BIGNETCOIN_TOKENID);
     * 
     * Transaction t = new Transaction(Configure.PARAMS); t.addOutput(new
     * TransactionOutput(Configure.PARAMS, t, amount,
     * ecKey.toAddress(Configure.PARAMS))); t.addSignedInput(spendableOutput,
     * transaction.getOutputs().get(0).getScriptPubKey(), Configure.OUT_KEY);
     * rollingBlock.addTransaction(t); rollingBlock.solve();
     * OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "saveBlock",
     * rollingBlock.bitcoinSerialize()); }
     */

    public static void give(ECKey ecKey) throws Exception {
        Block block = getAskTransactionBlock();
        Transaction transaction = new Transaction(Configure.PARAMS);

        byte[] privKeyBytes = Utils.HEX.decode(NetworkParameters.testPiv);
        ECKey toKey = ECKey.fromPrivate(privKeyBytes);
        List<UTXO> listUTXO = getTransactionAndGetBalances(toKey);
        
        Coin coinbase = Coin.valueOf(9999L, NetworkParameters.BIGNETCOIN_TOKENID);
        TransactionOutput multisigOutput = new FreeStandingTransactionOutput(Configure.PARAMS, listUTXO.get(0), 0);
        transaction.addOutput(coinbase, ecKey);
        transaction.addSignedInput(multisigOutput, toKey);
        block.addTransaction(transaction);
        block.solve();
        
        OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "saveBlock", block.bitcoinSerialize());
        
        System.err.println("outKey : " + ecKey.toAddress(Configure.PARAMS).toBase58() + ",,," + Utils.HEX.encode(ecKey.toAddress(Configure.PARAMS).getHash160()));
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
