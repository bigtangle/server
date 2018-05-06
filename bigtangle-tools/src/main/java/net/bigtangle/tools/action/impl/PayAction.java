package net.bigtangle.tools.action.impl;

import java.util.HashMap;

import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.script.Script;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.utils.BlockForTest;
import net.bigtangle.utils.OkHttp3Util;

public class PayAction extends Action {

    public PayAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        ECKey outKey = this.account.getBuyKey();
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(Configure.CONTEXT_ROOT + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = Configure.PARAMS.getDefaultSerializer().makeBlock(data);
        
        Transaction transaction = rollingBlock.getTransactions().get(0);
        TransactionOutPoint spendableOutput = new TransactionOutPoint(Configure.PARAMS, 0, transaction.getHash());
        byte[] spendableOutputScriptPubKey = transaction.getOutputs().get(0).getScriptBytes();
        
        ECKey toKey = new ECKey();
        Coin amount = Coin.valueOf(99999999, NetworkParameters.BIGNETCOIN_TOKENID);
        Transaction t = new Transaction(Configure.PARAMS);
        t.addOutput(new TransactionOutput(Configure.PARAMS, t, amount, toKey));
        t.addSignedInput(spendableOutput, new Script(spendableOutputScriptPubKey), outKey);
        
        rollingBlock = BlockForTest.createNextBlock(rollingBlock, null, Configure.PARAMS.getGenesisBlock().getHash());
        rollingBlock.addTransaction(t);
        rollingBlock.solve();
        
//        KafkaMessageProducer kafkaMessageProducer = KafkaMessageProducer.getInstance();
//        kafkaMessageProducer.sendMessage(rollingBlock.bitcoinSerialize());
        OkHttp3Util.post(Configure.CONTEXT_ROOT + "saveBlock", rollingBlock.bitcoinSerialize());
    }
}
