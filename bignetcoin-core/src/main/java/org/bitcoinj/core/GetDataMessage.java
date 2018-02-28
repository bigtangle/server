/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package org.bitcoinj.core;

/**
 * <p>Represents the "getdata" P2P network message, which requests the contents of blocks or transactions given their
 * hashes.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class GetDataMessage extends ListMessage {

    public GetDataMessage(NetworkParameters params, byte[] payloadBytes) throws ProtocolException {
        super(params, payloadBytes);
    }

    /**
     * Deserializes a 'getdata' message.
     * @param params NetworkParameters object.
     * @param payload Bitcoin protocol formatted byte array containing message content.
     * @param serializer the serializer to use for this message.
     * @param length The length of message if known.  Usually this is provided when deserializing of the wire
     * as the length will be provided as part of the header.  If unknown then set to Message.UNKNOWN_LENGTH
     * @throws ProtocolException
     */
    public GetDataMessage(NetworkParameters params, byte[] payload, MessageSerializer serializer, int length)
            throws ProtocolException {
        super(params, payload, serializer, length);
    }

    public GetDataMessage(NetworkParameters params) {
        super(params);
    }

    public void addTransaction(Sha256Hash hash) {
        addItem(new InventoryItem(InventoryItem.Type.Transaction, hash));
    }

    public void addBlock(Sha256Hash hash) {
        addItem(new InventoryItem(InventoryItem.Type.Block, hash));
    }

    public void addFilteredBlock(Sha256Hash hash) {
        addItem(new InventoryItem(InventoryItem.Type.FilteredBlock, hash));
    }

    public Sha256Hash getHashOf(int i) {
        return getItems().get(i).hash;
    }
}
