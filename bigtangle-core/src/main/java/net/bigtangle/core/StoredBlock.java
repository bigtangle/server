/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bigtangle.core;

import java.nio.ByteBuffer;
import java.util.Locale;

import com.google.common.base.Objects;

import net.bigtangle.core.exception.BlockStoreException;
import net.bigtangle.core.exception.NoBlockException;
import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.exception.VerificationException;

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

    public static final int COMPACT_SERIALIZED_SIZE = NetworkParameters.HEADER_SIZE + 8; // for
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
    public static StoredBlock build(Block block, long height) throws VerificationException {
        // Stored blocks track total work done in this graph, because the
        // canonical graph is the one that represents
        // the largest amount of work done not the tallest.
        return new StoredBlock(block, height);
    }

    /**
     * Given a block store, looks up the previous block in this graph.
     * Convenience method for doing
     * <tt>store.get(this.getHeader().getPrevBlockHash())</tt>.
     *
     * @return the previous block in the graph or null if it was not found in
     *         the store.
     * @throws NoBlockException
     */
    public StoredBlock getPrev(BlockStore store) throws BlockStoreException, NoBlockException {
        return store.get(getHeader().getPrevBlockHash());
    }

    public StoredBlock getPrevBranch(BlockStore store) throws BlockStoreException, NoBlockException {
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
        buffer.put(bytes, 0, NetworkParameters.HEADER_SIZE); // Trim the
                                                             // trailing 00 byte
        // (zero transactions).
    }

    /**
     * De-serializes the stored block from a custom packed format. Used by
     * {@link CheckpointManager}.
     */
    public static StoredBlock deserializeCompact(NetworkParameters params, ByteBuffer buffer) throws ProtocolException {

        Long height = buffer.getLong(); // +8 bytes
        byte[] header = new byte[NetworkParameters.HEADER_SIZE + 1]; // Extra
                                                                     // byte for
                                                                     // the
        // 00 transactions
        // length.
        buffer.get(header, 0, NetworkParameters.HEADER_SIZE);
        return new StoredBlock(params.getDefaultSerializer().makeBlock(header), height);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "Block %s at height %d: %s", getHeader().getHashAsString(), getHeight(),
                getHeader().toString());
    }
}
