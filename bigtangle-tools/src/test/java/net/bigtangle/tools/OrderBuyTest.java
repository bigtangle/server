package net.bigtangle.tools;

import java.util.HashMap;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class OrderBuyTest extends AbstractIntegrationTest {

    // buy everthing in test

    @Test
    public void buy() throws Exception {
        wallet1();
        while (true) {
         
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                    Json.jsonmapper().writeValueAsString(requestParam).getBytes());
             
            OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);

            for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
                HashMap<String, Object> map = new HashMap<String, Object>();

                if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(orderRecord.getOfferTokenid())) {
                    // sell order and make buy
                    long price = orderRecord.getTargetValue() / orderRecord.getOfferValue();
                   walletAppKit1.wallet().buyOrder(null, orderRecord.getOfferTokenid(), price,
                            orderRecord.getOfferValue(), null, null);
                }

            }
            Thread.sleep(5000);
        }
     
    }

    // let the wallet 1 has money to buy order
    @Test
    public void payMoneyToWallet1() throws Exception {
        ECKey fromkey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        giveMoneyResult.put(wallet1Keys.get(0).toAddress(networkParameters).toString(), 33333333300l);

     Block   b=  walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
     log.debug("block " + b.toString());

    }

}
