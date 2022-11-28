/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.server.model;

import java.io.Serializable;

import net.bigtangle.core.BlockMCMC;
import net.bigtangle.core.MultiSign;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Utils;
import net.bigtangle.server.data.DepthAndWeight;

/*
 * Evaluation of block, variable in time
 */
public class MCMCModel implements Serializable {

    private static final long serialVersionUID = 8388463657969339286L;

    private String hash;
    // Percentage of MCMC selected tips approving this block
    private Long rating;

    // Longest path to tip
    private Long depth;

    // Count of indirect approver blocks
    private Long cumulativeweight;

    public static MCMCModel from(DepthAndWeight m) {
        MCMCModel model = new MCMCModel();
        model.setHash(m.getHash());
        model.setDepth(m.getDepth());
        model.setCumulativeweight(m.getWeight());
        return model;
    }

    public BlockMCMC toBlockMCMC() {
        return new BlockMCMC(Sha256Hash.wrap(getHash()), getRating(), getDepth(), getCumulativeweight());

    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
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
