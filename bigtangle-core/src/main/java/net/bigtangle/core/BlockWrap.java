/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.Serializable;

/**
 * Wraps a {@link Block} object with extra data that can be derived from the blockstore
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

//    public BlockWrap(byte[] blockbyte, NetworkParameters params) {
//        super();
//        this.params = params;
//        block = params.getDefaultSerializer().makeBlock(blockbyte);
//       
//    }

    
//    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
//        int length = aInputStream.readInt();
//        byte[] dataRead = new byte[length];
//        // Read the byte[] itself
//        aInputStream.read(dataRead);
//        if(params==null) params=UnitTestParams.get();
//        block = params.getDefaultSerializer().makeBlock(dataRead);
//    }
//
//    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
//
//        byte[] a = block.bitcoinSerialize();
//        aOutputStream.writeInt(a.length);
//        aOutputStream.writeObject(a);
//    }

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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return getBlock().equals(((BlockWrap)o).getBlock()) 
                && getBlockEvaluation().equals(((BlockWrap)o).getBlockEvaluation());
    }
    
    @Override
    public int hashCode() {
        return getBlock().hashCode();
    }
}
