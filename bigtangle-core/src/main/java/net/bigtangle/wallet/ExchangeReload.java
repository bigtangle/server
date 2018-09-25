/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import net.bigtangle.core.Transaction;

public class ExchangeReload implements java.io.Serializable {

    private static final long serialVersionUID = 8257893832041422608L;

    private String fromAddress;
    
    private String fromTokenHex;
    
    private String fromAmount;
    
    private String toAddress;
    
    private String toTokenHex;
    
    private String toAmount;
    
    private String orderid;
    
    private Transaction transaction;

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromTokenHex() {
        return fromTokenHex;
    }

    public void setFromTokenHex(String fromTokenHex) {
        this.fromTokenHex = fromTokenHex;
    }

    public String getFromAmount() {
        return fromAmount;
    }

    public void setFromAmount(String fromAmount) {
        this.fromAmount = fromAmount;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getToTokenHex() {
        return toTokenHex;
    }

    public void setToTokenHex(String toTokenHex) {
        this.toTokenHex = toTokenHex;
    }

    public String getToAmount() {
        return toAmount;
    }

    public void setToAmount(String toAmount) {
        this.toAmount = toAmount;
    }

    public String getOrderid() {
        return orderid;
    }

    public void setOrderid(String orderid) {
        this.orderid = orderid;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }
    
}
