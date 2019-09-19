/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.ordermatch;

import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.MultiSignBy;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.ordermatch.resp.GetOrderResponse;
import net.bigtangle.core.http.server.req.MultiSignByRequest;
import net.bigtangle.params.OrdermatchReqCmd;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.server.ordermatch.service.schedule.ScheduleOrderMatchService;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.PayOTCOrder;
import net.bigtangle.wallet.SendRequest;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ClientIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NetworkParameters networkParameters;
    private static final Logger logger = LoggerFactory.getLogger(ClientIntegrationTest.class);

    @Autowired
    private ScheduleOrderMatchService scheduleOrderMatchService;

    @Test
    public void deleteOrder() throws Exception {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        HashMap<String, Object> request = new HashMap<String, Object>();
        ECKey outKey = new ECKey();
        request.put("address", outKey.toAddress(networkParameters).toBase58());
        request.put("tokenid", outKey.getPublicKeyAsHex());
        request.put("type", 1);
        request.put("price", 1);
        request.put("amount", 100);
        request.put("validateto", simpleDateFormat.format(new Date()));
        request.put("validatefrom", simpleDateFormat.format(new Date()));

        String resp = OkHttp3Util.postString(contextRoot + OrdermatchReqCmd.saveOrder.name(),
                Json.jsonmapper().writeValueAsString(request));
        logger.info("saveOrder resp : " + resp);

        resp = OkHttp3Util.postString(contextRoot + OrdermatchReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(new HashMap<String, Object>()));
        logger.info("getOrders resp : " + resp);

        GetOrderResponse getOrderResponse = Json.jsonmapper().readValue(resp, GetOrderResponse.class);
        OrderPublish orderPublish = getOrderResponse.getOrders().get(0);

        Block rollingBlock = new Block(networkParameters, networkParameters.getGenesisBlock(),networkParameters.getGenesisBlock() );
        Transaction transaction = new Transaction(this.networkParameters);
        transaction.setData(orderPublish.getOrderId().getBytes());

        Sha256Hash sighash = transaction.getHash();
        ECKey.ECDSASignature party1Signature = outKey.sign(sighash);
        byte[] buf1 = party1Signature.encodeToDER();

        List<MultiSignBy> multiSignBies = new ArrayList<MultiSignBy>();
        MultiSignBy multiSignBy0 = new MultiSignBy();
        multiSignBy0.setTokenid(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setTokenindex(0);
        multiSignBy0.setAddress(outKey.toAddress(networkParameters).toBase58());
        multiSignBy0.setPublickey(Utils.HEX.encode(outKey.getPubKey()));
        multiSignBy0.setSignature(Utils.HEX.encode(buf1));
        multiSignBies.add(multiSignBy0);
        MultiSignByRequest multiSignByRequest = MultiSignByRequest.create(multiSignBies);
        transaction.setDataSignature(Json.jsonmapper().writeValueAsBytes(multiSignByRequest));

        rollingBlock.addTransaction(transaction);
        rollingBlock.solve();

        resp = OkHttp3Util.post(contextRoot + OrdermatchReqCmd.deleteOrder.name(), rollingBlock.bitcoinSerialize());
        logger.info("deleteOrder resp : " + resp);
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

        String response = OkHttp3Util.post(contextRoot + OrdermatchReqCmd.saveOrder.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());
        logger.info("saveOrder resp : " + response);
        this.getOrders();
    }

    public void getOrders() throws Exception {
        HashMap<String, Object> request = new HashMap<String, Object>();

        String response = OkHttp3Util.post(contextRoot + OrdermatchReqCmd.getOrders.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());
        GetOrderResponse getOrderResponse = Json.jsonmapper().readValue(response, GetOrderResponse.class);
        for (OrderPublish orderPublish : getOrderResponse.getOrders()) {
            logger.info("getOrders resp : " + orderPublish);
        }
    }

    @SuppressWarnings({ "unchecked" })
   // @Test
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
            if (Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                myutxo = u;
            }
        }

        HashMap<String, Object> request = new HashMap<String, Object>();
        request.put("address", yourutxo.getAddress());
        request.put("tokenid", yourutxo.getTokenId());
        request.put("type", 1);
        request.put("price", 1000);
        request.put("amount", 1000);
        System.out.println("req : " + request);
        // sell token order
        String response = OkHttp3Util.post(contextRoot + OrdermatchReqCmd.saveOrder.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());

        request.put("address", myutxo.getAddress());
        request.put("tokenid", yourutxo.getTokenId());
        request.put("type", 2);
        request.put("price", 1000);
        request.put("amount", 1000);
        System.out.println("req : " + request);
        // buy token order
        response = OkHttp3Util.post(contextRoot + OrdermatchReqCmd.saveOrder.name(),
                Json.jsonmapper().writeValueAsString(request).getBytes());

        scheduleOrderMatchService.updateMatch();

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", myutxo.getAddress());
        response = OkHttp3Util.post(contextRoot + OrdermatchReqCmd.getExchange.name(),
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("exchanges");
        assertTrue(list.size() >= 1);
        Map<String, Object> exchangemap = list.get(0);

        String serverURL = "http://localhost:8090";
        String marketURL = "http://localhost:8090";
        String orderid = (String) exchangemap.get("orderid");

        PayOTCOrder payOrder1 = new PayOTCOrder(walletAppKit.wallet(), orderid, serverURL, marketURL);
        payOrder1.sign();

        PayOTCOrder payOrder2 = new PayOTCOrder(walletAppKit1.wallet(), orderid, serverURL, marketURL);
        payOrder2.sign();
        /*
         * Address fromAddress00 = new Address(networkParameters, (String)
         * exchangemap.get("fromAddress")); Address toAddress00 = new
         * Address(networkParameters, (String) exchangemap.get("toAddress"));
         * Coin fromAmount = Coin.valueOf( Long.parseLong( (String)
         * exchangemap.get("fromAmount")), Utils.HEX.decode((String)
         * exchangemap.get("fromTokenHex"))); Coin toAmount = Coin.valueOf(
         * Long.parseLong( (String) exchangemap.get("toAmount")),
         * Utils.HEX.decode((String) exchangemap.get("toTokenHex")));
         * 
         * SendRequest req = SendRequest.to(toAddress00,toAmount );
         * req.tx.addOutput(fromAmount , fromAddress00 );
         * 
         * HashMap<String, Address> addressResult = new HashMap<String,
         * Address>(); addressResult.put((String)
         * exchangemap.get("fromTokenHex"), toAddress00);
         * addressResult.put((String) exchangemap.get("toTokenHex"),
         * fromAddress00);
         * 
         * req.missingSigsMode = MissingSigsMode.USE_OP_ZERO;
         * ulist.addAll(utxos); walletAppKit.wallet().completeTx(req,
         * walletAppKit.wallet().transforSpendCandidates(ulist), false,
         * addressResult); walletAppKit.wallet().signTransaction(req);
         * 
         * byte[] a = req.tx.bitcoinSerialize(); HashMap<String, Object>
         * requestParam0 = new HashMap<String, Object>();
         * requestParam0.put("orderid", (String) exchangemap.get("orderid"));
         * requestParam0.put("dataHex", Utils.HEX.encode(a));
         * requestParam0.put("signtype", "to");
         * 
         * OkHttp3Util.post(contextRoot +
         * OrdermatchReqCmd.signTransaction.name(),
         * Json.jsonmapper().writeValueAsString(requestParam0));
         * 
         * 
         * Transaction transaction = (Transaction)
         * networkParameters.getDefaultSerializer().makeTransaction(a);
         * 
         * // byte[] buf = BeanSerializeUtil.serializer(req.tx); // Transaction
         * transaction = BeanSerializeUtil.deserialize(buf, //
         * Transaction.class);
         * 
         * req = SendRequest.forTx(transaction);
         * walletAppKit1.wallet().signTransaction(req);
         * exchangeTokenComplete(req.tx);
         * 
         * HashMap<String, Object> requestParam1 = new HashMap<String,
         * Object>(); requestParam1.put("orderid", (String)
         * exchangemap.get("orderid")); requestParam1.put("dataHex",
         * Utils.HEX.encode(transaction.bitcoinSerialize()));
         * requestParam1.put("signtype", "from"); OkHttp3Util.post(contextRoot +
         * OrdermatchReqCmd.signTransaction.name(),
         * Json.jsonmapper().writeValueAsString(requestParam1));
         */
    }

    /*
     * public void exchangeTokenComplete(Transaction tx) throws Exception { //
     * get new Block to be used from server HashMap<String, String> requestParam
     * = new HashMap<String, String>(); byte[] data =
     * OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
     * Json.jsonmapper().writeValueAsString(requestParam)); Block rollingBlock =
     * networkParameters.getDefaultSerializer().makeBlock(data);
     * rollingBlock.addTransaction(tx); rollingBlock.solve();
     * 
     * String res = OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(),
     * rollingBlock.bitcoinSerialize()); System.out.println(res); }
     */
    public void payToken(ECKey outKey) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.getTip.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        logger.info("resp block, hex : " + Utils.HEX.encode(data));
        // get other tokenid from wallet
        UTXO utxo = null;
        List<UTXO> ulist = testTransactionAndGetBalances();
        for (UTXO u : ulist) {
            if (!Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGTANGLE_TOKENID)) {
                utxo = u;
            }
        }
        System.out.println(utxo.getValue());
        // Coin baseCoin = utxo.getValue().subtract(Coin.parseCoin("10000",
        // utxo.getValue().getTokenid()));
        // System.out.println(baseCoin);
        Address destination = outKey.toAddress(networkParameters);
        SendRequest request = SendRequest.to(destination, utxo.getValue());
        walletAppKit.wallet().completeTx(request,null);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        logger.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));

    }
}
