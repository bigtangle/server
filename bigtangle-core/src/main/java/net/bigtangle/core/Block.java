/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Coin.FIFTY_COINS;
import static net.bigtangle.core.Sha256Hash.hashTwice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import net.bigtangle.equihash.EquihashProof;
import net.bigtangle.equihash.EquihashSolver;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;

/**
 * <p>
 * A block is a group of transactions, and is one of the fundamental data
 * structures of the Bitcoin system. It records a set of {@link Transaction}s
 * together with some data that links it into a place in the global block chain,
 * and proves that a difficult calculation was done over its contents. See
 * <a href="http://www.bitcoin.org/bitcoin.pdf">the Bitcoin technical paper</a>
 * for more detail on blocks.
 * <p/>
 *
 * <p>
 * To get a block, you can either build one from the raw bytes you can get from
 * another implementation, or request one specifically using
 * {@link Peer#getBlock(Sha256Hash)}, or grab one from a downloaded
 * {@link BlockGraph}.
 * </p>
 * 
 * <p>
 * Instances of this class are not safe for use by multiple threads.
 * </p>
 */
public class Block extends Message {
    /**
     * Flags used to control which elements of block validation are done on
     * received blocks.
     */
    public enum VerifyFlag {
        /** Check that block height is in coinbase transaction (BIP 34). */
        HEIGHT_IN_COINBASE
    }

    private static final Logger log = LoggerFactory.getLogger(Block.class);

    /**
     * How many bytes are required to represent a block header WITHOUT the
     * trailing 00 length byte.
     */
    public static final int HEADER_SIZE = 80 + 32 + 20 + EquihashProof.BYTE_LENGTH;

    static final long ALLOWED_TIME_DRIFT = 2 * 60 * 60; // Same value as Bitcoin
                                                        // Core.

    /**
     * A constant shared by the entire network: how large in bytes a block is
     * allowed to be. One day we may have to upgrade everyone to change this, so
     * Bitcoin can continue to grow. For now it exists as an anti-DoS measure to
     * avoid somebody creating a titanically huge but valid block and forcing
     * everyone to download/store it forever.
     */
    public static final int MAX_BLOCK_SIZE = 1 * 1000 * 1000;
    /**
     * A "sigop" is a signature verification operation. Because they're
     * expensive we also impose a separate limit on the number in a block to
     * prevent somebody mining a huge block that has way more sigops than
     * normal, so is very expensive/slow to verify.
     */
    public static final int MAX_BLOCK_SIGOPS = MAX_BLOCK_SIZE / 50;

    /**
     * A value for difficultyTarget (nBits) that allows half of all possible
     * hash solutions. Used in unit testing.
     */
    public static final long EASIEST_DIFFICULTY_TARGET = 0x207fFFFFL;
    // As client provided difficult for proof of work
    public static final long CLIENT_DIFFICULTY_TARGET = 541065215;
    /** Value to use if the block height is unknown */
    public static final int BLOCK_HEIGHT_UNKNOWN = -1;
    /** Height of the first block */
    public static final int BLOCK_HEIGHT_GENESIS = 0;

    public static final long BLOCK_VERSION_GENESIS = 1;
    /** Block version introduced in BIP 34: Height in coinbase */
    public static final long BLOCK_VERSION_BIP34 = 2;
    /** Block version introduced in BIP 66: Strict DER signatures */
    public static final long BLOCK_VERSION_BIP66 = 3;
    /** Block version introduced in BIP 65: OP_CHECKLOCKTIMEVERIFY */
    public static final long BLOCK_VERSION_BIP65 = 4;
    // we use new Version
    public static final long BLOCK_VERSION_BIG1 = 5;

    // Fields defined as part of the protocol format.
    private long version;
    private Sha256Hash prevBlockHash;
    // Add as tangle
    private Sha256Hash prevBranchBlockHash;

    private Sha256Hash merkleRoot;
    private long time;
    // private long difficultyTarget; // "nBits"
    private long nonce;
    // Utils.sha256hash160
    private byte[] mineraddress;

    private long blocktype;

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

    Block(NetworkParameters params, long setVersion) {

        this(params, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, NetworkParameters.BLOCKTYPE_TRANSFER, 0);
    }

    public Block(NetworkParameters params, long blockVersionGenesis, long blocktypeTransfer) {

        this(params, Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH, NetworkParameters.BLOCKTYPE_TRANSFER, 0);
    }

