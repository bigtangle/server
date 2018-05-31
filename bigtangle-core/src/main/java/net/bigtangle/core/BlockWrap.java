/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import net.bigtangle.params.UnitTestParams;

/**
 * Wraps a {@link Block} object with extra data that can be derived from the 
 * or disk.
 */
public class BlockWrap implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private Block block;

      NetworkParameters params;

    // Setters and Getters

    public BlockWrap(Block block, NetworkParameters params) {
        super();
        this.block = block;
        this.params = params;
    }

    public BlockWrap(byte[] blockbyte, NetworkParameters params) {
        super();
        this.params = params;
        block = params.getDefaultSerializer().makeBlock(blockbyte);
       
    }

    
    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        int length = aInputStream.readInt();
        byte[] dataRead = new byte[length];
        // Read the byte[] itself
        aInputStream.read(dataRead);
        if(params==null) params=UnitTestParams.get();
        block = params.getDefaultSerializer().makeBlock(dataRead);
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {

        byte[] a = block.bitcoinSerialize();
        aOutputStream.writeInt(a.length);
        aOutputStream.writeObject(a);
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public NetworkParameters getParams() {
        return params;
    }

    public void setParams(NetworkParameters params) {
        this.params = params;
    }

}
