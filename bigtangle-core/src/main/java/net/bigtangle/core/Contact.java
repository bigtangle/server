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

public class Contact {
    private String name = "";
    private String address = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);

            Utils.writeNBytesString(dos, name);
            Utils.writeNBytesString(dos, address); 
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
    
    public Contact parseDIS(DataInputStream dis) throws IOException {
        name = Utils.readNBytesString(dis); 
        address = Utils.readNBytesString(dis); 
        
        return this;
    }

    public Contact parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);
        
        dis.close();
        bain.close();
        return this;
    }
}
