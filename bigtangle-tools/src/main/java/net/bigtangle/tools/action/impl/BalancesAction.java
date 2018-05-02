package net.bigtangle.tools.action.impl;

import java.util.ArrayList;
import java.util.List;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BalancesAction extends Action {

    public BalancesAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        List<String> requestParams = new ArrayList<String>();
        for (ECKey ecKey : account.walletKeys()) {
            requestParams.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }
        String data = OkHttp3Util.post(Configure.CONTEXT_ROOT + "batchGetBalances", Json.jsonmapper().writeValueAsString(requestParams).getBytes());
        logger.info("account name : {}, Balances action resp : {} success", account.getName(), data);
    }

    private static final Logger logger = LoggerFactory.getLogger(BalancesAction.class);
}
