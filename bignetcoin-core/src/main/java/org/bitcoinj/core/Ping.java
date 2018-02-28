/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class Ping extends Message {
    private long nonce;
    private boolean hasNonce;
    
    public Ping(NetworkParameters params, byte[] payloadBytes) throws ProtocolException {
        super(params, payloadBytes, 0);
    }
    
    /**
     * Create a Ping with a nonce value.
     * Only use this if the remote node has a protocol version > 60000
     */
    public Ping(long nonce) {
        this.nonce = nonce;
        this.hasNonce = true;
    }
    
    /**
     * Create a Ping without a nonce value.
     * Only use this if the remote node has a protocol version <= 60000
     */
    public Ping() {
        this.hasNonce = false;
    }
    
    @Override
    public void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        if (hasNonce)
            Utils.int64ToByteStreamLE(nonce, stream);
    }

    @Override
    protected void parse() throws ProtocolException {
        try {
            nonce = readInt64();
            hasNonce = true;
        } catch(ProtocolException e) {
            hasNonce = false;
        }
        length = hasNonce ? 8 : 0;
    }
    
    public boolean hasNonce() {
        return hasNonce;
    }
    
    public long getNonce() {
        return nonce;
    }
}
