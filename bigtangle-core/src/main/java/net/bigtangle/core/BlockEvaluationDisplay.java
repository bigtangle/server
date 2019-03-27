package net.bigtangle.core;

import net.bigtangle.core.Block.Type;

public class BlockEvaluationDisplay extends BlockEvaluation {

	private Type blockType;

	public BlockEvaluationDisplay() {
	}

	public BlockEvaluationDisplay(BlockEvaluation other) {
		super(other);
		// TODO Auto-generated constructor stub
	}

	public Type getBlockType() {
		return blockType;
	}

	public void setBlockType(Type blockType) {
		this.blockType = blockType;
	}

	public static BlockEvaluationDisplay build(Sha256Hash blockhash, long rating, long depth, long cumulativeWeight,
			long height, boolean milestone, long milestoneLastUpdateTime, long milestoneDepth, long insertTime,
			boolean maintained, int blocktype) {
		BlockEvaluationDisplay blockEvaluation = new BlockEvaluationDisplay();
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
		blockEvaluation.setBlockType(blocktype);
		return blockEvaluation;
	}

	public void setBlockType(long blocktype) {
		setBlockType(Type.values()[(int) blocktype]);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

}
