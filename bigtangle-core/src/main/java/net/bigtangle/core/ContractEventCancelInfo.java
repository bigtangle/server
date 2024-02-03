/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

// This object being part of a signed transaction's data legitimates it
public class ContractEventCancelInfo implements java.io.Serializable {

    private static final long serialVersionUID = 5955604810374397496L;

    private Sha256Hash blockHash;

    public ContractEventCancelInfo() {
        super();
    }

    public ContractEventCancelInfo(Sha256Hash initialBlockHash) {
        super();

        this.blockHash = initialBlockHash;
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(Sha256Hash blockHash) {
        this.blockHash = blockHash;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.write(blockHash.getBytes());
            
            dos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
    }
    
    public ContractEventCancelInfo parseDIS(DataInputStream dis) throws IOException {
        byte[] buf = new byte[Sha256Hash.LENGTH];
        dis.readFully(buf);
        blockHash = Sha256Hash.wrap(buf);
        
        return this;
    }

    public ContractEventCancelInfo parse(byte[] buf) throws IOException {
        ByteArrayInputStream bain = new ByteArrayInputStream(buf);
        DataInputStream dis = new DataInputStream(bain);

        parseDIS(dis);
        
        dis.close();
        bain.close();
        return this;
    }

    public ContractEventCancelInfo parseChecked(byte[] buf) {
        try {
            return parse(buf);
        } catch (IOException e) {
            // Cannot happen since checked before
            throw new RuntimeException(e);
        }
    }
}
