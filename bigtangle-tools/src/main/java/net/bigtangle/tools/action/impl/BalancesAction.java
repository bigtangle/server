package net.bigtangle.tools.action.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bigtangle.core.ECKey;
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

    @SuppressWarnings("unchecked")
    @Override
    public void execute0() throws Exception {
        try {
            Set<String> pubKeyHashs = new HashSet<String>();
            for (ECKey ecKey : this.account.walletKeys()) {
                pubKeyHashs.add(Utils.HEX.encode(ecKey.toAddress(Configure.PARAMS).getHash160()));
            }
            String resp = OkHttp3Util.postString(Configure.SIMPLE_SERVER_CONTEXT_ROOT + "batchGetBalances",
                    Json.jsonmapper().writeValueAsString(pubKeyHashs));
            final Map<String, Object> data = Json.jsonmapper().readValue(resp, Map.class);
            if (data == null || data.isEmpty()) {
                return;
            }
            List<Map<String, Object>> tokens = (List<Map<String, Object>>) data.get("tokens");
            if (tokens == null || tokens.isEmpty()) {
                return;
            }
            this.account.syncTokenCoinbase(tokens);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
