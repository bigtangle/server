/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.listeners.WalletChangeEventListener;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativeWalletChangeEventListener implements WalletChangeEventListener {
    public long ptr;

    @Override
    public native void onWalletChanged(Wallet wallet);
}
