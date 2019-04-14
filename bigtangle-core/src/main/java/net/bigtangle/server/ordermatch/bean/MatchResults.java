package net.bigtangle.server.ordermatch.bean;

import net.bigtangle.core.Side;

public class MatchResults {
    private String restingOrderId;
    private String incomingOrderId;
    private Side incomingSide;
    private long price;
    private long executedQuantity;
    private long remainingQuantity;

    private String txhash;
    private String tokenid;

    private long inserttime;

    public MatchResults(String txhash, String tokenid, String restingOrderId, String incomingOrderId, Side incomingSide,
            long price, long executedQuantity, long remainingQuantity, long inserttime) {
        this.restingOrderId = restingOrderId;
        this.incomingOrderId = incomingOrderId;
        this.incomingSide = incomingSide;
        this.price = price;
        this.executedQuantity = executedQuantity;
        this.remainingQuantity = remainingQuantity;
        this.inserttime = inserttime;
        this.tokenid = tokenid;
        this.txhash = txhash;
    }

    public String getRestingOrderId() {
        return restingOrderId;
    }

    public void setRestingOrderId(String restingOrderId) {
        this.restingOrderId = restingOrderId;
    }

    public String getIncomingOrderId() {
        return incomingOrderId;
    }

    public void setIncomingOrderId(String incomingOrderId) {
        this.incomingOrderId = incomingOrderId;
    }

    public Side getIncomingSide() {
        return incomingSide;
    }

    public void setIncomingSide(Side incomingSide) {
        this.incomingSide = incomingSide;
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

    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(long remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public String getTxhash() {
        return txhash;
    }

    public void setTxhash(String txhash) {
        this.txhash = txhash;
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

}
