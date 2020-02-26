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

	// If true, this block is considered locally confirmed, i.e. approved by chain
	// consensus
	private long milestone;

	// Timestamp for entry into milestone as true, reset if flip to false
	private long milestoneLastUpdateTime;

	// Timestamp for entry into evaluations/reception time
	private long insertTime;

	// 0: unknown. -1: unsolid. 1: solid
	private long solid;

	// If true, this block is confirmed temporarily or by milestone
	private boolean confirmed;

	public BlockEvaluation() {
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
		setInsertTime(other.insertTime);
		setSolid(other.solid);
		setConfirmed(other.confirmed);
	}

	public static BlockEvaluation buildInitial(Block block) {
		long currentTimeMillis = System.currentTimeMillis();
		return BlockEvaluation.build(block.getHash(), 0, 0, 1, 0, -1, currentTimeMillis, currentTimeMillis, 0, false);
	}

	public static BlockEvaluation build(Sha256Hash blockhash, long rating, long depth, long cumulativeWeight,
			long height, long milestone, long milestoneLastUpdateTime, long insertTime, long solid, boolean confirmed) {
		BlockEvaluation blockEvaluation = new BlockEvaluation();
		blockEvaluation.setBlockHash(blockhash);
		blockEvaluation.setRating(rating);
		blockEvaluation.setDepth(depth);
		blockEvaluation.setCumulativeWeight(cumulativeWeight);

		blockEvaluation.setHeight(height);
		blockEvaluation.setMilestone(milestone);
		blockEvaluation.setMilestoneLastUpdateTime(milestoneLastUpdateTime);
		blockEvaluation.setInsertTime(insertTime);
		blockEvaluation.setSolid(solid);
		blockEvaluation.setConfirmed(confirmed);

		return blockEvaluation;
	}

	/*
	 * return false, if the the block is not in chain and the insert is is older
	 * than 5 Minutes
	 */
	public boolean checkRating() {
		if (getMilestone() == -1 && getRating() < 70) {
			return (System.currentTimeMillis() - 5 * 60 * 1000) > getInsertTime();
		}
		return true;
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

	public long getMilestone() {
		return milestone;
	}

	public void setMilestone(long milestone) {
		this.milestone = milestone;
	}

	public long getMilestoneLastUpdateTime() {
		return milestoneLastUpdateTime;
	}

	public void setMilestoneLastUpdateTime(long milestoneLastUpdateTime) {
		this.milestoneLastUpdateTime = milestoneLastUpdateTime;
	}

	public long getInsertTime() {
		return insertTime;
	}

	public void setInsertTime(long insertTime) {
		this.insertTime = insertTime;
	}

	public long getSolid() {
		return solid;
	}

	public void setSolid(long solid) {
		this.solid = solid;
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	public void setConfirmed(boolean confirmed) {
		this.confirmed = confirmed;
	}

	@Override
	public String toString() {
		return "BlockEvaluation [blockHash=" + blockHash + ", rating=" + rating + ", depth=" + depth
				+ ", cumulativeWeight=" + cumulativeWeight + ", height=" + height + ", milestone=" + milestone
				+ ", milestoneLastUpdateTime=" + milestoneLastUpdateTime + ", insertTime=" + insertTime + ", solid="
				+ solid + ", confirmed=" + confirmed + ", getBlockHexStr()=" + getBlockHexStr() + ", getBlockHash()="
				+ getBlockHash() + ", getRating()=" + getRating() + ", getDepth()=" + getDepth()
				+ ", getCumulativeWeight()=" + getCumulativeWeight() + ", getHeight()=" + getHeight()
				+ ", getMilestone()=" + getMilestone() + ", getMilestoneLastUpdateTime()="
				+ getMilestoneLastUpdateTime() + ", getInsertTime()=" + getInsertTime() + ", getSolid()=" + getSolid()
				+ ", isConfirmed()=" + isConfirmed() + ", getClass()=" + getClass() + ", hashCode()=" + hashCode()
				+ ", toString()=" + super.toString() + "]";
	}

}
