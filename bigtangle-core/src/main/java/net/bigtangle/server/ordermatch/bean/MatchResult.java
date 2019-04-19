package net.bigtangle.server.ordermatch.bean;

import java.io.Serializable;

public class MatchResult implements Serializable{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private long price;
    private long executedQuantity; 
    private String txhash;
    private String tokenid;

    private long inserttime;

    public MatchResult() {
        super();
     
    }

    public MatchResult(String txhash, String tokenid, long price, long executedQuantity, long inserttime) {

        this.price = price;
        this.executedQuantity = executedQuantity;
        this.inserttime = inserttime;
        this.tokenid = tokenid;
        this.txhash = txhash;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public long getExecutedQuantity() {
        return executedQuantity;
    }

    public void setExecutedQuantity(long executedQuantity) {
        this.executedQuantity = executedQuantity;
    } 

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public long getInserttime() {
        return inserttime;
    }

    public void setInserttime(long inserttime) {
        this.inserttime = inserttime;
    }

    public String getTxhash() {
        return txhash;
    }

    public void setTxhash(String txhash) {
        this.txhash = txhash;
    }
 

}
