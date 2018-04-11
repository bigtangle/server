/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NetworkParameters networkParameters;
    private static final Logger logger = LoggerFactory.getLogger(ClientIntegrationTest.class);
    @Autowired
    private MilestoneService milestoneService;
    
    @Test
    public void saveOrder() throws Exception {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("address", "111111111111111111111111111111111111111111111111111111");
        request.put("tokenid", "222222222222222222222222222222222222222222222222222222");
        request.put("type", 1);
        request.put("price", 1);
        request.put("amount", 100);
        request.put("validateto", simpleDateFormat.format(new Date()));
        request.put("validatefrom", simpleDateFormat.format(new Date()));
        
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.saveOrder.name()).content(Json.jsonmapper().writeValueAsString(request));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("saveOrder resp : " + response);
        this.getOrders();
    }
    
    @Test
    public void getOrders() throws Exception {
        HashMap<String, Object> request = new HashMap<String, Object>();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getOrders.name()).content(Json.jsonmapper().writeValueAsString(request));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("getOrders resp : " + response);
    }

    @Test
    public void getTokens() throws Exception {
        ECKey ecKey = new ECKey();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getTokens.name())
                .content(ecKey.getPubKeyHash());
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + data);
    }

    @SuppressWarnings("deprecation")
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
        
         byte[] a = req.tx.bitcoinSerialize();
         
         
         HashMap<String, Object> requestParam = new HashMap<String, Object>();
         requestParam.put("fromAddress", "fromAddress");
         requestParam.put("fromTokenHex", "fromTokenHex");
         requestParam.put("fromAmount", "22");
         requestParam.put("toAddress", "toAddress");
         requestParam.put("toTokenHex", "toTokenHex");
         requestParam.put("toAmount", "33");
         requestParam.put("dataHex", Utils.HEX.encode(a));
         MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.saveExchange.name())
                 .content(Json.jsonmapper().writeValueAsString(requestParam));
         MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
         String data = mvcResult.getResponse().getContentAsString();
         logger.info("testGetBalances resp : " + data);
         
         Transaction transaction = (Transaction) networkParameters.getDefaultSerializer().makeTransaction(a);
        
//        byte[] buf = BeanSerializeUtil.serializer(req.tx);
//        Transaction transaction = BeanSerializeUtil.deserialize(buf, Transaction.class);
        
         SendRequest request = SendRequest.forTx(transaction); 
        walletAppKit1.wallet().signTransaction(request);
        exchangeTokenComplete(request.tx);
        
        requestParam.clear();
        requestParam.put("address", "fromAddress");
        httpServletRequestBuilder = post(contextRoot + ReqCmd.getExchange.name())
                .content(Json.jsonmapper().writeValueAsString(requestParam));
        mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        data = mvcResult.getResponse().getContentAsString();
        logger.info("getExchange resp : " + data);
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
        System.out.println(res);
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
        milestoneService.update();
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
