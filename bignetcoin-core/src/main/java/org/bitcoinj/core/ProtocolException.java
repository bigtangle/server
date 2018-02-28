/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

@SuppressWarnings("serial")
public class ProtocolException extends VerificationException {

    public ProtocolException(String msg) {
        super(msg);
    }

    public ProtocolException(Exception e) {
        super(e);
    }

    public ProtocolException(String msg, Exception e) {
        super(msg, e);
    }
}
