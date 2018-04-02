/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Json;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.BeanSerializeUtil;
import org.bitcoinj.utils.OkHttp3Util;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet.MissingSigsMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.bignetcoin.server.service.MilestoneService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NetworkParameters networkParameters;
    private static final Logger logger = LoggerFactory.getLogger(ClientIntegrationTest.class);
    @Autowired
    private MilestoneService milestoneService;

    @Test
    public void getTokens() throws Exception {
        ECKey ecKey = new ECKey();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getTokens.name())
                .content(ecKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + data);
    }

    @Test
    public void exchangeToken() throws Exception {

        // get token from wallet to spent
        ECKey yourKey = walletAppKit1.wallet().walletKeys(null).get(0);

        payToken(yourKey);
        List<ECKey> keys = new ArrayList<ECKey>();
        keys.add(yourKey);
        List<UTXO> utxos = testTransactionAndGetBalances(false, keys);
        UTXO yourutxo = utxos.get(0);
        List<UTXO> ulist = testTransactionAndGetBalances();
        UTXO myutxo = null;
        for (UTXO u : ulist) {
            if (Arrays.equals(u.getTokenid(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                myutxo = u;
            }
        }
        SendRequest req = SendRequest.to(new Address(PARAMS, myutxo.getAddress()), yourutxo.getValue());
        req.tx.addOutput(myutxo.getValue(), new Address(PARAMS, yourutxo.getAddress()));
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        ulist.addAll(utxos);
        walletAppKit.wallet().completeTx(req, walletAppKit.wallet().transforSpendCandidates(ulist), false);
        walletAppKit.wallet().signTransaction(req);
        
//         byte[] a = req.tx.bitcoinSerialize();
//         Transaction transaction = (Transaction) networkParameters.getDefaultSerializer().makeTransaction(a);
        
        byte[] buf = BeanSerializeUtil.serializer(req.tx);
        Transaction transaction = BeanSerializeUtil.deserialize(buf, Transaction.class);
        
         SendRequest request = SendRequest.forTx(transaction); 
        walletAppKit1.wallet().signTransaction(request);
        exchangeTokenComplete(request.tx);
    }

    public void exchangeTokenComplete(Transaction tx) throws Exception {
        // get new Block to be used from server
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        rollingBlock.addTransaction(tx);
        rollingBlock.solve();

        String res = OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
    }

    public void payToken(ECKey outKey) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("resp block, hex : " + Utils.HEX.encode(data));
        // get other tokenid from wallet
        UTXO utxo = null;
        List<UTXO> ulist = testTransactionAndGetBalances();
        for (UTXO u : ulist) {
            if (!Arrays.equals(u.getTokenid(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                utxo = u;
            }
        }
        Address destination = outKey.toAddress(PARAMS);
        SendRequest request = SendRequest.to(destination, utxo.getValue());
        walletAppKit.wallet().completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
        milestoneService.update();
    }

    @Test
    public void createTransaction() throws Exception {

        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + "askTransaction",
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("resp block, hex : " + Utils.HEX.encode(data));

        Address destination = Address.fromBase58(networkParameters, "mqrXsaFj9xV9tKAw7YeP1B6zPmfEP2kjfK");

        Coin amount = Coin.parseCoin("0.0002", NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(destination, amount);
        walletAppKit.wallet().completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

        testTransactionAndGetBalances();
    }

}
