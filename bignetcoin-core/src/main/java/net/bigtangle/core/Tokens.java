package net.bigtangle.core;

public class Tokens {

    private byte[] tokenid;
    
    private String tokenname;
    
    private long amount;
    
    private String description;
    
    private int blocktype;
    
    public int getBlocktype() {
        return blocktype;
    }

    public void setBlocktype(int blocktype) {
        this.blocktype = blocktype;
    }

    public String getTokenHex() {
        return Utils.HEX.encode(this.tokenid);
    }

    public byte[] getTokenid() {
        return tokenid;
    }

    public void setTokenid(byte[] tokenid) {
        this.tokenid = tokenid;
    }

    public String getTokenname() {
        return tokenname;
    }

    public void setTokenname(String tokenname) {
        this.tokenname = tokenname;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
