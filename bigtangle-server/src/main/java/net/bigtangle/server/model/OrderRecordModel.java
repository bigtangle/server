/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.model;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;

public class OrderRecordModel extends SpentBlockModel {

    private static final long serialVersionUID = -2331665478149550684L;

    // order matching block
    private String issuingmatcherblockhash;
    private long offercoinvalue;
    private String offertokenid;

    private long targetcoinvalue;
    private String targettokenid;
    // owner public key of the order
    private String beneficiarypubkey;
    // owner public address of the order for query
    private String beneficiaryaddress;

    // order will be traded until this time
    private Long validtotime;
    // order will be traded after this time
    private Long validfromtime;
    // Side can be calculated as Big Coin as only base trading coin
    private String side;

    // price from the order open
    private String orderbasetoken;
    private int tokendecimals;
    // Base token for the order
    private Long price;

    /*
     * for wallet set the order status from cancel
     */

    private boolean cancelPending;
    private long cancelPendingTime;

    public OrderRecord toOrderRecord() {
        return new OrderRecord(Sha256Hash.wrap(getBlockhash()), Sha256Hash.wrap(getIssuingmatcherblockHash()),
                getOffercoinvalue(), getOffertokenid(), isConfirmed(), isSpent(), Sha256Hash.wrap(getSpenderblockhash()),
                getTargetcoinvalue(), getTargettokenid(), Utils.HEX.decode(getBeneficiarypubkey()), getValidtotime(),
                getValidfromtime(), getSide(), getBeneficiaryaddress(), getOrderbasetoken(), getPrice(),
                getTokendecimals());

    }

    public String getIssuingmatcherblockHash() {
        return issuingmatcherblockhash;
    }

    public void setIssuingmatcherblockHash(String issuingmatcherblockhash) {
        this.issuingmatcherblockhash = issuingmatcherblockhash;
    }

  
    public String getOffertokenid() {
        return offertokenid;
    }

    public void setOffertokenid(String offertokenid) {
        this.offertokenid = offertokenid;
    }
 
    public long getOffercoinvalue() {
        return offercoinvalue;
    }

    public void setOffercoinvalue(long offercoinvalue) {
        this.offercoinvalue = offercoinvalue;
    }

    public long getTargetcoinvalue() {
        return targetcoinvalue;
    }

    public void setTargetcoinvalue(long targetcoinvalue) {
        this.targetcoinvalue = targetcoinvalue;
    }

    public String getTargettokenid() {
        return targettokenid;
    }

    public void setTargettokenid(String targettokenid) {
        this.targettokenid = targettokenid;
    }

    public String getBeneficiarypubkey() {
        return beneficiarypubkey;
    }

    public void setBeneficiarypubkey(String beneficiarypubkey) {
        this.beneficiarypubkey = beneficiarypubkey;
    }

    public String getBeneficiaryaddress() {
        return beneficiaryaddress;
    }

    public void setBeneficiaryaddress(String beneficiaryaddress) {
        this.beneficiaryaddress = beneficiaryaddress;
    }

    public Long getValidtotime() {
        return validtotime;
    }

    public void setValidtotime(Long validtotime) {
        this.validtotime = validtotime;
    }

    public Long getValidfromtime() {
        return validfromtime;
    }

    public void setValidfromtime(Long validfromtime) {
        this.validfromtime = validfromtime;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getOrderbasetoken() {
        return orderbasetoken;
    }

    public void setOrderbasetoken(String orderbasetoken) {
        this.orderbasetoken = orderbasetoken;
    }

    public int getTokendecimals() {
        return tokendecimals;
    }

    public void setTokendecimals(int tokendecimals) {
        this.tokendecimals = tokendecimals;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public boolean isCancelPending() {
        return cancelPending;
    }

    public void setCancelPending(boolean cancelPending) {
        this.cancelPending = cancelPending;
    }

    public long getCancelPendingTime() {
        return cancelPendingTime;
    }

    public void setCancelPendingTime(long cancelPendingTime) {
        this.cancelPendingTime = cancelPendingTime;
    }

    public static OrderRecordModel from(OrderRecord record) {
        OrderRecordModel p = new OrderRecordModel();
        p.setBlockhash(record.getBlockHash().toString());
        p.setIssuingmatcherblockHash(record.getIssuingMatcherBlockHash().toString());
        p.setOffercoinvalue(record.getOfferValue());
        p.setOffertokenid(record.getOfferTokenid());
        p.setConfirmed(record.isConfirmed());
        p.setSpent(record.isSpent());
        p.setSpenderblockhash(record.getSpenderBlockHash() != null ? record.getSpenderBlockHash().toString() : null);
        p.setTargetcoinvalue(record.getTargetValue());
        p.setTargettokenid(record.getTargetTokenid());
        p.setBeneficiarypubkey(record.getBeneficiaryPubKey().toString());
        p.setValidtotime(record.getValidToTime());
        p.setValidfromtime(record.getValidFromTime());
        p.setSide(record.getSide() == null ? null : record.getSide().name());
        p.setBeneficiaryaddress(record.getBeneficiaryAddress());
        p.setOrderbasetoken(record.getOrderBaseToken());
        p.setPrice(record.getPrice());
        p.setTokendecimals(record.getTokenDecimals());
        p.fromSpentBlock(record);
        return p;
    }

}
