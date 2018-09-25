/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class TokenSerial implements java.io.Serializable {

    private static final long serialVersionUID = -1523724625828286333L;

    private String tokenid;

    private long tokenindex;

    private long amount;
    private long signnumber;
    private long count;

    public String getTokenid() {
        return tokenid;
    }

    public void setTokenid(String tokenid) {
        this.tokenid = tokenid;
    }

    public long getTokenindex() {
        return tokenindex;
    }

    public void setTokenindex(long tokenindex) {
        this.tokenindex = tokenindex;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public TokenSerial(String tokenid, long tokenindex, long amount,long signnumber,long count) {
        this.tokenid = tokenid;
        this.tokenindex = tokenindex;
        this.amount = amount;
        this.signnumber=signnumber;
        this.count=count;
    }
    public TokenSerial(String tokenid, long tokenindex, long amount) {
        this.tokenid = tokenid;
        this.tokenindex = tokenindex;
        this.amount = amount;
    }
    public TokenSerial() {
    }

    public TokenSerial copy(TokenSerial tokenSerial) {
        this.tokenid = tokenSerial.getTokenid();
        this.tokenindex = tokenSerial.getTokenindex();
        this.amount = tokenSerial.getAmount();
        return this;
    }

    public long getSignnumber() {
        return signnumber;
    }

    public void setSignnumber(long signnumber) {
        this.signnumber = signnumber;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
