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

    // pay money to all keys in other wallets
    @Test
    public void payMoney() throws Exception {
        int i = 0;
        while (true) {
            try {
             //   payMoneyToWallet1(i, ECKey.fromPrivate(Utils.HEX.decode(testPriv)));
                payTokenToWallet1(i, ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)));
                payTokenToWallet1(i, ECKey.fromPrivate(Utils.HEX.decode(USDTokenPriv)));
                if (i > 30) {
                    i = 0;
                } else {
                    i += 1;
                }
            } catch (Exception e) {
                log.debug(" ", e);
            }
        }
    }

    public void payMoneyToWallet1(int j, ECKey fromkey) throws Exception {
        // ECKey fromkey =
        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
        // Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        for (int i = 0; i < 10; i++) {
            giveMoneyResult.put(wallet1Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(),
                    3333000000L / LongMath.pow(2, j));

        }
        for (int i = 0; i < 10; i++) {
            giveMoneyResult.put(wallet2Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(),
                    333300000l / LongMath.pow(2, j));
        }

        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
        log.debug("block " + (b == null ? "block is null" : b.toString()));
    }

    public void payTokenToWallet1(int j, ECKey fromkey) throws Exception {
        // ECKey fromkey =
        // ECKey.fromPrivateAndPrecalculatedPublic(Utils.HEX.decode(testPriv),
        // Utils.HEX.decode(testPub));
        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        wallet1();
        for (int i = 0; i < 10; i++) {
            giveMoneyResult.put(wallet1Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(),
                    666L / LongMath.pow(2, j));

        }
        for (int i = 0; i < 10; i++) {
            giveMoneyResult.put(wallet2Keys.get(i % wallet1Keys.size()).toAddress(networkParameters).toString(),
                    555l / LongMath.pow(2, j));
        }

        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey, fromkey.getPubKey(), "", 3,
                1000);
        log.debug("block " + (b == null ? "block is null" : b.toString()));
    }

}
