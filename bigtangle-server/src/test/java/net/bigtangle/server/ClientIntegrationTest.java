/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server;

import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Tokens;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.server.service.MilestoneService;
import net.bigtangle.server.service.schedule.ScheduleOrderMatchService;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.SendRequest;
import net.bigtangle.wallet.Wallet.MissingSigsMode;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NetworkParameters networkParameters;
    private static final Logger logger = LoggerFactory.getLogger(ClientIntegrationTest.class);
    @Autowired
    private MilestoneService milestoneService;
    @Autowired
    private ScheduleOrderMatchService scheduleOrderMatchService;
    
    @Test
    public void searchBlock() throws Exception {
        List<ECKey> keys = walletAppKit.wallet().walletKeys(null);
        List<String> address = new ArrayList<String>();
        for (ECKey ecKey : keys) {
            address.add(ecKey.toAddress(networkParameters).toBase58());
        }
        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("address", address);
        
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.searchBlock.name())
                .content(Json.jsonmapper().writeValueAsString(request));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("searchBlock resp : " + response);
    }

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

        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.saveOrder.name())
                .content(Json.jsonmapper().writeValueAsString(request));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        logger.info("saveOrder resp : " + response);
        this.getOrders();
    }

    @Test
    public void getOrders() throws Exception {
        HashMap<String, Object> request = new HashMap<String, Object>();
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.getOrders.name())
                .content(Json.jsonmapper().writeValueAsString(request));
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
            if (Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                myutxo = u;
            }
        }
        
        Coin amount = Coin.valueOf(10000, yourutxo.getValue().tokenid);
        SendRequest req = SendRequest.to(new Address(networkParameters, myutxo.getAddress()), amount);
        req.tx.addOutput(myutxo.getValue(), new Address(networkParameters, yourutxo.getAddress()));
        
        System.out.println(myutxo.getAddress() + ", " + myutxo.getValue());
        System.out.println(yourutxo.getAddress() + ", " + amount);
        
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
        requestParam.put("orderid", UUID.randomUUID().toString());
        requestParam.put("dataHex", Utils.HEX.encode(a));
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.saveExchange.name())
                .content(Json.jsonmapper().writeValueAsString(requestParam));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();
        String data = mvcResult.getResponse().getContentAsString();
        logger.info("testGetBalances resp : " + data);

        Transaction transaction = (Transaction) networkParameters.getDefaultSerializer().makeTransaction(a);

        // byte[] buf = BeanSerializeUtil.serializer(req.tx);
        // Transaction transaction = BeanSerializeUtil.deserialize(buf,
        // Transaction.class);

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
            if (!Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                utxo = u;
            }
        }
        System.out.println(utxo.getValue());
        //Coin baseCoin = utxo.getValue().subtract(Coin.parseCoin("10000", utxo.getValue().getTokenid()));
        //System.out.println(baseCoin);
        Address destination = outKey.toAddress(networkParameters);
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

        Coin amount = Coin.parseCoin("0.02", NetworkParameters.BIGNETCOIN_TOKENID);
        SendRequest request = SendRequest.to(destination, amount);
        request.tx.setMemo("memo");
        walletAppKit.wallet().completeTx(request);
        request.tx.setDataType(10000);
        request.tx.setTokens(new Tokens(Utils.HEX.encode(NetworkParameters.BIGNETCOIN_TOKENID),
                "J", "J", "", 100, true, true, true));
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();

        OkHttp3Util.post(contextRoot + "saveBlock", rollingBlock.bitcoinSerialize());
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

        testTransactionAndGetBalances();
        
        Transaction transaction = (Transaction) networkParameters.getDefaultSerializer().makeTransaction(request.tx.bitcoinSerialize());
        logger.info("transaction, memo : " + transaction.getMemo());
        logger.info("transaction, tokens : " + transaction.getTokens());
        logger.info("transaction, datatype : " + transaction.getDataType());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void exchangeOrder() throws Exception {

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
            if (Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                myutxo = u;
            }
        }

        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("address", yourutxo.getAddress());
        request.put("tokenid", yourutxo.getTokenid());
        request.put("type", 1);
        request.put("price", 1000);
        request.put("amount", 1000);
        System.out.println("req : " + request);
        // sell token order
        MockHttpServletRequestBuilder httpServletRequestBuilder = post(contextRoot + ReqCmd.saveOrder.name())
                .content(Json.jsonmapper().writeValueAsString(request));
        MvcResult mvcResult = getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();

        request.put("address", myutxo.getAddress());
        request.put("tokenid", yourutxo.getTokenid());
        request.put("type", 2);
        request.put("price", 1000);
        request.put("amount", 1000);
        System.out.println("req : " + request);
        // buy token order
        httpServletRequestBuilder = post(contextRoot + ReqCmd.saveOrder.name())
                .content(Json.jsonmapper().writeValueAsString(request));
        getMockMvc().perform(httpServletRequestBuilder).andExpect(status().isOk()).andReturn();

        scheduleOrderMatchService.updateMatch();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", myutxo.getAddress());
        String response = OkHttp3Util.post(contextRoot + "getExchange",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("exchanges");
        assertTrue(list.size() >= 1);
        Map<String, Object> exchangemap = list.get(0);

        
        Address fromAddress00 = new Address(networkParameters, (String) exchangemap.get("fromAddress"));
        Address toAddress00 = new Address(networkParameters, (String) exchangemap.get("toAddress"));
        Coin fromAmount = Coin.valueOf( 
                Long.parseLong( (String) exchangemap.get("fromAmount")), Utils.HEX.decode((String) exchangemap.get("fromTokenHex")));
        Coin toAmount =  Coin.valueOf( 
                        Long.parseLong( (String) exchangemap.get("toAmount")), Utils.HEX.decode((String) exchangemap.get("toTokenHex")));
                   
        SendRequest req = SendRequest.to(toAddress00,toAmount  );
        req.tx.addOutput(fromAmount , fromAddress00 );
        
        HashMap<String, Address> addressResult = new HashMap<String, Address>();
        addressResult.put((String) exchangemap.get("fromTokenHex"), toAddress00);
        addressResult.put((String) exchangemap.get("toTokenHex"), fromAddress00);
        
        req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
        ulist.addAll(utxos);
        walletAppKit.wallet().completeTx(req, walletAppKit.wallet().transforSpendCandidates(ulist), false, addressResult);
        walletAppKit.wallet().signTransaction(req);

        byte[] a = req.tx.bitcoinSerialize();
        HashMap<String, Object> requestParam0 = new HashMap<String, Object>();
        requestParam0.put("orderid", (String) exchangemap.get("orderid"));
        requestParam0.put("dataHex", Utils.HEX.encode(a));
        requestParam0.put("signtype", "to");

        OkHttp3Util.post(contextRoot + "signTransaction", Json.jsonmapper().writeValueAsString(requestParam0));
  

        Transaction transaction = (Transaction) networkParameters.getDefaultSerializer().makeTransaction(a);

        // byte[] buf = BeanSerializeUtil.serializer(req.tx);
        // Transaction transaction = BeanSerializeUtil.deserialize(buf,
        // Transaction.class);

        req = SendRequest.forTx(transaction);
        walletAppKit1.wallet().signTransaction(req);
        exchangeTokenComplete(req.tx); 
        
        HashMap<String, Object> requestParam1 = new HashMap<String, Object>();
        requestParam1.put("orderid", (String) exchangemap.get("orderid"));
        requestParam1.put("dataHex", Utils.HEX.encode(transaction.bitcoinSerialize()));
        requestParam1.put("signtype", "from");
        OkHttp3Util.post(contextRoot + "signTransaction", Json.jsonmapper().writeValueAsString(requestParam1));
    }

}
