package net.bigtangle.tools.test;

import java.util.HashMap;

import org.junit.Test;

import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.exception.InsufficientMoneyException;
import net.bigtangle.core.response.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class OrderBuyTest extends HelpTest {

    // buy everything in test
    @Test
    public void buyTest() throws Exception {
        buy();
    }
   
    public void buy() throws Exception {
        setUp();
        importKeys(walletAppKit2.wallet());
        importKeys(walletAppKit1.wallet());

        while (true) {

            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            String response0 = OkHttp3Util.post(contextRoot + ReqCmd.getOrders.name(),
                    Json.jsonmapper().writeValueAsString(requestParam).getBytes());

            OrderdataResponse orderdataResponse = Json.jsonmapper().readValue(response0, OrderdataResponse.class);
           if( orderdataResponse.getAllOrdersSorted().isEmpty())   Thread.sleep(4000);
            int i = 0;
            for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
                try {
                    if (i % 2 == 0) {
                        if (isWallet1Token(orderRecord, orderdataResponse)) {
                            buy(TESTSERVER1, walletAppKit2.wallet(), orderRecord);
                        } else {
                            buy(TESTSERVER2, walletAppKit1.wallet(), orderRecord);
                        }
                    } else {
                        if (isWallet1Token(orderRecord, orderdataResponse)) {
                            buy(TESTSERVER1, walletAppKit2.wallet(), orderRecord);
                        } else {
                            buy(TESTSERVER2, walletAppKit1.wallet(), orderRecord);
                        }
                    }
                    i += 1;
                } catch (InsufficientMoneyException e) {
                    Thread.sleep(4000);
                } catch (Exception e) {
                    log.debug("", e);
                }
            }

        }

    }

    private boolean isWallet1Token(OrderRecord orderRecord, OrderdataResponse orderdataResponse) {
        return orderdataResponse.getTokennames().get(orderRecord.getOfferTokenid()).getTokenname().contains("test-1");

    }

    public void buy(String url, Wallet w, OrderRecord orderRecord) throws Exception {

        if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(orderRecord.getOfferTokenid())) {
            // sell order and make buy
            long price = orderRecord.getTargetValue() / orderRecord.getOfferValue();
            w.setServerURL(url);
            w.buyOrder(null, orderRecord.getOfferTokenid(), price, orderRecord.getOfferValue(), null, null);
        }

    }
}
