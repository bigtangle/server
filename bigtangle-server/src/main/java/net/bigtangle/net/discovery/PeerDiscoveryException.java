/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.net.discovery;

public class PeerDiscoveryException extends Exception {
    private static final long serialVersionUID = -2863411151549391392L;

    public PeerDiscoveryException() {
        super();
    }

    public PeerDiscoveryException(String message) {
        super(message);
    }

    public PeerDiscoveryException(Throwable arg0) {
        super(arg0);
    }

    public PeerDiscoveryException(String message, Throwable arg0) {
        super(message, arg0);
    }
}
