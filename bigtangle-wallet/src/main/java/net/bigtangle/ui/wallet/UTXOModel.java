/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.ui.wallet;

import javafx.beans.property.SimpleStringProperty;
import net.bigtangle.core.Utils;

public class UTXOModel {
    private SimpleStringProperty balance;
    private SimpleStringProperty tokentype;
    private SimpleStringProperty address;
    private SimpleStringProperty tokenid;
    private SimpleStringProperty spendPending;

    public UTXOModel(String balance, byte[] tokenid, String address, boolean spendPending, String tokenname) {
        this.balance = new SimpleStringProperty(balance);
        this.tokenid = new SimpleStringProperty(tokenname + ":" + Utils.HEX.encode(tokenid));
        this.tokentype = new SimpleStringProperty(tokenname);
        this.address = new SimpleStringProperty(address);
        this.spendPending = spendPending ? new SimpleStringProperty("*") : new SimpleStringProperty("");
    }

    public SimpleStringProperty balance() {
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

    public SimpleStringProperty spendPending() {
        return spendPending;
    }

    public String getBalance() {
        return balance.get();
    }

    public void setBalance(String balance) {
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

    public String getSpendPending() {
        return spendPending.get();
    }

    public void setSpendPending(boolean spendPending) {
        this.spendPending.set(spendPending ? "*" : "");
    }

}
