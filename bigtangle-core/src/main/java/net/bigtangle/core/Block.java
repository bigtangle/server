/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

import static net.bigtangle.core.Sha256Hash.hashTwice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import net.bigtangle.core.exception.ProtocolException;
import net.bigtangle.core.exception.VerificationException;
import net.bigtangle.core.exception.VerificationException.CoinbaseDisallowedException;
import net.bigtangle.core.exception.VerificationException.DifficultyTargetException;
import net.bigtangle.core.exception.VerificationException.LargerThanMaxBlockSize;
import net.bigtangle.core.exception.VerificationException.MerkleRootMismatchException;
import net.bigtangle.core.exception.VerificationException.ProofOfWorkException;
import net.bigtangle.core.exception.VerificationException.SigOpsException;
import net.bigtangle.core.exception.VerificationException.TimeTravelerException;
import net.bigtangle.equihash.EquihashProof;
import net.bigtangle.equihash.EquihashSolver;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;

/**
 * <p>
 * A block is a group of transactions, and is one of the fundamental data
 * structures of the Bitcoin system. It records a set of {@link Transaction}s
 * together with some data that links it into a place in the global block
 * structure, and proves that a difficult calculation was done over its
 * contents. See <a href="http://bitcoin.net/bigtangle.pdf">the white paper</a>
 * for more detail on blocks.
 * <p/>
 *
 * <p>
 * To get a block, you can either build one from the raw bytes you can get from
 * another implementation, or request one specifically using
 * {@link Peer#getBlock(Sha256Hash)}.
 * </p>
 * 
 */
public class Block extends Message {
    private static final Logger log = LoggerFactory.getLogger(Block.class);

    // Fields defined as part of the protocol format.
    private long version;
    private Sha256Hash prevBlockHash;
    private Sha256Hash prevBranchBlockHash; // second predecessor
    private Sha256Hash merkleRoot;
    private long time;
    private long difficultyTarget; // "nBits"
    private long lastMiningRewardBlock; // last approved reward blocks max
    private long nonce;
    private byte[] minerAddress; // Utils.sha256hash160
    private Type blockType;
    private long height;
    // If NetworkParameters.USE_EQUIHASH, this field will contain the PoW
    // solution
    /** If null, it means this PoW was not solved yet. */
    @Nullable
    private EquihashProof equihashProof;

    /** If null, it means this object holds only the headers. */
    @Nullable
    List<Transaction> transactions;

    /** Stores the hash of the block. If null, getHash() will recalculate it. */
    private Sha256Hash hash;

    protected boolean headerBytesValid;
    protected boolean transactionBytesValid;

    // Blocks can be encoded in a way that will use more bytes than is optimal
    // (due to VarInts having multiple encodings)
    // MAX_BLOCK_SIZE must be compared to the optimal encoding, not the actual
    // encoding, so when parsing, we keep track
    // of the size of the ideal encoding in addition to the actual message size
    // (which Message needs)
    protected int optimalEncodingMessageSize;

