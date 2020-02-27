package net.bigtangle.tools.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import net.bigtangle.core.Block;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.tools.SendEmptyBlock;

public class MoneyForOrderBuyTest2 extends HelpTest {

    @Test
    public void payMoney() throws Exception {

        // Test pay to mi@bigtangle.net
        List<String> paylist = Arrays.asList("16fbFkW7hS2WZPT7cDLk8S1zmA6UQ4Hzvi",
                //buy@bigtangle.net
                //"14jWa37yQnFEicGeHW2jV2vehLgRRKftFL",
                //sell@bigtangle.net
                //"1D5MymgyrtE1z8VaDpYD2bUmLydRt64ncU",
                "1DFZ2SMWFLXXgwdUTh2ZfyoWKhPdec4Cd1"
                );
        importKeys(walletAppKit1.wallet());
        for (String p : paylist) {
            payTokenToWallet1(p,  1888800000l,
                    NetworkParameters.BIGTANGLE_TOKENID);
            payTokenToWallet1(p, 10000l,  ECKey.fromPrivate(Utils.HEX.decode(yuanTokenPriv)).getPubKey());
           
        }
    }

    public void payTokenToWallet1(String address,  Long amount, byte[] tokenid) throws Exception {

        HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
        giveMoneyResult.put(address, amount);
      
        Block b = walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, tokenid, "", 3, 100000);
        log.debug("block " + (b == null ? "block is null" : b.toString()));
    }

}
