package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ContractExecutionResult extends DataClass implements java.io.Serializable {
	List<ContractEventInfo> spentContractEvents;
	Transaction outputTx;

	public ContractExecutionResult() {

	}

	public ContractExecutionResult(List<ContractEventInfo> spentOrders, Transaction outputTx) {
		this.spentContractEvents = spentOrders;
		this.outputTx = outputTx;

	}

	
	/*
	 * This is unique for OrderMatchingResul
	 */
	public Sha256Hash getOrderMatchingResultHash() {
		return getOutputTx().getHash();
	}


    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);

            dos.write(super.toByteArray());
            
            dos.writeInt(spentContractEvents.size());
            for (ContractEventInfo c : spentContractEvents)
                dos.write(c.toByteArray());
            
            dos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }
    
    @Override
    public ContractExecutionResult parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);

        spentContractEvents = new ArrayList<>();
        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
        	spentContractEvents.add(new ContractEventInfo().parseDIS(dis));
        }
        
        return this;
    }

    public ContractExecutionResult parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);
        
        dis.close();
        bain.close();
        return this;
    }

	
	public List<ContractEventInfo> getSpentContractEvents() {
		return spentContractEvents;
	}

	public void setSpentContractEvents(List<ContractEventInfo> spentContractEvents) {
		this.spentContractEvents = spentContractEvents;
	}

	public Transaction getOutputTx() {
		return outputTx;
	}

	public void setOutputTx(Transaction outputTx) {
		this.outputTx = outputTx;
	}

}