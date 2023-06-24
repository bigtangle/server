package net.bigtangle.server.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.SpentBlock;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;

public class ContractResult extends SpentBlock {
	String contracttokenid;
	Sha256Hash outputTxHash;
	List<Sha256Hash> spentContractEventRecord = new ArrayList<>();
	Sha256Hash prevblockhash;

	// not persistent not part of toArray for check
	Transaction outputTx;

	public ContractResult() {

	}

	public ContractResult(Sha256Hash blockhash, String contractid, List<Sha256Hash> toBeSpent, Sha256Hash outputTxHash,
			Transaction outputTx, Sha256Hash prevblockhash, long inserttime) {
		this.setBlockHash(blockhash);
		this.contracttokenid = contractid;
		this.spentContractEventRecord = toBeSpent;
		this.outputTxHash = outputTxHash;
		this.outputTx = outputTx;
		this.prevblockhash = prevblockhash;
		this.setTime(inserttime);

	}

	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);
			dos.write(super.toByteArray());
			Utils.writeNBytesString(dos, contracttokenid);
			Utils.writeNBytes(dos, outputTxHash.getBytes());
			Utils.writeNBytes(dos, prevblockhash.getBytes());
			Utils.writeNBytes(dos, getBlockHash().getBytes());
			dos.writeInt(spentContractEventRecord.size());

			for (Sha256Hash c : spentContractEventRecord)
				Utils.writeNBytes(dos, c.getBytes());

			dos.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	@Override
	public ContractResult parseDIS(DataInputStream dis) throws IOException {
		super.parseDIS(dis);
		contracttokenid = Utils.readNBytesString(dis);
		outputTxHash = Sha256Hash.wrap(Utils.readNBytes(dis));
		prevblockhash = Sha256Hash.wrap(Utils.readNBytes(dis));
		setBlockHash(Sha256Hash.wrap(Utils.readNBytes(dis)));
		spentContractEventRecord = new ArrayList<>();
		int size = dis.readInt();
		for (int i = 0; i < size; i++) {
			spentContractEventRecord.add(Sha256Hash.wrap(Utils.readNBytes(dis)));
		}

		return this;
	}

	public ContractResult parse(byte[] buf) throws IOException {
		ByteArrayInputStream bain = new ByteArrayInputStream(buf);
		DataInputStream dis = new DataInputStream(bain);
		parseDIS(dis);
		dis.close();
		bain.close();
		return this;
	}

	public List<Sha256Hash> getSpentContractEventRecord() {
		return spentContractEventRecord;
	}

	public void setSpentContractEventRecord(List<Sha256Hash> spentContractEventRecord) {
		this.spentContractEventRecord = spentContractEventRecord;
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

	@Override
	public String toString() {
		return "ContractResult [contracttokenid=" + contracttokenid + ", outputTxHash=" + outputTxHash
				+ ", spentContractEventRecord=" + spentContractEventRecord + ", prevblockhash=" + prevblockhash
				+ ", outputTx=" + outputTx + "]";
	}

}