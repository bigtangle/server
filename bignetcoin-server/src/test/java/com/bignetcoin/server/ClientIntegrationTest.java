/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.internal.lang.annotation.ajcDeclareAnnotation;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.MapToBeanMapperUtil;
import org.bitcoinj.utils.OkHttp3Util;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ClientIntegrationTest.class);
    
    public void exchangeToken() throws Exception {
        WalletAppKit bitcoin = new WalletAppKit(networkParameters, new File("."), "bignetcoin");
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        
        
        Address destination = Address.fromBase58(networkParameters, "");

        Coin amount = Coin.parseCoin("10000", NetworkParameters.BIGNETCOIN_TOKENID);
        
        SendRequest outputRequest = SendRequest.to(destination, amount);
        bitcoin.wallet().completeTx(outputRequest);
        
        Transaction transaction0 = outputRequest.tx;
        rollingBlock.addTransaction(transaction0);
        
        Transaction transaction1 = makeTransaction(destination.getHash160(), amount);
        rollingBlock.addTransaction(transaction1);
        
        rollingBlock.solve();
    }
    
    public Transaction makeTransaction(byte[] pubKeyTo, Coin coin) {
        Transaction transaction = new Transaction(networkParameters);
        transaction.tokenid = coin.tokenid;
        final ScriptBuilder inputBuilder = new ScriptBuilder();
        inputBuilder.data(new byte[] { (byte) 0x00, (byte) (0x01 >> 8) });
        transaction.addInput(new TransactionInput(networkParameters, transaction, inputBuilder.build().getProgram()));
        transaction.addOutput(new TransactionOutput(networkParameters, transaction, coin,
                ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(pubKeyTo)).getProgram()));
        return transaction;
    }

    @Autowired
    private NetworkParameters networkParameters;
    
    @Test
    public void createGenesisBlock() throws Exception {
        WalletAppKit bitcoin = new WalletAppKit(networkParameters, new File("."), "bignetcoin");
        List<ECKey> keys = getWalletKeyBag(bitcoin);
        ECKey outKey = keys.get(0);
        
        byte[] pubKey = outKey.getPubKey();
        ByteBuffer byteBuffer = ByteBuffer.allocate(4 + 4 + pubKey.length);
        byteBuffer.putInt(100000000);
        byteBuffer.putInt(pubKey.length);
        byteBuffer.put(pubKey);
        
        byte[] data = OkHttp3Util.postByte(contextRoot + ReqCmd.createGenesisBlock.name(), byteBuffer.array());
        Block block = networkParameters.getDefaultSerializer().makeBlock(data);
        
        logger.info("createGenesisBlock resp : " + block);
        logger.info("new tokenid : " + block.getTokenid());
    }

    @SuppressWarnings("unchecked")
    public void getBalances() throws Exception {
        WalletAppKit bitcoin = new WalletAppKit(networkParameters, new File("."), "bignetcoin");
        List<ECKey> keys = getWalletKeyBag(bitcoin);
        for (ECKey ecKey : keys) {
            String response = OkHttp3Util.post(contextRoot + "getBalances", ecKey.getPubKeyHash());
            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
            if (data != null && !data.isEmpty()) {
                List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("outputs");
                if (list != null && !list.isEmpty()) {
                    for (Map<String, Object> object : list) {
                        UTXO u = MapToBeanMapperUtil.parseUTXO(object);
                        Coin c = u.getValue();
                        long balance = c.getValue();
                        long tokenid = c.tokenid;
                        String address = u.getAddress();
                        if (!u.isSpent()) {
                            logger.info("outputs, balance : {}, tokenid : {}, address : {}", balance, tokenid, address);
                        }
                    }
                }
                list = (List<Map<String, Object>>) data.get("tokens");
                if (list != null && !list.isEmpty()) {
                    for (Map<String, Object> map : list) {
                        Coin coin = MapToBeanMapperUtil.parseCoin(map);
                        if (!coin.isZero()) {
                            logger.info("tokens, value : {}, tokenid : {}", coin.value, coin.tokenid);
                        }
                    }
                }
            }
        }
    }

    public void createTransaction() throws Exception {
        WalletAppKit bitcoin = new WalletAppKit(networkParameters, new File("."), "bignetcoin");
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("resp block, hex : " + Utils.HEX.encode(data));

        Address destination = Address.fromBase58(networkParameters, "");

        Coin amount = Coin.parseCoin("10000", NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(destination, amount);
        bitcoin.wallet().completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
    }

    private List<ECKey> getWalletKeyBag(WalletAppKit bitcoin) {
        KeyParameter aesKey = null;
        DecryptingKeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(bitcoin.wallet(), aesKey);
        List<ECKey> keys = new ArrayList<ECKey>();
        for (ECKey key : bitcoin.wallet().getImportedKeys()) {
            ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
            keys.add(ecKey);
        }
        for (DeterministicKeyChain chain : bitcoin.wallet().getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey key : chain.getLeafKeys()) {
                ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
                keys.add(ecKey);
            }
        }
        return keys;
    }
}
