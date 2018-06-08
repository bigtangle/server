package net.bigtangle.core;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class ContactInfo {
    private String name;
    private String address;

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
            this.name = contactInfo.getName();
            this.address = contactInfo.getAddress();
       
      
        return this;
    }

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

}
