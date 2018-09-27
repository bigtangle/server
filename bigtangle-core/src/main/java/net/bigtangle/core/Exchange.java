/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.beans.Transient;
import java.util.List;

import javax.sound.midi.VoiceStatus;

import org.apache.commons.lang3.StringUtils;

import net.bigtangle.utils.UUIDUtil;

public class Exchange implements java.io.Serializable {

    private static final long serialVersionUID = -702493172094450451L;

    private String orderid;
    
    private String fromAddress;
    
    private String fromTokenHex;
    
    private String fromAmount;
    
    private String toAddress;
    
    private String toTokenHex;
    
    private String toAmount;
    
    private byte[] data;
    
    private int toSign;
    
    private int fromSign;
    
    private String toOrderId;
    
    private String fromOrderId;
    
    private String market;
    
    private List<ExchangeMulti> exchangeMultis;
    
    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getToOrderId() {
        return toOrderId;
    }

    public void setToOrderId(String toOrderId) {
        this.toOrderId = toOrderId;
    }

    public String getFromOrderId() {
        return fromOrderId;
    }

    public void setFromOrderId(String fromOrderId) {
        this.fromOrderId = fromOrderId;
    }

    public int getToSign() {
        return toSign;
    }

    public void setToSign(int toSign) {
        this.toSign = toSign;
    }

    public int getFromSign() {
        return fromSign;
    }

    public void setFromSign(int fromSign) {
        this.fromSign = fromSign;
    }

    public Exchange(String fromAddress, String fromTokenHex, String fromAmount, String toAddress,
            String toTokenHex, String toAmount, byte[] data) {
        this.orderid = UUIDUtil.randomUUID();
        this.fromAddress = fromAddress;
        this.fromTokenHex = fromTokenHex;
        this.fromAmount = fromAmount;
        this.toAddress = toAddress;
        this.toTokenHex = toTokenHex;
        this.toAmount = toAmount;
        this.data = data;
    }
    
    public Exchange(String fromOrderId, String fromAddress, String fromTokenHex, String fromAmount, String toOrderId, String toAddress,
            String toTokenHex, String toAmount, byte[] data, String market) {
        this(fromAddress, fromTokenHex, fromAmount, toAddress, toTokenHex, toAmount, data);
        this.toOrderId = toOrderId;
        this.fromOrderId = fromOrderId;
        this.market = market;
    }

    public Exchange() {
    }

    @Transient
    public byte[] getData() {
        return data;
    }
    
    public String getDataHex() {
    	if (this.data == null) {
    		return "";
    	}
        return Utils.HEX.encode(this.data);
    }
    
	public void setDataHex(String dataHex) {
		if (StringUtils.isBlank(dataHex)) {
			this.data = null;
		} else {
			this.data = Utils.HEX.decode(dataHex);
		}
	}
    
    public void setData(byte[] data) {
        this.data = data;
    }

    public String getOrderid() {
        return orderid;
    }

    public void setOrderid(String orderid) {
        this.orderid = orderid;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromTokenHex() {
        return fromTokenHex;
    }

    public void setFromTokenHex(String fromTokenHex) {
        this.fromTokenHex = fromTokenHex;
    }

    public String getFromAmount() {
        return fromAmount;
    }

    public void setFromAmount(String fromAmount) {
        this.fromAmount = fromAmount;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getToTokenHex() {
        return toTokenHex;
    }

    public void setToTokenHex(String toTokenHex) {
        this.toTokenHex = toTokenHex;
    }

    public String getToAmount() {
        return toAmount;
    }

    public void setToAmount(String toAmount) {
        this.toAmount = toAmount;
    }

    public List<ExchangeMulti> getExchangeMultis() {
        return exchangeMultis;
    }

    public void setExchangeMultis(List<ExchangeMulti> exchangeMultis) {
        this.exchangeMultis = exchangeMultis;
    }
}
