/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

public class ExchangeMulti implements java.io.Serializable {

    private static final long serialVersionUID = -702493172094450451L;

    private String orderid;

    private String pubkey;

    private byte[] signInputData;

    public ExchangeMulti(String orderid, String pubkey, byte[] signInputData, int sign) {
        super();
        this.orderid = orderid;
        this.pubkey = pubkey;
        this.signInputData = signInputData;
        this.sign = sign;
    }

    private int sign;

    public ExchangeMulti() {
        super();
        // TODO Auto-generated constructor stub
    }

    public ExchangeMulti(String orderid, String pubkey, int sign) {
        super();
        this.orderid = orderid;
        this.pubkey = pubkey;
        this.sign = sign;
    }

    public String getOrderid() {
        return orderid;
    }

    public void setOrderid(String orderid) {
        this.orderid = orderid;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }

    public byte[] getSignInputData() {
        return signInputData;
    }

    public void setSignInputData(byte[] signInputData) {
        this.signInputData = signInputData;
    }

}
