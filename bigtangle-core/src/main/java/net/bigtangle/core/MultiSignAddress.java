/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class MultiSignAddress implements java.io.Serializable {

    private static final long serialVersionUID = -2956933642847534834L;
    
    private String blockhash;

    // TODO Why is this required?
    private String tokenid;
    
    private String address;
    
    private String pubKeyHex;
    
    private int posIndex;
    
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
    
    public String getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
    }

    public MultiSignAddress(String tokenid, String address, String pubKeyHex) {
        this.tokenid = tokenid;
        this.address = address;
        this.pubKeyHex = pubKeyHex;
    }

    public MultiSignAddress() {
    }
}
