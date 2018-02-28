/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

@SuppressWarnings("serial")
public class ScriptException extends VerificationException {

    public ScriptException(String msg) {
        super(msg);
    }

    public ScriptException(String msg, Exception e) {
        super(msg, e);
    }
}
