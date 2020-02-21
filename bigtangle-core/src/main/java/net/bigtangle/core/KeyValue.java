package net.bigtangle.core;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public class KeyValue implements java.io.Serializable {

    private static final long serialVersionUID = 1L;
    private String key;
    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeInt(key.getBytes("UTF-8").length);
            dos.write(key.getBytes("UTF-8"));

            dos.writeBoolean(value != null);
            if (value != null) {
                dos.writeInt(value.getBytes("UTF-8").length);
                dos.write(value.getBytes("UTF-8"));
            }

            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public KeyValue parseDIS(DataInputStream dis) throws IOException {

        key = Utils.readNBytesString(dis);

        value = Utils.readNBytesString(dis);

        return this;
    }

}
