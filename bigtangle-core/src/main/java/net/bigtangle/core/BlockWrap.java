/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

/**
 * Wraps a {@link Block} object with extra data that can be derived from the
 * blockstore
 */
public class BlockWrap {
    protected Block block;
    protected BlockEvaluation blockEvaluation;
    protected NetworkParameters params;
    
    protected BlockWrap() {
        super();
    }

    public BlockWrap(Block block, BlockEvaluation blockEvaluation, NetworkParameters params) {
        super();
        this.block = block;
        this.blockEvaluation = blockEvaluation;
        this.params = params;
    }

    public Block getBlock() {
        return block;
    }

    public BlockEvaluation getBlockEvaluation() {
        return blockEvaluation;
    }

    public NetworkParameters getParams() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return getBlock().equals(((BlockWrap) o).getBlock());
    }

    @Override
    public int hashCode() {
        return getBlock().hashCode();
    }

    public Sha256Hash getBlockHash() {
        return block.getHash();
    }
}
