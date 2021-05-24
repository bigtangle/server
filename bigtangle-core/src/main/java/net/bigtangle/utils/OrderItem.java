package net.bigtangle.utils;

/*
 * display order class
 */
public class OrderItem {


    private String type;

    private String amount;

    private String tokenId;

    private String price;

    private String orderId;

    private String validateTo;

    private String validateFrom;

    private String address;

    private String initialBlockHashHex;

    private String tokenName;

    private boolean cancelPending;

    private String total;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getValidateTo() {
        return validateTo;
    }

    public void setValidateTo(String validateTo) {
        this.validateTo = validateTo;
    }

    public String getValidateFrom() {
        return validateFrom;
    }

    public void setValidateFrom(String validateFrom) {
        this.validateFrom = validateFrom;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getInitialBlockHashHex() {
        return initialBlockHashHex;
    }

    public void setInitialBlockHashHex(String initialBlockHashHex) {
        this.initialBlockHashHex = initialBlockHashHex;
    }

    public String getTokenName() {
        return tokenName;
    }

    public void setTokenName(String tokenName) {
        this.tokenName = tokenName;
    }

    public boolean isCancelPending() {
        return cancelPending;
    }

    public void setCancelPending(boolean cancelPending) {
        this.cancelPending = cancelPending;
    }

    public String getTotal() {
        return total;
    }

    public void setTotal(String total) {
        this.total = total;
    }
    
    
}
