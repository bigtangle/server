package net.bigtangle.tools;

import java.util.HashMap;
import java.util.Random;

import org.junit.Test;

import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.http.server.resp.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class OrderBuyTest extends AbstractIntegrationTest {

    // buy everthing in test

    @Test
    public void buy() throws Exception {

        while (true) {

            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            String response0 = OkHttp3Util.post("https://bigtangle.de/" + ReqCmd.getOrders.name(),
                    Json.jsonmapper().writeValueAsString(requestParam).getBytes());

            OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);

            int i = 0;
            for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
                if (i % 2 == 0) {
                    buy("https://bigtangle.org/", walletAppKit.wallet(), orderRecord);
                } else {
                    buy("https://bigtangle.de/", walletAppKit1.wallet(), orderRecord);
                }
                i+=1;
            }

        }

    }

    public void buy(String url, Wallet w, OrderRecord orderRecord) throws Exception {

        HashMap<String, Object> map = new HashMap<String, Object>();

        if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(orderRecord.getOfferTokenid())) {
            // sell order and make buy
            long price = orderRecord.getTargetValue() / orderRecord.getOfferValue();
            w.setServerURL(url);
            w.buyOrder(null, orderRecord.getOfferTokenid(), price, orderRecord.getOfferValue(), null, null);
        }

    }
}
