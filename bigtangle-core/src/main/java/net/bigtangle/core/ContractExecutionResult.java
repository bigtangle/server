package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContractExecutionResult extends DataClass implements java.io.Serializable {
    /**
     * Contract execution: input inKeyValueList and used with
     * List<ContractEventInfo> ContractEventInfo out: outKeyValueList and
     * Transaction
     */
    private static final long serialVersionUID = 1L;
    KeyValueList inKeyValueList;
    List<ContractEventInfo> spentContractEvents;
    byte[] outputTx;
    KeyValueList outKeyValueList;

    public ContractExecutionResult() {

    }

    public ContractExecutionResult(List<ContractEventInfo> spentOrders, byte[] outputTx) {
        this.spentContractEvents = spentOrders;
        this.outputTx = outputTx;

    }

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);

            dos.write(super.toByteArray());
            dos.write(inKeyValueList.toByteArray());
            dos.writeInt(spentContractEvents.size());
            for (ContractEventInfo c : spentContractEvents)
                dos.write(c.toByteArray());
            dos.write(outKeyValueList.toByteArray());
            // byte[] tx = outputTx.bitcoinSerialize();
            dos.writeInt(outputTx.length);
            dos.write(outputTx);
            dos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    @Override
    public ContractExecutionResult parseDIS(DataInputStream dis) throws IOException {
        super.parseDIS(dis);
        inKeyValueList = new KeyValueList().parseDIS(dis);
        spentContractEvents = new ArrayList<>();
        int size = dis.readInt();
        for (int i = 0; i < size; i++) {
            spentContractEvents.add(new ContractEventInfo().parseDIS(dis));
        }
        outKeyValueList = new KeyValueList().parseDIS(dis);
        outKeyValueList = new KeyValueList().parseDIS(dis);

        outputTx = new byte[dis.readInt()];
        dis.readFully(outputTx);
        // transaction = (Transaction)
        // this.wallet().getNetworkParameters().getDefaultSerializer()
        // .makeTransaction(data);
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

    public KeyValueList getInKeyValueList() {
        return inKeyValueList;
    }

    public void setInKeyValueList(KeyValueList inKeyValueList) {
        this.inKeyValueList = inKeyValueList;
    }

    public byte[] getOutputTx() {
        return outputTx;
    }

    public void setOutputTx(byte[] outputTx) {
        this.outputTx = outputTx;
    }

    public KeyValueList getOutKeyValueList() {
        return outKeyValueList;
    }

    public void setOutKeyValueList(KeyValueList outKeyValueList) {
        this.outKeyValueList = outKeyValueList;
    }

}