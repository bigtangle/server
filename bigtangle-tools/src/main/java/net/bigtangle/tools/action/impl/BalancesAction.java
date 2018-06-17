package net.bigtangle.tools.action.impl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.Action;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;

public class BalancesAction extends Action {

    public BalancesAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
//        try {
//            Simulator.give(this.account.getBuyKey());
//        } catch (Exception e) {
//        }
        List<String> requestParams = new ArrayList<String>();
        requestParams.add(Utils.HEX.encode(this.account.getBuyKey().getPubKeyHash()));
        try {
            String data;
            data = OkHttp3Util.post(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "batchGetBalances", Json.jsonmapper().writeValueAsString(requestParams).getBytes());
            logger.info("account name : {}, Balances action resp : {} success", this.account.getName(), data);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(BalancesAction.class);
}
