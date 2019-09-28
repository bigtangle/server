package net.bigtangle.tools.action.impl;

import java.util.HashSet;
import java.util.Set;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.tools.account.Account;
import net.bigtangle.tools.action.SimpleAction;
import net.bigtangle.tools.config.Configure;
import net.bigtangle.utils.OkHttp3Util;

public class BalancesAction extends SimpleAction {

    public BalancesAction(Account account) {
        super(account);
    }

    @Override
    public void callback() {
    }

    @Override
    public void execute0() throws Exception {
        try {
            Set<String> pubKeyHashs = new HashSet<String>();
            for (ECKey ecKey : this.account.walletKeys()) {
                pubKeyHashs.add(Utils.HEX.encode(ecKey.toAddress(Configure.PARAMS).getHash160()));
            }
            String resp = OkHttp3Util.postString(Configure.SIMPLE_SERVER_CONTEXT_ROOT + ReqCmd.getBalances.name(),
                    Json.jsonmapper().writeValueAsString(pubKeyHashs));
            GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(resp, GetBalancesResponse.class);
            this.account.syncTokenCoinbase(getBalancesResponse.getBalance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
