/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.util.Arrays;

public class OrderRecord extends SpentBlock  {

    private static final long serialVersionUID = -2331665478149550684L;
 
    // order matching block
    private Sha256Hash issuingMatcherBlockHash;
    private long offerValue;
    private String offerTokenid;
 
    private long targetValue;
    private String targetTokenid;
    // owner public key of the order
    private byte[] beneficiaryPubKey;
    // owner public address of the order for query
    private String beneficiaryAddress;

    // order will be traded until this time
    private Long validToTime; 
    // order will be traded after this time
    private Long validFromTime;
    // Side can be calculated as Big Coin as only base trading coin
    private Side side;

    
    /*
     * for wallet set the order status from cancel
     */
    
    private boolean cancelPending;
    private long cancelPendingTime;
  
    
    public OrderRecord() {
    }

    public OrderRecord(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash, long offerValue,
            String offerTokenid, boolean confirmed, boolean spent, Sha256Hash spenderBlockHash, long targetValue,
            String targetTokenid, byte[] beneficiaryPubKey, Long validToTime,  Long validFromTime,
            String side, String beneficiaryAddress) {
        super();
        this.setBlockHash ( initialBlockHash);
        this.issuingMatcherBlockHash = issuingMatcherBlockHash;
        this.offerValue = offerValue;
        this.offerTokenid = offerTokenid;
        this.setConfirmed (confirmed);
        this.setSpent( spent);
        this.setSpenderBlockHash( spenderBlockHash);
        this.targetValue = targetValue;
        this.targetTokenid = targetTokenid;
        this.beneficiaryPubKey = beneficiaryPubKey;
        this.validToTime = validToTime;
 
        this.validFromTime = validFromTime;
        try {
            this.side = Side.valueOf(side);
        } catch (Exception e) {
            this.side = null;
        }
        this.beneficiaryAddress = beneficiaryAddress;
    }

    public long price() {
        return getTargetValue() / getOfferValue();
    }

     

    public boolean isTimeouted(long blockTime) {
        return blockTime > validToTime;
    }

    public boolean isValidYet(long blockTime) {
        return blockTime >= validFromTime;
    }

     
    public Sha256Hash getIssuingMatcherBlockHash() {
        return issuingMatcherBlockHash;
    }

    public void setIssuingMatcherBlockHash(Sha256Hash issuingMatcherBlockHash) {
        this.issuingMatcherBlockHash = issuingMatcherBlockHash;
    }

    public long getOfferValue() {
        return offerValue;
    }

    public void setOfferValue(long offerValue) {
        this.offerValue = offerValue;
    }

    public String getOfferTokenid() {
        return offerTokenid;
    }

    public void setOfferTokenid(String offerTokenid) {
        this.offerTokenid = offerTokenid;
    }
  
    public long getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(long targetValue) {
        this.targetValue = targetValue;
    }

    public String getTargetTokenid() {
        return targetTokenid;
    }

    public void setTargetTokenid(String targetTokenid) {
        this.targetTokenid = targetTokenid;
    }

    public byte[] getBeneficiaryPubKey() {
        return beneficiaryPubKey;
    }

    public void setBeneficiaryPubKey(byte[] beneficiaryPubKey) {
        this.beneficiaryPubKey = beneficiaryPubKey;
    }

    public Long getValidToTime() {
        return validToTime;
    }

    public void setValidToTime(Long validToTime) {
        this.validToTime = validToTime;
    }

  
    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public Long getValidFromTime() {
        return validFromTime;
    }

    public void setValidFromTime(Long validFromTime) {
        this.validFromTime = validFromTime;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public String getBeneficiaryAddress() {
        return beneficiaryAddress;
    }

    public void setBeneficiaryAddress(String beneficiaryAddress) {
        this.beneficiaryAddress = beneficiaryAddress;
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

    @Override
    public String toString() {
        return "OrderRecord [issuingMatcherBlockHash=" + issuingMatcherBlockHash + ", offerValue=" + offerValue
                + ", offerTokenid=" + offerTokenid + ", targetValue=" + targetValue + ", targetTokenid=" + targetTokenid
                + ", beneficiaryPubKey=" + Arrays.toString(beneficiaryPubKey) + ", beneficiaryAddress="
                + beneficiaryAddress + ", validToTime=" + validToTime +   ", validFromTime="
                + validFromTime + ", side=" + side + "]";
    }

}
