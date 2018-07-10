package net.bigtangle.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class ContactInfo implements java.io.Serializable {
    private static final long serialVersionUID = -1965429530354669140L;
    private List<Contact> contactList = new ArrayList<Contact>();

    public byte[] toByteArray() {
        try {
            String jsonStr = Json.jsonmapper().writeValueAsString(this);
            return jsonStr.getBytes();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new byte[0];
    }

    public ContactInfo parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
        String jsonStr = new String(buf);

        ContactInfo contactInfo = Json.jsonmapper().readValue(jsonStr, ContactInfo.class);
        if (contactInfo == null)
            return this;
        this.contactList = contactInfo.getContactList();

        return this;
    }

    public List<Contact> getContactList() {
        return contactList;
    }

    public void setContactList(List<Contact> contactList) {
        this.contactList = contactList;
    }

}
