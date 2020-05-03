/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.SpentBlock;

public class ContractEventRecord extends SpentBlock {

    private static final long serialVersionUID = -2331665478149550684L;

    // order matching block
    private Sha256Hash issuingMatcherBlockHash;
    private String contractTokenid;
    private BigInteger targetValue;
    private String targetTokenid;
    // owner public key of the order
    private byte[] beneficiaryPubKey;
    // owner public address of the order for query
    private String beneficiaryAddress;

    // order will be traded until this time
    private Long validToTime;
    // order will be traded after this time
    private Long validFromTime;
      /*
     * for wallet set the order status from cancel
     */

    private boolean cancelPending;
    private long cancelPendingTime;

    public ContractEventRecord() {
    }

    public ContractEventRecord(Sha256Hash initialBlockHash, Sha256Hash issuingMatcherBlockHash, 
            String contractTokenid, boolean confirmed, boolean spent, Sha256Hash spenderBlockHash, BigInteger targetValue,
            String targetTokenid, byte[] beneficiaryPubKey, Long validToTime, Long validFromTime, 
            String beneficiaryAddress) {
        super();
        this.setBlockHash(initialBlockHash);
        this.issuingMatcherBlockHash = issuingMatcherBlockHash;
 
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

    public static ContractEventRecord cloneOrderRecord(ContractEventRecord old) {
        return new ContractEventRecord(old.getBlockHash(), old.issuingMatcherBlockHash,   old.contractTokenid,
                old.isConfirmed(), old.isSpent(), old.getSpenderBlockHash(), old.targetValue, old.targetTokenid,
                old.beneficiaryPubKey, old.validToTime, old.validFromTime, old.beneficiaryAddress);
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

   
 

    public BigInteger getTargetValue() {
		return targetValue;
	}

	public void setTargetValue(BigInteger targetValue) {
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

	public String getContractTokenid() {
		return contractTokenid;
	}

	public void setContractTokenid(String contractTokenid) {
		this.contractTokenid = contractTokenid;
	}

	@Override
	public String toString() {
		return "ContractEventRecord [issuingMatcherBlockHash=" + issuingMatcherBlockHash + ", contractTokenid="
				+ contractTokenid + ", targetValue=" + targetValue + ", targetTokenid=" + targetTokenid
				+ ", beneficiaryPubKey=" + Arrays.toString(beneficiaryPubKey) + ", beneficiaryAddress="
				+ beneficiaryAddress + ", validToTime=" + validToTime + ", validFromTime=" + validFromTime
				+ ", cancelPending=" + cancelPending + ", cancelPendingTime=" + cancelPendingTime + "]";
	}

 
}
