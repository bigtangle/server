/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.store;

/**
 * Thrown when something goes wrong with storing a block. Examples: out of disk space.
 */
public class BlockStoreException extends Exception {
    public BlockStoreException(String message) {
        super(message);
    }

    public BlockStoreException(Throwable t) {
        super(t);
    }

    public BlockStoreException(String message, Throwable t) {
        super(message, t);
    }
}
