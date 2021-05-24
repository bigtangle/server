package net.bigtangle.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Token;
import net.bigtangle.utils.MonetaryFormat;

public class MarketOrderItem implements java.io.Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -4622211003160381995L;

    public static MarketOrderItem build(OrderRecord orderRecord, Map<String, Token> tokennames,
            NetworkParameters networkParameters, String buy, String sell) {
        MonetaryFormat mf = MonetaryFormat.FIAT.noCode();
        MarketOrderItem marketOrderItem = new MarketOrderItem();
        Token base = tokennames.get(orderRecord.getOrderBaseToken());
        Integer priceshift = networkParameters.getOrderPriceShift(orderRecord.getOrderBaseToken());
        if (orderRecord.getOrderBaseToken().equals(orderRecord.getOfferTokenid())) {
            Token t = tokennames.get(orderRecord.getTargetTokenid());
            marketOrderItem.setType(buy);
            marketOrderItem.setAmount(mf.format(orderRecord.getTargetValue(), t.getDecimals()));
            marketOrderItem.setTokenId(orderRecord.getTargetTokenid());
            marketOrderItem.setTokenName(t.getTokennameDisplay());
            marketOrderItem.setPrice(mf.format(orderRecord.getPrice(), base.getDecimals() + priceshift));
            marketOrderItem.setTotal(mf.format(orderRecord.getOfferValue(), base.getDecimals()) + " ("
                    + base.getTokennameDisplay() + ")");
        } else {
            Token t = tokennames.get(orderRecord.getOfferTokenid());
            marketOrderItem.setType(sell);
            marketOrderItem.setAmount(mf.format(orderRecord.getOfferValue(), t.getDecimals()));
            marketOrderItem.setTokenId(orderRecord.getOfferTokenid());
            marketOrderItem.setTokenName(t.getTokennameDisplay());
            marketOrderItem.setPrice(mf.format(orderRecord.getPrice(), base.getDecimals() + priceshift));
            marketOrderItem.setTotal(mf.format(orderRecord.getTargetValue(), base.getDecimals()) + " ("
                    + base.getTokennameDisplay() + ")");
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        marketOrderItem.setValidateTo(dateFormat.format(new Date(orderRecord.getValidToTime() * 1000)));
        marketOrderItem.setValidateFrom(dateFormat.format(new Date(orderRecord.getValidFromTime() * 1000)));
        marketOrderItem.setAddress(
                ECKey.fromPublicOnly(orderRecord.getBeneficiaryPubKey()).toAddress(networkParameters).toString());
        marketOrderItem.setInitialBlockHashHex(orderRecord.getBlockHashHex());
        marketOrderItem.setCancelPending(orderRecord.isCancelPending());

        return marketOrderItem;
    }

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

    public String getTotal() {
        return total;
    }

    public void setTotal(String total) {
        this.total = total;
    }

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
}
