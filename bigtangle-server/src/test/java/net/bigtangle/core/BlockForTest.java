/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import javax.annotation.Nullable;

public class BlockForTest {

    private static final ECKey keyForTesting = new ECKey();

    public static Block createNextBlock(Block block, @Nullable Address to, TransactionOutPoint prevOut,
            Block branchBlock) {
        return block.createNextBlock(branchBlock, Block.BLOCK_VERSION_GENESIS, keyForTesting.getPubKeyHash());
    }

    public static Block createNextBlock(Block block, @Nullable Address to,  Block branchBlock) {
        return block.createNextBlock(branchBlock, Block.BLOCK_VERSION_GENESIS, keyForTesting.getPubKeyHash());
    }

    public static Block createNextBlock(Block block, Address to, long version, long time, int blockHeight,
            Block branchBlock) {
        return block.createNextBlock(branchBlock, version, keyForTesting.getPubKeyHash());
    }

    /**
     * Create a block. This method is intended for test use only.
     */
    public static Block createNextBlock(Block block, long version, Block branchBlock) {
        return block.createNextBlock(branchBlock, version, keyForTesting.getPubKeyHash());
    }

}
