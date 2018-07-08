package net.bigtangle.tools.action.impl;

import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.OrderState;

public class BuyOrderAction extends Action {

    public BuyOrderAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute0() throws Exception {
        logger.info("account name : {}, buy order action start", account.getName());
        try {
            String resp = OkHttp3Util.postString(Configure.ORDER_MATCH_CONTEXT_ROOT + "getOrders",
                    Json.jsonmapper().writeValueAsString(new HashMap<String, Object>()));
            HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
            List<HashMap<String, Object>> list = (List<HashMap<String, Object>>) result.get("orders");
            if (list == null || list.isEmpty())
                return;
            for (HashMap<String, Object> map : list) {
                int state = (Integer) map.get("state");
                if (state != OrderState.publish.ordinal()) {
                    continue;
                }
                String tokenHex = (String) map.get("tokenid");
                HashMap<String, Object> requestParams = new HashMap<String, Object>();
                ECKey ecKey = this.account.getBuyKey();
                requestParams.put("address", ecKey.toAddress(Configure.PARAMS).toBase58());
                requestParams.put("tokenid", tokenHex);
                requestParams.put("type", 2);

                int price = (Integer) map.get("price");
                int amount = (Integer) map.get("amount");
                requestParams.put("price", price);
                requestParams.put("amount", amount);
                OkHttp3Util.post(Configure.ORDER_MATCH_CONTEXT_ROOT + "saveOrder",
                        Json.jsonmapper().writeValueAsString(requestParams).getBytes());
            }
        } catch (Exception e) {
            logger.error("account name : {}, buy order action exception", account.getName(), e);
        }
        logger.info("account name : {}, buy order action end", account.getName());
    }

    private static final Logger logger = LoggerFactory.getLogger(BuyOrderAction.class);
}
