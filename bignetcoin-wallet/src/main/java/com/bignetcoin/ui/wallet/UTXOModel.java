/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package com.bignetcoin.ui.wallet;

import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;

public class UTXOModel {
    private SimpleLongProperty balance;
    private SimpleStringProperty tokentype;
    private SimpleStringProperty address;
    private SimpleStringProperty tokenid;

    public UTXOModel(long balance, byte[] tokenid, String address) {
        this.balance = new SimpleLongProperty(balance);
        this.tokenid = new SimpleStringProperty(Utils.HEX.encode(tokenid));
        this.tokentype = new SimpleStringProperty(Utils.HEX.encode(tokenid));
        this.address = new SimpleStringProperty(address);
    }

    public SimpleLongProperty balance() {
        return balance;
    }

    public SimpleStringProperty tokentype() {
        return tokentype;
    }

    public SimpleStringProperty tokenid() {
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

    public String getTokenid() {
        return tokenid.get();
    }

    public void setTokenid(String tokenid) {
        this.tokenid.set(tokenid);
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

}
