package net.bigtangle.apps.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import net.bigtangle.core.Coin;
import net.bigtangle.core.DataClass;
import net.bigtangle.core.Utils;

public class Prescription extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /*
     * this is the prescription written as text
     */
    private String prescription;
    
    /*
     * this is the type of Prescription
     */
    private String type;
    /*
     * this is the  country code  de = germany etc
     */
    private String countrycode;
    
    // this is the associated file name for example scan.pdf
    private String filename;
    // this the binary file
    private byte[] file;
    // this is the list a medical listed in the prescription and amount
    List<Coin> coins = new ArrayList<Coin>();

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {

            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());

            Utils.writeNBytesString(dos, prescription);
            Utils.writeNBytesString(dos, type);
            Utils.writeNBytesString(dos, countrycode);
            Utils.writeNBytesString(dos, filename);
            Utils.writeNBytes(dos, file);
            dos.writeInt(coins.size());
            for (Coin c : coins) {
                Utils.writeNBytes(dos, c.getValue().toByteArray());
                Utils.writeNBytes(dos, c.getTokenid());
            }

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
        type = Utils.readNBytesString(dis);
        countrycode = Utils.readNBytesString(dis);
        filename = Utils.readNBytesString(dis);
        file = Utils.readNBytes(dis);

        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
            coins.add(new Coin(new BigInteger(Utils.readNBytes(dis)), Utils.readNBytes(dis)));
        }

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

    public List<Coin> getCoins() {
        return coins;
    }

    public void setCoins(List<Coin> coins) {
        this.coins = coins;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCountrycode() {
        return countrycode;
    }

    public void setCountrycode(String countrycode) {
        this.countrycode = countrycode;
    }

}
