/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.ScriptsChangeEventListener;

import java.util.List;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativeScriptsChangeEventListener implements ScriptsChangeEventListener {
    public long ptr;

    @Override
    public native void onScriptsChanged(Wallet wallet, List<Script> scripts, boolean isAddingScripts);
}