    /**
     * To add new BLOCKTYPES, implement their rules in the switches:
     * FullPrunedBlockGraph.connectBlock FullPrunedBlockGraph.confirmBlock
     * FullPrunedBlockGraph.unconfirmBlock
     * FullPrunedBlockGraph.unconfirmDependents
     * ValidatorService.checkTypeSpecificSolidity
     * BlockWrap.addTypeSpecificConflictCandidates ConflictCandidate enum +
     * switches
     */
    public enum Type {
        // TODO implement all conditions for each block type in all switches
        // TODO add size multiplier to pow difficulty
        BLOCKTYPE_INITIAL(false, 0, 0, Integer.MAX_VALUE, false), // Genesis block
        BLOCKTYPE_TRANSFER(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // Default
                                                                                   // block
        BLOCKTYPE_REWARD(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, true), // Rewards
                                                                                 // of
                                                                                 // mining
                                                                                 // //
                                                                                 // TODO
                                                                                 // rename
                                                                                 // to
                                                                                 // consensus
        BLOCKTYPE_TOKEN_CREATION(true, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // Custom
                                                                                        // token
                                                                                        // issuance
        BLOCKTYPE_USERDATA(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // TODO
                                                                                   // User-defined
                                                                                   // data
        BLOCKTYPE_VOS(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // TODO
                                                                              // Smart
                                                                              // contracts
        BLOCKTYPE_GOVERNANCE(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // TODO
                                                                                     // Governance
                                                                                     // of
                                                                                     // software
        BLOCKTYPE_FILE(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // TODO
                                                                               // User-defined
                                                                               // file
        BLOCKTYPE_VOS_EXECUTE(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // TODO
                                                                                      // VOS
                                                                                      // execution
                                                                                      // result
        BLOCKTYPE_CROSSTANGLE(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // TODO
                                                                                      // transfer
                                                                                      // from
                                                                                      // mainnet
                                                                                      // to
                                                                                      // permissioned
        BLOCKTYPE_ORDER_OPEN(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // Opens
                                                                                     // a
                                                                                     // new
                                                                                     // order
        BLOCKTYPE_ORDER_OP(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, false), // Issues
                                                                                   // a
                                                                                   // refresh/cancel
                                                                                   // of
                                                                                   // an
                                                                                   // order
        BLOCKTYPE_ORDER_RECLAIM(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, true), // Reclaims
                                                                                        // lost
                                                                                        // orders
        BLOCKTYPE_ORDER_MATCHING(false, 1, 1, NetworkParameters.MAX_DEFAULT_BLOCK_SIZE, true); // Perform
                                                                                         // order
                                                                                         // matching

        private boolean allowCoinbaseTransaction;
        private int powMultiplier; // TODO use in reward calcs
        private int rewardMultiplier; // TODO use in reward calcs
        private int maxSize;
        private boolean needsCalculation;

        private Type(boolean allowCoinbaseTransaction, int powMultiplier, int rewardMultiplier, int maxSize, boolean needsCalculation) {
            this.allowCoinbaseTransaction = allowCoinbaseTransaction;
            this.powMultiplier = powMultiplier;
            this.rewardMultiplier = rewardMultiplier;
            this.maxSize = maxSize;
            this.needsCalculation = needsCalculation;
        }

        public boolean allowCoinbaseTransaction() {
            return allowCoinbaseTransaction;
        }

        public int getPowMultiplier() {
            return powMultiplier;
        }

        public int getRewardMultiplier() {
            return rewardMultiplier;
        }

        public int getMaxBlockSize() {
            return maxSize;
        }

        public boolean needsCalculation() {
            return needsCalculation;
        }
    }

    Block(NetworkParameters params, long setVersion) {
        this(params, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, Block.Type.BLOCKTYPE_TRANSFER.ordinal(), 0, 0,
                NetworkParameters.EASIEST_DIFFICULTY_TARGET, 0);
    }

    public Block(NetworkParameters params, long blockVersionGenesis, long type) {
        this(params, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, type, 0, 0,
                NetworkParameters.EASIEST_DIFFICULTY_TARGET, 0);
    }

    public Block(NetworkParameters params, Block r1, Block r2) {
        this(params, r1.getHash(), r2.getHash(), Block.Type.BLOCKTYPE_TRANSFER.ordinal(),
                Math.max(r1.getTimeSeconds(), r2.getTimeSeconds()),
                Math.max(r1.getLastMiningRewardBlock(), r2.getLastMiningRewardBlock()),
                r1.getLastMiningRewardBlock() > r2.getLastMiningRewardBlock() ? r1.getDifficultyTarget()
                        : r2.getDifficultyTarget(),
                Math.max(r1.getHeight(), r2.getHeight()) + 1);
    }

    public Block(NetworkParameters params, Sha256Hash prevBlockHash, Sha256Hash prevBranchBlockHash, long blocktype,
            long minTime, long lastMiningRewardBlock, long difficultyTarget, long heigth) {
        this(params, prevBlockHash, prevBranchBlockHash, Type.values()[(int) blocktype], minTime, lastMiningRewardBlock,
                difficultyTarget);
    }

    public Block(NetworkParameters params, Sha256Hash prevBlockHash, Sha256Hash prevBranchBlockHash, Type blocktype,
            long minTime, long lastMiningRewardBlock, long difficultyTarget) {
        super(params);
        // Set up a few basic things. We are not complete after this though.
        this.version = NetworkParameters.BLOCK_VERSION_GENESIS;
        this.difficultyTarget = difficultyTarget;
        this.lastMiningRewardBlock = lastMiningRewardBlock;
        this.time = System.currentTimeMillis() / 1000;
        if (this.time < minTime)
            this.time = minTime;
        this.prevBlockHash = prevBlockHash;
        this.prevBranchBlockHash = prevBranchBlockHash;

        this.blockType = blocktype;
        this.minerAddress = new byte[20];
        length = NetworkParameters.HEADER_SIZE;
        this.transactions = new ArrayList<>();
    }

    /**
     * Construct a block object from the Bitcoin wire format.
     * 
     * @param params
     *            NetworkParameters object.
     * @param payloadBytes
     *            the payload to extract the block from.
     * @param serializer
     *            the serializer to use for this message.
     * @param length
     *            The length of message if known. Usually this is provided when
     *            deserializing of the wire as the length will be provided as
     *            part of the header. If unknown then set to
     *            Message.UNKNOWN_LENGTH
     * @throws ProtocolException
     */
    public Block(NetworkParameters params, byte[] payloadBytes, MessageSerializer serializer, int length)
            throws ProtocolException {
        super(params, payloadBytes, 0, serializer, length);
    }

    /**
     * Construct a block object from the Bitcoin wire format.
     * 
     * @param params
     *            NetworkParameters object.
     * @param payloadBytes
     *            the payload to extract the block from.
     * @param offset
     *            The location of the first payload byte within the array.
     * @param serializer
     *            the serializer to use for this message.
     * @param length
     *            The length of message if known. Usually this is provided when
     *            deserializing of the wire as the length will be provided as
     *            part of the header. If unknown then set to
     *            Message.UNKNOWN_LENGTH
     * @throws ProtocolException
     */
    public Block(NetworkParameters params, byte[] payloadBytes, int offset, MessageSerializer serializer, int length)
            throws ProtocolException {
        super(params, payloadBytes, offset, serializer, length);
    }

    /**
     * Construct a block object from the Bitcoin wire format. Used in the case
     * of a block contained within another message (i.e. for AuxPoW header).
     *
     * @param params
     *            NetworkParameters object.
     * @param payloadBytes
     *            Bitcoin protocol formatted byte array containing message
     *            content.
     * @param offset
     *            The location of the first payload byte within the array.
     * @param parent
     *            The message element which contains this block, maybe null for
     *            no parent.
     * @param serializer
     *            the serializer to use for this block.
     * @param length
     *            The length of message if known. Usually this is provided when
     *            deserializing of the wire as the length will be provided as
     *            part of the header. If unknown then set to
     *            Message.UNKNOWN_LENGTH
     * @throws ProtocolException
     */
    public Block(NetworkParameters params, byte[] payloadBytes, int offset, @Nullable Message parent,
            MessageSerializer serializer, int length) throws ProtocolException {
        super(params, payloadBytes, offset, serializer, length);
    }

    /**
     * Parse transactions from the block.
     * 
     * @param transactionsOffset
     *            Offset of the transactions within the block. Useful for
     *            non-Bitcoin chains where the block header may not be a fixed
     *            size.
     */
    protected void parseTransactions(final int transactionsOffset) throws ProtocolException {
        cursor = transactionsOffset;
        optimalEncodingMessageSize = NetworkParameters.HEADER_SIZE;
        if (payload.length == cursor) {
            // This message is just a header, it has no transactions.
            transactionBytesValid = false;
            return;
        }

        int numTransactions = (int) readVarInt();
        optimalEncodingMessageSize += VarInt.sizeOf(numTransactions);
        transactions = new ArrayList<Transaction>(numTransactions);
        for (int i = 0; i < numTransactions; i++) {
            Transaction tx = new Transaction(params, payload, cursor, this, serializer, UNKNOWN_LENGTH);
            // Label the transaction as coming from the P2P network, so code
            // that cares where we first saw it knows.
            // tx.getConfidence().setSource(TransactionConfidence.Source.NETWORK);
            transactions.add(tx);
            cursor += tx.getMessageSize();
            optimalEncodingMessageSize += tx.getOptimalEncodingMessageSize();
        }
        transactionBytesValid = serializer.isParseRetainMode();
    }

    @Override
    protected void parse() throws ProtocolException {
        // header
        cursor = offset;
        version = readUint32();
        prevBlockHash = readHash();
        prevBranchBlockHash = readHash();
        merkleRoot = readHash();
        time = readInt64();
        difficultyTarget = readInt64();
        lastMiningRewardBlock = readInt64();
        nonce = readUint32();
        minerAddress = readBytes(20);
        blockType = Type.values()[(int) readUint32()];
        height = readInt64();
        hash = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(payload, offset, cursor - offset));
        headerBytesValid = serializer.isParseRetainMode();

        // PoW
        if (NetworkParameters.USE_EQUIHASH)
            setEquihashProof(EquihashProof.from(readBytes(EquihashProof.BYTE_LENGTH)));

        // transactions
        parseTransactions(cursor);
        length = cursor - offset;
    }

    public int getOptimalEncodingMessageSize() {
        if (optimalEncodingMessageSize != 0)
            return optimalEncodingMessageSize;
        optimalEncodingMessageSize = bitcoinSerialize().length;
        return optimalEncodingMessageSize;
    }

    // default for testing
    void writeHeader(OutputStream stream) throws IOException {
        // try for cached write first
        if (headerBytesValid && payload != null && payload.length >= offset + NetworkParameters.HEADER_SIZE) {
            stream.write(payload, offset, NetworkParameters.HEADER_SIZE);
            return;
        }

        // fall back to manual write
        Utils.uint32ToByteStreamLE(version, stream);
        stream.write(prevBlockHash.getReversedBytes());
        stream.write(prevBranchBlockHash.getReversedBytes());
        stream.write(getMerkleRoot().getReversedBytes());
        Utils.int64ToByteStreamLE(time, stream);
        Utils.int64ToByteStreamLE(difficultyTarget, stream);
        Utils.int64ToByteStreamLE(lastMiningRewardBlock, stream);
        Utils.uint32ToByteStreamLE(nonce, stream);
        stream.write(minerAddress);
        Utils.uint32ToByteStreamLE(blockType.ordinal(), stream);
        Utils.int64ToByteStreamLE(height, stream);
    }

    void writePoW(OutputStream stream) throws IOException {
        if (NetworkParameters.USE_EQUIHASH) {
            if (getEquihashProof() != null) {
                stream.write(getEquihashProof().serialize());
            } else {
                stream.write(EquihashProof.getDummy().serialize());
                log.warn("serializing block with dummy PoW, ensure that PoW is computed before publishing");
            }
        }
    }

    private void writeTransactions(OutputStream stream) throws IOException {
        // check for no transaction conditions first
        // must be a more efficient way to do this but I'm tired atm.
        if (transactions == null) {
            return;
        }

        // confirmed we must have transactions either cached or as objects.
        if (transactionBytesValid && payload != null && payload.length >= offset + length) {
            stream.write(payload, offset + NetworkParameters.HEADER_SIZE, length - NetworkParameters.HEADER_SIZE);
            return;
        }

        if (transactions != null) {
            stream.write(new VarInt(transactions.size()).encode());
            for (Transaction tx : transactions) {
                tx.bitcoinSerialize(stream);
            }
        }
    }

    /**
     * Special handling to check if we have a valid byte array for both header
     * and transactions
     *
     * @throws IOException
     */
    @Override
    public byte[] bitcoinSerialize() {
        // we have completely cached byte array.
        if (headerBytesValid && transactionBytesValid) {
            Preconditions.checkNotNull(payload,
                    "Bytes should never be null if headerBytesValid && transactionBytesValid");
            if (length == payload.length) {
                return payload;
            } else {
                // byte array is offset so copy out the correct range.
                byte[] buf = new byte[length];
                System.arraycopy(payload, offset, buf, 0, length);
                return buf;
            }
        }

        // At least one of the two cacheable components is invalid
        // so fall back to stream write since we can't be sure of the length.
        ByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(
                length == UNKNOWN_LENGTH ? NetworkParameters.HEADER_SIZE + guessTransactionsLength() : length);
        try {
            writeHeader(stream);
            writePoW(stream);
            writeTransactions(stream);
        } catch (IOException e) {
            // Cannot happen, we are serializing to a memory stream.
        }
        return stream.toByteArray();
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        writeHeader(stream);
        writePoW(stream);
        // We may only have enough data to write the header and PoW.
        writeTransactions(stream);
    }

    /**
     * Provides a reasonable guess at the byte length of the transactions part
     * of the block. The returned value will be accurate in 99% of cases and in
     * those cases where not will probably slightly oversize.
     *
     * This is used to preallocate the underlying byte array for a
     * ByteArrayOutputStream. If the size is under the real value the only
     * penalty is resizing of the underlying byte array.
     */
    private int guessTransactionsLength() {
        if (transactionBytesValid)
            return payload.length - NetworkParameters.HEADER_SIZE;
        if (transactions == null)
            return 0;
        int len = VarInt.sizeOf(transactions.size());
        for (Transaction tx : transactions) {
            // 255 is just a guess at an average tx length
            len += tx.length == UNKNOWN_LENGTH ? 255 : tx.length;
        }
        return len;
    }

    @Override
    protected void unCache() {
        // Since we have alternate uncache methods to use internally this will
        // only ever be called by a child
        // transaction so we only need to invalidate that part of the cache.
        unCacheTransactions();
    }

    private void unCacheHeader() {
        headerBytesValid = false;
        if (!transactionBytesValid)
            payload = null;
        hash = null;
    }

    private void unCacheTransactions() {
        transactionBytesValid = false;
        if (!headerBytesValid)
            payload = null;
        // Current implementation has to uncache headers as well as any change
        // to a tx will alter the merkle root. In
        // future we can go more granular and cache merkle root separately so
        // rest of the header does not need to be
        // rewritten.
        unCacheHeader();
        // Clear merkleRoot last as it may end up being parsed during
        // unCacheHeader().
        merkleRoot = null;
    }

    /**
     * Calculates the block hash by serializing the block and hashing the
     * resulting bytes.
     */
    private Sha256Hash calculateHash() {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(NetworkParameters.HEADER_SIZE);
            writeHeader(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    /**
     * Calculates the hash relevant for PoW difficulty checks.
     */
    private Sha256Hash calculatePoWHash() {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(NetworkParameters.HEADER_SIZE);
            writeHeader(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    /**
     * Returns the hash of the block (which for a valid, solved block should be
     * below the target) in the form seen on the block explorer. If you call
     * this on block 1 in the mainnet chain you will get
     * "00000000839a8e6886ab5951d76f411475428afc90947ee320161bbf18eb6048".
     */
    public String getHashAsString() {
        return getHash().toString();
    }

    /**
     * Returns the hash of the block (which for a valid, solved block should be
     * below the target). Big endian.
     */
    @Override
    public Sha256Hash getHash() {
        if (hash == null)
            hash = calculateHash();
        return hash;
    }

    /**
     * The number that is one greater than the largest representable SHA-256
     * hash.
     */
    private static BigInteger LARGEST_HASH = BigInteger.ONE.shiftLeft(256);

    /**
     * Returns the work represented by this block.
     * <p>
     *
     * Work is defined as the number of tries needed to solve a block in the
     * average case. Consider a difficulty target that covers 5% of all possible
     * hash values. Then the work of the block will be 20. As the target gets
     * lower, the amount of work goes up.
     */
    public BigInteger getWork() throws VerificationException {
        BigInteger target = getDifficultyTargetAsInteger();
        return LARGEST_HASH.divide(target.add(BigInteger.ONE));
    }

    /** Returns a copy of the block */
    public Block cloneAsHeader() {
        Block block = new Block(params, NetworkParameters.BLOCK_VERSION_GENESIS);
        copyBitcoinHeaderTo(block);
        return block;
    }

    /** Copy the block into the provided block. */
    protected final void copyBitcoinHeaderTo(final Block block) {
        block.nonce = nonce;
        block.prevBlockHash = prevBlockHash;
        block.prevBranchBlockHash = prevBranchBlockHash;
        block.merkleRoot = getMerkleRoot();
        block.version = version;
        block.time = time;
        block.difficultyTarget = difficultyTarget;
        block.lastMiningRewardBlock = lastMiningRewardBlock;
        block.minerAddress = minerAddress;
        block.equihashProof = equihashProof;

        block.blockType = blockType;
        block.transactions = null;
        block.hash = getHash();
    }

    /**
     * Returns a multi-line string containing a description of the contents of
     * the block. Use for debugging purposes only.
     */
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("block hash: ").append(getHashAsString()).append('\n');
        if (transactions != null && transactions.size() > 0) {
            s.append("   ").append(transactions.size()).append(" transaction(s):\n");
            for (Transaction tx : transactions) {
                s.append(tx);
            }
        }
        s.append("   version: ").append(version);
        s.append('\n');
        s.append("   heigth: ").append(height).append("\n");
        s.append("   previous: ").append(getPrevBlockHash()).append("\n");
        s.append("   branch: ").append(getPrevBranchBlockHash()).append("\n");
        s.append("   merkle: ").append(getMerkleRoot()).append("\n");
        s.append("   time: ").append(time).append(" (").append(Utils.dateTimeFormat(time * 1000)).append(")\n");
        s.append("   difficulty target (nBits):    ").append(difficultyTarget).append("\n");
        s.append("   nonce: ").append(nonce).append("\n");
        if (minerAddress != null)
            s.append("   mineraddress: ").append(new Address(params, minerAddress)).append("\n");

        s.append("   blocktype: ").append(blockType).append("\n");

        return s.toString();
    }

    /**
     * <p>
     * Finds a value of nonce and equihashProof if using Equihash that validates
     * correctly.
     */
    public void solve() {
        // Add randomness to prevent new empty blocks from same miner with same
        // approved blocks to be the same
        setNonce(gen.nextLong());

        while (true) {
            try {
                // Is our proof of work valid yet?
                if (checkProofOfWork(false))
                    return;

                // No, so increment the nonce and try again.
                setNonce(getNonce() + 1);

                // Find Equihash solution for this configuration
                if (NetworkParameters.USE_EQUIHASH) {
                    equihashProof = EquihashSolver.calculateProof(params.equihashN, params.equihashK, getHash());
                }
            } catch (VerificationException e) {
                throw new RuntimeException(e); // Cannot happen.
            }
        }
    }

    /**
     * Returns the difficulty target as a 256 bit value that can be compared to
     * a SHA-256 hash. Inside a block the target is represented using a compact
     * form. If this form decodes to a value that is out of bounds, an exception
     * is thrown.
     */
    public BigInteger getDifficultyTargetAsInteger() throws VerificationException {
        BigInteger target = Utils.decodeCompactBits(difficultyTarget);
        if (target.signum() < 0 || target.compareTo(NetworkParameters.MAX_TARGET) > 0)
            throw new DifficultyTargetException();
        return target;
    }

    /**
     * Returns true if the PoW of the block is OK
     */
    protected boolean checkProofOfWork(boolean throwException) throws VerificationException {
        // No PoW for genesis block
        if (getBlockType() == Block.Type.BLOCKTYPE_INITIAL) {
            return true;
        }

        // This part is key - it is what proves the block was as difficult to
        // make as it claims
        // to be. Note however that in the context of this function, the block
        // can claim to be
        // as difficult as it wants to be .... if somebody was able to take
        // control of our network
        // connection and fork us onto a different chain, they could send us
        // valid blocks with
        // ridiculously easy difficulty and this function would accept them.
        //
        // To prevent this attack from being possible, elsewhere we check that
        // the difficultyTarget
        // field is of the right value. This requires us to have the preceeding
        // blocks.

        // Equihash
        if (NetworkParameters.USE_EQUIHASH) {
            if (!EquihashSolver.testProof(params.equihashN, params.equihashK, getHash(), getEquihashProof())) {
                // Proof of work check failed!
                if (throwException)
                    throw new ProofOfWorkException();
                else
                    return false;
            }
        } else {
            BigInteger target = getDifficultyTargetAsInteger();
            BigInteger h = calculatePoWHash().toBigInteger();

            if (h.compareTo(target) > 0) {
                // Proof of work check failed!
                if (throwException)
                    throw new ProofOfWorkException();
                else
                    return false;
            }
        }

        return true;
    }

    private void checkTimestamp() throws VerificationException {
        // Allow injection of a fake clock to allow unit testing.
        long currentTime = Utils.currentTimeSeconds();
        if (time > currentTime + NetworkParameters.ALLOWED_TIME_DRIFT)
            throw new TimeTravelerException();
        // TODO this shouldn't throw because it does not make the block invalid
        // forever.
    }

    private void checkSigOps() throws VerificationException {
        // Check there aren't too many signature verifications in the block.
        // This is an anti-DoS measure, see the
        // comments for MAX_BLOCK_SIGOPS.
        int sigOps = 0;
        if (transactions == null)
            return;
        for (Transaction tx : transactions) {
            sigOps += tx.getSigOpCount();
        }
        if (sigOps > NetworkParameters.MAX_BLOCK_SIGOPS)
            throw new SigOpsException();
    }

    private void checkMerkleRoot() throws VerificationException {
        Sha256Hash calculatedRoot = calculateMerkleRoot();
        if (!calculatedRoot.equals(merkleRoot)) {
            log.error("Merkle tree did not verify");
            throw new MerkleRootMismatchException();
        }
    }

    private Sha256Hash calculateMerkleRoot() {
        List<byte[]> tree = buildMerkleTree();
        if (tree.isEmpty())
            return Sha256Hash.ZERO_HASH;
        return Sha256Hash.wrap(tree.get(tree.size() - 1));
    }

    private List<byte[]> buildMerkleTree() {
        // The Merkle root is based on a tree of hashes calculated from the
        // transactions:
        //
        // root
        // / \
        // A B
        // / \ / \
        // t1 t2 t3 t4
        //
        // The tree is represented as a list: t1,t2,t3,t4,A,B,root where each
        // entry is a hash.
        //
        // The hashing algorithm is double SHA-256. The leaves are a hash of the
        // serialized contents of the transaction.
        // The interior nodes are hashes of the concenation of the two child
        // hashes.
        //
        // This structure allows the creation of proof that a transaction was
        // included into a block without having to
        // provide the full block contents. Instead, you can provide only a
        // Merkle branch. For example to prove tx2 was
        // in a block you can just provide tx2, the hash(tx1) and B. Now the
        // other party has everything they need to
        // derive the root, which can be checked against the block header. These
        // proofs aren't used right now but
        // will be helpful later when we want to download partial block
        // contents.
        //
        // Note that if the number of transactions is not even the last tx is
        // repeated to make it so (see
        // tx3 above). A tree with 5 transactions would look like this:
        //
        // root
        // / \
        // 1 5
        // / \ / \
        // 2 3 4 4
        // / \ / \ / \
        // t1 t2 t3 t4 t5 t5
        ArrayList<byte[]> tree = new ArrayList<byte[]>();
        if (transactions == null)
            transactions = new ArrayList<Transaction>();
        // Start by adding all the hashes of the transactions as leaves of the
        // tree.
        for (Transaction t : transactions) {
            tree.add(t.getHash().getBytes());
        }
        int levelOffset = 0; // Offset in the list where the currently processed
                             // level starts.
        // Step through each level, stopping when we reach the root (levelSize
        // == 1).
        for (int levelSize = transactions.size(); levelSize > 1; levelSize = (levelSize + 1) / 2) {
            // For each pair of nodes on that level:
            for (int left = 0; left < levelSize; left += 2) {
                // The right hand node can be the same as the left hand, in the
                // case where we don't have enough
                // transactions.
                int right = Math.min(left + 1, levelSize - 1);
                byte[] leftBytes = Utils.reverseBytes(tree.get(levelOffset + left));
                byte[] rightBytes = Utils.reverseBytes(tree.get(levelOffset + right));
                tree.add(Utils.reverseBytes(hashTwice(leftBytes, 0, 32, rightBytes, 0, 32)));
            }
            // Move to the next level.
            levelOffset += levelSize;
        }
        return tree;
    }

    /**
     * Checks the block data to ensure it follows the rules laid out in the
     * network parameters. Specifically, throws an exception if the proof of
     * work is invalid, or if the timestamp is too far from what it should be.
     * This is <b>not</b> everything that is required for a block to be valid,
     * only what is checkable independent of the chain and without a transaction
     * index.
     *
     * @throws VerificationException
     */
    public void verifyHeader() throws VerificationException {
        // Prove that this block is OK. It might seem that we can just ignore
        // most of these checks given that the
        // network is also verifying the blocks, but we cannot as it'd open us
        // to a variety of obscure attacks.
        //
        // Firstly we need to ensure this block does in fact represent real work
        // done. If the difficulty is high
        // enough, it's probably been done by the network.
        checkProofOfWork(true);
        checkTimestamp();
    }

    /**
     * Checks the block contents formally
     *
     * @throws VerificationException
     *             if there was an error verifying the block.
     */
    public void verifyTransactions() throws VerificationException {
        // Now we need to check that the body of the block actually matches the
        // headers. The network won't generate
        // an invalid block, but if we didn't validate this then an untrusted
        // man-in-the-middle could obtain the next
        // valid block from the network and simply replace the transactions in
        // it with their own fictional
        // transactions that reference spent or non-existant inputs.
        // if (transactions.isEmpty())
        // throw new VerificationException("Block had no transactions");
        if (this.getOptimalEncodingMessageSize() > getMaxBlockSize())
            throw new LargerThanMaxBlockSize();
        checkMerkleRoot();
        checkSigOps();
        if (transactions == null)
            return;
        for (Transaction transaction : transactions) {
            if (!allowCoinbaseTransaction() && transaction.isCoinBase()) {
                throw new CoinbaseDisallowedException();
            }

            transaction.verify();
        }
    }

    private int getMaxBlockSize() {
        return blockType.getMaxBlockSize();
    }

    /**
     * Verifies both the header and that the transactions hash to the merkle
     * root.
     *
     * @param height
     *            block height, if known, or -1 otherwise.
     * @param flags
     *            flags to indicate which tests should be applied (i.e. whether
     *            to test for height in the coinbase transaction).
     * @throws VerificationException
     *             if there was an error verifying the block.
     */
    public void verify(final int height) throws VerificationException {
        verifyHeader();
        verifyTransactions();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return getHash().equals(((Block) o).getHash());
    }

    @Override
    public int hashCode() {
        return getHash().hashCode();
    }

    /**
     * Returns the merkle root in big endian form, calculating it from
     * transactions if necessary.
     */
    public Sha256Hash getMerkleRoot() {
        if (merkleRoot == null) {
            unCacheHeader();
            merkleRoot = calculateMerkleRoot();
        }
        return merkleRoot;
    }

    /** Exists only for unit testing. */
    void setMerkleRoot(Sha256Hash value) {
        unCacheHeader();
        merkleRoot = value;
        hash = null;
    }

    /**
     * Adds a transaction to this block. The nonce and merkle root are invalid
     * after this.
     */
    public void addTransaction(Transaction t) {
        addTransaction(t, true);
    }

    /**
     * Adds a transaction to this block, with or without checking the sanity of
     * doing so
     */
    void addTransaction(Transaction t, boolean runSanityChecks) {
        unCacheTransactions();
        if (transactions == null) {
            transactions = new ArrayList<Transaction>();
        }
        t.setParent(this);
        // cui
        transactions.add(t);
        adjustLength(transactions.size(), t.length);
        // Force a recalculation next time the values are needed.
        merkleRoot = null;
        hash = null;
    }

    /**
     * Returns the version of the block data structure as defined by the Bitcoin
     * protocol.
     */
    public long getVersion() {
        return version;
    }

    /**
     * Returns the hash of the previous trunk block in the chain, as defined by
     * the block header.
     */
    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }

    public void setPrevBlockHash(Sha256Hash prevBlockHash) {
        unCacheHeader();
        this.prevBlockHash = prevBlockHash;
        this.hash = null;
    }

    /**
     * Returns the hash of the previous branch block in the chain, as defined by
     * the block header.
     */
    public Sha256Hash getPrevBranchBlockHash() {
        return prevBranchBlockHash;
    }

    public void setPrevBranchBlockHash(Sha256Hash prevBranchBlockHash) {
        unCacheHeader();
        this.prevBranchBlockHash = prevBranchBlockHash;
        this.hash = null;
    }

    /**
     * Returns the time at which the block was solved and broadcast, according
     * to the clock of the solving node. This is measured in seconds since the
     * UNIX epoch (midnight Jan 1st 1970).
     */
    public long getTimeSeconds() {
        return time;
    }

    /**
     * Returns the time at which the block was solved and broadcast, according
     * to the clock of the solving node.
     */
    public Date getTime() {
        return new Date(getTimeSeconds() * 1000);
    }

    public void setTime(long time) {
        unCacheHeader();
        this.time = time;
        this.hash = null;
    }

    /**
     * Returns the nonce, an arbitrary value that exists only to make the hash
     * of the block header fall below the difficulty target.
     */
    public long getNonce() {
        return nonce;
    }

    /** Sets the nonce and clears any cached data. */
    public void setNonce(long nonce) {
        unCacheHeader();
        this.nonce = nonce;
        this.hash = null;
    }

    /**
     * Returns an immutable list of transactions held in this block, or null if
     * this object represents just a header.
     */
    // return new List<> to avoid check null @Nullable
    public List<Transaction> getTransactions() {
        return transactions == null ? new ArrayList<Transaction>() : ImmutableList.copyOf(transactions);
    }

    // ///////////////////////////////////////////////////////////////////////////////////////////////
    // Unit testing related methods.

    // Used to make transactions unique.
    private static int txCounter;

    /**
     * Adds a coinbase transaction to the block. This exists for unit tests.
     * 
     * @param height
     *            block height, if known, or -1 otherwise.
     */

    public void addCoinbaseTransaction(byte[] pubKeyTo, Coin value) {
        this.addCoinbaseTransaction(pubKeyTo, value, null);
    }

    public void addCoinbaseTransactionData(byte[] pubKeyTo, Coin value, DataClassName dataClassName, byte[] data) {
        unCacheTransactions();
        transactions = new ArrayList<Transaction>();

        Transaction coinbase = new Transaction(params);

        coinbase.setData(data);
        coinbase.setDataClassName(dataClassName.name());

        // coinbase.tokenid = value.tokenid;
        final ScriptBuilder inputBuilder = new ScriptBuilder();

        inputBuilder.data(new byte[] { (byte) txCounter, (byte) (txCounter++ >> 8) });

        // A real coinbase transaction has some stuff in the scriptSig like the
        // extraNonce and difficulty. The
        // transactions are distinguished by every TX output going to a
        // different key.
        //
        // Here we will do things a bit differently so a new address isn't
        // needed every time. We'll put a simple
        // counter in the scriptSig so every transaction has a different hash.
        coinbase.addInput(new TransactionInput(params, coinbase, inputBuilder.build().getProgram()));
        coinbase.addOutput(new TransactionOutput(params, coinbase, value,
                ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(pubKeyTo)).getProgram()));

        transactions.add(coinbase);
        coinbase.setParent(this);
        coinbase.length = coinbase.unsafeBitcoinSerialize().length;
        adjustLength(transactions.size(), coinbase.length);
    }

    public void addCoinbaseTransactionPubKeyData(byte[] pubKeyTo, Coin value, DataClassName dataClassName,
            byte[] data) {
        unCacheTransactions();
        transactions = new ArrayList<Transaction>();

        Transaction coinbase = new Transaction(params);

        ByteBuffer byteBuffer = ByteBuffer.allocate(pubKeyTo.length + 4 + data.length + 4);
        byteBuffer.putInt(pubKeyTo.length);
        byteBuffer.put(pubKeyTo);
        byteBuffer.putInt(data.length);
        byteBuffer.put(data);
        coinbase.setData(byteBuffer.array());
        coinbase.setDataClassName(dataClassName.name());

        // coinbase.tokenid = value.tokenid;
        final ScriptBuilder inputBuilder = new ScriptBuilder();

        inputBuilder.data(new byte[] { (byte) txCounter, (byte) (txCounter++ >> 8) });

        // A real coinbase transaction has some stuff in the scriptSig like the
        // extraNonce and difficulty. The
        // transactions are distinguished by every TX output going to a
        // different key.
        //
        // Here we will do things a bit differently so a new address isn't
        // needed every time. We'll put a simple
        // counter in the scriptSig so every transaction has a different hash.
        coinbase.addInput(new TransactionInput(params, coinbase, inputBuilder.build().getProgram()));
        coinbase.addOutput(new TransactionOutput(params, coinbase, value,
                ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(pubKeyTo)).getProgram()));

        transactions.add(coinbase);
        coinbase.setParent(this);
        coinbase.length = coinbase.unsafeBitcoinSerialize().length;
        adjustLength(transactions.size(), coinbase.length);
    }

    public void addCoinbaseTransaction(byte[] pubKeyTo, Coin value, TokenInfo tokenInfo) {
        unCacheTransactions();
        transactions = new ArrayList<Transaction>();

        Transaction coinbase = new Transaction(params);
        if (tokenInfo != null) {
            coinbase.setDataClassName(DataClassName.TOKEN.name());
            byte[] buf = tokenInfo.toByteArray();
            coinbase.setData(buf);
        }

        // coinbase.tokenid = value.tokenid;
        final ScriptBuilder inputBuilder = new ScriptBuilder();

        inputBuilder.data(new byte[] { (byte) txCounter, (byte) (txCounter++ >> 8) });

        // A real coinbase transaction has some stuff in the scriptSig like the
        // extraNonce and difficulty. The
        // transactions are distinguished by every TX output going to a
        // different key.
        //
        // Here we will do things a bit differently so a new address isn't
        // needed every time. We'll put a simple
        // counter in the scriptSig so every transaction has a different hash.
        coinbase.addInput(new TransactionInput(params, coinbase, inputBuilder.build().getProgram()));
        if (tokenInfo == null) {
            coinbase.addOutput(new TransactionOutput(params, coinbase, value,
                    ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(pubKeyTo)).getProgram()));
        } else {

            if (tokenInfo.getToken() == null || tokenInfo.getToken().getSignnumber() == 0) {
                coinbase.addOutput(new TransactionOutput(params, coinbase, value,
                        ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(pubKeyTo)).getProgram()));

            } else {
                long signnumber = tokenInfo.getToken().getSignnumber();

                List<ECKey> keys = new ArrayList<ECKey>();
                for (MultiSignAddress multiSignAddress : tokenInfo.getMultiSignAddresses()) {
                    if (multiSignAddress.getTokenHolder() == 1) {
                        ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(multiSignAddress.getPubKeyHex()));
                        keys.add(ecKey);
                    }
                }
                if (keys.size() == 1) {
                    signnumber =1;
                    pubKeyTo = keys.get(0).getPubKey();
                }
                if (signnumber <= 1 && keys.size() <= 1) {
                    coinbase.addOutput(new TransactionOutput(params, coinbase, value,
                            ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(pubKeyTo)).getProgram()));
                } else {
                    int n = keys.size();
                    Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript(n, keys);
                    coinbase.addOutput(new TransactionOutput(params, coinbase, value, scriptPubKey.getProgram()));
                }
            }
        }
        transactions.add(coinbase);
        coinbase.setParent(this);
        coinbase.length = coinbase.unsafeBitcoinSerialize().length;
        adjustLength(transactions.size(), coinbase.length);
    }

    public boolean allowCoinbaseTransaction() {
        return blockType.allowCoinbaseTransaction();
    }

    private static Random gen = new Random();

    /**
     * Returns a solved, valid empty block that builds on top of this one and
     * the specified other Block.
     * 
     */
    public Block createNextBlock() {
        return createNextBlock(this);
    }

    /**
     * Returns a unsolved, valid empty block that builds on top of this one and
     * the specified other Block.
     * 
     */
    public Block createNextBlock(Block branchBlock) {
        return createNextBlock(branchBlock, NetworkParameters.BLOCK_VERSION_GENESIS,
                Address.fromBase58(params, "1Kbm8rqjcX6j5oLbq9J8FapksdvrfGUA88").getHash160());
    }

    /**
     * Returns a solved, valid empty block that builds on top of this one and
     * the specified other Block.
     */
    public Block createNextBlock(Block branchBlock, final long version, byte[] mineraddress) {
        Block b = new Block(params, version);

        b.setMinerAddress(mineraddress);
        b.setPrevBlockHash(getHash());
        b.setPrevBranchBlockHash(branchBlock.getHash());

        // Set difficulty according to previous consensus
        // only BLOCKTYPE_REWARD and BLOCKTYPE_INITIAL should overwrite this
        b.setLastMiningRewardBlock(Math.max(lastMiningRewardBlock, branchBlock.lastMiningRewardBlock));
        b.setDifficultyTarget(lastMiningRewardBlock >= branchBlock.lastMiningRewardBlock ? difficultyTarget
                : branchBlock.difficultyTarget);

        b.setHeight(Math.max(getHeight(), branchBlock.getHeight()) + 1);

        // Don't let timestamp go backwards
        long currTime = System.currentTimeMillis() / 1000;
        long minTime = Math.max(currTime, branchBlock.getTimeSeconds());
        if (currTime >= minTime)
            b.setTime(currTime + 1);
        else
            b.setTime(minTime);
        b.solve();
        try {
            b.verifyHeader();
        } catch (VerificationException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
        if (b.getVersion() != version) {
            throw new RuntimeException();
        }
        return b;
    }

    /**
     * Return whether this block contains any transactions.
     * 
     * @return true if the block contains transactions, false otherwise (is
     *         purely a header).
     */
    public boolean hasTransactions() {
        return !this.transactions.isEmpty();
    }

    public byte[] getMinerAddress() {
        return minerAddress;
    }

    public void setMinerAddress(byte[] mineraddress) {
        unCacheHeader();
        this.minerAddress = mineraddress;
        this.hash = null;
    }

    public Type getBlockType() {
        return blockType;
    }

    public void setBlockType(long blocktype) {
        setBlockType(Type.values()[(int) blocktype]);
    }

    public void setBlockType(Type blocktype) {
        unCacheHeader();
        this.blockType = blocktype;
        this.hash = null;
    }

    public EquihashProof getEquihashProof() {
        if (equihashProof != null) {
            return equihashProof;
        } else {
            return EquihashProof.getDummy();
        }
    }

    public void setEquihashProof(EquihashProof equihashProof) {
        this.equihashProof = equihashProof;
    }

    public long getDifficultyTarget() {
        return difficultyTarget;
    }

    public void setDifficultyTarget(long difficultyTarget) {
        this.difficultyTarget = difficultyTarget;
    }

    public long getLastMiningRewardBlock() {
        return lastMiningRewardBlock;
    }

    public void setLastMiningRewardBlock(long lastMiningRewardBlock) {
        this.lastMiningRewardBlock = lastMiningRewardBlock;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        unCacheHeader();
        this.height = height;
        this.hash = null;

    }

}
