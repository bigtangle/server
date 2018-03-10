package org.bitcoinj.core;

import org.bitcoinj.core.Sha256Hash;

/*
 * Evaluation of block, variable can change in time and graph
 */
public class BlockEvaluation {

    public BlockEvaluation() {
    }
    
    public static BlockEvaluation build(Sha256Hash blockhash, int rating, int depth, int cumulativeweight) {
        BlockEvaluation blockEvaluation = new BlockEvaluation();
        blockEvaluation.setBlockhash(blockhash);
        blockEvaluation.setRating(rating);
        blockEvaluation.setDepth(depth);
        blockEvaluation.setCumulativeweight(cumulativeweight);
        return blockEvaluation;
    }

    public Sha256Hash blockhash;
    public int rating;
    public int depth;
    public int cumulativeweight;
 
    //no broken block in the graph
    public boolean solid = false;  
    //rating >= 75 && depth > MINDEPTH && no conflict set to true
    // if set to true for older than 7 days, remove it from this table
    public boolean milestone = false;  
    //Timestamp for entry into milestone as true, reset if flip to false
    private long milestonelasttruetime;

    public Sha256Hash getBlockhash() {
        return blockhash;
    }
    public void setBlockhash(Sha256Hash blockhash) {
        this.blockhash = blockhash;
    }
    public int getRating() {
        return rating;
    }
    public void setRating(int rating) {
        this.rating = rating;
    }
    public int getDepth() {
        return depth;
    }
    public void setDepth(int depth) {
        this.depth = depth;
    }
    public int getCumulativeweight() {
        return cumulativeweight;
    }
    public void setCumulativeweight(int cumulativeweight) {
        this.cumulativeweight = cumulativeweight;
    }
    public boolean isSolid() {
        return solid;
    }
    public void setSolid(boolean solid) {
        this.solid = solid;
    }
    
    
    
}
