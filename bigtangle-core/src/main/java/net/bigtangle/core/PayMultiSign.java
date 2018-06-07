package net.bigtangle.core;

public class PayMultiSign implements java.io.Serializable {

    private static final long serialVersionUID = 8438153762231442643L;

    private String orderid;
    
    private String tokenid;
    
    private String toaddress;
    
    private String outputhash;
    
    private byte[] blockhash;
    
    private long amount;
    
    private long minsignnumber;
    
    public String getBlockhashHex() {
        if (blockhash == null) {
            return null;
        }
        return Utils.HEX.encode(this.blockhash);
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

    public String getOutputhash() {
        return outputhash;
    }

    public void setOutputhash(String outputhash) {
        this.outputhash = outputhash;
    }

    public byte[] getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(byte[] blockhash) {
        this.blockhash = blockhash;
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
}
