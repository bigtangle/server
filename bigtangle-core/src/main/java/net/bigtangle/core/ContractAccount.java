/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.math.BigInteger;

public class ContractAccount extends DataClass {

	 
	private Sha256Hash blockHash;
	private String contractTokenid;

	private String tokenid;
	private BigInteger value;

	public ContractAccount() {
	}

	public ContractAccount(Sha256Hash initialBlockHash, String contractTokenid, BigInteger targetValue,
			String tokenid) {
		super();
		this.blockHash = initialBlockHash;
		this.value = targetValue;
		this.tokenid = tokenid;
	}

	public Sha256Hash getBlockHash() {
		return blockHash;
	}

	public void setBlockHash(Sha256Hash blockHash) {
		this.blockHash = blockHash;
	}

	public String getContractTokenid() {
		return contractTokenid;
	}

	public void setContractTokenid(String contractTokenid) {
		this.contractTokenid = contractTokenid;
	}

	public String getTokenid() {
		return tokenid;
	}

	public void setTokenid(String tokenid) {
		this.tokenid = tokenid;
	}

	public BigInteger getValue() {
		return value;
	}

	public void setValue(BigInteger value) {
		this.value = value;
	}

	@Override
	public String toString() {
		return "ContractAccount [blockHash=" + blockHash + ", contractTokenid=" + contractTokenid + ", tokenid="
				+ tokenid + ", value=" + value + "]";
	}

}
