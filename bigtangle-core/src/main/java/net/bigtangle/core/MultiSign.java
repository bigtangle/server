/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.util.Arrays;

public class MultiSign implements java.io.Serializable {

    private static final long serialVersionUID = 571782646849163955L;

    private String id;

    private String tokenid;

    private long tokenindex;

    private byte[] blockbytes;

    private String address;

    public String getBlockhashHex() {
        if (this.blockbytes == null) {
            return "";
        }
        return Utils.HEX.encode(this.blockbytes);
    }
    
    public void setBlockhashHex(String blockhashHex) {
        if (blockhashHex == null) {
            this.blockbytes = null;
        }
        else {
            this.blockbytes = Utils.HEX.decode(blockhashHex);
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

   

    public byte[] getBlockbytes() {
        return blockbytes;
    }

    public void setBlockbytes(byte[] blockbytes) {
        this.blockbytes = blockbytes;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "MultiSign [id=" + id + ", tokenid=" + tokenid + ", tokenindex=" + tokenindex + ", blockbytes="
                + Arrays.toString(blockbytes) + ", address=" + address + ", sign=" + sign + "]";
    }
    
}
