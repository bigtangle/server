package net.bigtangle.tools.action.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.PayOrder;

public class SignOrderAction extends Action {

    public SignOrderAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    private static final Logger logger = LoggerFactory.getLogger(SellOrderAction.class);

    @SuppressWarnings("unchecked")
    @Override
    public void execute0() throws Exception {
        logger.info("account name : {}, sign order action start", account.getName());
        List<Map<String, Object>> exchangeList = new ArrayList<Map<String, Object>>();
        for (ECKey key : this.account.walletKeys()) {
            try {
                String address = key.toAddress(Configure.PARAMS).toString();
                HashMap<String, Object> requestParam = new HashMap<String, Object>();
                requestParam.put("address", address);
                String response = OkHttp3Util.post(Configure.ORDER_MATCH_CONTEXT_ROOT + "getExchange",
                        Json.jsonmapper().writeValueAsString(requestParam).getBytes());
                final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
                if (data == null) {
                    continue;
                }
                List<Map<String, Object>> exchanges = (List<Map<String, Object>>) data.get("exchanges");
                if (exchanges == null || exchanges.isEmpty()) {
                    continue;
                }
                for (Map<String, Object> result : exchanges) {
                    try {
                        if ((Integer) result.get("toSign") + (Integer) result.get("fromSign") == 2) {
                            continue;
                        }
                        int toSign = (int) result.get("toSign");
                        int fromSign = (int) result.get("fromSign");
                        String toAddress = (String) result.get("toAddress");
                        String fromAddress = (String) result.get("fromAddress");
                        if (toSign == 1 && this.account.wallet().calculatedAddressHit(toAddress)) {
                            continue;
                        }
                        if (fromSign == 1 && this.account.wallet().calculatedAddressHit(fromAddress)) {
                            continue;
                        }
                        exchangeList.add(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }
        for (Map<String, Object> result : exchangeList) {
            try {
                String orderid = (String) result.get("orderid");
                PayOrder payOrder = new PayOrder(this.account.wallet(), orderid, Configure.SIMPLE_SERVER_CONTEXT_ROOT,
                        Configure.ORDER_MATCH_CONTEXT_ROOT);
                payOrder.sign();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        logger.info("account name : {}, sign order action end", account.getName());
    }
}
