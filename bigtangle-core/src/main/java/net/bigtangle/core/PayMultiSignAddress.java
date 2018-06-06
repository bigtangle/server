package net.bigtangle.core;

public class PayMultiSignAddress implements java.io.Serializable {

    private static final long serialVersionUID = 2618349280766774228L;

    private String orderid;
    
    private String pubKey;

    private String signature;
    
    private int sign;

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

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }
}
