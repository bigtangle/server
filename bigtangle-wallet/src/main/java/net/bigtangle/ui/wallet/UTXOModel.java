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
    private SimpleStringProperty memo;
    private SimpleStringProperty minimumsign;
    private SimpleStringProperty tokenid;
    private SimpleStringProperty spendPending;

    private String hashHex;
    private String hash;
    private long outputindex;
    
    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public long getOutputindex() {
        return outputindex;
    }

    public void setOutputindex(long outputindex) {
        this.outputindex = outputindex;
    }

    public UTXOModel(String balance, byte[] tokenid, String address, boolean spendPending, String tokenname,
            String memo, String minimumsign, String hashHex, String hash, long outputindex) {
        this.balance = new SimpleStringProperty(balance);
        this.tokenid = new SimpleStringProperty(tokenname + ":" + Utils.HEX.encode(tokenid));
        this.tokentype = new SimpleStringProperty(tokenname);
        this.address = new SimpleStringProperty(address);
        this.spendPending = spendPending ? new SimpleStringProperty("*") : new SimpleStringProperty("");
        this.memo = new SimpleStringProperty(memo);
        this.minimumsign = new SimpleStringProperty(minimumsign);
        this.hashHex = hashHex;
        this.hash = hash;
        this.outputindex = outputindex;
    }

    public SimpleStringProperty balance() {
        return balance;
    }

    public SimpleStringProperty minimumsign() {
        return minimumsign;
    }

    public SimpleStringProperty memo() {
        return memo;
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

    public String getMinimumsign() {
        return minimumsign.get();
    }

    public void setMinimumsign(String minimumsign) {
        this.minimumsign.set(minimumsign);
    }

    public String getMemo() {
        return memo.get();
    }

    public void setMemo(String memo) {
        this.memo.set(memo);
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

    public String getHashHex() {
        return hashHex;
    }

    public void setHashHex(String hashHex) {
        this.hashHex = hashHex;
    }

}
