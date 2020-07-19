package net.bigtangle.core;

import net.bigtangle.core.Block.Type;

public class BlockEvaluationDisplay extends BlockEvaluation {

    private Type blockType;

    /*
     * the latest chain number 
     */
    private long latestchainnumber;
    
    public BlockEvaluationDisplay() {
    }

    public BlockEvaluationDisplay(BlockEvaluation other) {
        super(other); 
    }

    public Type getBlockType() {
        return blockType;
    }

    public void setBlockType(Type blockType) {
        this.blockType = blockType;
    }

    public static BlockEvaluationDisplay build(Sha256Hash blockhash, long rating, long depth, long cumulativeWeight,
            long height, long milestone, long milestoneLastUpdateTime, long insertTime,
            int blocktype, long solid, boolean confirmed, long latestchainnumber) {
        BlockEvaluationDisplay blockEvaluation = new BlockEvaluationDisplay();
        blockEvaluation.setBlockHash(blockhash);
        blockEvaluation.setNormalizeRating(rating);
        blockEvaluation.setDepth(depth);
        blockEvaluation.setCumulativeWeight(cumulativeWeight);

        blockEvaluation.setHeight(height);
        blockEvaluation.setMilestone(milestone);
        blockEvaluation.setMilestoneLastUpdateTime(milestoneLastUpdateTime);
        blockEvaluation.setInsertTime(insertTime);
        blockEvaluation.setBlockTypeInt(blocktype);
        blockEvaluation.setSolid(solid);
        blockEvaluation.setConfirmed(confirmed);
        blockEvaluation.setLatestchainnumber(latestchainnumber);
        return blockEvaluation;
    }

    public void setBlockTypeInt(int blocktype) {
        setBlockType(Type.values()[blocktype]);
    }

    public void setNormalizeRating(long rating) {
        setRating(rating *100 / NetworkParameters.NUMBER_RATING_TIPS);
    }
    
    public long getLatestchainnumber() {
        return latestchainnumber;
    }

    public void setLatestchainnumber(long latestchainnumber) {
        this.latestchainnumber = latestchainnumber;
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return " blockType=" + blockType + ", latestchainnumber=" + latestchainnumber
                + super.toString();
    }

}
