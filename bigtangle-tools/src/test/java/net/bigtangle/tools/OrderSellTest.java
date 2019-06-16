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

public class OrderSellTest extends AbstractIntegrationTest {

    // buy everything in test

  

    @Test
    public void sellThread() throws Exception {

        while (true) {
            try {
        //    sell(HTTPS_BIGTANGLE_INFO);
            sell(HTTPS_BIGTANGLE_ORG);
            }catch (Exception e) {
                // TODO: handle exception
             //   Thread.sleep(3000);
            }
        }

    }

    public void sell(String url) throws Exception {

        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : wallet1Keys) {
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }

        String response = OkHttp3Util.post(url + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        List<UTXO> utxos = getBalancesResponse.getOutputs();
        Collections.shuffle(utxos);
        for (UTXO utxo : utxos) {
            if(!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(utxo.getTokenId())
                    && utxo.getValue().getValue() >0 )
            {
                walletAppKit1.wallet().setServerURL(url);
            walletAppKit1.wallet().sellOrder(null, utxo.getTokenId(), 100, 2, null, null);
            
            }
        }

    }

}
