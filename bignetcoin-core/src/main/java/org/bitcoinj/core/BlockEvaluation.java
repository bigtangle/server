package org.bitcoinj.core;

/*
 * Evaluation of block, variable in time
 */
public class BlockEvaluation {

	private BlockEvaluation() { 
	}

	public static BlockEvaluation buildInitial(Sha256Hash blockhash) {
		return BlockEvaluation.build(blockhash, 
				0, 0, 1, false, 0, false, System.currentTimeMillis());
	}

	public static BlockEvaluation build(Sha256Hash blockhash, long rating, long depth, long cumulativeWeight, boolean solid, long height, boolean milestone, long milestoneLastUpdateTime) {
		BlockEvaluation blockEvaluation = new BlockEvaluation();
		blockEvaluation.setBlockhash(blockhash);
		blockEvaluation.setRating(rating);
		blockEvaluation.setDepth(depth);
		blockEvaluation.setCumulativeWeight(cumulativeWeight);
		blockEvaluation.setSolid(solid);
		blockEvaluation.setHeight(height);
		blockEvaluation.setMilestone(milestone);
		blockEvaluation.setMilestoneLastUpdateTime(milestoneLastUpdateTime);	
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
	private boolean solid = false;
	
	// longest path to genesis block
	private long height;

	// rating >= 75 && depth > MINDEPTH && no conflict set to true
	// if set to true for older than 7 days, remove it from this table
	private boolean milestone = false;

	// Timestamp for entry into milestone as true, reset if flip to false
	private long milestoneLastUpdateTime;

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
}
