package net.bigtangle.tools;

import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import com.google.common.math.LongMath;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.Utils;

public class MoneyForOrderBuyTest extends HelpTest {

    //pay money to all keys in other wallets
    @Test
    public void payMoney () throws Exception {
        int i=0;
        while (true) {
            try {
            payMoneyToWallet1(i);
            Thread.sleep(10000);
            payMoneyToWallet2(i);
            if(i >30) {i=0;} else {i+=1;} 
            }catch (Exception e) {
                log.debug(" " ,e);
            }
        }
    }
    public void payMoneyToWallet1(int j) throws Exception {
        ECKey fromkey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        for(int i=0;i<10; i++) {
        giveMoneyResult.put(wallet1Keys.get(i % wallet1Keys.size() ).toAddress(networkParameters).toString(),
                3333000000L/ LongMath.pow(2, j));

        }
        List<UTXO> l = walletAppKit1.wallet().calculateAllSpendCandidatesUTXO(null, false);
        for(UTXO u: l) {
        log.debug(u.toString());
        }
        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + (b==null? "block is null": b.toString()));

    }

   
    public void payMoneyToWallet2(int j) throws Exception {
        ECKey fromkey =  ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv), Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet2();
        for(int i=0;i<10; i++) {
        giveMoneyResult.put(wallet2Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(),
                333300000l/ LongMath.pow(2, j));
        }
        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + (b==null? "block is null": b.toString()));

    }

 
    
}
