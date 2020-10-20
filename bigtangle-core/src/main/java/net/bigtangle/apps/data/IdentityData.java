package net.bigtangle.apps.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.bigtangle.core.DataClass;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;

public class IdentityData extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private String code = "p";
    private String identificationnumber;

    private byte[] signatureofholder;
    private byte[] photo;
    private byte[] machinereadable;
    // identity in English
    IdentityCore identityCoreEn = new IdentityCore();
    // original language UTF-8
    IdentityCore identityCore = new IdentityCore();

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());
            Utils.writeNBytesString(dos, code);
            Utils.writeNBytesString(dos, identificationnumber);
            Utils.writeNBytes(dos, signatureofholder);
            Utils.writeNBytes(dos, photo);
            Utils.writeNBytes(dos, machinereadable);
            dos.write(identityCoreEn.toByteArray());
            dos.write(identityCore.toByteArray());
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public IdentityData parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);

        dis.close();
        bain.close();
        return this;
    }

    public IdentityData parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);

        code = Utils.readNBytesString(dis);
        identificationnumber = Utils.readNBytesString(dis);
        signatureofholder = Utils.readNBytes(dis);
        photo = Utils.readNBytes(dis);
        machinereadable = Utils.readNBytes(dis);
        identityCoreEn = new IdentityCore().parseDIS(dis);
        identityCore = new IdentityCore().parseDIS(dis);
        dis.close();

        return this;
    }

    /*
     * This is unique token name for a identity using Hash
     */
    public String uniqueNameIdentity() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            Utils.writeNBytesString(dos, identificationnumber);
            Utils.writeNBytesString(dos, identityCore.getSurname());
            Utils.writeNBytesString(dos, identityCore.getForenames());
            Utils.writeNBytesString(dos, identityCore.getDateofbirth());
            return Sha256Hash.of(baos.toByteArray()).toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                dos.close();
            } catch (IOException e) {

            }
        }
    }

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
}
