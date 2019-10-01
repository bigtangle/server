package net.bigtangle.tools;

import java.util.HashMap;

import org.junit.Test;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class OrderCancel extends AbstractIntegrationTest {

    // cancel all orders

    @Test
    public void cancel() throws Exception {

        importKeys(walletAppKit2.wallet());
        importKeys(walletAppKit1.wallet());

        while (true) {

            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                    Json.jsonmapper().writeValueAsString(requestParam).getBytes());

            OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);

            for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
                cancel(HTTPS_BIGTANGLE_DE, walletAppKit1.wallet(), orderRecord);
                cancel(HTTPS_BIGTANGLE_DE, walletAppKit2.wallet(), orderRecord);

            }

        }

    }

    public void cancel(String url, Wallet w, OrderRecord orderRecord) throws Exception {

        if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(orderRecord.getOfferTokenid())) {
            w.setServerURL(url);
            for (ECKey ecKey : w.walletKeys()) {
                if (orderRecord.getBeneficiaryPubKey().equals(ecKey.getPubKey())) {
                    w.cancelOrder(orderRecord.getBlockHash(), ecKey);
                    break;
                }
            }
        }

    }

}
