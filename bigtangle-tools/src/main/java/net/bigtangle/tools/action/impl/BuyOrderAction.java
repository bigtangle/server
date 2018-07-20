package net.bigtangle.tools.action.impl;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.OrderPublish;
import net.bigtangle.core.http.ordermatch.resp.GetOrderResponse;
import net.bigtangle.params.OrdermatchReqCmd;
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

    @Override
    public void execute0() throws Exception {
        /*Coin coinbase = this.account.defaultCoinAmount();
        if (coinbase.getValue() <= 0) {
            return;
        }*/
        //logger.info("account name : {}, buy order action start", account.getName());
        try {
            String resp = OkHttp3Util.postString(Configure.ORDER_MATCH_CONTEXT_ROOT + OrdermatchReqCmd.getOrders.name(),
                    Json.jsonmapper().writeValueAsString(new HashMap<String, Object>()));
            GetOrderResponse getOrderResponse = Json.jsonmapper().readValue(resp, GetOrderResponse.class);
            for (OrderPublish orderPublish : getOrderResponse.getOrders()) {
                if (orderPublish.getState() != OrderState.publish.ordinal()) {
                    continue;
                }
                if (this.account.wallet().calculatedAddressHit(orderPublish.getAddress())) {
                    continue;
                }
                if (orderPublish.getType() != 1) {
                    continue;
                }
                HashMap<String, Object> requestParams = new HashMap<String, Object>();
                ECKey ecKey = this.account.getBuyKey();
                requestParams.put("address", ecKey.toAddress(Configure.PARAMS).toBase58());
                requestParams.put("tokenid", orderPublish.getTokenId());
                requestParams.put("type", 2);
//                if (orderPublish.getPrice() > coinbase.getValue()) {
//                    continue;
//                }
                requestParams.put("price", orderPublish.getPrice());
                requestParams.put("amount", orderPublish.getAmount());
                OkHttp3Util.post(Configure.ORDER_MATCH_CONTEXT_ROOT + OrdermatchReqCmd.saveOrder,
                        Json.jsonmapper().writeValueAsString(requestParams).getBytes());
//                coinbase.subtract(Coin.valueOf(orderPublish.getPrice(), NetworkParameters.BIGNETCOIN_TOKENID_STRING));
            }
        } catch (Exception e) {
            logger.error("account name : {}, buy order action exception", account.getName(), e);
        }
        //logger.info("account name : {}, buy order action end", account.getName());
    }

    private static final Logger logger = LoggerFactory.getLogger(BuyOrderAction.class);
}
