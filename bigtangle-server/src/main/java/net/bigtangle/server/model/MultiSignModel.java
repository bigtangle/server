/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import net.bigtangle.core.MultiSign;
import net.bigtangle.core.Utils;

public class MultiSignModel implements java.io.Serializable {

    private static final long serialVersionUID = 571782646849163955L;

    private String id;

    private String tokenid;

    private Long tokenindex;

    private String blockbytes;

    private String address;

    private int sign;

    public MultiSign toMultiSign() {
        MultiSign multiSign = new MultiSign();
        multiSign.setId(id);
        multiSign.setTokenindex(tokenindex);
        multiSign.setTokenid(tokenid);
        multiSign.setAddress(address);
        multiSign.setBlockbytes(Utils.HEX.decode(blockbytes));
        multiSign.setSign(sign);
        return multiSign;
    }

    public static MultiSignModel from(MultiSign m) {
        MultiSignModel multiSignModel = new MultiSignModel();
        multiSignModel.setId(m.getId());
        multiSignModel.setTokenindex(m.getTokenindex());
        multiSignModel.setTokenid(m.getTokenid());
        multiSignModel.setAddress(m.getAddress());
        multiSignModel.setBlockbytes(Utils.HEX.encode(m.getBlockbytes()));
        multiSignModel.setSign(m.getSign());
        return multiSignModel;
    }
    
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    

    public String getBlockbytes() {
        return blockbytes;
    }

    public void setBlockbytes(String blockbytes) {
        this.blockbytes = blockbytes;
    }

    public void setTokenindex(Long tokenindex) {
        this.tokenindex = tokenindex;
    }

}
