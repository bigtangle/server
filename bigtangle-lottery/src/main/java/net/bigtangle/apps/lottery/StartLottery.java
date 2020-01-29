/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.apps.lottery;

import java.io.File;
import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.MainNetParams;

public class StartLottery {

    private static final Logger log = LoggerFactory.getLogger(Lottery.class);
    public static String CNYTOKENIDPROD = "03bed6e75294e48556d8bb2a53caf6f940b70df95760ee4c9772681bbf90df85ba";

    //  with context_root= https://p.bigtangle.org:8088/  
    public static void main(String[] args) throws Exception {

        KeyParameter aesKey = null;
        NetworkParameters params = MainNetParams.get();

        Lottery startLottery = new Lottery();

        startLottery.context_root = args[0];

        WalletAppKit walletAdmin = new WalletAppKit(params, new File(args[1]), args[2]);
        walletAdmin.wallet().setServerURL(startLottery.context_root);
        // importKeys(walletAdmin.wallet());
        startLottery.setWalletAdmin(walletAdmin);

        startLottery.setTokenid(CNYTOKENIDPROD);
        startLottery.setWinnerAmount(new BigInteger("100000"));

        while (true) {
            startLottery.start();
        }

    }

}
