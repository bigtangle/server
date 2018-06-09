package net.bigtangle.core;

public class PayMultiSignAddress implements java.io.Serializable {

    private static final long serialVersionUID = 2618349280766774228L;

    private String orderid;
    
    private String pubKey;

    private int sign;
    
    private byte[] signInputData;
    
    public String getSignInputDataHex() {
        if (this.signInputData == null) {
            return "";
        }
        return Utils.HEX.encode(this.signInputData);
    }
    
    public byte[] getSignInputData() {
        return signInputData;
    }

    public void setSignInputData(byte[] signInputData) {
        this.signInputData = signInputData;
    }

    public String getOrderid() {
        return orderid;
    }

    public void setOrderid(String orderid) {
        this.orderid = orderid;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }
}
