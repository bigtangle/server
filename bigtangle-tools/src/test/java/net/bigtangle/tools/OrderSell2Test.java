package net.bigtangle.tools;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class OrderSell2Test extends AbstractIntegrationTest {

    @Test
    public void sellThread() throws Exception {

        importKeys(walletAppKit2.wallet());
        importKeys(walletAppKit1.wallet());

        while (true) {
            try {
                sell(TESTSERVER1, walletAppKit2.wallet());
                sell(TESTSERVER2, walletAppKit1.wallet());
                sell(TESTSERVER2, walletAppKit2.wallet());
                sell(TESTSERVER1, walletAppKit1.wallet());
            } catch (Exception e) {
                // TODO: handle exception
                // Thread.sleep(3000);
                log.debug("", e);
            }
        }

    }

    public void sell(String url, Wallet wallet) throws Exception {

        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : wallet.walletKeys()) {
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }

        String response = OkHttp3Util.post(url + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);
        List<UTXO> utxos = getBalancesResponse.getOutputs();
        Collections.shuffle(utxos);
        long q = 8;
        for (UTXO utxo : utxos) {
            if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(utxo.getTokenId())
                    && utxo.getValue().getValue().signum() > 0
                    && utxo.getValue().getValue().compareTo(BigInteger.valueOf(q)) >= 0) {
                wallet.setServerURL(url);
                wallet.sellOrder(null, utxo.getTokenId(), 10000000, q, null, null);

            }
        }

    }

}
