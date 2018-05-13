package net.bigtangle.core;

public class MultiSignAddress implements java.io.Serializable {

    private static final long serialVersionUID = -2956933642847534834L;

    private String tokenid;
    
    private String address;

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public MultiSignAddress(String tokenid, String address) {
        this.tokenid = tokenid;
        this.address = address;
    }

    public MultiSignAddress() {
    }
}
