package net.bigtangle.tools.test;

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

public class OrderCancel extends HelpTest {

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
			int count = 0;

			for (OrderRecord orderRecord : orderdataResponse.getAllOrdersSorted()) {
				if (!orderRecord.isCancelPending()) {
					count += 1;
					cancel(TESTSERVER1, walletAppKit1.wallet(), orderRecord);
					// cancel(HTTPS_BIGTANGLE_DE, walletAppKit2.wallet(), orderRecord);
				}
			}
			log.debug(" Total Order size = " + orderdataResponse.getAllOrdersSorted().size() + " canceled ordrs ="
					+ count);
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
