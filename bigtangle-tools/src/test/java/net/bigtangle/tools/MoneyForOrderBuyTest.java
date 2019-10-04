package net.bigtangle.tools;

import java.util.HashMap;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;

public class MoneyForOrderBuyTest extends AbstractIntegrationTest {

    //pay money to all keys in other wallets
    @Test
    public void payMoney () throws Exception {
        while (true) {
            try {
            payMoneyToWallet1();
            Thread.sleep(10000);
            payMoneyToWallet2();
            }catch (Exception e) {
                log.debug(" " ,e);
            }
        }
    }
    public void payMoneyToWallet1() throws Exception {
        ECKey fromkey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        for(int i=0;i<100; i++) {
        giveMoneyResult.put(wallet1Keys.get(i % wallet1Keys.size() ).toAddress(networkParameters).toString(), 3333000000L);

        }
        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + b==null? "block is null": b.toString());

    }

   
    public void payMoneyToWallet2() throws Exception {
        ECKey fromkey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet2();
        for(int i=0;i<100; i++) {
        giveMoneyResult.put(wallet2Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(), 333300000l);
        }
        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + b.toString());

    }

 
    
}
