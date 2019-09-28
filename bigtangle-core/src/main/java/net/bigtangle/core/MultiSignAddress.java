/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class MultiSignAddress implements java.io.Serializable {

    private static final long serialVersionUID = -2956933642847534834L;

    private Sha256Hash blockhash;
    private String tokenid;
    private String address;
    private String pubKeyHex;
    private int posIndex;
    private int tokenHolder;

    public int getPosIndex() {
        return posIndex;
    }

    public void setPosIndex(int posIndex) {
        this.posIndex = posIndex;
    }

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

    public Sha256Hash getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(Sha256Hash blockhash) {
        this.blockhash = blockhash;
    }

    public int getTokenHolder() {
        return tokenHolder;
    }

    public void setTokenHolder(int tokenHolder) {
        this.tokenHolder = tokenHolder;
    }

    public MultiSignAddress(String tokenid, String address, String pubKeyHex, int tokenHolder) {
        this.tokenid = tokenid;
        this.address = address;
        this.pubKeyHex = pubKeyHex;
        this.tokenHolder = tokenHolder;
    }

    public MultiSignAddress(String tokenid, String address, String pubKeyHex) {
        this(tokenid, address, pubKeyHex, 1);
    }

    public MultiSignAddress() {
    }
}
