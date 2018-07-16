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
 * blockstore
 */
public class BlockWrap implements Serializable {

    private static final long serialVersionUID = 1L;

    private Block block;
    private BlockEvaluation blockEvaluation;
    private NetworkParameters params;

    public BlockWrap(Block block, BlockEvaluation blockEvaluation, NetworkParameters params) {
        super();
        this.block = block;
        this.blockEvaluation = blockEvaluation;
        this.params = params;
    }

    //Used in Spark 
    public BlockWrap(byte[] blockbyte, NetworkParameters params) {
        super();
        this.params = params;
        block = params.getDefaultSerializer().makeBlock(blockbyte);

    }
    
    //Used in Spark 
    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        int length = aInputStream.readInt();
        byte[] dataRead = new byte[length];
        // Read the byte[] itself
        aInputStream.read(dataRead);
        if (params == null)
            params = UnitTestParams.get();
        block = params.getDefaultSerializer().makeBlock(dataRead);
    }
    
    //Used in Spark 
    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {

        byte[] a = block.bitcoinSerialize();
        aOutputStream.writeInt(a.length);
        aOutputStream.writeObject(a);
    }

    /**
     * @return
     */
    public Block getBlock() {
        return block;
    }

    public BlockEvaluation getBlockEvaluation() {
        return blockEvaluation;
    }

    public NetworkParameters getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return getBlock().equals(((BlockWrap) o).getBlock())
                && getBlockEvaluation().equals(((BlockWrap) o).getBlockEvaluation());
    }

    @Override
    public int hashCode() {
        return getBlock().hashCode();
    }

	public Sha256Hash getBlockHash() {
		return block.getHash();
	}
}
