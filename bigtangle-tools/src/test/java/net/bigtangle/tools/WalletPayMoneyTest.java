package net.bigtangle.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;

import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;
import net.bigtangle.kits.WalletAppKit;

public class WalletPayMoneyTest extends AbstractIntegrationTest {

    @Test
    public void pay() throws Exception {
        importKeys(walletAppKit2.wallet());
        importKeys(walletAppKit1.wallet());

        walletAppKit1.wallet().setServerURL(HTTPS_BIGTANGLE_LOCAL);
        walletAppKit2.wallet().setServerURL(HTTPS_BIGTANGLE_LOCAL);

        List<WalletAppKit> walletAppKits = new ArrayList<WalletAppKit>();
        for (int i = 10; i < 20; i++) {
            WalletAppKit walletAppKit = this.createAndInitWallet("bigtangle" + i);
            walletAppKit.wallet().setServerURL(HTTPS_BIGTANGLE_LOCAL);
            walletAppKits.add(walletAppKit);
        }

        HashMap<Integer, WalletBook> bookData = new HashMap<Integer, WalletBook>();
        for (WalletBook walletBook : payCoinbaseToWallet(walletAppKits)) {
            bookData.put(walletBook.getWalletID(), walletBook);
        }

        while (true) {

        }
    }

    private List<WalletBook> payCoinbaseToWallet(List<WalletAppKit> walletAppKits) throws Exception {
        List<WalletBook> walletBooks = new ArrayList<WalletBook>();
        int i = 0;
        for (WalletAppKit walletAppKit : walletAppKits) {
            WalletBook walletBook = new WalletBook(i++, walletAppKit);

            for (ECKey fromkey : this.wallet1Keys) {
                ECKey ecKey = new ECKey();
                walletAppKit.wallet().importKey(ecKey);

                HashMap<String, Long> giveMoneyResult = new HashMap<String, Long>();
                giveMoneyResult.put(ecKey.toAddress(networkParameters).toString(), 1l);
                walletAppKit1.wallet().payMoneyToECKeyList(null, giveMoneyResult, fromkey);
                
                WalletKeyBook walletKeyBook = new WalletKeyBook(ecKey, this.sumUTXOBalance(Utils.HEX.encode(fromkey.getPubKey()), ecKey));
                walletBook.addWalletKeyBook(walletKeyBook);
            }
        }
        return walletBooks;
    }
}
