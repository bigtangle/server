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

public class Certificate extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /*
     * this is the prescription written as text
     */
    private String description;
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

            Utils.writeNBytesString(dos, description);
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

    public Certificate parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);

        dis.close();
        bain.close();
        return this;
    }

    public Certificate parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);
        description = Utils.readNBytesString(dis);
        filename = Utils.readNBytesString(dis);
        file = Utils.readNBytes(dis);

        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
            coins.add(new Coin(new BigInteger(Utils.readNBytes(dis)), Utils.readNBytes(dis)));
        }

        dis.close();

        return this;
    }

   
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

}
