package net.bigtangle.core;

public class MultiSignAddress implements java.io.Serializable {

    private static final long serialVersionUID = -2956933642847534834L;

    private String tokenid;
    
    private String address;
    
    private String pubKeyHex;
    
    public String getPubKeyHex() {
        return pubKeyHex;
    }

    public void setPubKeyHex(String pubKeyHex) {
        this.pubKeyHex = pubKeyHex;
    }

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

    public MultiSignAddress(String tokenid, String address, String pubKeyHex) {
        this.tokenid = tokenid;
        this.address = address;
        this.pubKeyHex = pubKeyHex;
    }

    public MultiSignAddress() {
    }

    public MultiSignAddress copy(MultiSignAddress multiSignAddress) {
        this.tokenid = multiSignAddress.getTokenid();
        this.address = multiSignAddress.getAddress();
        this.pubKeyHex = multiSignAddress.getPubKeyHex();
        return this;
    }
}
