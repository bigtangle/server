package net.bigtangle.server.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bigtangle.core.OrderRecord;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.SpentBlock;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.Utils;
import net.bigtangle.core.ordermatch.TradePair;
import net.bigtangle.core.ordermatch.OrderBookEvents.Event;

/*
 * OrderExecutionResult provide the results from the execution based on prev results.
 * It must be check on every node and should return the same result.
 * The data is saved in table OrderExecutionResult mainly as byte.
 */
public class OrderExecutionResult extends SpentBlock {

	// reference the previous ContractResult block, it forms a chain
	Sha256Hash prevblockhash;
	// referenced new order blocks
	Set<Sha256Hash> referencedBlocks = new HashSet<>();;

	// coinbase outputTxHash
	Sha256Hash outputTxHash;
	// all records used in this calculation
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

	Collection<OrderRecord> remainderOrderRecord;
	Set<OrderRecord> spentOrderRecord;
	Map<TradePair, List<Event>> tokenId2Events;
	
	public OrderExecutionResult() {

	}

	public OrderExecutionResult(Sha256Hash blockhash, Set<Sha256Hash> toBeSpent, Sha256Hash outputTxHash,
			Transaction outputTx, Sha256Hash prevblockhash, Set<Sha256Hash> cancelRecords,
			Set<Sha256Hash> remainderRecords, long inserttime, Collection<OrderRecord> remainderOrderRecord,
			Set<OrderRecord> spentOrderRecord,
			Set<Sha256Hash> referencedOrderBlocks, Map<TradePair, List<Event>> tokenId2Events ) {
		this.setBlockHash(blockhash);

		this.prevblockhash = prevblockhash;
		this.outputTxHash = outputTxHash;
		this.outputTx = outputTx;
		this.allRecords = toBeSpent;
		this.cancelRecords = cancelRecords;
		this.remainderRecords = remainderRecords;
		this.setTime(inserttime);

		this.remainderOrderRecord = remainderOrderRecord;
		this.referencedBlocks = referencedOrderBlocks;
		this.	spentOrderRecord= spentOrderRecord;
		this.tokenId2Events=tokenId2Events;
	}

	public byte[] toByteArray() {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			DataOutputStream dos = new DataOutputStream(baos);
			dos.write(super.toByteArray());

			Utils.writeNBytes(dos, outputTxHash.getBytes());
			Utils.writeNBytes(dos, prevblockhash.getBytes());

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
	public OrderExecutionResult parseDIS(DataInputStream dis) throws IOException {
		super.parseDIS(dis);

		outputTxHash = Sha256Hash.wrap(Utils.readNBytes(dis));
		prevblockhash = Sha256Hash.wrap(Utils.readNBytes(dis));
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

	public OrderExecutionResult parseChecked(byte[] buf) {
		try {
			return parse(buf);
		} catch (IOException e) {
			// Cannot happen since checked before
			throw new RuntimeException(e);
		}
	}

	public OrderExecutionResult parse(byte[] buf) throws IOException {
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

	public Set<Sha256Hash> getReferencedBlocks() {
		return referencedBlocks;
	}

	public void setReferencedBlocks(Set<Sha256Hash> referencedBlocks) {
		this.referencedBlocks = referencedBlocks;
	}

	public Collection<OrderRecord> getRemainderOrderRecord() {
		return remainderOrderRecord;
	}

	public void setRemainderOrderRecord(Collection<OrderRecord> remainderOrderRecord) {
		this.remainderOrderRecord = remainderOrderRecord;
	}

	public Set<OrderRecord> getSpentOrderRecord() {
		return spentOrderRecord;
	}

	public void setSpentOrderRecord(Set<OrderRecord> spentOrderRecord) {
		this.spentOrderRecord = spentOrderRecord;
	}

	public Map<TradePair, List<Event>> getTokenId2Events() {
		return tokenId2Events;
	}

	public void setTokenId2Events(Map<TradePair, List<Event>> tokenId2Events) {
		this.tokenId2Events = tokenId2Events;
	}

}