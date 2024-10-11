package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Orderresult extends SpentBlock implements java.io.Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Sha256Hash prevblockhash;

	private byte[] orderExecutionResult;
	private long orderchainLength;

	// this is for json
	public Orderresult() {

	}

	public Orderresult(Sha256Hash hash, boolean confirmed, boolean spent, Sha256Hash prevBlockHash,
			Sha256Hash spenderblockhash, byte[] orderExecutionResult,  long orderchainLength,   long inserttime ) {
		super();
		this.setBlockHash(hash);
		this.setConfirmed(confirmed);
		this.setSpent(spent);
		this.setTime(inserttime); 
		this.prevblockhash = prevBlockHash;
		this.setSpenderBlockHash(spenderblockhash);
		this.orderExecutionResult = orderExecutionResult;
		this.orderchainLength = orderchainLength;
	}

	public static Orderresult zeroOrderresult( ) {
	 return new Orderresult(Sha256Hash.ZERO_HASH, false, false, null, null, null, 0l, 0l);
	}
	
	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);
			dos.write(super.toByteArray());
			Utils.writeNBytes(dos, prevblockhash.getBytes());
			Utils.writeNBytes(dos, orderExecutionResult ); 
			dos.writeLong(orderchainLength);
			dos.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return baos.toByteArray();
	}

	@Override
	public Orderresult parseDIS(DataInputStream dis) throws IOException {
		super.parseDIS(dis);

		prevblockhash = Sha256Hash.wrap(Utils.readNBytes(dis));

		orderExecutionResult = Utils.readNBytes(dis);
		orderchainLength = dis.readLong();

		return this;
	}

	public Orderresult parse(byte[] buf) throws IOException {
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
 

	 

	public byte[] getOrderExecutionResult() {
		return orderExecutionResult;
	}

	public void setOrderExecutionResult(byte[] orderExecutionResult) {
		this.orderExecutionResult = orderExecutionResult;
	}

	public long getOrderchainLength() {
		return orderchainLength;
	}

	public void setOrderchainLength(long orderchainLength) {
		this.orderchainLength = orderchainLength;
	}
  

}
