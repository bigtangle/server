/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.model;

import java.io.Serializable;

import net.bigtangle.core.BlockMCMC;
import net.bigtangle.core.Sha256Hash;

/*
 * Evaluation of block, variable in time
 */
public class MCMCModel extends BlockModel implements Serializable {

    private static final long serialVersionUID = 8388463657969339286L;

   

    // Percentage of MCMC selected tips approving this block
    private Long rating;

    // Longest path to tip
    private Long depth;

    // Count of indirect approver blocks
    private Long cumulativeweight;

    public BlockMCMC toBlockMCMC()   {
        return new BlockMCMC(Sha256Hash.wrap( getHash() ), getRating(), getDepth(),
                getCumulativeweight());

    }
 

    public Long getRating() {
        return rating;
    }

    public void setRating(Long rating) {
        this.rating = rating;
    }

    public Long getDepth() {
        return depth;
    }

    public void setDepth(Long depth) {
        this.depth = depth;
    }

    public Long getCumulativeweight() {
        return cumulativeweight;
    }

    public void setCumulativeweight(Long cumulativeweight) {
        this.cumulativeweight = cumulativeweight;
    }

}
