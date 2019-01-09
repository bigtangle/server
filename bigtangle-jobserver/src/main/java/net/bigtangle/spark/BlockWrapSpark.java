/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.spark;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashSet;

import net.bigtangle.core.Block;
import net.bigtangle.core.BlockEvaluation;
import net.bigtangle.core.BlockWrap;
import net.bigtangle.core.ConflictCandidate;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.params.MainNetParams;
import scala.collection.mutable.ListMap;

/**
 * Wraps BlockWraps for Spark serialization and adds Spark computation result members.
 *
 */
public class BlockWrapSpark extends BlockWrap implements Serializable {
    private static final long serialVersionUID = 660084694396085961L;

    /** transient members for temporary Spark calculations */
    private HashSet<Sha256Hash> weightHashes;
    private HashSet<Sha256Hash> receivedWeightHashes;
    private HashSet<ConflictCandidate> approvedNonMilestoneConflicts;
    private HashSet<ConflictCandidate> receivedConflictPoints;
    private boolean milestoneConsistent;
    private double incomingTransitionWeightSum;
    private ListMap<Sha256Hash, scala.Double> incomingTransitionWeights;
    private double currentTransitionRealization;
    
    //@vertex: conflictpoint sets + milestone validity, outgoing weight unnormalized, transient dicerolls
    //@edges: applicable diceroll interval for sendmsg
    
    
    // Empty constructor for serialization in Spark
    @SuppressWarnings("unused")
    private BlockWrapSpark() {
        super();
    }

    // Used in Spark
    public BlockWrapSpark(Block block, BlockEvaluation blockEvaluation, NetworkParameters params) {
        super(block, blockEvaluation, params);
        weightHashes = new HashSet<>();
        receivedWeightHashes = new HashSet<>();
        approvedNonMilestoneConflicts = new HashSet<>();
        receivedConflictPoints = new HashSet<>();
    }

    // Used in Spark
    public BlockWrapSpark(byte[] blockbyte, BlockEvaluation blockEvaluation, NetworkParameters params) {
        super(params.getDefaultSerializer().makeBlock(blockbyte), blockEvaluation, params);
        weightHashes = new HashSet<>();
        receivedWeightHashes = new HashSet<>();
        approvedNonMilestoneConflicts = new HashSet<>();
        receivedConflictPoints = new HashSet<>();
    }
    
    public BlockWrapSpark(BlockWrapSpark other) {
        super(other.getBlock(), new BlockEvaluation(other.getBlockEvaluation()), other.params);
        weightHashes = new HashSet<>(other.getWeightHashes());
        receivedWeightHashes = new HashSet<>(other.getReceivedWeightHashes());
        approvedNonMilestoneConflicts = new HashSet<>(other.getApprovedNonMilestoneConflicts());
    }

    // Used in Spark
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        int length = aInputStream.readInt();
        byte[] dataRead = new byte[length];
        params = MainNetParams.get();
        aInputStream.readFully(dataRead, 0, length);
        block = params.getDefaultSerializer().makeBlock(dataRead);
        blockEvaluation = (BlockEvaluation) aInputStream.readObject();
        weightHashes = (HashSet<Sha256Hash>) aInputStream.readObject();
        receivedWeightHashes = (HashSet<Sha256Hash>) aInputStream.readObject();
        approvedNonMilestoneConflicts = (HashSet<ConflictCandidate>) aInputStream.readObject();
        receivedConflictPoints = (HashSet<ConflictCandidate>) aInputStream.readObject();
        milestoneConsistent = aInputStream.readBoolean();
        incomingTransitionWeightSum = aInputStream.readDouble();
        incomingTransitionWeights = (ListMap<Sha256Hash, scala.Double>) aInputStream.readObject();
        currentTransitionRealization = aInputStream.readDouble();
    }

    // Used in Spark
    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
        byte[] a = block.bitcoinSerialize();
        aOutputStream.writeInt(a.length);
        aOutputStream.write(a);
        aOutputStream.writeObject(blockEvaluation);
        aOutputStream.writeObject(weightHashes);
        aOutputStream.writeObject(receivedWeightHashes);
        aOutputStream.writeObject(approvedNonMilestoneConflicts);
        aOutputStream.writeObject(receivedConflictPoints);
        aOutputStream.writeBoolean(milestoneConsistent);
        aOutputStream.writeDouble(incomingTransitionWeightSum);
        aOutputStream.writeObject(incomingTransitionWeights);
        aOutputStream.writeDouble(currentTransitionRealization);
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

    public double getCurrentTransitionRealization() {
        return currentTransitionRealization;
    }

    public void setCurrentTransitionRealization(double currentTransitionRealization) {
        this.currentTransitionRealization = currentTransitionRealization;
    }

    public HashSet<Sha256Hash> getReceivedWeightHashes() {
        return receivedWeightHashes;
    }

    public void setReceivedWeightHashes(HashSet<Sha256Hash> receivedHashes) {
        this.receivedWeightHashes = receivedHashes;
    }

    public HashSet<ConflictCandidate> getReceivedConflictPoints() {
        return receivedConflictPoints;
    }

    public void setReceivedConflictPoints(HashSet<ConflictCandidate> receivedConflictPoints) {
        this.receivedConflictPoints = receivedConflictPoints;
    }

    public HashSet<ConflictCandidate> getApprovedNonMilestoneConflicts() {
        return approvedNonMilestoneConflicts;
    }

    public void setApprovedNonMilestoneConflicts(HashSet<ConflictCandidate> approvedNonMilestoneConflicts) {
        this.approvedNonMilestoneConflicts = approvedNonMilestoneConflicts;
    }

    public boolean isMilestoneConsistent() {
        return milestoneConsistent;
    }

    public void setMilestoneConsistent(boolean milestoneConsistent) {
        this.milestoneConsistent = milestoneConsistent;
    }

    public double getIncomingTransitionWeightSum() {
        return incomingTransitionWeightSum;
    }

    public void setIncomingTransitionWeightSum(double currentIncomingWeightSum) {
        this.incomingTransitionWeightSum = currentIncomingWeightSum;
    }

    public ListMap<Sha256Hash, scala.Double> getIncomingTransitionWeights() {
        return incomingTransitionWeights;
    }

    public void setIncomingTransitionWeights(ListMap<Sha256Hash, scala.Double> incomingTransitionWeights) {
        this.incomingTransitionWeights = incomingTransitionWeights;
    }
}