package net.bigtangle.tools;

import java.util.HashMap;

import org.junit.Test;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class OrderSellTest extends AbstractIntegrationTest {

    // buy everthing in test

    @Test
    public void sell() throws Exception {
    
        while (true) {
            Thread.sleep(5000);
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrder.name(),
                    Json.jsonmapper().writeValueAsString(requestParam).getBytes());
             
    

        }

    }

    // let the wallet 1 has money to buy order
    @Test
    public void payMoneyToWallet1() throws Exception {
        ECKey fromkey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        giveMoneyResult.put(wallet1Keys.get(0).toAddress(networkParameters).toString(), 33333333300l);

        walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);

    }

}
