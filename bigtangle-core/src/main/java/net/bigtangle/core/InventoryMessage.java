/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * <p>Represents the "inv" P2P network message. An inv contains a list of hashes of either blocks or transactions. It's
 * a bandwidth optimization - on receiving some data, a (fully validating) peer sends every connected peer an inv
 * containing the hash of what it saw. It'll only transmit the full thing if a peer asks for it with a
 * {@link GetDataMessage}.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class InventoryMessage extends ListMessage {

    /** A hard coded constant in the protocol. */
    public static final int MAX_INV_SIZE = 50000;

    public InventoryMessage(NetworkParameters params, byte[] bytes) throws ProtocolException {
        super(params, bytes);
    }

    /**
     * Deserializes an 'inv' message.
     * @param params NetworkParameters object.
     * @param payload Bitcoin protocol formatted byte array containing message content.
     * @param serializer the serializer to use for this message.
     * @param length The length of message if known.  Usually this is provided when deserializing of the wire
     * as the length will be provided as part of the header.  If unknown then set to Message.UNKNOWN_LENGTH
     * @throws ProtocolException
     */
    public InventoryMessage(NetworkParameters params, byte[] payload, MessageSerializer serializer, int length)
            throws ProtocolException {
        super(params, payload, serializer, length);
    }

    public InventoryMessage(NetworkParameters params) {
        super(params);
    }

    public void addBlock(Block block) {
        addItem(new InventoryItem(InventoryItem.Type.Block, block.getHash()));
    }

    public void addTransaction(Transaction tx) {
        addItem(new InventoryItem(InventoryItem.Type.Transaction, tx.getHash()));
    }

    /** Creates a new inv message for the given transactions. */
    public static InventoryMessage with(Transaction... txns) {
        checkArgument(txns.length > 0);
        InventoryMessage result = new InventoryMessage(txns[0].getParams());
        for (Transaction tx : txns)
            result.addTransaction(tx);
        return result;
    }
}
