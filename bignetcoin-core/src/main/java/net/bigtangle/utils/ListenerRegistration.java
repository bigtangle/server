/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.utils;

import java.util.List;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;

/**
* A simple wrapper around a listener and an executor, with some utility methods.
*/
public class ListenerRegistration<T> {
    public final T listener;
    public final Executor executor;

    public ListenerRegistration(T listener, Executor executor) {
        this.listener = checkNotNull(listener);
        this.executor = checkNotNull(executor);
    }

    /** Returns true if the listener was removed, else false. */
    public static <T> boolean removeFromList(T listener, List<? extends ListenerRegistration<T>> list) {
        checkNotNull(listener);

        ListenerRegistration<T> item = null;
        for (ListenerRegistration<T> registration : list) {
            if (registration.listener == listener) {
                item = registration;
                break;
            }
        }
        return item != null && list.remove(item);
    }
}
