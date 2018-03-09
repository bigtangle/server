/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import static org.bitcoinj.core.Coin.FIFTY_COINS;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;

public class BlockForTest {

    // It's pretty weak to have this around at runtime: fix later.
    private static final byte[] pubkeyForTesting = new ECKey().getPubKey();

    public static Block createNextBlock(Block block, @Nullable Address to, TransactionOutPoint prevOut,
            Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(to, Block.BLOCK_VERSION_GENESIS, prevOut, block.getTimeSeconds() + 5,
                pubkeyForTesting, FIFTY_COINS, Block.BLOCK_HEIGHT_UNKNOWN, prevBranchBlockHash);
    }

    public static Block createNextBlock(Block block, @Nullable Address to, Coin value, Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(to, Block.BLOCK_VERSION_GENESIS, null, block.getTimeSeconds() + 5,
                pubkeyForTesting, value, Block.BLOCK_HEIGHT_UNKNOWN, prevBranchBlockHash);
    }

    @VisibleForTesting
    public static Block createNextBlock(Block block, @Nullable Address to, Sha256Hash prevBranchBlockHash) {
        return createNextBlock(block, to, FIFTY_COINS, prevBranchBlockHash);
    }

    public static Block createNextBlockWithCoinbase(Block block, long version, byte[] pubKey, Coin coinbaseValue,
            final int height, Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(null, version, (TransactionOutPoint) null, Utils.currentTimeSeconds(), pubKey,
                coinbaseValue, height, prevBranchBlockHash);
    }

    public static Block createNextBlock(Block block, Address to, long version, long time, int blockHeight,
            Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(to, version, null, time, pubkeyForTesting, FIFTY_COINS, blockHeight,
                prevBranchBlockHash);
    }

    /**
     * Create a block sending 50BTC as a coinbase transaction to the public
     * static key specified. This method is intended for test use only.
     */

    public static Block createNextBlockWithCoinbase(Block block, long version, byte[] pubKey, final int height,
            Sha256Hash prevBranchBlockHash) {
        return block.createNextBlock(null, version, (TransactionOutPoint) null, Utils.currentTimeSeconds(), pubKey,
                FIFTY_COINS, height, prevBranchBlockHash);
    }
   


}
