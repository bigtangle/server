package net.bigtangle.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class OrderSell2Test extends AbstractIntegrationTest {

    // buy everything in test

    @Test
    public void sellThread() throws Exception {

        while (true) {
            try {
                sell("https://bigtangle.org/");
                sell("https://bigtangle.de/");
            } catch (Exception e) {
                // TODO: handle exception
                // Thread.sleep(3000);
            }
        }

    }

    public void sell(String url) throws Exception {

        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : wallet2Keys) {
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }

        String response = OkHttp3Util.post(url + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        List<UTXO> utxos = getBalancesResponse.getOutputs();
        Collections.shuffle(utxos);
        for (UTXO utxo : utxos) {
            if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(utxo.getTokenId())
                    && utxo.getValue().getValue() > 0) {
                walletAppKit2.wallet().setServerURL(url);
                walletAppKit2.wallet().sellOrder(null, utxo.getTokenId(), 100, 2, null, null);

            }
        }

    }

}
