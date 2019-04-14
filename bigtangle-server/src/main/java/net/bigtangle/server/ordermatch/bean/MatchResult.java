package net.bigtangle.server.ordermatch.bean;

import net.bigtangle.core.Side;

public class MatchResult {
    private String restingOrderId;
    private String incomingOrderId;
    private Side incomingSide;
    private long price;
    private long executedQuantity;
    private long remainingQuantity;

    private String txhash;
    private String tokenid;
    private String incomingBuy;
    private long inserttime;
    

    public MatchResult(String restingOrderId, String incomingOrderId, 
            Side incomingSide, long price, long executedQuantity,
            long remainingQuantity) {
        this.restingOrderId = restingOrderId;
        this.incomingOrderId = incomingOrderId;
        this.incomingSide = incomingSide;
        this.price = price;
        this.executedQuantity = executedQuantity;
        this.remainingQuantity = remainingQuantity;
    }
    

}
