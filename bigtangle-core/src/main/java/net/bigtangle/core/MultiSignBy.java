package net.bigtangle.core;

public class MultiSignBy implements java.io.Serializable {

    private static final long serialVersionUID = 3478025339600098446L;

    private String tokenid;

    private long tokenindex;

    private String address;
    private String publickey;

    public MultiSignBy(String tokenid, long tokenindex, String address, String publickey, String signature) {
        super();
        this.tokenid = tokenid;
        this.tokenindex = tokenindex;
        this.address = address;
        this.publickey = publickey;
        this.signature = signature;
    }

    private String signature;

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

    public MultiSignBy copy(MultiSignBy multiSignBy) {
        this.tokenid = multiSignBy.getTokenid();
        this.tokenindex = multiSignBy.getTokenindex();
        this.address = multiSignBy.getAddress();
        this.publickey = multiSignBy.publickey;
        this.signature = multiSignBy.signature;
        return this;
    }

    public String getPublickey() {
        return publickey;
    }

    public void setPublickey(String publickey) {
        this.publickey = publickey;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

}
