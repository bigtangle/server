/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

/**
 * <p>Represents the "getaddr" P2P protocol message, which requests network {@link AddressMessage}s from a peer. Not to
 * be confused with {@link Address} which is sort of like an account number.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class GetAddrMessage extends EmptyMessage {

    public GetAddrMessage(NetworkParameters params) {
        super(params);
    }
}
