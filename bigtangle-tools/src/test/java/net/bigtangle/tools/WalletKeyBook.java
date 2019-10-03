package net.bigtangle.tools;

import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;

public class WalletKeyBook {

    public WalletKeyBook(ECKey walletKey, Coin coinbase) {
        this.walletKey = walletKey;
        this.coinbase = coinbase;
        this.tokenHex = coinbase.getTokenHex();
    }

    private ECKey walletKey;

    private String tokenHex;

    private Coin coinbase;

    public void addCurrentAmount(final long amount) {
        coinbase.add(Coin.valueOf(amount, tokenHex));
    }

    public void subCurrentAmount(long amount) {
        coinbase.add(Coin.valueOf(-amount, tokenHex));
    }

    public ECKey getWalletKey() {
        return walletKey;
    }
}
