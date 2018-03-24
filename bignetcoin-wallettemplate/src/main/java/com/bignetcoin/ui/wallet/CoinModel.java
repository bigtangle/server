package com.bignetcoin.ui.wallet;

import javafx.beans.property.SimpleLongProperty;

public class CoinModel {
    private SimpleLongProperty value;
    private SimpleLongProperty tokenid;

    public SimpleLongProperty value() {
        return value;
    }

    public SimpleLongProperty tokenid() {
        return tokenid;
    }

    public long getValue() {
        return value.get();
    }

    public void setValue(long value) {
        this.value.set(value);
    }

    public long getTokenid() {
        return tokenid.get();
    }

    public void setTokenid(int tokenid) {
        this.tokenid.set(tokenid);
    }

    public CoinModel(long value, long tokenid) {
        super();
        this.value = new SimpleLongProperty(value);
        this.tokenid = new SimpleLongProperty(tokenid);
    }

}
