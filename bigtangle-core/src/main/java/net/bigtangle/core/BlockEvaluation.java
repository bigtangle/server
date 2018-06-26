/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.core;

import java.io.Serializable;

/*
 * Evaluation of block, variable in time
 */
public class BlockEvaluation implements Serializable{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;


    public String getBlockHexStr() {
        return Utils.HEX.encode(this.blockhash.getBytes());
    }

	private BlockEvaluation() { 
	}

	public static BlockEvaluation buildInitial(Block block) {
		long currentTimeMillis = System.currentTimeMillis();
        return BlockEvaluation.build(block.getHash(), 
				0, 0, 1, true, 0, false, currentTimeMillis, 0, currentTimeMillis, true, false);
	}

	public static BlockEvaluation build(Sha256Hash blockhash, long rating, long depth, long cumulativeWeight, 
	        boolean solid, long height, boolean milestone, long milestoneLastUpdateTime,
	        long milestoneDepth, long insertTime, boolean maintained, boolean validityAssessment) {
		BlockEvaluation blockEvaluation = new BlockEvaluation();
		blockEvaluation.setBlockhash(blockhash);
		blockEvaluation.setRating(rating);
		blockEvaluation.setDepth(depth);
		blockEvaluation.setCumulativeWeight(cumulativeWeight);
		blockEvaluation.setSolid(solid);
		blockEvaluation.setHeight(height);
		blockEvaluation.setMilestone(milestone);
		blockEvaluation.setMilestoneLastUpdateTime(milestoneLastUpdateTime);	
        blockEvaluation.setMilestoneDepth(milestoneDepth);   
        blockEvaluation.setInsertTime(insertTime);    
        blockEvaluation.setMaintained(maintained);    
        blockEvaluation.setRewardValid(validityAssessment);    
		return blockEvaluation;
	}

	// hash of corresponding block
	private Sha256Hash blockhash;
	
	// percentage of MCMC selected tips approving this block
	private long rating;
	
	// longest path to tip
	private long depth;
	
	// count of indirect approver blocks
	private long cumulativeWeight;

	// all approved blocks exist and are solid
	private boolean solid;
	
	// longest path to genesis block
	private long height;

	// rating >= 75 && depth > MINDEPTH && no conflict set to true
	// if set to true for older than 7 days, remove it from this table
	private boolean milestone;

	// Timestamp for entry into milestone as true, reset if flip to false
	private long milestoneLastUpdateTime;
	
	//NEW FIELDS
    // Longest path length to any indirect milestone approver
    private long milestoneDepth;
    // Timestamp for entry into evaluations/reception time
    private long insertTime;
    // if set to false, this evaluation is not maintained anymore and can be pruned
    private boolean maintained;
    // only relevant for mining reward blocks, true if local assessment deems mining reward block valid
    private boolean rewardValid;
	

	public Sha256Hash getBlockhash() {
		return blockhash;
	}

	public void setBlockhash(Sha256Hash blockhash) {
		this.blockhash = blockhash;
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

	public boolean isSolid() {
		return solid;
	}

	public void setSolid(boolean solid) {
		this.solid = solid;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return getBlockhash().equals(((BlockEvaluation)o).getBlockhash());
    }

    @Override
    public int hashCode() {
        return getBlockhash().hashCode();
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

    public boolean isRewardValid() {
        return rewardValid;
    }

    public void setRewardValid(boolean rewardValidityAssessment) {
        this.rewardValid = rewardValidityAssessment;
    }
}
