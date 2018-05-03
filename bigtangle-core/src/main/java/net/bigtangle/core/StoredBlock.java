/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import java.nio.ByteBuffer;
import java.util.Locale;

import com.google.common.base.Objects;

/**
 * Wraps a {@link Block} object with extra data that can be derived from the
 * block graph but is slow or inconvenient to calculate. By storing it alongside
 * the block header we reduce the amount of work required significantly.
 * <p>
 *
 * StoredBlocks are put inside a {@link BlockStore} which saves them to memory
 * or disk.
 */
public class StoredBlock {

    // A BigInteger representing the total amount of work done so far on this
    // graph. As of May 2011 it takes 8
    // bytes to represent this field, so 12 bytes should be plenty for now.

    public static final int COMPACT_SERIALIZED_SIZE = Block.HEADER_SIZE + 4; // for
                                                                             // height

    private Block header;
    private long height;

    public StoredBlock(Block header, long height) {
        this.header = header;
        this.height = height;
    }

    /**
     * The block header this object wraps. The referenced block object must not
     * have any transactions in it.
     */
    public Block getHeader() {
        return header;
    }

    /**
     * Position in the graph for this block. The genesis block has a height of
     * zero.
     */
    public long getHeight() {
        return height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StoredBlock other = (StoredBlock) o;
        return header.equals(other.header) && height == other.height;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(header, height);
    }

    /**
     * Creates a new StoredBlock, calculating the additional fields by adding to
     * the values in this block.
     */
    public static StoredBlock  build(Block block, StoredBlock storedPrev, StoredBlock storedPrevBranch)
            throws VerificationException {
        // Stored blocks track total work done in this graph, because the
        // canonical graph is the one that represents
        // the largest amount of work done not the tallest.
        long height = Block.BLOCK_HEIGHT_UNKNOWN;
        if (storedPrev != null && storedPrevBranch != null) {
            height = Math.max(storedPrev.getHeight(), storedPrevBranch.getHeight()) + 1;
        }

        return new StoredBlock(block, height);
    }

    /**
     * Given a block store, looks up the previous block in this graph.
     * Convenience method for doing
     * <tt>store.get(this.getHeader().getPrevBlockHash())</tt>.
     *
     * @return the previous block in the graph or null if it was not found in
     *         the store.
     */
    public StoredBlock getPrev(BlockStore store) throws BlockStoreException {
        return store.get(getHeader().getPrevBlockHash());
    }

    public StoredBlock getPrevBranch(BlockStore store) throws BlockStoreException {
        return store.get(getHeader().getPrevBranchBlockHash());
    }

    /**
     * Serializes the stored block to a custom packed format. Used by
     * {@link CheckpointManager}.
     */
    public void serializeCompact(ByteBuffer buffer) {

        buffer.putLong(getHeight());
        // Using unsafeBitcoinSerialize here can give us direct access to the
        // same bytes we read off the wire,
        // avoiding serialization round-trips.
        byte[] bytes = getHeader().unsafeBitcoinSerialize();
        buffer.put(bytes, 0, Block.HEADER_SIZE); // Trim the trailing 00 byte
                                                 // (zero transactions).
    }

    /**
     * De-serializes the stored block from a custom packed format. Used by
     * {@link CheckpointManager}.
     */
    public static StoredBlock deserializeCompact(NetworkParameters params, ByteBuffer buffer) throws ProtocolException {

        int height = buffer.getInt(); // +4 bytes
        byte[] header = new byte[Block.HEADER_SIZE + 1]; // Extra byte for the
                                                         // 00 transactions
                                                         // length.
        buffer.get(header, 0, Block.HEADER_SIZE);
        return new StoredBlock(params.getDefaultSerializer().makeBlock(header), height);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Block %s at height %d: %s", getHeader().getHashAsString(), getHeight(),
                getHeader().toString());
    }
}
