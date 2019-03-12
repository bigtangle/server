/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class OrderRecord implements java.io.Serializable {

    private static final long serialVersionUID = -2331665478149550684L;
    // order is in this block
    private Sha256Hash initialBlockHash;
    // order matching block
    private Sha256Hash issuingMatcherBlockHash;
    private long offerValue;
    private String offerTokenid;
    // order block is confirmed
    private boolean confirmed;
    // spent true is matched and closed, false is open.
    private boolean spent;
    private Sha256Hash spenderBlockHash;
    private long targetValue;
    private String targetTokenid;
    // owner public key of the order
    private byte[] beneficiaryPubKey;
    // owner public address of the order for query
    private String beneficiaryAddress;

    // order will be traded until this time
    private Long validToTime;
    private int opIndex;
    // order will be traded after this time
    private Long validFromTime;
    // Side can be calculated as Big Coin as only base trading coin
    private Side side;

    public OrderRecord() {
    }

    public OrderRecord(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash, long offerValue,
            String offerTokenid, boolean confirmed, boolean spent, Sha256Hash spenderBlockHash, long targetValue,
            String targetTokenid, byte[] beneficiaryPubKey, Long validToTime, int opIndex, Long validFromTime,
            String side, String beneficiaryAddress) {
        super();
        this.initialBlockHash = initialBlockHash;
        this.issuingMatcherBlockHash = issuingMatcherBlockHash;
        this.offerValue = offerValue;
        this.offerTokenid = offerTokenid;
        this.confirmed = confirmed;
        this.spent = spent;
        this.spenderBlockHash = spenderBlockHash;
        this.targetValue = targetValue;
        this.targetTokenid = targetTokenid;
        this.beneficiaryPubKey = beneficiaryPubKey;
        this.validToTime = validToTime;
        this.opIndex = opIndex;
        this.validFromTime = validFromTime;
        try {
            this.side = Side.valueOf(side);
        } catch (Exception e) {
            this.side = null;
        }
        this.beneficiaryAddress = beneficiaryAddress;
    }

    @Override
    public String toString() {
        return "Order \n[initialBlockHash=" + initialBlockHash + ", \nissuingMatcherBlockHash="
                + issuingMatcherBlockHash + ", \nofferValue=" + offerValue + ", \nofferTokenid=" + offerTokenid
                + ", \nconfirmed=" + confirmed + ", \nspent=" + spent + ", \nspenderBlockHash=" + spenderBlockHash
                + ", \ntargetValue=" + targetValue + ", \ntargetTokenid=" + targetTokenid + ", \nbeneficiaryPubKey="
                + Utils.HEX.encode(beneficiaryPubKey) + ", \nvalidToTime=" + validToTime + ", \nopIndex=" + opIndex
                + ", \nside=" + side + ", \nvalidFromTime=" + validFromTime + "]\n";
    }

    public boolean isTimeouted(long blockTime) {
        return blockTime > validToTime;
    }

    public boolean isValidYet(long blockTime) {
        return blockTime >= validFromTime;
    }

    public Sha256Hash getInitialBlockHash() {
        return initialBlockHash;
    }

    public void setInitialBlockHash(Sha256Hash initialBlockHash) {
        this.initialBlockHash = initialBlockHash;
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

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public boolean isSpent() {
        return spent;
    }

    public void setSpent(boolean spent) {
        this.spent = spent;
    }

    public Sha256Hash getSpenderBlockHash() {
        return spenderBlockHash;
    }

    public void setSpenderBlockHash(Sha256Hash spenderBlockHash) {
        this.spenderBlockHash = spenderBlockHash;
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

    public int getOpIndex() {
        return opIndex;
    }

    public void setOpIndex(int opIndex) {
        this.opIndex = opIndex;
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

}
