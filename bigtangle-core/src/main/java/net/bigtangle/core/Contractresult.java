package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class Contractresult extends SpentBlock implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Sha256Hash prevblockhash;

	private byte[] contractExecutionResult;
	private long contractchainlength;
	private String contracttokenid;
	private long milestone;
	// this is for json
	public Contractresult() {

	}

	public Contractresult(Sha256Hash hash, boolean confirmed, boolean spent, Sha256Hash prevBlockHash,
			Sha256Hash spenderblockhash, byte[] contractExecutionResult,  long contractchainLength,String contracttokenid,long milestone,   long inserttime ) {
		super();
		this.setBlockHash(hash);
		this.setConfirmed(confirmed);
		this.setSpent(spent);
		this.setTime(inserttime); 
		this.prevblockhash = prevBlockHash;
		this.setSpenderBlockHash(spenderblockhash);
		this.contractExecutionResult = contractExecutionResult;
		this.contractchainlength = contractchainLength;
		this.contracttokenid =contracttokenid;
		this.milestone =milestone;
	}
	public static  Contractresult zeroContractresult() {
		return new Contractresult(Sha256Hash.ZERO_HASH, false, false, null, null, null, 0,null, -1,0l);
	}
	
	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);
			dos.write(super.toByteArray());
			Utils.writeNBytes(dos, prevblockhash.getBytes());
			Utils.writeNBytes(dos, contractExecutionResult ); 
			dos.writeLong(contractchainlength);
			Utils.writeNBytesString(dos, contracttokenid ); 
			dos.writeLong(milestone);
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
		contractExecutionResult = Utils.readNBytes(dis);
		contractchainlength = dis.readLong();
		contracttokenid =  Utils.readNBytesString(dis);
		milestone = dis.readLong();
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
 
 

	public byte[] getContractExecutionResult() {
		return contractExecutionResult;
	}

	public void setContractExecutionResult(byte[] contractExecutionResult) {
		this.contractExecutionResult = contractExecutionResult;
	}

	public long getContractchainlength() {
		return contractchainlength;
	}

	public void setContractchainlength(long contractchainLength) {
		this.contractchainlength = contractchainLength;
	}

	public String getContracttokenid() {
		return contracttokenid;
	}

	public void setContracttokenid(String contracttokenid) {
		this.contracttokenid = contracttokenid;
	}

	public long getMilestone() {
		return milestone;
	}

	public void setMilestone(long milestone) {
		this.milestone = milestone;
	}

	@Override
	public String toString() {
		return "Contractresult [prevblockhash=" + prevblockhash 
				+ ", contractchainlength=" + contractchainlength + ", contracttokenid=" + contracttokenid
				+ ", milestone=" + milestone + "]";
	}

	 
 

}
