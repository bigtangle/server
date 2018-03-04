package com.bignetcoin.server.model;

import org.bitcoinj.core.Sha256Hash;

/*
 * Evaluation of block, variable can change in time and graph
 */
public class BlockEvaluation {

    public Sha256Hash blockhash;
    public int rating;
    public int depth;
    public int cumulativeweight;
 
    //no broken block in the graph
    public boolean solid = false;  
    


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
