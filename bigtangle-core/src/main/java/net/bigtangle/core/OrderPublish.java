/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.util.Date;

import net.bigtangle.utils.OrderState;
import net.bigtangle.utils.UUIDUtil;

public class OrderPublish implements java.io.Serializable {

    public static OrderPublish create(String address, String tokenid, int type, Date validateto, Date validatefrom,
            long price, long amount, String market) {
        return new OrderPublish(address, tokenid, type, validateto, validatefrom, price, amount, market);
    }

    private static final long serialVersionUID = 190060684620430983L;

    private String orderId;

    private String address;

    private String tokenId;

    private int type;

    private Date validateTo;

    private Date validateFrom;

    private int state;

    private long price;

    private long amount;
    
    private String market;
    
    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Date getValidateTo() {
        return validateTo;
    }

    public void setValidateTo(Date validateTo) {
        this.validateTo = validateTo;
    }

    public Date getValidateFrom() {
        return validateFrom;
    }

    public void setValidateFrom(Date validateFrom) {
        this.validateFrom = validateFrom;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public OrderPublish(String address, String tokenid, int type, Date validateto, Date validatefrom, 
            long price, long amount, String market) {
        this.orderId = UUIDUtil.randomUUID();
        this.address = address;
        this.tokenId = tokenid;
        this.type = type;
        this.validateTo = validateto;
        this.validateFrom = validatefrom;
        this.state = OrderState.publish.ordinal();
        this.price = price;
        this.amount = amount;
        this.market = market;
    }

    public OrderPublish() {
    }
}
