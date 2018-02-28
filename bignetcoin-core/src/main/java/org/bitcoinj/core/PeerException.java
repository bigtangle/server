/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

/**
 * Thrown when a problem occurs in communicating with a peer, and we should
 * retry.
 */
@SuppressWarnings("serial")
public class PeerException extends Exception {
    public PeerException(String msg) {
        super(msg);
    }

    public PeerException(Exception e) {
        super(e);
    }

    public PeerException(String msg, Exception e) {
        super(msg, e);
    }
}
