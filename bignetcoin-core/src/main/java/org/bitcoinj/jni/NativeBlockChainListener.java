/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import org.bitcoinj.core.*;

import java.util.List;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.ReorganizeListener;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.store.BlockGraph;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativeBlockChainListener implements NewBestBlockListener, ReorganizeListener, TransactionReceivedInBlockListener {
    public long ptr;

    @Override
    public native void notifyNewBestBlock(StoredBlock block) throws VerificationException;

    @Override
    public native void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) throws VerificationException;

    @Override
    public native void receiveFromBlock(Transaction tx, StoredBlock block, BlockGraph.NewBlockType blockType,
                                        int relativityOffset) throws VerificationException;

    @Override
    public native boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, BlockGraph.NewBlockType blockType,
                                                     int relativityOffset) throws VerificationException;
}
