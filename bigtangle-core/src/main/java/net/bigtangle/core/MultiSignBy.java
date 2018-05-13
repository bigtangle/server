package net.bigtangle.core;

public class MultiSignBy implements java.io.Serializable {

    private static final long serialVersionUID = 3478025339600098446L;

    private String tokenid;
    
    private long tokenindex;
    
    private String address;

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public long getTokenindex() {
        return tokenindex;
    }

    public void setTokenindex(long tokenindex) {
        this.tokenindex = tokenindex;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public MultiSignBy(String tokenid, long tokenindex, String address) {
        this.tokenid = tokenid;
        this.tokenindex = tokenindex;
        this.address = address;
    }

    public MultiSignBy() {
    }
}
