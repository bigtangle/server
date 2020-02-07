package net.bigtangle.data.identity;

public class Identity {

    private String code = "p";
    private String identificationnumber;
    // optional
    private String pubkey;
    private byte[] signatureofholder;
    private byte[] photo;
    private byte[] machinereadable;
    // identity in English
    IdentityCore identityCoreEn;
    // original language UTF-8
    IdentityCore identityCore;

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

    public byte[] getMachinereadable() {
        return machinereadable;
    }

    public void setMachinereadable(byte[] machinereadable) {
        this.machinereadable = machinereadable;
    }

    public IdentityCore getIdentityCoreEn() {
        return identityCoreEn;
    }

    public void setIdentityCoreEn(IdentityCore identityCoreEn) {
        this.identityCoreEn = identityCoreEn;
    }

    public IdentityCore getIdentityCore() {
        return identityCore;
    }

    public void setIdentityCore(IdentityCore identityCore) {
        this.identityCore = identityCore;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

}
