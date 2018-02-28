/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

public class UTXOProviderException extends Exception {
    public UTXOProviderException() {
        super();
    }

    public UTXOProviderException(String message) {
        super(message);
    }

    public UTXOProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public UTXOProviderException(Throwable cause) {
        super(cause);
    }
}
