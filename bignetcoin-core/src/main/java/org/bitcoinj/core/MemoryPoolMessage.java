/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>The "mempool" message asks a remote peer to announce all transactions in its memory pool, possibly restricted by
 * any Bloom filter set on the connection. The list of transaction hashes comes back in an inv message. Note that
 * this is different to the {@link TxConfidenceTable} object which doesn't try to keep track of all pending transactions,
 * it's just a holding area for transactions that a part of the app may find interesting. The mempool message has
 * no fields.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class MemoryPoolMessage extends Message {
    @Override
    protected void parse() throws ProtocolException {}

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {}
}
