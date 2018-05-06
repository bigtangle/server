package net.bigtangle.tools.action.impl;

import java.util.HashMap;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.tools.container.TokenPost;
import net.bigtangle.utils.OkHttp3Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyOrderAction extends Action {

    public BuyOrderAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        try {
            HashMap<String, Object> requestParams = new HashMap<String, Object>();
            ECKey ecKey = this.account.getBuyKey();
            requestParams.put("address", ecKey.toAddress(Configure.PARAMS).toBase58());
            String tokenHex = TokenPost.getInstance().randomTokenHex();
            requestParams.put("tokenid", tokenHex);
            requestParams.put("type", 2);
            requestParams.put("price", 1000);
            requestParams.put("amount", 1);
            String data = OkHttp3Util.post(Configure.CONTEXT_ROOT + "saveOrder", Json.jsonmapper().writeValueAsString(requestParams).getBytes());
            logger.info("account name : {}, buyOrder action resp : {} success", account.getName(), data);
        }
        catch (Exception e) {
            logger.error("account name : {}, buyOrder action fail", account.getName(), e);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(BuyOrderAction.class);
}
