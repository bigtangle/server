package net.bigtangle.data.realestate;

public class Realestate {


    private String code="p";
    private String identificationnumber;

    private byte[] signatureofholder;
    private byte[] photo;
   
 
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    public String getIdentificationnumber() {
        return identificationnumber;
    }
    public void setIdentificationnumber(String identificationnumber) {
        this.identificationnumber = identificationnumber;
    }
    public byte[] getSignatureofholder() {
        return signatureofholder;
    }
    public void setSignatureofholder(byte[] signatureofholder) {
        this.signatureofholder = signatureofholder;
    }
    public byte[] getPhoto() {
        return photo;
    }
    public void setPhoto(byte[] photo) {
        this.photo = photo;
    }
    
    
}
