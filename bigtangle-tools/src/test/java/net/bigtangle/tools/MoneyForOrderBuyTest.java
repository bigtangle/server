package net.bigtangle.tools;

import java.util.HashMap;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.OrderdataResponse;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class MoneyForOrderBuyTest extends AbstractIntegrationTest {
 

    // let the wallet 1 has money to buy order
    @Test
    public void payMoneyToWallet1() throws Exception {
        ECKey fromkey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        giveMoneyResult.put(wallet1Keys.get(0).toAddress(networkParameters).toString(), 33333333300l);

     Block   b=  walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
     log.debug("block " + b.toString());

    }

}
