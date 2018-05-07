package net.bigtangle.tools.action.impl;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bigtangle.core.Json;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.utils.RandomTrade;
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
        try {
            HashMap<String, Object> requestParams = new HashMap<String, Object>();
            RandomTrade random0 = account.getRandomTrade();
            requestParams.put("address", random0.getAddress());
            requestParams.put("tokenid", random0.getTokenID());
            requestParams.put("type", 1);
            requestParams.put("price", 1000);
            requestParams.put("amount", 1);
            String data = OkHttp3Util.post(Configure.CONTEXT_ROOT + "saveOrder", Json.jsonmapper().writeValueAsString(requestParams).getBytes());
            logger.info("account name : {}, sellOrder action resp : {} success", account.getName(), data);
        }
        catch (Exception e) {
            logger.error("account name : {}, sellOrder action fail", account.getName(), e);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(SellOrderAction.class);
}
