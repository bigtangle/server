package net.bigtangle.server.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.SpentBlock;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;

/*
 * Contract Execution provide the results from the execution based on prev results.
 * It must be check on every node and should be the same result.
 * The data is saved in table ContractResult mainly as byte.
 */
public class ContractExecutionResult extends SpentBlock {

	String contracttokenid;
	// reference the previous ContractResult block, it forms a chain
	Sha256Hash prevblockhash;
	// reference the previous ContractResult block, it forms a chain
	long contractchainlength;
	// referenced new order blocks
	Set<Sha256Hash> referencedBlocks = new HashSet<>();;

	// this ContractResult produces coinbase outputTxHash
	Sha256Hash outputTxHash;
	// all records used in this calculation of ContractResult
	Set<Sha256Hash> allRecords = new HashSet<>();
	// the cancelled records referenced by this ContractResult
	Set<Sha256Hash> cancelRecords = new HashSet<>();
	// remainder Record is open records after execution
	Set<Sha256Hash> remainderRecords = new HashSet<>();
	// allRecords (this execution) = newRecords (this execution) + remainderRecords
	// (previous execution)

	// not part of toArray, not persistent, but data after the check
	// with re calculation to save
	Transaction outputTx;

	Set<ContractEventRecord> remainderContractEventRecord;

	public ContractExecutionResult() {

	}

