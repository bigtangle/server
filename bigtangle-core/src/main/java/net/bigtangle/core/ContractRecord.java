package net.bigtangle.core;

public class ContractRecord extends OrderRecord {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

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

	private String contractTokenid;
	private String offerSystem;
	private String targetSystem;

	public ContractRecord() {
	}

	public ContractRecord(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash, long offerValue,
			String offerTokenid, boolean confirmed, boolean spent, Sha256Hash spenderBlockHash, long targetValue,
			String targetTokenid, byte[] beneficiaryPubKey, Long validToTime, Long validFromTime, String side,
			String beneficiaryAddress, String orderBaseToken, Long price, int tokenDecimals) {
		super();
		this.setBlockHash(initialBlockHash);
		this.issuingMatcherBlockHash = issuingMatcherBlockHash;
		this.offerValue = offerValue;
		this.offerTokenid = offerTokenid;
		this.setConfirmed(confirmed);
		this.setSpent(spent);
		this.setSpenderBlockHash(spenderBlockHash);
		this.targetValue = targetValue;
		this.targetTokenid = targetTokenid;
		this.beneficiaryPubKey = beneficiaryPubKey;
		this.validToTime = validToTime; 
		this.validFromTime = validFromTime; 
		this.beneficiaryAddress = beneficiaryAddress;
 
	}

	public String getContractTokenid() {
		return contractTokenid;
	}

	public void setContractTokenid(String contractTokenid) {
		this.contractTokenid = contractTokenid;
	}

	public String getOfferSystem() {
		return offerSystem;
	}

	public void setOfferSystem(String offerSystem) {
		this.offerSystem = offerSystem;
	}

	public String getTargetSystem() {
		return targetSystem;
	}

	public void setTargetSystem(String targetSystem) {
		this.targetSystem = targetSystem;
	}

}
