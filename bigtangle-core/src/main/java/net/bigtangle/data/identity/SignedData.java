package net.bigtangle.data.identity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.SignatureException;

import org.spongycastle.crypto.InvalidCipherTextException;

import net.bigtangle.core.DataClass;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.KeyValue;
import net.bigtangle.core.TokenKeyValues;
import net.bigtangle.core.Utils;
import net.bigtangle.encrypt.ECIESCoder;

public class SignedData extends DataClass implements java.io.Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    // dataClassName of serialized data
    String dataClassName;

    // serialized data Utils.HEX.encode before encryption
    String serializedData;

    private byte[] pubsignkey;
    private String signature;

    public void verify() throws SignatureException {
        ECKey.fromPublicOnly(pubsignkey).verifyMessage(serializedData, signature);

    }

    public void signMessage(ECKey key) throws SignatureException {
        signature = key.signMessage(serializedData);
    }

    public void setSerializedData(byte[] byteData) {
        this.serializedData = Utils.HEX.encode(byteData);
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            dos.write(super.toByteArray());
            Utils.writeNBytesString(dos, dataClassName);
            Utils.writeNBytesString(dos, serializedData);
            Utils.writeNBytes(dos, pubsignkey);
            Utils.writeNBytesString(dos, signature);
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public SignedData parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);

        dis.close();
        bain.close();
        return this;
    }

    public SignedData parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);
        dataClassName = Utils.readNBytesString(dis);
        serializedData = Utils.readNBytesString(dis);
        pubsignkey = Utils.readNBytes(dis);
        signature = Utils.readNBytesString(dis);

        dis.close();

        return this;
    }

    /*
     * transform the data with encryption to be saved in token 
     */
    public TokenKeyValues toTokenKeyValues(ECKey key, ECKey userkey, byte[] originalData, String dataClassname)
            throws InvalidCipherTextException, IOException, SignatureException {
        TokenKeyValues tokenKeyValues = new TokenKeyValues(); 
        setSerializedData(originalData); 
        setPubsignkey(key.getPubKey());
        setDataClassName(dataClassname);
        signMessage(key); 
        byte[] data = this.toByteArray(); 
        byte[] cipher = ECIESCoder.encrypt(key.getPubKeyPoint(), data);
        KeyValue kv = new KeyValue();
        kv.setKey(key.getPublicKeyAsHex());
        kv.setValue(Utils.HEX.encode(cipher));
        tokenKeyValues.addKeyvalue(kv);
        byte[] cipher1 = ECIESCoder.encrypt(userkey.getPubKeyPoint(), data);
        kv = new KeyValue();
        kv.setKey(userkey.getPublicKeyAsHex());
        kv.setValue(Utils.HEX.encode(cipher1));
        tokenKeyValues.addKeyvalue(kv);
        return tokenKeyValues;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

     

    public String getSerializedData() {
        return serializedData;
    }

    public void setSerializedData(String serializedData) {
        this.serializedData = serializedData;
    }

    public byte[] getPubsignkey() {
        return pubsignkey;
    }

    public void setPubsignkey(byte[] pubsignkey) {
        this.pubsignkey = pubsignkey;
    }

    public String getDataClassName() {
        return dataClassName;
    }

    public void setDataClassName(String dataClassName) {
        this.dataClassName = dataClassName;
    }

}
