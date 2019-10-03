package net.bigtangle.tools;

import java.util.ArrayList;
import java.util.List;

import net.bigtangle.core.ECKey;
import net.bigtangle.kits.WalletAppKit;

public class WalletBook {

    public List<ECKey> walletKeys() {
        return this.walletAppKit.wallet().walletKeys();
    }

    private int walletID;

    private WalletAppKit walletAppKit;

    public int getWalletID() {
        return walletID;
    }

    public WalletAppKit getWalletAppKit() {
        return walletAppKit;
    }

    public WalletBook(int walletID, WalletAppKit walletAppKit) {
        this.walletID = walletID;
        this.walletAppKit = walletAppKit;
        this.walletKeyBooks = new ArrayList<WalletKeyBook>();
    }
    
    public List<WalletKeyBook> walletKeyBooks;
    
    public void addWalletKeyBook(WalletKeyBook walletKeyBook) {
        this.walletKeyBooks.add(walletKeyBook);
    }
}
