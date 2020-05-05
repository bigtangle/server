package net.bigtangle.apps.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.bigtangle.core.DataClass;
import net.bigtangle.core.Utils;

public class Certificate extends DataClass implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /*
     * this is the description of Certificate
     */
    private String description;
    /*
     * this is the type of Certificate
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

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {

            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());

            Utils.writeNBytesString(dos, description);
            Utils.writeNBytesString(dos, type);
            Utils.writeNBytesString(dos, countrycode);
            Utils.writeNBytesString(dos, filename);
            Utils.writeNBytes(dos, file);

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
        type = Utils.readNBytesString(dis);
        countrycode = Utils.readNBytesString(dis);
        filename = Utils.readNBytesString(dis);
        file = Utils.readNBytes(dis);

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
