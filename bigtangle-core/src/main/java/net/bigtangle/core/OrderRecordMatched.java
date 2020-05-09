/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class OrderRecordMatched extends OrderRecord {

    private static final long serialVersionUID = -2331665478149550684L;
    String transactionHash;
    Long matchBlockTime;

    //for JSON
    public OrderRecordMatched() {
       
    }

    public OrderRecordMatched(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash, long offerValue,
            String offerTokenid, boolean confirmed, boolean spent, Sha256Hash spenderBlockHash, long targetValue,
            String targetTokenid, byte[] beneficiaryPubKey, Long validToTime, Long validFromTime, String side,
            String beneficiaryAddress, String transactionHash, Long matchBlockTime) {
        this.setBlockHash(initialBlockHash);
        this.setIssuingMatcherBlockHash(issuingMatcherBlockHash);
        this.setOfferValue(offerValue);
        this.setOfferTokenid(offerTokenid);
        this.setConfirmed(confirmed);
        this.setSpent(spent);
        this.setSpenderBlockHash(spenderBlockHash);
        this.setTargetValue(targetValue);
        this.setTargetTokenid(targetTokenid);
        this.setBeneficiaryPubKey(beneficiaryPubKey);
        this.setValidToTime(validToTime);

        this.setValidFromTime(validFromTime);
        this.setSide(Side.valueOf(side));
        this.setBeneficiaryAddress(beneficiaryAddress);
        this.matchBlockTime = matchBlockTime;
        this.transactionHash = transactionHash;
    }

    public static OrderRecordMatched fromOrderRecord(OrderRecord old) {
        return new OrderRecordMatched(old.getBlockHash(), old.getIssuingMatcherBlockHash(), old.getOfferValue(),
                old.getOfferTokenid(), old.isConfirmed(), old.isSpent(), old.getSpenderBlockHash(),
                old.getTargetValue(), old.getTargetTokenid(), old.getBeneficiaryPubKey(), old.getValidToTime(),
                old.getValidFromTime(), old.getSide().name(), old.getBeneficiaryAddress(), null, null);
    }

    public String getTransactionHash() {
        return transactionHash;
    }

    public void setTransactionHash(String transactionHash) {
        this.transactionHash = transactionHash;
    }

    public long getMatchBlockTime() {
        return matchBlockTime;
    }

    public void setMatchBlockTime(long matchBlockTime) {
        this.matchBlockTime = matchBlockTime;
    }

}