    public Block(NetworkParameters params, Sha256Hash prevBlockHash, Sha256Hash prevBranchBlockHash, long blocktype,
            long minTime) {
        super(params);
        // Set up a few basic things. We are not complete after this though.
        version = Block.BLOCK_VERSION_GENESIS;
        // difficultyTarget = EASIEST_DIFFICULTY_TARGET;
        this.time = System.currentTimeMillis() / 1000;
        if (this.time < minTime)
            this.time = minTime;
        this.prevBlockHash = prevBlockHash;
        this.prevBranchBlockHash = prevBranchBlockHash;

        this.blocktype = blocktype;
        mineraddress = new byte[20];
        length = HEADER_SIZE;
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
     * Construct a block initialized with all the given fields.
     * 
     * @param params
     *            Which network the block is for.
     * @param version
     *            This should usually be set to 1 or 2, depending on if the
     *            height is in the coinbase input.
     * @param prevBlockHash
     *            Reference to previous block in the chain or
     *            {@link Sha256Hash#ZERO_HASH} if genesis.
     * @param merkleRoot
     *            The root of the merkle tree formed by the transactions.
     * @param time
     *            UNIX time when the block was mined.
     * @param difficultyTarget
     *            Number which this block hashes lower than.
     * @param nonce
     *            Arbitrary number to make the block hash lower than the target.
     * @param transactions
     *            List of transactions including the coinbase.
     */
    public Block(NetworkParameters params, long version, Sha256Hash prevBlockHash, Sha256Hash merkleRoot, long time,
            long difficultyTarget, long nonce, List<Transaction> transactions, Sha256Hash prevBranchBlockHash) {
        super(params);
        this.version = version;
        this.prevBlockHash = prevBlockHash;
        this.prevBranchBlockHash = prevBranchBlockHash;
        this.merkleRoot = merkleRoot;
        this.time = time;
        // this.difficultyTarget = difficultyTarget;
        this.nonce = nonce;
        this.transactions = new LinkedList<Transaction>();
        this.transactions.addAll(transactions);
    }

    /**
     * <p>
     * A utility method that calculates how much new Bitcoin would be created by
     * the block at the given height. The inflation of Bitcoin is predictable
     * and drops roughly every 4 years (210,000 blocks). At the dawn of the
     * system it was 50 coins per block, in late 2012 it went to 25 coins per
     * block, and so on. The size of a coinbase transaction is inflation plus
     * fees.
     * </p>
     *
     * <p>
     * The half-life is controlled by
     * {@link net.bigtangle.core.NetworkParameters#getSubsidyDecreaseBlockCount()}.
     * </p>
     */
    public Coin getBlockInflation(int height) {
        return FIFTY_COINS.shiftRight(height / params.getSubsidyDecreaseBlockCount());
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
        optimalEncodingMessageSize = HEADER_SIZE;
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
        time = readUint32();
        // difficultyTarget = readUint32();
        nonce = readUint32();
        mineraddress = readBytes(20);
        blocktype = readUint32();

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
        if (headerBytesValid && payload != null && payload.length >= offset + HEADER_SIZE) {
            stream.write(payload, offset, HEADER_SIZE);
            return;
        }
        // fall back to manual write

        Utils.uint32ToByteStreamLE(version, stream);

        stream.write(prevBlockHash.getReversedBytes());
        stream.write(prevBranchBlockHash.getReversedBytes());
        stream.write(getMerkleRoot().getReversedBytes());
        Utils.uint32ToByteStreamLE(time, stream);
        // Utils.uint32ToByteStreamLE(difficultyTarget, stream);
        Utils.uint32ToByteStreamLE(nonce, stream);
        stream.write(mineraddress);

        Utils.uint32ToByteStreamLE(blocktype, stream);
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
            stream.write(payload, offset + HEADER_SIZE, length - HEADER_SIZE);
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
                length == UNKNOWN_LENGTH ? HEADER_SIZE + guessTransactionsLength() : length);
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
            return payload.length - HEADER_SIZE;
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
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(HEADER_SIZE);
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
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(HEADER_SIZE);
            writeHeader(bos);

            if (NetworkParameters.USE_EQUIHASH)
                writePoW(bos);

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
        Block block = new Block(params, BLOCK_VERSION_GENESIS);
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
        // block.difficultyTarget = difficultyTarget
        block.mineraddress = mineraddress;
        block.equihashProof = equihashProof;

        block.blocktype = blocktype;
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
        String bips = Joiner.on(", ").skipNulls().join(isBIP34() ? "BIP34" : null, isBIP66() ? "BIP66" : null,
                isBIP65() ? "BIP65" : null);
        if (!bips.isEmpty())
            s.append(" (").append(bips).append(')');
        s.append('\n');
        s.append("   previous block: ").append(getPrevBlockHash()).append("\n");
        s.append("   branch block: ").append(getPrevBranchBlockHash()).append("\n");
        s.append("   merkle root: ").append(getMerkleRoot()).append("\n");
        s.append("   time: ").append(time).append(" (").append(Utils.dateTimeFormat(time * 1000)).append(")\n");
        // s.append(" difficulty target (nBits):
        // ").append(difficultyTarget).append("\n");
        s.append("   nonce: ").append(nonce).append("\n");
        if (mineraddress != null)
            s.append("   mineraddress: ").append(new Address(params, mineraddress)).append("\n");

        s.append("   blocktype: ").append(blocktype).append("\n");

        return s.toString();
    }

    /**
     * <p>
     * Finds a value of nonce that makes the blocks hash lower than the
     * difficulty target. This is called mining, but solve() is far too slow to
     * do real mining with. It exists only for unit testing purposes.
     *
     * <p>
     * This can loop forever if a solution cannot be found solely by
     * incrementing nonce. It doesn't change extraNonce.
     * </p>
     */
    public void solve() {
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
        BigInteger target = Utils.decodeCompactBits(EASIEST_DIFFICULTY_TARGET);
        // Utils.encodeCompactBits(target.divide(new BigInteger("2")));
        if (target.signum() < 0 || target.compareTo(NetworkParameters.MAX_TARGET) > 0)
            throw new VerificationException("Difficulty target is bad: " + target.toString());
        return target;
    }

    /**
     * Returns true if the hash of the block is OK (lower than difficulty
     * target).
     */
    protected boolean checkProofOfWork(boolean throwException) throws VerificationException {
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
        if (NetworkParameters.USE_EQUIHASH)
            if (!EquihashSolver.testProof(params.equihashN, params.equihashK, getHash(), getEquihashProof()))
                return false;

        BigInteger target = getDifficultyTargetAsInteger();

        BigInteger h = calculatePoWHash().toBigInteger();
        if (h.compareTo(target) > 0) {
            // Proof of work check failed!
            if (throwException)
                throw new VerificationException(
                        "Hash is higher than target: " + getHashAsString() + " vs " + target.toString(16));
            else
                return false;
        }
        return true;
    }

    private void checkTimestamp() throws VerificationException {
        // Allow injection of a fake clock to allow unit testing.
        long currentTime = Utils.currentTimeSeconds();
        if (time > currentTime + ALLOWED_TIME_DRIFT)
            throw new VerificationException(String.format(Locale.US, "Block too far in future: %d vs %d", time,
                    currentTime + ALLOWED_TIME_DRIFT));
    }

    private void checkSigOps() throws VerificationException {
        // Check there aren't too many signature verifications in the block.
        // This is an anti-DoS measure, see the
        // comments for MAX_BLOCK_SIGOPS.
        int sigOps = 0;
        for (Transaction tx : transactions) {
            sigOps += tx.getSigOpCount();
        }
        if (sigOps > MAX_BLOCK_SIGOPS)
            throw new VerificationException("Block had too many Signature Operations");
    }

    private void checkMerkleRoot() throws VerificationException {
        Sha256Hash calculatedRoot = calculateMerkleRoot();
        if (!calculatedRoot.equals(merkleRoot)) {
            log.error("Merkle tree did not verify");
            throw new VerificationException("Merkle hashes do not match: " + calculatedRoot + " vs " + merkleRoot);
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
     * Verify the transactions on a block partly (for solidity).
     *
     * @param height
     *            block height, if known, or -1 otherwise. If provided, used to
     *            validate the coinbase input script of v2 and above blocks.
     * @throws VerificationException
     *             if there was an error verifying the block.
     */
    public void checkTransactionSolidity(final long height) throws VerificationException {
        // The transactions must adhere to their block type rules
        if (blocktype == NetworkParameters.BLOCKTYPE_TRANSFER) {
            // TODO no coinbases
            for (int i = 1; i < transactions.size(); i++) {
                if (transactions.get(i).isCoinBase())
                    throw new VerificationException("TX " + i + " is coinbase when it should not be.");
            }
        } else if (blocktype == NetworkParameters.BLOCKTYPE_TOKEN_CREATION) {
            if (transactions.size() != 1)
                throw new VerificationException("Too many or too few transactions for token creation.");

            if (!transactions.get(0).isCoinBase())
                throw new VerificationException("TX is not coinbase when it should be.");

            // TODO token ids of tx must be equal to blocks token id
            // TODO token issuance sum must not overflow
            // TODO signature for coinbases must be correct (equal to pubkey
            // hash of tokenid)
        } else if (blocktype == NetworkParameters.BLOCKTYPE_REWARD) {
            if (transactions.size() != 1)
                throw new VerificationException("Too many or too few transactions for token creation.");

            if (!transactions.get(0).isCoinBase())
                throw new VerificationException("TX is not coinbase when it should be.");

            // Check that the tx has correct data (long fromHeight)
            try {
                if (transactions.get(0).getData() == null)
                    throw new VerificationException("Missing fromHeight");
                long u = Utils.readInt64(transactions.get(0).getData(), 0);
                if (u % NetworkParameters.REWARD_HEIGHT_INTERVAL != 0)
                    throw new VerificationException("Invalid fromHeight");
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new VerificationException(e);
            }
        }
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
     * Checks the block contents
     *
     * @param height
     *            block height, if known, or -1 otherwise. If valid, used to
     *            validate the coinbase input script of v2 and above blocks.
     * @param flags
     *            flags to indicate which tests should be applied (i.e. whether
     *            to test for height in the coinbase transaction).
     * @throws VerificationException
     *             if there was an error verifying the block.
     */
    @SuppressWarnings("unchecked")
    public void verifyTransactions(final long height, final EnumSet<VerifyFlag> flags) throws VerificationException {
        // Now we need to check that the body of the block actually matches the
        // headers. The network won't generate
        // an invalid block, but if we didn't validate this then an untrusted
        // man-in-the-middle could obtain the next
        // valid block from the network and simply replace the transactions in
        // it with their own fictional
        // transactions that reference spent or non-existant inputs.
        // if (transactions.isEmpty())
        // throw new VerificationException("Block had no transactions");
        if (this.getOptimalEncodingMessageSize() > MAX_BLOCK_SIZE)
            throw new VerificationException("Block larger than MAX_BLOCK_SIZE");
        checkMerkleRoot();
        checkSigOps();
        // genesis blocktype? check signature
        for (Transaction transaction : transactions)
            transaction.verify();
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
    public void verify(final int height, final EnumSet<VerifyFlag> flags) throws VerificationException {
        verifyHeader();
        verifyTransactions(height, flags);
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
     * Returns the hash of the previous block in the chain, as defined by the
     * block header.
     */
    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }

    void setPrevBlockHash(Sha256Hash prevBlockHash) {
        unCacheHeader();
        this.prevBlockHash = prevBlockHash;
        this.hash = null;
    }

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
    @Nullable
    public List<Transaction> getTransactions() {
        return transactions == null ? null : ImmutableList.copyOf(transactions);
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
        coinbase.setDataclassname(dataClassName.name());

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
            coinbase.setDataclassname(DataClassName.TOKEN.name());
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

            if (tokenInfo.getTokens() == null || tokenInfo.getTokens().getSignnumber() == 0) {
                coinbase.addOutput(new TransactionOutput(params, coinbase, value,
                        ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(pubKeyTo)).getProgram()));

            } else {
                long signnumber = tokenInfo.getTokens().getSignnumber();

                List<ECKey> keys = new ArrayList<ECKey>();
                for (MultiSignAddress multiSignAddress : tokenInfo.getMultiSignAddresses()) {
                    ECKey ecKey = ECKey.fromPublicOnly(Utils.HEX.decode(multiSignAddress.getPubKeyHex()));
                    keys.add(ecKey);
                }
                if (signnumber <= 1 && keys.size() <= 1) {
                    coinbase.addOutput(new TransactionOutput(params, coinbase, value,
                            ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(pubKeyTo)).getProgram()));
                } else {
                    Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript((int) signnumber, keys);
                    coinbase.addOutput(new TransactionOutput(params, coinbase, value, scriptPubKey.getProgram()));
                }
            }
        }
        transactions.add(coinbase);
        coinbase.setParent(this);
        coinbase.length = coinbase.unsafeBitcoinSerialize().length;
        adjustLength(transactions.size(), coinbase.length);
    }

    public static final byte[] EMPTY_BYTES = new byte[32];

    public boolean allowCoinbaseTransaction() {
        return blocktype == NetworkParameters.BLOCKTYPE_INITIAL
                || blocktype == NetworkParameters.BLOCKTYPE_TOKEN_CREATION
                || blocktype == NetworkParameters.BLOCKTYPE_REWARD;
    }

    /**
     * Returns a solved block that builds on top of this one. This exists for
     * unit tests. In this variant you can specify a public key (pubkey) for use
     * in generating coinbase blocks.
     * 
     * @param height
     *            block height, if known, or -1 otherwise.
     */

    public Block createNextBlock(@Nullable final Address to, final long version, @Nullable TransactionOutPoint prevOut,
            final long time, final byte[] pubKey, final Coin coinbaseValue, final int height,
            Sha256Hash prevBranchBlockHash, byte[] mineraddress) {
        Block b = new Block(params, version);
        // b.setDifficultyTarget(difficultyTarget);
        // only BLOCKTYPE_TOKEN_CREATION, BLOCKTYPE_REWARD, BLOCKTYPE_INITIAL
        b.addCoinbaseTransaction(pubKey, coinbaseValue);
        b.setMineraddress(mineraddress);
        if (to != null) {
            // Add a transaction paying 50 coins to the "to" address.
            Transaction t = new Transaction(params);
            t.addOutput(new TransactionOutput(params, t, coinbaseValue, to));
            // The input does not really need to be a valid signature, as long
            // as it has the right general form.
            TransactionInput input;
            if (prevOut == null) {
                input = new TransactionInput(params, t, Script.createInputScript(EMPTY_BYTES, EMPTY_BYTES));
                // Importantly the outpoint hash cannot be zero as that's how we
                // detect a coinbase transaction in isolation
                // but it must be unique to avoid 'different' transactions
                // looking the same.
                byte[] counter = new byte[32];
                counter[0] = (byte) txCounter;
                counter[1] = (byte) (txCounter++ >> 8);
                input.getOutpoint().setHash(Sha256Hash.wrap(counter));
            } else {
                input = new TransactionInput(params, t, Script.createInputScript(EMPTY_BYTES, EMPTY_BYTES));
            }
            t.addInput(input);
            b.addTransaction(t);
        }

        b.setPrevBlockHash(getHash());
        b.setPrevBranchBlockHash(prevBranchBlockHash);

        // Don't let timestamp go backwards
        if (getTimeSeconds() >= time)
            b.setTime(getTimeSeconds() + 1);
        else
            b.setTime(time);

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

    @VisibleForTesting
    boolean isHeaderBytesValid() {
        return headerBytesValid;
    }

    @VisibleForTesting
    boolean isTransactionBytesValid() {
        return transactionBytesValid;
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

    /**
     * Returns whether this block conforms to <a href=
     * "https://github.com/bitcoin/bips/blob/master/bip-0034.mediawiki">BIP34:
     * Height in Coinbase</a>.
     */
    public boolean isBIP34() {
        return version >= BLOCK_VERSION_BIP34;
    }

    /**
     * Returns whether this block conforms to <a href=
     * "https://github.com/bitcoin/bips/blob/master/bip-0066.mediawiki">BIP66:
     * Strict DER signatures</a>.
     */
    public boolean isBIP66() {
        return version >= BLOCK_VERSION_BIP66;
    }

    /**
     * Returns whether this block conforms to <a href=
     * "https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki">BIP65:
     * OP_CHECKLOCKTIMEVERIFY</a>.
     */
    public boolean isBIP65() {
        return version >= BLOCK_VERSION_BIP65;
    }

    public byte[] getMineraddress() {
        return mineraddress;
    }

    public void setMineraddress(byte[] mineraddress) {
        unCacheHeader();
        this.mineraddress = mineraddress;
        this.hash = null;
    }

    public long getBlocktype() {
        return blocktype;
    }

    public void setBlocktype(long blocktype) {
        unCacheHeader();
        this.blocktype = blocktype;
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

}
