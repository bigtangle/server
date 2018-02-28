/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativeWalletCoinsReceivedEventListener implements WalletCoinsReceivedEventListener {
    public long ptr;

    @Override
    public native void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance);
}
