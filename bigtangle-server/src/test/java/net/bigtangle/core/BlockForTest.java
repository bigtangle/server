/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import javax.annotation.Nullable;

public class BlockForTest {
    
    // TODO remove this

    public static final ECKey keyForTesting = new ECKey();

    public static Block createNextBlock(Block block, @Nullable Address to, TransactionOutPoint prevOut,
            Block branchBlock) {
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub));
        return block.createNextBlock(branchBlock, NetworkParameters.BLOCK_VERSION_GENESIS, genesiskey.getPubKeyHash());
    }

    public static Block createNextBlock(Block block, @Nullable Address to,  Block branchBlock) {
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub));
        return block.createNextBlock(branchBlock, NetworkParameters.BLOCK_VERSION_GENESIS, genesiskey.getPubKeyHash());
    }

    public static Block createNextBlock(Block block, Address to, long version, long time, int blockHeight,
            Block branchBlock) {
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub));
        return block.createNextBlock(branchBlock, version, genesiskey.getPubKeyHash());
    }

    /**
     * Create a block. This method is intended for test use only.
     */
    public static Block createNextBlock(Block block, long version, Block branchBlock) {
        ECKey genesiskey = new ECKey(Utils.HEX.decode(NetworkParameters.testPriv), Utils.HEX.decode(NetworkParameters.testPub));
        return block.createNextBlock(branchBlock, version, genesiskey.getPubKeyHash());
    }

}
