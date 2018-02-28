/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.wallet.listeners.KeyChainEventListener;

import java.util.List;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativeKeyChainEventListener implements KeyChainEventListener {
    public long ptr;

    @Override
    public native void onKeysAdded(List<ECKey> keys);
}
