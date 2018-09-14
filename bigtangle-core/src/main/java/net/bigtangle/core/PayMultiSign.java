package net.bigtangle.core;

public class PayMultiSign implements java.io.Serializable {

    private static final long serialVersionUID = 8438153762231442643L;

    private String orderid;

    private String tokenid;

    private String toaddress;

    private String blockhashHex;
    private String tokenBlockhashHex;

    private byte[] blockhash;

    private long amount;

    private long minsignnumber;

    private String pubKeyHex;

    private String outpusHashHex;

    public String getOutpusHashHex() {
        return outpusHashHex;
    }

    public void setOutpusHashHex(String outpusHashHex) {
        this.outpusHashHex = outpusHashHex;
    }

    public String getPubKeyHex() {
        return pubKeyHex;
    }

    public void setPubKeyHex(String pubKeyHex) {
        this.pubKeyHex = pubKeyHex;
    }

    public String getOrderid() {
        return orderid;
    }

    public void setOrderid(String orderid) {
        this.orderid = orderid;
    }

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public String getToaddress() {
        return toaddress;
    }

    public void setToaddress(String toaddress) {
        this.toaddress = toaddress;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public long getMinsignnumber() {
        return minsignnumber;
    }

    public void setMinsignnumber(long minsignnumber) {
        this.minsignnumber = minsignnumber;
    }

    public String getBlockhashHex() {
        return blockhashHex;
    }

    public void setBlockhashHex(String blockhashHex) {
        this.blockhashHex = blockhashHex;
    }

    public byte[] getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(byte[] blockhash) {
        this.blockhash = blockhash;
    }

    public String getTokenBlockhashHex() {
        return tokenBlockhashHex;
    }

    public void setTokenBlockhashHex(String tokenBlockhashHex) {
        this.tokenBlockhashHex = tokenBlockhashHex;
    }
}
