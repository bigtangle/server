package net.bigtangle.server;

import java.util.ArrayList;
import java.util.Arrays;
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
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.crypto.TransactionSignature;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.FreeStandingTransactionOutput;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DoubleSpenderBlockTest extends AbstractIntegrationTest {

    @Autowired
    private NetworkParameters networkParameters;

    public Thread createThreadCountDownLatch(int method, CountDownLatch countDownLatch) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (method == 1) {
                        test100EmptyBlock();
                    } else {
                        test50AskTransactionBlock();
                    }
                } catch (Exception e) {
                }
                countDownLatch.countDown();
            }
        });
        return thread;
    }
    
    @Test
    public void initializationTest() {
    	
    }

    //@Test
    public void testThreadDoubleSpenderRace() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        
         Thread thread1 = createThreadCountDownLatch(1, countDownLatch);
         Thread thread2 = createThreadCountDownLatch(2, countDownLatch);
         
         thread1.start();
         thread2.start();
         
         try {
             countDownLatch.await();
         }
         catch (Exception e) {
        }
    }
    
    @SuppressWarnings("deprecation")
    public void test50AskTransactionBlock() throws Exception {
        for (int i = 0; i < 50; i++) {
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block block = networkParameters.getDefaultSerializer().makeBlock(buf);
            block.solve();
            try {
                OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(buf);
        
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        
        ECKey outKey = new ECKey();
        Coin coinbase = Coin.valueOf(1, NetworkParameters.BIGNETCOIN_TOKENID);
        this.giveBlockDoubleSpentTransaction(block, outKey, coinbase, outputs);
        block.solve();
        try {
            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        } catch (Exception e) {
            e.printStackTrace();
        }
        LOGGER.info("test 50 ask transaction method end");
    }

    private void giveBlockDoubleSpentTransaction(Block rollingBlock, ECKey outKey, Coin coinbase, List<UTXO> outputs)
            throws Exception {
        for (UTXO output : outputs) {
            if (Arrays.equals(output.getValue().getTokenid(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                giveBlockDoubleSpentTransaction(rollingBlock, outKey, coinbase, output);
                return;
            }
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DoubleSpenderBlockTest.class);

    @SuppressWarnings("deprecation")
    public void test100EmptyBlock() throws Exception {
        Sha256Hash sha256Hash1, sha256Hash2;
        {
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
            rollingBlock.solve();
            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
            sha256Hash1 = rollingBlock.getHash();
        }
        {
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            byte[] buf = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                    Json.jsonmapper().writeValueAsString(requestParam));
            Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(buf);
            rollingBlock.solve();
            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
            sha256Hash2 = rollingBlock.getHash();
        }
        for (int i = 0; i < 100; i++) {
            Block block = new Block(this.networkParameters, sha256Hash1, sha256Hash2,
                    NetworkParameters.BLOCKTYPE_TRANSFER, System.currentTimeMillis() / 1000);
            if (i == 99) {
                break;
            }
            block.solve();
            OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
            if (i % 2 == 0) {
                sha256Hash1 = block.getHash();
            } else {
                sha256Hash2 = block.getHash();
            }
        }
        
        Block block = new Block(this.networkParameters, sha256Hash1, sha256Hash2,
                NetworkParameters.BLOCKTYPE_TRANSFER, System.currentTimeMillis() / 1000);
        
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv),
                Utils.HEX.decode(NetworkParameters.testPub));
        List<UTXO> outputs = testTransactionAndGetBalances(false, genesiskey);
        
        ECKey outKey = new ECKey();
        Coin coinbase = Coin.valueOf(1, NetworkParameters.BIGNETCOIN_TOKENID);
        giveBlockDoubleSpentTransaction(block, outKey, coinbase, outputs.get(0));
        block.solve();
        
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), block.bitcoinSerialize());
        LOGGER.info("test 100 empty block method end");
    }

    public void giveBlockDoubleSpentTransaction(Block rollingBlock, ECKey outKey, Coin coinbase, UTXO output)
            throws Exception {
        @SuppressWarnings("deprecation")
        ECKey genesiskey = new ECKey(Utils.HEX.decode(testPriv),
                Utils.HEX.decode(testPub));
        
        Transaction doublespent = new Transaction(this.networkParameters);
        doublespent.addOutput(new TransactionOutput(this.networkParameters, doublespent, coinbase, outKey));

        TransactionOutput spendableOutput = new FreeStandingTransactionOutput(this.networkParameters, output, 0);
//        Coin amount2 = spendableOutput.getValue().subtract(coinbase);
//        doublespent.addOutput(amount2, genesiskey);
        TransactionInput input = doublespent.addInput(spendableOutput);
        Sha256Hash sighash = doublespent.hashForSignature(0, spendableOutput.getScriptBytes(), Transaction.SigHash.ALL,
                false);

        TransactionSignature tsrecsig = new TransactionSignature(genesiskey.sign(sighash), Transaction.SigHash.ALL,
                false);
        Script inputScript = ScriptBuilder.createInputScript(tsrecsig);
        input.setScriptSig(inputScript);

        rollingBlock.addTransaction(doublespent);
    }
    
    //@Test
    public void testThreadCreateToken() {
        int threadCount = 10;
        final ECKey ecKey = new ECKey();
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        createToken(ecKey);
                    } catch (Exception e) {
                    }
                    countDownLatch.countDown();
                }
            });

            thread.start();
        }
        try {
            countDownLatch.await();
        } catch (Exception e) {
        }
    }
    
    public void createToken(ECKey ecKey) throws Exception {
        byte[] pubKey = ecKey.getPubKey();
        TokenInfo tokenInfo = new TokenInfo();
        Tokens tokens = new Tokens(Utils.HEX.encode(pubKey), "test", "", "", 1, false, false, false);
        tokenInfo.setTokens(tokens);

        tokenInfo.getMultiSignAddresses()
                .add(new MultiSignAddress(tokens.getTokenid(), "", ecKey.getPublicKeyAsHex()));

        Coin basecoin = Coin.valueOf(100000L, pubKey);

        long amount = basecoin.getValue();
        tokenInfo.setTokenSerial(new TokenSerial(tokens.getTokenid(), 0, amount));

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        block.setBlockType(NetworkParameters.BLOCKTYPE_TOKEN_CREATION);
        block.addCoinbaseTransaction(ecKey.getPubKey(), basecoin, tokenInfo);

        Transaction transaction = block.getTransactions().get(0);

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = ecKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(Utils.HEX.encode(pubKey));
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(ecKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(ecKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        // save block
        block.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.multiSign.name(), block.bitcoinSerialize());
    }
}
