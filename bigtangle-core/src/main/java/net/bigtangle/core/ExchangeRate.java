/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.math.BigDecimal;

/*
 * exchange rate of two coins, for example baseCurrency=CNY, baseCurrencyUnit=1 currency=BIG:tokenid rate=5000
 * 
 */
public class ExchangeRate implements java.io.Serializable {

    private static final long serialVersionUID = -702493172094450451L;
    public Long id;
    private String baseCurrency;
    private Long baseCurrencyUnit = 1l;
    private String currency;
    private java.sql.Timestamp exchangedate;
    private BigDecimal rate;
    // can be cancel
    private String status;
    // default the origin of rate
    private String remark;
    // who cahneg the data
    private String changedby;
    // change timetsamp
    private java.sql.Timestamp changeddate;
    // differency to last rate and is used only to display.
    private String changedTolast;

    public BigDecimal exchangeAmount(BigDecimal amount) {

        return rate.multiply(amount).divide(new BigDecimal(baseCurrencyUnit));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public java.sql.Timestamp getExchangedate() {
        return exchangedate;
    }

    public void setExchangedate(java.sql.Timestamp exchangedate) {
        this.exchangedate = exchangedate;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getChangedby() {
        return changedby;
    }

    public void setChangedby(String changedby) {
        this.changedby = changedby;
    }

    public java.sql.Timestamp getChangeddate() {
        return changeddate;
    }

    public void setChangeddate(java.sql.Timestamp changeddate) {
        this.changeddate = changeddate;
    }

    public String getChangedTolast() {
        return changedTolast;
    }

    public void setChangedTolast(String changedTolast) {
        this.changedTolast = changedTolast;
    }

    public Long getBaseCurrencyUnit() {
        return baseCurrencyUnit;
    }

    public void setBaseCurrencyUnit(Long baseCurrencyUnit) {
        this.baseCurrencyUnit = baseCurrencyUnit;
    }

}
