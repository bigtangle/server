/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class UserSettingData implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -7176158338120349182L;
    private String key;
    private String value;
    private String domain;


    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);

            Utils.writeNBytesString(dos, key);
            Utils.writeNBytesString(dos, value); 
            Utils.writeNBytesString(dos, domain); 
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
    
    public UserSettingData parseDIS(DataInputStream dis) throws IOException {
        key = Utils.readNBytesString(dis); 
        value = Utils.readNBytesString(dis); 
        domain = Utils.readNBytesString(dis); 
        return this;
    }

    public UserSettingData parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);
        
        dis.close();
        bain.close();
        return this;
    }
    
 
    
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

}
