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

	private byte[] contractresult;
	private long contractchainlength;
	private String contracttokenid;
	// this is for json
	public Contractresult() {

	}

	public Contractresult(Sha256Hash hash, boolean confirmed, boolean spent, Sha256Hash prevBlockHash,
			Sha256Hash spenderblockhash, byte[] contractresult,  long contractchainLength,String contracttokenid,   long inserttime ) {
		super();
		this.setBlockHash(hash);
		this.setConfirmed(confirmed);
		this.setSpent(spent);
		this.setTime(inserttime); 
		this.prevblockhash = prevBlockHash;
		this.setSpenderBlockHash(spenderblockhash);
		this.contractresult = contractresult;
		this.contractchainlength = contractchainLength;
		this.contracttokenid =contracttokenid;
	}
	public static  Contractresult zeroContractresult() {
		return new Contractresult(Sha256Hash.ZERO_HASH, false, false, null, null, null, 0,null, 0l);
	}
	
	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);
			dos.write(super.toByteArray());
			Utils.writeNBytes(dos, prevblockhash.getBytes());
			Utils.writeNBytes(dos, contractresult ); 
			dos.writeLong(contractchainlength);
			Utils.writeNBytesString(dos, contracttokenid ); 
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
		contracttokenid =  Utils.readNBytesString(dis);
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

	public String getContracttokenid() {
		return contracttokenid;
	}

	public void setContracttokenid(String contracttokenid) {
		this.contracttokenid = contracttokenid;
	}

	@Override
	public String toString() {
		return "Contractresult [prevblockhash=" + prevblockhash  
				+ ", contractchainlength=" + contractchainlength + ", contracttokenid=" + contracttokenid + "]";
	}

	 
 

}
