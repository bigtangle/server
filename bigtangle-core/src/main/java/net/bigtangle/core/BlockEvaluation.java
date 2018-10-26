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

    private static final long serialVersionUID = 8388463657969339286L;

    // Hash of corresponding block
    private Sha256Hash blockHash;

    // Percentage of MCMC selected tips approving this block
    private long rating;

    // Longest path to tip
    private long depth;

    // Count of indirect approver blocks
    private long cumulativeWeight;

    // Longest path to genesis block
    private long height;

    // If true, this block is considered locally confirmed, e.g. sufficient rating etc.
    private boolean milestone;

    // Timestamp for entry into milestone as true, reset if flip to false
    private long milestoneLastUpdateTime;

    // Longest path length to any indirect milestone approver
    private long milestoneDepth;
    
    // Timestamp for entry into evaluations/reception time
    private long insertTime;
    
    // If false, this block has no influence on MCMC
    private boolean maintained;

    private BlockEvaluation() {
    }

    // deep copy constructor
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

    public static BlockEvaluation buildInitial(Block block) {
        long currentTimeMillis = System.currentTimeMillis();
        return BlockEvaluation.build(block.getHash(), 0, 0, 1, 0, false, currentTimeMillis, 0, currentTimeMillis, true);
    }

    /**
     * Returns an invalid evaluation that is considered confirmed with max rating for the prototype Block
     * 
     * @param block prototype block
     * @return an evaluation that is considered confirmed with max rating for the prototype Block
     */
    public static BlockEvaluation getPrototypeEvaluation(Block block) {
        long currentTimeMillis = System.currentTimeMillis();
        return BlockEvaluation.build(block.getHash(), 100, Long.MAX_VALUE, Long.MAX_VALUE, 1, true, currentTimeMillis, Long.MAX_VALUE, currentTimeMillis, false);
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

    public String getBlockHexStr() {
        return Utils.HEX.encode(this.blockHash.getBytes());
    }

    public void setBlockHexStr(String blockHexStr) {
        this.blockHash = Sha256Hash.wrap(blockHexStr);
    }

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
