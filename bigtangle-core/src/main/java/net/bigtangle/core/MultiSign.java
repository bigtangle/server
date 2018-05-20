package net.bigtangle.core;

public class MultiSign implements java.io.Serializable {

    private static final long serialVersionUID = -8690336614680749494L;
    
    private String id;

    private String tokenid;

    private long tokenindex;
    
    private byte[] blockhash;
    
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

    public byte[] getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(byte[] blockhash) {
        this.blockhash = blockhash;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
