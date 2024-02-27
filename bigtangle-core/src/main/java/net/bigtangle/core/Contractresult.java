package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Contractresult extends SpentBlock implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Sha256Hash prevblockhash;

	private byte[] contractresult;
	private long contractchainlength;

	// this is for json
	public Contractresult() {

	}

	public Contractresult(Sha256Hash hash, boolean confirmed, boolean spent, Sha256Hash prevBlockHash,
			Sha256Hash spenderblockhash, byte[] contractresult,  long contractchainLength,   long inserttime ) {
		super();
		this.setBlockHash(hash);
		this.setConfirmed(confirmed);
		this.setSpent(spent);
		this.setTime(inserttime); 
		this.prevblockhash = prevBlockHash;
		this.setSpenderBlockHash(spenderblockhash);
		this.contractresult = contractresult;
		this.contractchainlength = contractchainLength;
	}
	public static  Contractresult zeroContractresult() {
		return new Contractresult(Sha256Hash.ZERO_HASH, false, false, null, null, null, 0, 0l);
	}
	
	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);
			dos.write(super.toByteArray());
			Utils.writeNBytes(dos, prevblockhash.getBytes());
			Utils.writeNBytes(dos, contractresult ); 
			dos.writeLong(contractchainlength);
			dos.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	@Override
	public Contractresult parseDIS(DataInputStream dis) throws IOException {
		super.parseDIS(dis);

		prevblockhash = Sha256Hash.wrap(Utils.readNBytes(dis)); 
		contractresult = Utils.readNBytes(dis);
		contractchainlength = dis.readLong();

		return this;
	}

	public Contractresult parse(byte[] buf) throws IOException {
		ByteArrayInputStream bain = new ByteArrayInputStream(buf);
		DataInputStream dis = new DataInputStream(bain);
		parseDIS(dis);
		dis.close();
		bain.close();
		return this;
	}

	public Sha256Hash getPrevblockhash() {
		return prevblockhash;
	}

	public void setPrevblockhash(Sha256Hash prevblockhash) {
		this.prevblockhash = prevblockhash;
	}
 

	public byte[] getContractresult() {
		return contractresult;
	}

	public void setContractresult(byte[] contractresult) {
		this.contractresult = contractresult;
	}

	public long getContractchainlength() {
		return contractchainlength;
	}

	public void setContractchainlength(long contractchainLength) {
		this.contractchainlength = contractchainLength;
	}

	@Override
	public String toString() {
		return "Contractresult [prevblockhash=" + prevblockhash + ", contractchainlength=" + contractchainlength + "]";
	}

 

}
