/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.server.data;

import java.math.BigInteger;
import java.util.Objects;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.SpentBlock;

public class ContractEventRecord extends SpentBlock {

    private static final long serialVersionUID = -2331665478149550684L;

  
    private String contractTokenid;
    private BigInteger targetValue;
    private String targetTokenid;
    // owner public address of the order for query
    private String beneficiaryAddress;

 
    public ContractEventRecord() {
    }

    public ContractEventRecord(Sha256Hash initialBlockHash, 
            String contractTokenid, boolean confirmed, boolean spent, Sha256Hash spenderBlockHash, BigInteger targetValue,
            String targetTokenid,   
            String beneficiaryAddress) {
        super();
        this.setBlockHash(initialBlockHash); 
        this.setConfirmed(confirmed);
        this.setSpent(spent);
        this.setSpenderBlockHash(spenderBlockHash);
        this.targetValue = targetValue;
        this.targetTokenid = targetTokenid;
  
   
        this.beneficiaryAddress = beneficiaryAddress;
        this.contractTokenid=contractTokenid;
    }

    public static ContractEventRecord cloneOrderRecord(ContractEventRecord old) {
        return new ContractEventRecord(old.getBlockHash(),    old.contractTokenid,
                old.isConfirmed(), old.isSpent(), old.getSpenderBlockHash(), old.targetValue, old.targetTokenid,
                   old.beneficiaryAddress);
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

 
 
    public static long getSerialversionuid() {
        return serialVersionUID;
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
	public int hashCode() {
		return Objects.hash(beneficiaryAddress, contractTokenid, targetTokenid, targetValue );
	}

	 

	@Override
	public String toString() {
		return "ContractEventRecord [blockhash=" + getBlockHashHex() + ", contractTokenid=" + contractTokenid
				+ ", targetValue=" + targetValue + ", targetTokenid=" + targetTokenid + ", beneficiaryAddress="
				+ beneficiaryAddress   + "]";
	}
	 
 
}
