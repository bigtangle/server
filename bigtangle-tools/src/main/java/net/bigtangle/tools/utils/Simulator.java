package net.bigtangle.tools.utils;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;

public class Simulator {

    public static void give(ECKey ecKey) throws Exception {
        int height = 1;
        Block ref = getAskTransactionBlock();
        Block ref1 = getAskTransactionBlock();
        Block rollingBlock = BlockForTest.createNextBlockWithCoinbase(ref,
                Block.BLOCK_VERSION_GENESIS, Configure.OUT_KEY.getPubKey(), height++,
                ref1.getHash());

        OkHttp3Util.post(Configure.CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());

        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(Configure.PARAMS, 0, transaction.getHash());

        for (int i = 1; i < 3; i++) {
            rollingBlock = BlockForTest.createNextBlockWithCoinbase(rollingBlock, Block.BLOCK_VERSION_GENESIS,
                    Configure.OUT_KEY.getPubKey(), height++, ref.getHash());
            OkHttp3Util.post(Configure.CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());
        }
        
        Block b = createGenesisBlock(ecKey);
        rollingBlock = BlockForTest.createNextBlock(b, null, ref1.getHash());
        Coin amount = Coin.valueOf(999999999999L, NetworkParameters.BIGNETCOIN_TOKENID);

        Transaction t = new Transaction(Configure.PARAMS);
        t.addOutput(new TransactionOutput(Configure.PARAMS, t, amount, ecKey.toAddress(Configure.PARAMS)));
        t.addSignedInput(spendableOutput, transaction.getOutputs().get(0).getScriptPubKey(), Configure.OUT_KEY);
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        OkHttp3Util.post(Configure.CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());
    }

    private static Block createGenesisBlock(ECKey outKey) throws Exception {
        byte[] pubKey = outKey.getPubKey();
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("pubKeyHex", Utils.HEX.encode(pubKey));
        requestParam.put("amount", 999999999999L);
        requestParam.put("tokenname", "Test");
        requestParam.put("description", "Test");
        requestParam.put("blocktype", false);
        requestParam.put("tokenHex", Utils.HEX.encode(outKey.getPubKeyHash()));
        byte[] data = OkHttp3Util.post(Configure.CONTEXT_ROOT + "createGenesisBlock", Json.jsonmapper().writeValueAsString(requestParam));
        Block block = Configure.PARAMS.getDefaultSerializer().makeBlock(data);
        return block;
    }
    public static Block getAskTransactionBlock() throws JsonProcessingException, Exception {
        final  Map<String, Object> requestParam = new HashMap<String, Object>();
        byte[] data  = OkHttp3Util.post(Configure.CONTEXT_ROOT + "askTransaction",  Json.jsonmapper().writeValueAsString(requestParam));
            
         return Configure.PARAMS.getDefaultSerializer().makeBlock(data);
    }
}
