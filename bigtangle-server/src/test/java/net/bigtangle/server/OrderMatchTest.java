package net.bigtangle.server;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.PayOrder;
import net.bigtangle.wallet.SendRequest;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderMatchTest extends AbstractIntegrationTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderMatchTest.class);
	
	public void payToken(ECKey outKey) throws Exception {
        HashMap<String, String> requestParam = new HashMap<String, String>();
        byte[] data = OkHttp3Util.post(contextRoot + ReqCmd.askTransaction.name(),
                Json.jsonmapper().writeValueAsString(requestParam));
        Block rollingBlock = networkParameters.getDefaultSerializer().makeBlock(data);
        LOGGER.info("resp block, hex : " + Utils.HEX.encode(data));
        UTXO utxo = null;
        List<UTXO> ulist = testTransactionAndGetBalances();
        for (UTXO u : ulist) {
            if (!Arrays.equals(u.getTokenidBuf(), NetworkParameters.BIGNETCOIN_TOKENID)) {
                utxo = u;
            }
        }
        System.out.println(utxo.getValue());
        Address destination = outKey.toAddress(networkParameters);
        SendRequest request = SendRequest.to(destination, utxo.getValue());
        walletAppKit.wallet().completeTx(request);
        rollingBlock.addTransaction(request.tx);
        rollingBlock.solve();
        OkHttp3Util.post(contextRoot + ReqCmd.saveBlock.name(), rollingBlock.bitcoinSerialize());
        LOGGER.info("req block, hex : " + Utils.HEX.encode(rollingBlock.bitcoinSerialize()));
    }

	@SuppressWarnings("unchecked")
    @Test
    public void exchangeOrder() throws Exception {
        String marketURL = "http://localhost:8089/";
        
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
        request.put("tokenid", yourutxo.getTokenId());
        request.put("type", 1);
        request.put("price", 1000);
        request.put("amount", 1000);
        System.out.println("req : " + request);
        // sell token order
        String response = OkHttp3Util.post(marketURL + "saveOrder",
                Json.jsonmapper().writeValueAsString(request).getBytes());
        
        request.put("address", myutxo.getAddress());
        request.put("tokenid", yourutxo.getTokenId());
        request.put("type", 2);
        request.put("price", 1000);
        request.put("amount", 1000);
        System.out.println("req : " + request);
        // buy token order
          response = OkHttp3Util.post(marketURL + "saveOrder",
                Json.jsonmapper().writeValueAsString(request).getBytes());

        Thread.sleep(10000);

        HashMap<String, Object> requestParam = new HashMap<String, Object>();
        requestParam.put("address", myutxo.getAddress());
          response = OkHttp3Util.post(marketURL + "getExchange",
                Json.jsonmapper().writeValueAsString(requestParam).getBytes());
        final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
        List<Map<String, Object>> list = (List<Map<String, Object>>) data.get("exchanges");
        assertTrue(list.size() >= 1);
        Map<String, Object> exchangemap = list.get(0);
        
        String serverURL = contextRoot;
        String orderid = (String) exchangemap.get("orderid");
        
        PayOrder payOrder1 = new PayOrder(walletAppKit.wallet(), orderid, serverURL, marketURL);
        payOrder1.sign();
        
        PayOrder payOrder2 = new PayOrder(walletAppKit1.wallet(), orderid, serverURL, marketURL);
        payOrder2.sign();
    }
}

