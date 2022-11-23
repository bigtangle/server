/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import net.bigtangle.core.MultiSignAddress;
import net.bigtangle.core.Sha256Hash;

public class MultiSignAddressModel implements java.io.Serializable {

    private static final long serialVersionUID = -2956933642847534834L;

    private String blockhash;
    private String tokenid;
    private String address;
    private String pubkeyhex;
    private int posindex;
    private int tokenholder;
 
   

    public MultiSignAddressModel(String tokenid, String address, String pubKeyHex, int tokenHolder,Sha256Hash blockhash, int posIndex) {
        this.tokenid = tokenid;
        this.address = address;
        this.pubkeyhex = pubKeyHex;
        this.tokenholder = tokenHolder;
        this.posindex = posIndex;
        this.blockhash = blockhash.toString();
        
    } 
   
    public   MultiSignAddress  toMultiSignAddress( ) {
        MultiSignAddress m=      new MultiSignAddress(   getTokenid(),getAddress(),getPubkeyhex(),getTokenholder()   );
        m.setBlockhash(Sha256Hash.wrap(getBlockhash()));
        m.setPosIndex(m.getPosIndex());
      return m;
    }
    
    public static MultiSignAddressModel from(MultiSignAddress m) {
        return new MultiSignAddressModel(     m.getTokenid(), m.getAddress(), m.getPubKeyHex(),
        m.getTokenHolder(),  m.getBlockhash() , m.getPosIndex());
      
    }

    public String getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
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

    public String getPubkeyhex() {
        return pubkeyhex;
    }

    public void setPubkeyhex(String pubkeyhex) {
        this.pubkeyhex = pubkeyhex;
    }

    public int getPosindex() {
        return posindex;
    }

    public void setPosindex(int posindex) {
        this.posindex = posindex;
    }

    public int getTokenholder() {
        return tokenholder;
    }

    public void setTokenholder(int tokenholder) {
        this.tokenholder = tokenholder;
    }
    
    
    
}
