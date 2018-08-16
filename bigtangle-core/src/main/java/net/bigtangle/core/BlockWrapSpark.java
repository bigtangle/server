/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;

import net.bigtangle.params.UnitTestParams;

public class BlockWrapSpark extends BlockWrap implements Serializable {
    private static final long serialVersionUID = 660084694396085961L;

    /** unpersisted members for Spark calculations, weightHashes includes own hash */
    private HashSet<Sha256Hash> weightHashes;
    private HashSet<ConflictPoint> approvedNonMilestoneConflicts;
    
    
    
    //@vertex: conflictpoint sets + milestone validity, outgoing weight unnormalized, transient dicerolls
    //@edges: applicable diceroll interval for sendmsg
    
    
    // Used in Spark
    @SuppressWarnings("unused")
    private BlockWrapSpark() {
        super();
        weightHashes = new HashSet<>();
    }

    // Used in Spark
    public BlockWrapSpark(Block block, BlockEvaluation blockEvaluation, NetworkParameters params) {
        super(block, blockEvaluation, params);
    }

    // Used in Spark
    public BlockWrapSpark(byte[] blockbyte, BlockEvaluation blockEvaluation, NetworkParameters params) {
        super(params.getDefaultSerializer().makeBlock(blockbyte), blockEvaluation, params);
    }

    // Used in Spark
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        int length = aInputStream.readInt();
        byte[] dataRead = new byte[length];
        aInputStream.readFully(dataRead, 0, length);

        // TODO remember the params
        if (params == null)
            params = UnitTestParams.get();

        block = params.getDefaultSerializer().makeBlock(dataRead);
        blockEvaluation = (BlockEvaluation) aInputStream.readObject();
        weightHashes = (HashSet<Sha256Hash>) aInputStream.readObject();
    }

    // Used in Spark
    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
        byte[] a = block.bitcoinSerialize();
        aOutputStream.writeInt(a.length);
        aOutputStream.write(a);
        aOutputStream.writeObject(blockEvaluation);
        aOutputStream.writeObject(weightHashes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return getBlockHash().equals(((BlockWrapSpark) o).getBlockHash())
                && getBlockEvaluation().getRating() == ((BlockWrapSpark) o).getBlockEvaluation().getRating()
                && getBlockEvaluation().getDepth() == ((BlockWrapSpark) o).getBlockEvaluation().getDepth()
                && getBlockEvaluation().getCumulativeWeight() == ((BlockWrapSpark) o).getBlockEvaluation()
                        .getCumulativeWeight()
                && getBlockEvaluation().getHeight() == ((BlockWrapSpark) o).getBlockEvaluation().getHeight()
                && getBlockEvaluation().isMilestone() == ((BlockWrapSpark) o).getBlockEvaluation().isMilestone()
                && getBlockEvaluation().getMilestoneLastUpdateTime() == ((BlockWrapSpark) o)
                        .getBlockEvaluation().getMilestoneLastUpdateTime()
                && getBlockEvaluation().getMilestoneDepth() == ((BlockWrapSpark) o).getBlockEvaluation().getMilestoneDepth()
                && getBlockEvaluation().getInsertTime() == ((BlockWrapSpark) o).getBlockEvaluation().getInsertTime()
                && getBlockEvaluation().isMaintained() == ((BlockWrapSpark) o).getBlockEvaluation().isMaintained()
                && getWeightHashes() == ((BlockWrapSpark) o).getWeightHashes();
    }

    @Override
    public int hashCode() {
        return getBlockHash().hashCode();
    }

    public HashSet<Sha256Hash> getWeightHashes() {
        return weightHashes;
    }

    public void setWeightHashes(HashSet<Sha256Hash> weightHashes) {
        this.weightHashes = weightHashes;
    }
}