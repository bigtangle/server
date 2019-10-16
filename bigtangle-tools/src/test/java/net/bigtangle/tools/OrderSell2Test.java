package net.bigtangle.tools;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;
import net.bigtangle.core.response.GetBalancesResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.wallet.Wallet;

public class OrderSell2Test extends HelpTest {

    @Test
    public void sellThread() throws Exception {

        importKeys(walletAppKit2.wallet());
        importKeys(walletAppKit1.wallet());

        List<UTXO> utxo = getUTXOs(TESTSERVER1, walletAppKit2.wallet());

        while (true) {
            try {
                sell(TESTSERVER1, walletAppKit2.wallet(), utxo);
                sell(TESTSERVER2, walletAppKit1.wallet(), utxo);
                sell(TESTSERVER2, walletAppKit2.wallet(), utxo);
                sell(TESTSERVER1, walletAppKit1.wallet(), utxo);
            } catch (Exception e) {
                // TODO: handle exception
                // Thread.sleep(3000);
                log.debug("", e);
            }
        }

    }

    public void sell(String url, Wallet wallet, List<UTXO> utxos) throws Exception {

        Collections.shuffle(utxos);
        long q = Math.abs((new Random()).nextInt() % 10);
        for (UTXO utxo : utxos) {
            if (!NetworkParameters.BIGTANGLE_TOKENID_STRING.equals(utxo.getTokenId())
                    && utxo.getValue().getValue().signum() > 0 && !utxo.isSpendPending()
                    && utxo.getValue().getValue().compareTo(BigInteger.valueOf(q)) >= 0) {
                wallet.setServerURL(url);
                wallet.sellOrder(null, utxo.getTokenId(), Math.abs((new Random()).nextInt() * 10000000 % 1000000000), q,
                        null, null);

            }
        }

    }

    private List<UTXO> getUTXOs(String url, Wallet wallet)
            throws IOException, JsonProcessingException, JsonParseException, JsonMappingException {
        List<String> keyStrHex000 = new ArrayList<String>();

        for (ECKey ecKey : wallet.walletKeys()) {
            keyStrHex000.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
        }

        String response = OkHttp3Util.post(url + ReqCmd.getBalances.name(),
                Json.jsonmapper().writeValueAsString(keyStrHex000).getBytes());

        GetBalancesResponse getBalancesResponse = Json.jsonmapper().readValue(response, GetBalancesResponse.class);

        return getBalancesResponse.getOutputs();
    }

}
