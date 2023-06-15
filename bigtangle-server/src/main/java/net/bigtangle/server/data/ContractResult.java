package net.bigtangle.server.data;

import java.util.Set;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;

public class ContractResult {
	Set<ContractEventRecord> spentContractEventRecord;
	Transaction outputTx;

	public ContractResult() {

	}

	public ContractResult(Set<ContractEventRecord> spentOrders, Transaction outputTx) {
		this.spentContractEventRecord = spentOrders;
		this.outputTx = outputTx;

	}

	/*
	 * This is unique for ResultHash
	 */
	public Sha256Hash getResultHash() {
		return getOutputTx().getHash();
	}

 

	public Set<ContractEventRecord> getSpentContractEventRecord() {
		return spentContractEventRecord;
	}

	public void setSpentContractEventRecord(Set<ContractEventRecord> spentContractEventRecord) {
		this.spentContractEventRecord = spentContractEventRecord;
	}

	public Transaction getOutputTx() {
		return outputTx;
	}

	public void setOutputTx(Transaction outputTx) {
		this.outputTx = outputTx;
	}

}