	public ContractExecutionResult(Sha256Hash blockhash, String contractid, Set<Sha256Hash> toBeSpent,
			Sha256Hash outputTxHash, Transaction outputTx, Sha256Hash prevblockhash, long contractchainlength,
			Set<Sha256Hash> cancelRecords, Set<Sha256Hash> remainderRecords, long inserttime,
			Set<ContractEventRecord> remainderContractEventRecord, Set<Sha256Hash> referencedOrderBlocks) {
		this.setBlockHash(blockhash);
		this.contracttokenid = contractid;
		this.prevblockhash = prevblockhash;
		this.outputTxHash = outputTxHash;
		this.outputTx = outputTx;
		this.allRecords = toBeSpent;
		this.cancelRecords = cancelRecords;
		this.remainderRecords = remainderRecords;
		this.setTime(inserttime);

		this.remainderContractEventRecord = remainderContractEventRecord;
		this.referencedBlocks = referencedOrderBlocks;
		this.contractchainlength = contractchainlength;
	}

	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);
			dos.write(super.toByteArray());
			Utils.writeNBytesString(dos, contracttokenid);
			Utils.writeNBytes(dos, outputTxHash.getBytes());
			Utils.writeNBytes(dos, prevblockhash.getBytes());
			Utils.writeLong(dos, contractchainlength);
			dos.writeInt(allRecords.size());
			for (Sha256Hash c : allRecords) {
				Utils.writeNBytes(dos, c.getBytes());
			}

			dos.writeInt(cancelRecords.size());
			for (Sha256Hash c : cancelRecords) {
				Utils.writeNBytes(dos, c.getBytes());
			}
			dos.writeInt(remainderRecords.size());
			for (Sha256Hash c : remainderRecords) {
				Utils.writeNBytes(dos, c.getBytes());
			}
			dos.writeInt(referencedBlocks.size());
			for (Sha256Hash c : referencedBlocks) {
				Utils.writeNBytes(dos, c.getBytes());
			}

			dos.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	@Override
	public ContractExecutionResult parseDIS(DataInputStream dis) throws IOException {
		super.parseDIS(dis);
		contracttokenid = Utils.readNBytesString(dis);
		outputTxHash = Sha256Hash.wrap(Utils.readNBytes(dis));
		prevblockhash = Sha256Hash.wrap(Utils.readNBytes(dis));
		contractchainlength = Utils.readLong(dis);
		allRecords = new HashSet<>();
		int allRecordsSize = dis.readInt();
		for (int i = 0; i < allRecordsSize; i++) {
			allRecords.add(Sha256Hash.wrap(Utils.readNBytes(dis)));
		}
		cancelRecords = new HashSet<>();
		int cancelRecordsSize = dis.readInt();
		for (int i = 0; i < cancelRecordsSize; i++) {
			cancelRecords.add(Sha256Hash.wrap(Utils.readNBytes(dis)));
		}
		remainderRecords = new HashSet<>();
		int remainderRecordsSize = dis.readInt();
		for (int i = 0; i < remainderRecordsSize; i++) {
			remainderRecords.add(Sha256Hash.wrap(Utils.readNBytes(dis)));
		}
		int blocksSize = dis.readInt();
		referencedBlocks = new HashSet<>();
		for (int i = 0; i < blocksSize; i++) {
			referencedBlocks.add(Sha256Hash.wrap(Utils.readNBytes(dis)));
		}

		return this;
	}

	public ContractExecutionResult parseChecked(byte[] buf) {
		try {
			return parse(buf);
		} catch (IOException e) {
			// Cannot happen since checked before
			throw new RuntimeException(e);
		}
	}

	public ContractExecutionResult parse(byte[] buf) throws IOException {
		ByteArrayInputStream bain = new ByteArrayInputStream(buf);
		DataInputStream dis = new DataInputStream(bain);
		parseDIS(dis);
		dis.close();
		bain.close();
		return this;
	}

	public Sha256Hash getOutputTxHash() {
		return outputTxHash;
	}

	public void setOutputTxHash(Sha256Hash outputTxHash) {
		this.outputTxHash = outputTxHash;
	}

	public Transaction getOutputTx() {
		return outputTx;
	}

	public void setOutputTx(Transaction outputTx) {
		this.outputTx = outputTx;
	}

	public String getContracttokenid() {
		return contracttokenid;
	}

	public void setContracttokenid(String contracttokenid) {
		this.contracttokenid = contracttokenid;
	}

	public Sha256Hash getPrevblockhash() {
		return prevblockhash;
	}

	public void setPrevblockhash(Sha256Hash prevblockhash) {
		this.prevblockhash = prevblockhash;
	}

	public Set<Sha256Hash> getAllRecords() {
		return allRecords;
	}

	public void setAllRecords(Set<Sha256Hash> allRecords) {
		this.allRecords = allRecords;
	}

	public Set<Sha256Hash> getCancelRecords() {
		return cancelRecords;
	}

	public void setCancelRecords(Set<Sha256Hash> cancelRecords) {
		this.cancelRecords = cancelRecords;
	}

	public Set<Sha256Hash> getRemainderRecords() {
		return remainderRecords;
	}

	public void setRemainderRecords(Set<Sha256Hash> remainderRecords) {
		this.remainderRecords = remainderRecords;
	}

	public Set<ContractEventRecord> getRemainderContractEventRecord() {
		return remainderContractEventRecord;
	}

	public void setRemainderContractEventRecord(Set<ContractEventRecord> remainderContractEventRecord) {
		this.remainderContractEventRecord = remainderContractEventRecord;
	}

	public Set<Sha256Hash> getReferencedBlocks() {
		return referencedBlocks;
	}

	public void setReferencedBlocks(Set<Sha256Hash> referencedBlocks) {
		this.referencedBlocks = referencedBlocks;
	}

	public long getContractchainlength() {
		return contractchainlength;
	}

	public void setContractchainlength(long contractchainlength) {
		this.contractchainlength = contractchainlength;
	}

	@Override
	public String toString() {
		return "ContractExecutionResult [contracttokenid=" + contracttokenid + ", prevblockhash=" + prevblockhash
				+ ", contractchainlength=" + contractchainlength + ", referencedBlocks=" + referencedBlocks
				+ ", outputTxHash=" + outputTxHash + ", allRecords=" + allRecords + ", cancelRecords=" + cancelRecords
				+ ", remainderRecords=" + remainderRecords + ", outputTx=" + outputTx
				+ ", remainderContractEventRecord=" + remainderContractEventRecord + "]";
	}
 

}