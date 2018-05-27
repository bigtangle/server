/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

/**
 * Thrown when something goes wrong with storing a block. Examples: out of disk space.
 */
public class BlockStoreException extends Exception {

    private static final long serialVersionUID = -2924176331904923534L;

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
