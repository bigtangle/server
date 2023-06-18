package net.bigtangle.server.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.bigtangle.core.DataClass;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;

public class ContractResult extends DataClass {
	String contractid;
	Sha256Hash outputTxHash;
	List<Sha256Hash> spentContractEventRecord = new ArrayList<>();

	// not part of toArray for check
	Transaction outputTx;
	
	public ContractResult() {

	}

	public ContractResult(String contractid, List<Sha256Hash> toBeSpent, Sha256Hash outputTxHash, Transaction outputTx) {
		this.contractid = contractid;
		this.spentContractEventRecord = toBeSpent;
		this.outputTxHash = outputTxHash;
		this.outputTx=   outputTx;

	}

	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);
			dos.write(super.toByteArray());
			Utils.writeNBytesString(dos, contractid);
			Utils.writeNBytes(dos, outputTxHash.getBytes());
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
		contractid = Utils.readNBytesString(dis);
		outputTxHash   = Sha256Hash.wrap(Utils. readNBytes(dis));
		spentContractEventRecord = new ArrayList<>();
		int size = dis.readInt();
		for (int i = 0; i < size; i++) {
			spentContractEventRecord.add(
					 Sha256Hash.wrap(Utils. readNBytes(dis)));
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

	public String getContractid() {
		return contractid;
	}

	public void setContractid(String contractid) {
		this.contractid = contractid;
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

}