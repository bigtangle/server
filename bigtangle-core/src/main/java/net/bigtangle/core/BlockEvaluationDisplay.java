package net.bigtangle.core;

import net.bigtangle.core.Block.Type;

public class BlockEvaluationDisplay extends BlockEvaluation {

    private Type blockType;

    /*
     * the latest chain number 
     */
    private long latestchainnumber;
    
    
    BlockMCMC mcmc;
    
    
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

    public static BlockEvaluationDisplay build(Sha256Hash blockhash, 
            long height, long milestone, long milestoneLastUpdateTime, long insertTime,
            int blocktype, long solid, boolean confirmed, long latestchainnumber) {
        BlockEvaluationDisplay blockEvaluation = new BlockEvaluationDisplay();
        blockEvaluation.setBlockHash(blockhash); 
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
        mcmc.setRating(rating *100 / NetworkParameters.NUMBER_RATING_TIPS);
    }
    

    public long getLatestchainnumber() {
        return latestchainnumber;
    }

    public void setLatestchainnumber(long latestchainnumber) {
        this.latestchainnumber = latestchainnumber;
    }

    public BlockMCMC getMcmc() {
        return mcmc;
    }

    public void setMcmc(BlockMCMC mcmc) {
        this.mcmc = mcmc;
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
