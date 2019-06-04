package net.bigtangle.tools;

import java.util.HashMap;

import org.junit.Test;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Utils;
import net.bigtangle.core.http.server.resp.OrderdataResponse;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.ReqCmd;
import net.bigtangle.utils.OkHttp3Util;

public class MoneyForOrderBuyTest extends AbstractIntegrationTest {

    // let the wallet 1 has money to buy order
    @Test
    public void payMoneyToWallet1() throws Exception {
        ECKey fromkey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        giveMoneyResult.put(wallet1Keys.get(0).toAddress(networkParameters).toString(), 333333333300L);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + b.toString());

    }

    @Test
    public void payMoneyToWallet2() throws Exception {
        ECKey fromkey = new ECKey(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet2();
        giveMoneyResult.put(wallet2Keys.get(0).toAddress(networkParameters).toString(), 333333333300l);

        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + b.toString());

    }

    @Test
    public void payMoneyToTestKey() throws Exception {
    
        wallet1();
        walletAppKit1.wallet().pay(null, Address.fromBase58(MainNetParams.get(), "14a4YnkmSCBGUqcmN2PX3tzxFthrDmyDXE"),
                Coin.valueOf(333333300l, NetworkParameters.BIGTANGLE_TOKENID_STRING), "大网充值");

    }

}
