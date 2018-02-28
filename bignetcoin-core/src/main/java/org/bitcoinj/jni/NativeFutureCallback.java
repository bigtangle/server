/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.jni;

import com.google.common.util.concurrent.FutureCallback;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativeFutureCallback implements FutureCallback {
    public long ptr;

    @Override
    public native void onSuccess(Object o);

    @Override
    public native void onFailure(Throwable throwable);
}
