package net.bigtangle.airdrop.bean;

import java.math.BigDecimal;

public class Vm_deposit {
    public Long userid;
    public String useraccount;
    public String deposittype;
    public String description;
    public BigDecimal amount;
    public String currency;
    public String status;
    public String pubkey;

    public Long getUserid() {
        return userid;
    }

    public void setUserid(Long userid) {
        this.userid = userid;
    }

    public String getUseraccount() {
        return useraccount;
    }

    public void setUseraccount(String useraccount) {
        this.useraccount = useraccount;
    }

    public String getDeposittype() {
        return deposittype;
    }

    public void setDeposittype(String deposittype) {
        this.deposittype = deposittype;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

}
