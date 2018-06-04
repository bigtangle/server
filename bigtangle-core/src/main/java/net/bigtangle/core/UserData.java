package net.bigtangle.core;

public class UserData implements java.io.Serializable {

    private static final long serialVersionUID = 709353912782171256L;

    private Sha256Hash blockhash;

    private String dataclassname;

    private String pubKey;

    private byte[] data;

    public Sha256Hash getBlockhash() {
        return blockhash;
    }

    public void setBlockhash(Sha256Hash blockhash) {
        this.blockhash = blockhash;
    }

    public String getDataclassname() {
        return dataclassname;
    }

    public void setDataclassname(String dataclassname) {
        this.dataclassname = dataclassname;
    }

    public String getPubKey() {
        return pubKey;
    }

    public void setPubKey(String pubKey) {
        this.pubKey = pubKey;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
