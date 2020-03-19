package net.bigtangle.data.identity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.bigtangle.core.DataClass;
import net.bigtangle.core.Utils;

public class IdentityCore extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String surname;
    private String forenames;
    private String nationality;
    private String dateofbirth;
    private String sex;
    private String placeofbirth;
    private String dateofissue;
    private String dateofexpiry;
    private String authority;

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
       
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());
            Utils.writeNBytesString(dos, surname);
            Utils.writeNBytesString(dos, forenames);
            Utils.writeNBytesString(dos, nationality);
            Utils.writeNBytesString(dos, dateofbirth);
            Utils.writeNBytesString(dos, sex);
            Utils.writeNBytesString(dos, placeofbirth);
            Utils.writeNBytesString(dos, dateofissue);
            Utils.writeNBytesString(dos, dateofexpiry);
            Utils.writeNBytesString(dos, authority);
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public IdentityCore parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);

        dis.close();
        bain.close();
        return this;
    }

    public IdentityCore parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);

        surname = Utils.readNBytesString(dis);
        forenames = Utils.readNBytesString(dis);
        nationality = Utils.readNBytesString(dis);
        dateofbirth = Utils.readNBytesString(dis);
        sex = Utils.readNBytesString(dis);
        placeofbirth = Utils.readNBytesString(dis);
        dateofissue = Utils.readNBytesString(dis);
        dateofexpiry = Utils.readNBytesString(dis);
        authority = Utils.readNBytesString(dis);

        dis.close();

        return this;
    }

    private String nameatbirth;

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getForenames() {
        return forenames;
    }

    public void setForenames(String forenames) {
        this.forenames = forenames;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getDateofbirth() {
        return dateofbirth;
    }

    public void setDateofbirth(String dateofbirth) {
        this.dateofbirth = dateofbirth;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getPlaceofbirth() {
        return placeofbirth;
    }

    public void setPlaceofbirth(String placeofbirth) {
        this.placeofbirth = placeofbirth;
    }

    public String getDateofissue() {
        return dateofissue;
    }

    public void setDateofissue(String dateofissue) {
        this.dateofissue = dateofissue;
    }

    public String getDateofexpiry() {
        return dateofexpiry;
    }

    public void setDateofexpiry(String dateofexpiry) {
        this.dateofexpiry = dateofexpiry;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public String getNameatbirth() {
        return nameatbirth;
    }

    public void setNameatbirth(String nameatbirth) {
        this.nameatbirth = nameatbirth;
    }

}
