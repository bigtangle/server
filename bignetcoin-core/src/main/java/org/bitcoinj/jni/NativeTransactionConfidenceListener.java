/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import net.bigtangle.core.TransactionConfidence;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativeTransactionConfidenceListener implements TransactionConfidence.Listener {
    public long ptr;

    @Override
    public native void onConfidenceChanged(TransactionConfidence confidence, ChangeReason reason);
}
