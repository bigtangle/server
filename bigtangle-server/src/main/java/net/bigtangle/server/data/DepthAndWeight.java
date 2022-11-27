package net.bigtangle.server.data;

public class DepthAndWeight {
    private String hash;
    private  long weight;
    private long depth;
    
  
    public DepthAndWeight(String hash, long weight, long depth) {
        super();
        this.hash = hash;
        this.weight = weight;
        this.depth = depth;
    }
    public String getHash() {
        return hash;
    }
    public void setHash(String hash) {
        this.hash = hash;
    }
    public long getWeight() {
        return weight;
    }
    public void setWeight(long weight) {
        this.weight = weight;
    }
    public long getDepth() {
        return depth;
    }
    public void setDepth(long depth) {
        this.depth = depth;
    }

}
