/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.apps.lottery;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import net.bigtangle.core.NetworkParameters;
import net.bigtangle.kits.WalletAppKit;
import net.bigtangle.params.MainNetParams;

public class StartLottery {

    private static final Logger log = LoggerFactory.getLogger(Lottery.class);

    // "http://localhost:8088/";//
    public static void main(String[] args) throws Exception {

        Lottery startLottery = new Lottery();
        if (args.length > 1) {
            startLottery.context_root = args[0];
            startLottery.context_root = args[1];
        }
        KeyParameter aesKey = null;
        NetworkParameters params= MainNetParams.get();

        WalletAppKit walletAdmin = new WalletAppKit(params, new File("/home/cui/Downloads"), "201811210100000002");
        walletAdmin.wallet().setServerURL(startLottery.context_root);
        // importKeys(walletAdmin.wallet());
        startLottery.setWalletAdmin(walletAdmin);

    }

}
