package net.bigtangle.data.identity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.SignatureException;

import net.bigtangle.core.DataClass;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Utils;

public class Identity extends DataClass implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    // message
    String identityData;

    private byte[] pubsignkey;
    private String signature;

    public void verify() throws SignatureException {
        ECKey.fromPublicOnly(pubsignkey).verifyMessage(identityData, signature);

    }

    public void signMessage(ECKey key) throws SignatureException {
        signature = key.signMessage(identityData);
    }

    public void setIdentityData(byte[] identityData) {
        this.identityData = Utils.HEX.encode(identityData);
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());

            Utils.writeNBytesString(dos, identityData);
            Utils.writeNBytes(dos, pubsignkey);
            Utils.writeNBytesString(dos, signature);
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public Identity parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);

        dis.close();
        bain.close();
        return this;
    }

    public Identity parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);
        identityData = Utils.readNBytesString(dis);
        pubsignkey = Utils.readNBytes(dis);
        signature = Utils.readNBytesString(dis);

        dis.close();

        return this;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getIdentityData() {
        return identityData;
    }

    public void setIdentityData(String identityData) {
        this.identityData = identityData;
    }

    public byte[] getPubsignkey() {
        return pubsignkey;
    }

    public void setPubsignkey(byte[] pubsignkey) {
        this.pubsignkey = pubsignkey;
    }

}
