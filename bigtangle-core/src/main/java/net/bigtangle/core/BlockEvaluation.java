/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.io.Serializable;

/*
 * Evaluation of block, variable in time
 */
public class BlockEvaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    public String getBlockHexStr() {
        return Utils.HEX.encode(this.blockHash.getBytes());
    }

    public void setBlockHexStr(String blockHexStr) {
        this.blockHash = Sha256Hash.wrap(blockHexStr);
    }

    private BlockEvaluation() {
    }

    public static BlockEvaluation buildInitial(Block block) {
        long currentTimeMillis = System.currentTimeMillis();
        return BlockEvaluation.build(block.getHash(), 0, 0, 1, 0, false, currentTimeMillis, 0, currentTimeMillis, true);
    }

    public static BlockEvaluation build(Sha256Hash blockhash, long rating, long depth, long cumulativeWeight,
            long height, boolean milestone, long milestoneLastUpdateTime, long milestoneDepth, long insertTime,
            boolean maintained) {
        BlockEvaluation blockEvaluation = new BlockEvaluation();
        blockEvaluation.setBlockHash(blockhash);
        blockEvaluation.setRating(rating);
        blockEvaluation.setDepth(depth);
        blockEvaluation.setCumulativeWeight(cumulativeWeight);

        blockEvaluation.setHeight(height);
        blockEvaluation.setMilestone(milestone);
        blockEvaluation.setMilestoneLastUpdateTime(milestoneLastUpdateTime);
        blockEvaluation.setMilestoneDepth(milestoneDepth);
        blockEvaluation.setInsertTime(insertTime);
        blockEvaluation.setMaintained(maintained);

        return blockEvaluation;
    }

    public BlockEvaluation(BlockEvaluation other) {
        setBlockHash(other.blockHash);
        setRating(other.rating);
        setDepth(other.depth);
        setCumulativeWeight(other.cumulativeWeight);
        setHeight(other.height);
        setMilestone(other.milestone);
        setMilestoneLastUpdateTime(other.milestoneLastUpdateTime);
        setMilestoneDepth(other.milestoneDepth);
        setInsertTime(other.insertTime);
        setMaintained(other.maintained);
    }

    // hash of corresponding block
    private Sha256Hash blockHash;

    // percentage of MCMC selected tips approving this block
    private long rating;

    // longest path to tip
    private long depth;

    // count of indirect approver blocks
    private long cumulativeWeight;

    // longest path to genesis block
    private long height;

    // rating >= 75 && depth > MINDEPTH && no conflict set to true
    // if set to true for older than 7 days, remove it from this table
    private boolean milestone;

    // Timestamp for entry into milestone as true, reset if flip to false
    private long milestoneLastUpdateTime;

    // NEW FIELDS
    // Longest path length to any indirect milestone approver
    private long milestoneDepth;
    // Timestamp for entry into evaluations/reception time
    private long insertTime;
    // if set to false, this evaluation is not maintained anymore and can be
    // pruned
    private boolean maintained;
    // only relevant for mining reward blocks, true if local assessment deems

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(Sha256Hash blockHash) {
        this.blockHash = blockHash;
    }

    public long getRating() {
        return rating;
    }

    public void setRating(long rating) {
        this.rating = rating;
    }

    public long getDepth() {
        return depth;
    }

    public void setDepth(long depth) {
        this.depth = depth;
    }

    public long getCumulativeWeight() {
        return cumulativeWeight;
    }

    public void setCumulativeWeight(long cumulativeWeight) {
        this.cumulativeWeight = cumulativeWeight;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public boolean isMilestone() {
        return milestone;
    }

    public void setMilestone(boolean milestone) {
        this.milestone = milestone;
    }

    public long getMilestoneLastUpdateTime() {
        return milestoneLastUpdateTime;
    }

    public void setMilestoneLastUpdateTime(long milestoneLastUpdateTime) {
        this.milestoneLastUpdateTime = milestoneLastUpdateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
//        return false;
        return getBlockHash().equals(((BlockEvaluation) o).getBlockHash()) && rating == ((BlockEvaluation) o).rating
                && depth == ((BlockEvaluation) o).depth && cumulativeWeight == ((BlockEvaluation) o).cumulativeWeight
                && height == ((BlockEvaluation) o).height && milestone == ((BlockEvaluation) o).milestone
                && milestoneLastUpdateTime == ((BlockEvaluation) o).milestoneLastUpdateTime && milestoneDepth == ((BlockEvaluation) o).milestoneDepth
                && insertTime == ((BlockEvaluation) o).insertTime && maintained == ((BlockEvaluation) o).maintained;
    }

    @Override
    public int hashCode() {
        return getBlockHash().hashCode();
    }

    public long getMilestoneDepth() {
        return milestoneDepth;
    }

    public void setMilestoneDepth(long milestoneDepth) {
        this.milestoneDepth = milestoneDepth;
    }

    public long getInsertTime() {
        return insertTime;
    }

    public void setInsertTime(long insertTime) {
        this.insertTime = insertTime;
    }

    public boolean isMaintained() {
        return maintained;
    }

    public void setMaintained(boolean maintained) {
        this.maintained = maintained;
    }

}
