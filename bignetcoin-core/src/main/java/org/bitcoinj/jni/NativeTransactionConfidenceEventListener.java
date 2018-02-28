/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.core.Transaction;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativeTransactionConfidenceEventListener implements TransactionConfidenceEventListener {
    public long ptr;

    @Override
    public native void onTransactionConfidenceChanged(Wallet wallet, Transaction tx);
}
