/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.data;

import java.math.BigInteger;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.SpentBlock;

public class ContractEventRecord extends SpentBlock {

    private static final long serialVersionUID = -2331665478149550684L;

  
    private String contractTokenid;
    private BigInteger targetValue;
    private String targetTokenid;
    // owner public address of the order for query
    private String beneficiaryAddress;

    // order will be traded until this time
    private Long validToTime;
    // order will be traded after this time
    private Long validFromTime;


    public ContractEventRecord() {
    }

    public ContractEventRecord(Sha256Hash initialBlockHash, 
            String contractTokenid, boolean confirmed, boolean spent, Sha256Hash spenderBlockHash, BigInteger targetValue,
            String targetTokenid,   Long validToTime, Long validFromTime, 
            String beneficiaryAddress) {
        super();
        this.setBlockHash(initialBlockHash); 
        this.setConfirmed(confirmed);
        this.setSpent(spent);
        this.setSpenderBlockHash(spenderBlockHash);
        this.targetValue = targetValue;
        this.targetTokenid = targetTokenid;
 
        this.validToTime = validToTime;

        this.validFromTime = validFromTime;
   
        this.beneficiaryAddress = beneficiaryAddress;
    }

    public static ContractEventRecord cloneOrderRecord(ContractEventRecord old) {
        return new ContractEventRecord(old.getBlockHash(),    old.contractTokenid,
                old.isConfirmed(), old.isSpent(), old.getSpenderBlockHash(), old.targetValue, old.targetTokenid,
                  old.validToTime, old.validFromTime, old.beneficiaryAddress);
    }

    

    public boolean isTimeouted(long blockTime) {
        return blockTime > validToTime;
    }

    public boolean isValidYet(long blockTime) {
        return blockTime >= validFromTime;
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
 
	public String getContractTokenid() {
		return contractTokenid;
	}

	public void setContractTokenid(String contractTokenid) {
		this.contractTokenid = contractTokenid;
	}

 

	@Override
	public String toString() {
		return "ContractEventRecord [blockhash=" + getBlockHashHex() + ", contractTokenid=" + contractTokenid
				+ ", targetValue=" + targetValue + ", targetTokenid=" + targetTokenid + ", beneficiaryAddress="
				+ beneficiaryAddress + ", validToTime=" + validToTime + ", validFromTime=" + validFromTime + "]";
	}
	 
 
}
