package net.bigtangle.tools.action.impl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Exchange;
import net.bigtangle.core.Json;
import net.bigtangle.core.http.ordermatch.resp.GetExchangeResponse;
import net.bigtangle.params.OrdermatchReqCmd;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.SimpleAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.PayOrder;

public class SignOrderAction extends SimpleAction {

    public SignOrderAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    private static final Logger logger = LoggerFactory.getLogger(SellOrderAction.class);

    @Override
    public void execute0() throws Exception {
        logger.info("account name : {}, sign order action start", account.getName());
        List<String> addressList = new ArrayList<String>();
        for (ECKey ecKey : this.account.walletKeys()) {
            String address = ecKey.toAddress(Configure.PARAMS).toString();
            addressList.add(address);
        }
        String response = OkHttp3Util.post(
                Configure.ORDER_MATCH_CONTEXT_ROOT + OrdermatchReqCmd.getBatchExchange.name(),
                Json.jsonmapper().writeValueAsString(addressList).getBytes());

        GetExchangeResponse getExchangeResponse = Json.jsonmapper().readValue(response, GetExchangeResponse.class);
        for (Exchange exchange : getExchangeResponse.getExchanges()) {
            try {
                /*
                 * if (exchange.getToSign() + exchange.getFromSign() == 2) { continue; }
                 */
                int toSign = exchange.getToSign();
                int fromSign = exchange.getFromSign();
                String toAddress = exchange.getToAddress();
                // mj61qqqkFDcXFx6P5bMtspDH7tJZ7jVHL4
                String fromAddress = exchange.getFromAddress();
                if (toSign == 1 && this.account.wallet().calculatedAddressHit(null,toAddress)) {
                    continue;
                }
                if (fromSign == 1 && this.account.wallet().calculatedAddressHit(null,fromAddress)) {
                    continue;
                }
               // exchangeList.add(exchange);
                try {
                    String orderid = exchange.getOrderid();
                    PayOrder payOrder = new PayOrder(this.account.wallet(), orderid, Configure.SIMPLE_SERVER_CONTEXT_ROOT,
                            Configure.ORDER_MATCH_CONTEXT_ROOT);
                    payOrder.sign();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
     
        logger.info("account name : {}, sign order action end", account.getName());
    }
}
