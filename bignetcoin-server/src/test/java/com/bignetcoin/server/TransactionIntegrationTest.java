/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.utils.OkHttp3Util;
import org.bitcoinj.wallet.DecryptingKeyBag;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.spongycastle.crypto.params.KeyParameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransactionIntegrationTest extends AbstractIntegrationTest {
    
    @Override
    public void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext).build();
        objectMapper = new ObjectMapper();
    }
    
    @Autowired
    private NetworkParameters networkParameters;
    
    @Test
    public void transactionExchangeTest() throws Exception {
        WalletAppKit bitcoin = new WalletAppKit(networkParameters, new File("../bignetcoin-wallet"), "bignetcoin");
        bitcoin.wallet().setServerURL(contextRoot);
        KeyParameter aesKey = null;
        DecryptingKeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(bitcoin.wallet(), aesKey);
        List<ECKey> ecKeys = new ArrayList<ECKey>();
        for (ECKey key : bitcoin.wallet().getImportedKeys()) {
            ecKeys.add(maybeDecryptingKeyBag.maybeDecrypt(key));
        }
        for (DeterministicKeyChain chain : bitcoin.wallet().getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey key : chain.getLeafKeys()) {
                ecKeys.add(maybeDecryptingKeyBag.maybeDecrypt(key));
            }
        }
        ECKey outKey = ecKeys.get(0);
        {
            String response = OkHttp3Util.post(contextRoot + "getBalances", outKey.getPubKeyHash());
            System.out.println(response);
        }
        assertTrue(outKey.getPublicKeyAsHex().equals("02e8c53bb848244724c890cf216abc8781c8b8dc2b808f153b2bdaedc97c9d9429"));
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction", Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        
        ECKey toKey = new ECKey();
        Transaction transaction = new Transaction(networkParameters);
        transaction.addOutput(Coin.parseCoin("0.0000001", NetworkParameters.BIGNETCOIN_TOKENID), toKey);
        {
            SendRequest req = SendRequest.forTx(transaction);
            req.changeAddress = new Address(networkParameters, outKey.getPubKeyHash());
            bitcoin.wallet().completeTx(req);
        }
        {
            transaction.addOutput(Coin.parseCoin("0.0000001", NetworkParameters.BIGNETCOIN_TOKENID), outKey);
            SendRequest req = SendRequest.forTx(transaction);
            req.changeAddress = new Address(networkParameters, toKey.getPubKeyHash());
            // TODO tokey => outkey ex
            bitcoin.wallet().completeTx(req);
        }
        rollingBlock.addTransaction(transaction);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        {
            String response = OkHttp3Util.post(contextRoot + "getBalances", toKey.getPubKeyHash());
            System.out.println(response);
        }
        {
            String response = OkHttp3Util.post(contextRoot + "getBalances", outKey.getPubKeyHash());
            System.out.println(response);
        }
    }
}