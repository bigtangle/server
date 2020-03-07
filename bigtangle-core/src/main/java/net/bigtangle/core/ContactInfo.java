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
import java.util.ArrayList;
import java.util.List;

public class ContactInfo extends DataClass implements java.io.Serializable {
    private static final long serialVersionUID = -1965429530354669140L;
    private List<Contact> contactList = new ArrayList<Contact>();

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());
            dos.writeInt(contactList.size());
            for (Contact c : contactList)
                dos.write(c.toByteArray());

            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public ContactInfo parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);
        contactList = new ArrayList<>();
        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
            contactList.add(new Contact().parseDIS(dis));
        }

        return this;
    }

    public ContactInfo parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);
        parseDIS(dis);
        dis.close();
        bain.close();
        return this;
    }

    public List<Contact> getContactList() {
        return contactList;
    }

    public void setContactList(List<Contact> contactList) {
        this.contactList = contactList;
    }

}
