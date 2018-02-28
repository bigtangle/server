/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>Parent class for header only messages that don't have a payload.
 * Currently this includes getaddr, verack and special bitcoinj class UnknownMessage.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public abstract class EmptyMessage extends Message {

    public EmptyMessage() {
        length = 0;
    }

    public EmptyMessage(NetworkParameters params) {
        super(params);
        length = 0;
    }

    public EmptyMessage(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
        length = 0;
    }

    @Override
    protected final void bitcoinSerializeToStream(OutputStream stream) throws IOException {
    }

    @Override
    protected void parse() throws ProtocolException {
    }

    /* (non-Javadoc)
      * @see Message#bitcoinSerialize()
      */
    @Override
    public byte[] bitcoinSerialize() {
        return new byte[0];
    }
}
