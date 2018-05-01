/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

public class OrderMatch implements java.io.Serializable {

    private static final long serialVersionUID = -3412284777635559808L;

    private String matchid;
    
    private String restingOrderId;
    
    private String incomingOrderId;
    
    private int type;
    
    private long price;
    
    private long executedQuantity;
    
    private long remainingQuantity;

    public String getMatchid() {
        return matchid;
    }

    public void setMatchid(String matchid) {
        this.matchid = matchid;
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

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
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
}
