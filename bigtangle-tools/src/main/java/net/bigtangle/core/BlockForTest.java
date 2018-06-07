/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Coin.FIFTY_COINS;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;

import net.bigtangle.core.Address;
import net.bigtangle.core.Block;
import net.bigtangle.core.Coin;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.Utils;

public class BlockForTest {

    // It's pretty weak to have this around at runtime: fix later.
    private static final ECKey keyForTesting = new ECKey();
    // .getPubKey();

    public static Block createNextBlock(Block block, @Nullable Address to, TransactionOutPoint prevOut,
            Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(to, Block.BLOCK_VERSION_GENESIS, prevOut, block.getTimeSeconds() + 5,
                keyForTesting.getPubKey(),  Block.BLOCK_HEIGHT_UNKNOWN, prevBranchBlockHash,
                keyForTesting.getPubKeyHash());
    }

    public static Block createNextBlock(Block block, @Nullable Address to,  Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(to, Block.BLOCK_VERSION_GENESIS, null, block.getTimeSeconds() + 5,
                keyForTesting.getPubKey(),   Block.BLOCK_HEIGHT_UNKNOWN, prevBranchBlockHash,
                keyForTesting.getPubKeyHash());
    }

   
 
    public static Block createNextBlock(Block block, Address to, long version, long time, int blockHeight,
            Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(to, version, null, time, keyForTesting.getPubKey(),  blockHeight,
                prevBranchBlockHash, keyForTesting.getPubKeyHash());
    }

    /**
     * Create a block sending 50BTC as a coinbase transaction to the public
     * static key specified. This method is intended for test use only.
     */

    public static Block createNextBlockWithCoinbase(Block block, long version, byte[] pubKey, final int height,
            Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(null, version, (TransactionOutPoint) null, Utils.currentTimeSeconds(), pubKey,
                 height, prevBranchBlockHash, keyForTesting.getPubKeyHash() 
                );
    }

    public static Block createNextBlock(Block block, long version, byte[] pubKey, final int height,
            Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(null, version, (TransactionOutPoint) null, Utils.currentTimeSeconds(), pubKey,
                  height, prevBranchBlockHash, keyForTesting.getPubKeyHash() 
                );
    }

}
