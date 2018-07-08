package net.bigtangle.tools.action.impl;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;

public class SellOrderAction extends Action {

    public SellOrderAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        logger.info("account name : {}, sell order action start", account.getName());
        try {
            HashMap<String, Object> requestParams = new HashMap<String, Object>();
            ECKey ecKey = account.getRandomTradeECKey();
            String address = ecKey.toAddress(Configure.PARAMS).toBase58();
            String tokenid = ecKey.getPublicKeyAsHex();
            requestParams.put("address", address);
            requestParams.put("tokenid", tokenid);
            requestParams.put("type", 1);
            requestParams.put("price", 1000);
            requestParams.put("amount", 1);
            OkHttp3Util.post(Configure.ORDER_MATCH_CONTEXT_ROOT + "saveOrder",
                    Json.jsonmapper().writeValueAsString(requestParams).getBytes());
        } catch (Exception e) {
            logger.error("account name : {}, sell order action exception", account.getName(), e);
        }
        logger.info("account name : {}, sell order action end", account.getName());
    }

    private static final Logger logger = LoggerFactory.getLogger(SellOrderAction.class);
}
