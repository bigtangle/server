package net.bigtangle.core;

public class OutputsMulti {

    private Sha256Hash hash;

    private String toaddress;
    
    private long outputindex;
    
    private long minimumsign;
    
    public OutputsMulti() {
    }

    public OutputsMulti(Sha256Hash hash, String toaddress, long outputindex, long minimumsign) {
        this.hash = hash;
        this.toaddress = toaddress;
        this.outputindex = outputindex;
        this.minimumsign = minimumsign;
    }

    public Sha256Hash getHash() {
        return hash;
    }

    public void setHash(Sha256Hash hash) {
        this.hash = hash;
    }

    public String getToaddress() {
        return toaddress;
    }

    public void setToaddress(String toaddress) {
        this.toaddress = toaddress;
    }

    public long getOutputindex() {
        return outputindex;
    }

    public void setOutputindex(long outputindex) {
        this.outputindex = outputindex;
    }

    public long getMinimumsign() {
        return minimumsign;
    }

    public void setMinimumsign(long minimumsign) {
        this.minimumsign = minimumsign;
    }
}
