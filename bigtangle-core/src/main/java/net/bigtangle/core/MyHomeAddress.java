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

public class MyHomeAddress implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = -4636810580856125604L;
    private String country;
    private String province;
    private String city;
    private String street;
    private String email;
    private String remark;

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeBoolean(country != null);
            if (country != null) {
                dos.writeInt(country.getBytes("UTF-8").length);
                dos.write(country.getBytes("UTF-8"));
            }

            dos.writeBoolean(province != null);
            if (province != null) {
                dos.writeInt(province.getBytes("UTF-8").length);
                dos.write(province.getBytes("UTF-8"));
            }

            dos.writeBoolean(city != null);
            if (city != null) {
                dos.writeInt(city.getBytes("UTF-8").length);
                dos.write(city.getBytes("UTF-8"));
            }

            dos.writeBoolean(street != null);
            if (street != null) {
                dos.writeInt(street.getBytes("UTF-8").length);
                dos.write(street.getBytes("UTF-8"));
            }

            dos.writeBoolean(email != null);
            if (email != null) {
                dos.writeInt(email.getBytes("UTF-8").length);
                dos.write(email.getBytes("UTF-8"));
            }

            dos.writeBoolean(remark != null);
            if (remark != null) {
                dos.writeInt(remark.getBytes("UTF-8").length);
                dos.write(remark.getBytes("UTF-8"));
            }
            
            dos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }

    public MyHomeAddress parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        country = Utils.readNBytesString(dis); 
        province = Utils.readNBytesString(dis); 
        city = Utils.readNBytesString(dis); 
        street = Utils.readNBytesString(dis); 
        email = Utils.readNBytesString(dis); 
        remark = Utils.readNBytesString(dis); 
        
        dis.close();
        bain.close();
        return this;
    }

//    public byte[] toByteArray() {
//        try {
//            String jsonStr = Json.jsonmapper().writeValueAsString(this);
//            return jsonStr.getBytes();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return new byte[0];
//    }
//
//    public MyHomeAddress parse(byte[] buf) throws JsonParseException, JsonMappingException, IOException {
//        String jsonStr = new String(buf);
//
//        MyHomeAddress myHomeAddress = Json.jsonmapper().readValue(jsonStr, MyHomeAddress.class);
//        if (myHomeAddress == null)
//            return this;
//        this.city = myHomeAddress.city;
//        this.country = myHomeAddress.country;
//        this.email = myHomeAddress.email;
//        this.province = myHomeAddress.province;
//        this.remark = myHomeAddress.remark;
//        this.street = myHomeAddress.street;
//        return this;
//    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}
