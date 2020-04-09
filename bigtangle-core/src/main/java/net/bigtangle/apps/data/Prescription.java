package net.bigtangle.apps.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.bigtangle.core.DataClass;
import net.bigtangle.core.Utils;

public class Prescription extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private String prescription;
    private String filename;
    private byte[] file;

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {

            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());

            Utils.writeNBytesString(dos, prescription);
            Utils.writeNBytesString(dos, filename);
            Utils.writeNBytes(dos, file);
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public Prescription parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);

        dis.close();
        bain.close();
        return this;
    }

    public Prescription parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis); 
        prescription = Utils.readNBytesString(dis);
        filename = Utils.readNBytesString(dis);
        file = Utils.readNBytes(dis); 
        dis.close();

        return this;
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

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

}
