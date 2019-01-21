/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

public class OrderRecord implements java.io.Serializable {

	private static final long serialVersionUID = -2331665478149550684L;
	
    private Sha256Hash txHash;
    private Sha256Hash issuingMatcherBlockHash;
	private long offerValue;
    private String offerTokenid;
    private boolean confirmed;
    private boolean spent;
    private Sha256Hash spenderBlockHash;
    private long targetValue; 
    private String targetTokenid;
    private byte[] beneficiaryPubKey;
    private int ttl;
    private int opIndex; 

    public OrderRecord(Sha256Hash txHash, Sha256Hash issuingMatcherBlockHash, long offerValue, String offerTokenid,
			boolean confirmed, boolean spent, Sha256Hash spenderBlockHash, long targetValue, String targetTokenid,
			byte[] beneficiaryPubKey, int ttl, int opIndex) {
		super();
		this.txHash = txHash;
		this.issuingMatcherBlockHash = issuingMatcherBlockHash;
		this.offerValue = offerValue;
		this.offerTokenid = offerTokenid;
		this.confirmed = confirmed;
		this.spent = spent;
		this.spenderBlockHash = spenderBlockHash;
		this.targetValue = targetValue;
		this.targetTokenid = targetTokenid;
		this.beneficiaryPubKey = beneficiaryPubKey;
		this.ttl = ttl;
		this.opIndex = opIndex;
	}

	public Sha256Hash getTxHash() {
		return txHash;
	}

	public void setTxHash(Sha256Hash txHash) {
		this.txHash = txHash;
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

	public int getTtl() {
		return ttl;
	}

	public void setTtl(int ttl) {
		this.ttl = ttl;
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
}
