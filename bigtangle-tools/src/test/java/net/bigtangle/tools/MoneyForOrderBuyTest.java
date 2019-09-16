package net.bigtangle.tools;

import java.util.HashMap;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;

public class MoneyForOrderBuyTest extends AbstractIntegrationTest {

    // let the wallet 1 has money to buy order
    @Test
    public void payMoneyToWallet1() throws Exception {
        ECKey fromkey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        for(int i=0;i<100; i++) {
        giveMoneyResult.put(wallet1Keys.get(i % wallet1Keys.size() ).toAddress(networkParameters).toString(), 3333000000L);

        }
        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + b.toString());

    }

    @Test
    public void payMoneyToWallet2() throws Exception {
        ECKey fromkey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet2();
        for(int i=0;i<100; i++) {
        giveMoneyResult.put(wallet2Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(), 333300000l);
        }
        Block b = walletAppKit.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + b.toString());

    }

 
    
}
