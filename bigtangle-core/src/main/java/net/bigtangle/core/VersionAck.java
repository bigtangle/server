/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

/**
 * <p>The verack message, sent by a client accepting the version message they
 * received from their peer.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class VersionAck extends EmptyMessage {
    public VersionAck() {
    }

    // this is needed by the BitcoinSerializer
    public VersionAck(NetworkParameters params, byte[] payload) {
    }
}
