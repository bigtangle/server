/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.UserData;
import net.bigtangle.core.Utils;

public class UserDataModel implements java.io.Serializable {

     
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private String blockhash;

    private String dataclassname;

    private String pubkey;

    private String data;
    
    private long blocktype;

    public static UserDataModel from(UserData userData) {
        UserDataModel u= new UserDataModel();
        u.setBlockhash(userData.getBlockhash().toString());
        u.setData(Utils.HEX.encode(userData.getData()));
        u.setBlocktype(userData.getBlocktype());
        u.setDataclassname(userData.getDataclassname());
        u.setPubkey(userData.getPubKey());
        return u;
    }
    
 
    public UserData toUserData() {
    UserData userData = new UserData();
    
    userData.setBlockhash(Sha256Hash.wrap(getBlockhash()));
    userData.setData(Utils.HEX.decode(getData()));
    userData.setDataclassname(getDataclassname());
    userData.setPubKey(getPubkey());
    userData.setBlocktype(getBlocktype());
    return userData;
    }
    public String getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(String blockhash) {
        this.blockhash = blockhash;
    }

    public String getDataclassname() {
        return dataclassname;
    }

    public void setDataclassname(String dataclassname) {
        this.dataclassname = dataclassname;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public long getBlocktype() {
        return blocktype;
    }

    public void setBlocktype(long blocktype) {
        this.blocktype = blocktype;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }
 
 
}
