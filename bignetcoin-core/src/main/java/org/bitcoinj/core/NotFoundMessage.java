/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Sent by a peer when a getdata request doesn't find the requested data in the mempool. It has the same format
 * as an inventory message and lists the hashes of the missing items.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class NotFoundMessage extends InventoryMessage {
    public static int MIN_PROTOCOL_VERSION = 70001;

    public NotFoundMessage(NetworkParameters params) {
        super(params);
    }

    public NotFoundMessage(NetworkParameters params, byte[] payloadBytes) throws ProtocolException {
        super(params, payloadBytes);
    }

    public NotFoundMessage(NetworkParameters params, List<InventoryItem> items) {
        super(params);
        this.items = new ArrayList<InventoryItem>(items);
    }
}
