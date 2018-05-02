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

public class ExchangeAction extends Action {

    public ExchangeAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        for (ECKey ecKey : this.account.walletKeys()) {
            String address = ecKey.toAddress(Configure.PARAMS).toString();
            HashMap<String, Object> requestParam = new HashMap<String, Object>();
            requestParam.put("address", address);
            String data = OkHttp3Util.post(Configure.CONTEXT_ROOT + "getExchange", Json.jsonmapper().writeValueAsString(requestParam).getBytes());
            logger.info("account name : {}, exchange action resp : {} success", account.getName(), data);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(ExchangeAction.class);
}
