/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class MultiSign implements java.io.Serializable {

    private static final long serialVersionUID = 571782646849163955L;

    private String id;

    private String tokenid;

    private long tokenindex;

    private byte[] blockhash;

    private String address;

    public String getBlockhashHex() {
        if (this.blockhash == null) {
            return "";
        }
        return Utils.HEX.encode(this.blockhash);
    }
    
    public void setBlockhashHex(String blockhashHex) {
        if (blockhashHex == null) {
            this.blockhash = null;
        }
        else {
            this.blockhash = Utils.HEX.decode(blockhashHex);
        }
    }

    private int sign;

    public int getSign() {
        return sign;
    }

    public void setSign(int sign) {
        this.sign = sign;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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
