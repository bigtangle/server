package com.bignetcoin.ui.wallet;

import org.bitcoinj.core.NetworkParameters;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

public class UTXOModel {
    private SimpleLongProperty balance;
    private SimpleStringProperty tokentype;
    private SimpleStringProperty address;
    private SimpleLongProperty tokenid;

    public UTXOModel(long balance, long tokenid, String address) {
        this.balance = new SimpleLongProperty(balance);
        this.tokenid = new SimpleLongProperty(tokenid);
        this.tokentype = new SimpleStringProperty(
                tokenid == NetworkParameters.BIGNETCOIN_TOKENID ? "bignetcoin" : "other");
        this.address = new SimpleStringProperty(address);
    }

    public SimpleLongProperty balance() {
        return balance;
    }

    public SimpleStringProperty tokentype() {
        return tokentype;
    }

    public SimpleLongProperty tokenid() {
        return tokenid;
    }

    public SimpleStringProperty address() {
        return address;
    }

    public long getBalance() {
        return balance.get();
    }

    public void setBalance(long balance) {
        this.balance.set(balance);
    }

    public String getTokentype() {
        return tokentype.get();
    }

    public void setTokentype(String tokentype) {
        this.tokentype.set(tokentype);
    }

    public long getTokenid() {
        return tokenid.get();
    }

    public void setTokenid(long tokenid) {
        this.tokenid.set(tokenid);
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

}
