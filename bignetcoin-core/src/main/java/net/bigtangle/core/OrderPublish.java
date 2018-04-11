package net.bigtangle.core;

import java.util.Date;
import java.util.UUID;

public class OrderPublish implements java.io.Serializable {

    public static OrderPublish create(String address, String tokenid, int type, Date validateto, Date validatefrom,
            long price, long amount) {
        return new OrderPublish(address, tokenid, type, validateto, validatefrom, price, amount);
    }

    private static final long serialVersionUID = 190060684620430983L;

    private String orderid;

    private String address;

    private String tokenid;

    private int type;

    private Date validateto;

    private Date validatefrom;

    private int state;

    private long price;

    private long amount;

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

    public String getOrderid() {
        return orderid;
    }

    public void setOrderid(String orderid) {
        this.orderid = orderid;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Date getValidateto() {
        return validateto;
    }

    public void setValidateto(Date validateto) {
        this.validateto = validateto;
    }

    public Date getValidatefrom() {
        return validatefrom;
    }

    public void setValidatefrom(Date validatefrom) {
        this.validatefrom = validatefrom;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public OrderPublish(String address, String tokenid, int type, Date validateto, Date validatefrom, long price, long amount) {
        this.orderid = UUID.randomUUID().toString().replaceAll("-", "");
        this.address = address;
        this.tokenid = tokenid;
        this.type = type;
        this.validateto = validateto;
        this.validatefrom = validatefrom;
        this.state = 0;
        this.price = price;
        this.amount = amount;
    }

    public OrderPublish() {
    }
}
