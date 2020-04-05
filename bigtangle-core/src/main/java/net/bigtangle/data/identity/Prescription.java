package net.bigtangle.data.identity;

import java.util.Date;

import net.bigtangle.core.DataClass;

public class Prescription extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private Date dateofissue; 
    private String prescription;
    private byte[] file;
    public Date getDateofissue() {
        return dateofissue;
    }
    public void setDateofissue(Date dateofissue) {
        this.dateofissue = dateofissue;
    }
    public String getPrescription() {
        return prescription;
    }
    public void setPrescription(String prescription) {
        this.prescription = prescription;
    }
    public byte[] getFile() {
        return file;
    }
    public void setFile(byte[] file) {
        this.file = file;
    }
    
}
