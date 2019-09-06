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
import net.bigtangle.wallet.Wallet;

public class OrderSell2Test extends AbstractIntegrationTest {

    // sell tokens from wallet 1 and 2 on different servers 

    @Test
    public void sellThread() throws Exception {

        importKeys(walletAppKit2.wallet());
        importKeys(walletAppKit1.wallet());
        importKeys(walletAppKit.wallet());
        while (true) {
            try {
                sell(HTTPS_BIGTANGLE_INFO,walletAppKit2.wallet());
                sell(HTTPS_BIGTANGLE_DE,walletAppKit1.wallet());
                sell(HTTPS_BIGTANGLE_DE,walletAppKit2.wallet());
                sell(HTTPS_BIGTANGLE_INFO,walletAppKit1.wallet());
            } catch (Exception e) {
                // TODO: handle exception
                // Thread.sleep(3000);
                log.debug("",e);
            }
        }

    }

    public void sell(String url, Wallet wallet) throws Exception {

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
                    && utxo.getValue().getValue().signum() > 0) {
                wallet .setServerURL(url);
                wallet.sellOrder(null, utxo.getTokenId(), 100, 2, null, null);

            }
        }

    }

}
