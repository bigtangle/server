package net.bigtangle.server;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

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
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DoubleSpenderBlockTest extends AbstractIntegrationTest {
    
    @Autowired
    private NetworkParameters networkParameters;

    public Thread createThreadCountDownLatch(CountDownLatch start, CountDownLatch end, int method) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    start.await();
                    test100EmptyBlock();
//                    if (method == 1) {
//                    }
//                    else {
//                        test50AskTransactionBlock();
//                    }
                    end.countDown();
                } catch (Exception e) {
                }
            }
        });
        return thread;
    }

    @Test
    public void testThreadDoubleSpenderRace() {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end = new CountDownLatch(2);

        Thread thread1 = this.createThreadCountDownLatch(start, end, 1);
//        Thread thread2 = this.createThreadCountDownLatch(start, end, 2);
        
        thread1.start();
//        thread2.start();

        try {
            start.countDown();
            end.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void test50AskTransactionBlock() throws Exception {
        List<UTXO> outputs = testTransactionAndGetBalances();

        Block rollingBlock = null;
        for (int i = 0; i < 50; i++) {
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            byte[] buf = OkHttp3Util.post(contextRoot + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));
            rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);

            ECKey outKey = new ECKey();
            Coin coinbase = Coin.valueOf(1, NetworkParameters.BIGNETCOIN_TOKENID);
            giveBlockDoubleSpentTransaction(rollingBlock, outKey, coinbase, outputs.get(0));
            rollingBlock.solve();
            OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        }
        LOGGER.info("test 50 ask transaction method end");
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DoubleSpenderBlockTest.class);

    public void test100EmptyBlock() throws Exception {
        Sha256Hash sha256Hash1, sha256Hash2;
        {
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            byte[] buf = OkHttp3Util.post(contextRoot + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
            rollingBlock.solve();
            OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
            sha256Hash1 = rollingBlock.getHash();
        }
        {
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            byte[] buf = OkHttp3Util.post(contextRoot + "askTransaction",
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
            rollingBlock.solve();
            OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
            sha256Hash2 = rollingBlock.getHash();
        }
        Block rollingBlock = null;
        for (int i = 0; i < 100; i++) {
            rollingBlock = new Block(this.networkParameters, sha256Hash1, sha256Hash2,
                    NetworkParameters.BLOCKTYPE_TRANSFER, System.currentTimeMillis() / 1000);
            if (i == 99) {
                break;
            }
            rollingBlock.solve();
            OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
            if (i % 2 == 0) {
                sha256Hash1 = rollingBlock.getHash();
            } else {
                sha256Hash2 = rollingBlock.getHash();
            }
        }
        List<UTXO> outputs = testTransactionAndGetBalances();
        ECKey outKey = new ECKey();
        Coin coinbase = Coin.valueOf(1, NetworkParameters.BIGNETCOIN_TOKENID);
        giveBlockDoubleSpentTransaction(rollingBlock, outKey, coinbase, outputs.get(0));
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        LOGGER.info("test 100 empty block method end");
    }

    public void giveBlockDoubleSpentTransaction(Block rollingBlock, ECKey outKey, Coin coinbase, UTXO output)
            throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPiv),
                Utils.HEX.decode(NetworkParameters.testPub));

        Transaction doublespent = new Transaction(networkParameters);
        doublespent.addOutput(new TransactionOutput(networkParameters, doublespent, coinbase, outKey));
        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(networkParameters, output, 0);
        Coin amount2 = spendableOutput.getValue().subtract(coinbase);
        doublespent.addOutput(amount2, genesiskey);
        TransactionInput input = doublespent.addInput(spendableOutput);
        Sha256Hash sighash = doublespent.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);
        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);
        rollingBlock.addTransaction(doublespent);
    }
}
