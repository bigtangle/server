package org.bitcoinj.core;

import org.bitcoinj.core.Sha256Hash;

/*
 * Evaluation of block, variable can change in time and graph
 */
public class BlockEvaluation {

	public BlockEvaluation() { 
		//TODO make constructor private and check if all fields are correctly set
	}

	public static BlockEvaluation build(Sha256Hash blockhash, long rating, long depth, long cumulativeweight) {
		//TODO add missing fields
		BlockEvaluation blockEvaluation = new BlockEvaluation();
		blockEvaluation.setBlockhash(blockhash);
		blockEvaluation.setRating(rating);
		blockEvaluation.setDepth(depth);
		blockEvaluation.setCumulativeWeight(cumulativeweight);
		return blockEvaluation;
	}

	public Sha256Hash blockhash;
	public long rating;
	public long height;
	public long depth;
	public long cumulativeWeight;

	// no broken block in the graph
	public boolean solid = false;

	// rating >= 75 && depth > MINDEPTH && no conflict set to true
	// if set to true for older than 7 days, remove it from this table
	public boolean milestone = false;

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
}
