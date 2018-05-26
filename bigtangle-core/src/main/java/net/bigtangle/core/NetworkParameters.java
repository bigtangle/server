/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.core;

import static net.bigtangle.core.Utils.HEX;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

import net.bigtangle.params.MainNetParams;
import net.bigtangle.params.RegTestParams;
import net.bigtangle.params.TestNet3Params;
import net.bigtangle.params.UnitTestParams;
import net.bigtangle.script.Script;
import net.bigtangle.utils.MonetaryFormat;
import net.bigtangle.utils.VersionTally;

/**
 * <p>
 * NetworkParameters contains the data needed for working with an instantiation
 * of a Bitcoin chain.
 * </p>
 *
 * <p>
 * This is an abstract class, concrete instantiations can be found in the params
 * package. There are four: one for the main network ({@link MainNetParams}),
 * one for the public test network, and two others that are intended for unit
 * testing and local app development purposes. Although this class contains some
 * aliases for them, you are encouraged to call the static get() methods on each
 * specific params class directly.
 * </p>
 */
public abstract class NetworkParameters {

    /**
     * The string returned by getId() for the main, production network where
     * people trade things.
     */
    public static final String ID_MAINNET = "net.bigtangle";
    /** The string returned by getId() for the testnet. */
    public static final String ID_TESTNET = "net.bigtangle.test";
    /** The string returned by getId() for regtest mode. */
    public static final String ID_REGTEST = "net.bigtangle.regtest";
    /** Unit test network. */
    public static final String ID_UNITTESTNET = "net.bigtangle.unittest";

    // Token id for System Coin  
    public static final String BIGNETCOIN_TOKENID_STRING = "0000000000000000000000000000000000000000";
    public static final byte[] BIGNETCOIN_TOKENID = HEX.decode(BIGNETCOIN_TOKENID_STRING);

    // DUMMY Token id byte[20]
    public static final byte[] DUMMY_TOKENID = HEX.decode("1111111111111111111111111111111111111111");

    // BLOCKTYPE
    public static final long BLOCKTYPE_INITIAL = 0; // Genesis Block for a
                                                    // token, only onetime
    public static final long BLOCKTYPE_TRANSFER = 1; // normal transfer of token
    public static final long BLOCKTYPE_TOKEN_CREATION= 3; // Genesis Block
                                                             // for a token,
                                                             // multiple times
                                                             // // value

    public static final long BLOCKTYPE_REWARD = 2; // Reward of mining

    // TODO: Seed nodes should be here as well.

    protected Block genesisBlock;
    protected BigInteger maxTarget;
    protected int port;
    protected long packetMagic; // Indicates message origin network and is used
                                // to seek to the next message when stream state
                                // is unknown.
    protected int addressHeader;
    protected int p2shHeader;
    protected int dumpedPrivateKeyHeader;
    protected int interval;
    protected int targetTimespan;
    protected byte[] alertSigningKey;
    protected int bip32HeaderPub;
    protected int bip32HeaderPriv;

    /** Used to check majorities for block version upgrade */
    protected int majorityEnforceBlockUpgrade;
    protected int majorityRejectBlockOutdated;
    protected int majorityWindow;

    /**
     * See getId(). This may be null for old deserialized wallets. In that case
     * we derive it heuristically by looking at the port number.
     */
    protected String id;

    /**
     * The depth of blocks required for a coinbase transaction to be spendable.
     */
    protected int spendableCoinbaseDepth;
    protected int subsidyDecreaseBlockCount;

    protected int[] acceptableAddressCodes;
    protected String[] dnsSeeds;
    protected int[] addrSeeds;

    protected Map<Long, Sha256Hash> checkpoints = new HashMap<Long, Sha256Hash>();
    protected transient MessageSerializer defaultSerializer = null;

    protected NetworkParameters() {

        genesisBlock = createGenesis(this);
    }

    public static Block createGenesis(NetworkParameters n) {
        Block genesisBlock = new Block(n, Block.BLOCK_VERSION_GENESIS, BLOCKTYPE_INITIAL);
        // TODO read first transaction from ICO file
        // Transaction t = new Transaction(n);
        // try {
        // // A script containing the difficulty bits and the following
        // // message:
        // //
        // // "The Times 03/Jan/2009 Chancellor on brink of second bailout for
        // // banks"
        // byte[] bytes = Utils.HEX.decode(
        // "04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73");
        // t.addInput(new TransactionInput(n, t, bytes));
        // ByteArrayOutputStream scriptPubKeyBytes = new
        // ByteArrayOutputStream();
        // Script.writeBytes(scriptPubKeyBytes, Utils.HEX.decode(
        // "04678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5f"));
        // scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
        // t.addOutput(new TransactionOutput(n, t, FIFTY_COINS,
        // scriptPubKeyBytes.toByteArray()));
        // } catch (Exception e) {
        // // Cannot happen.
        // throw new RuntimeException(e);
        // }
        // genesisBlock.addTransaction(t);
        return genesisBlock;
    }

    public static final int TARGET_TIMESPAN = 14 * 24 * 60 * 60; // 2 weeks per
                                                                 // difficulty
                                                                 // cycle, on
                                                                 // average.
    public static final int TARGET_SPACING = 10 * 60; // 10 minutes per block.
    public static final int INTERVAL = TARGET_TIMESPAN / TARGET_SPACING;

    /**
     * Blocks with a timestamp after this should enforce BIP 16, aka "Pay to
     * script hash". This BIP changed the network rules in a soft-forking
     * manner, that is, blocks that don't follow the rules are accepted but not
     * mined upon and thus will be quickly re-orged out as long as the majority
     * are enforcing the rule.
     */
    public static final int BIP16_ENFORCE_TIME = 1333238400;

    public static final int MILESTONE_UPPER_THRESHOLD = 75;
    public static final int MILESTONE_LOWER_THRESHOLD = 66;
    public static final int MAX_RATING_TIP_COUNT = 100;

    public static final long ENTRYPOINT_RATING_LOWER_DEPTH_CUTOFF = 0;
    public static final long ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF = 60;

    public static final long ENTRYPOINT_TIPSELECTION_DEPTH_CUTOFF = 20;
    
    public static final int REWARD_HEIGHT_INTERVAL = 100;

    /**
     * The maximum number of coins to be generated
     */
    // public static final long MAX_COINS = 21000000;

    /**
     * The maximum money to be generated
     */

    /**
     * A Java package style string acting as unique ID for these parameters
     */
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return getId().equals(((NetworkParameters) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    /**
     * Returns the network parameters for the given string ID or NULL if not
     * recognized.
     */
    @Nullable
    public static NetworkParameters fromID(String id) {
        if (id.equals(ID_MAINNET)) {
            return MainNetParams.get();
        } else if (id.equals(ID_TESTNET)) {
            return TestNet3Params.get();
        } else if (id.equals(ID_UNITTESTNET)) {
            return UnitTestParams.get();
        } else if (id.equals(ID_REGTEST)) {
            return RegTestParams.get();
        } else {
            return null;
        }
    }

    public int getSpendableCoinbaseDepth() {
        return spendableCoinbaseDepth;
    }

    /**
     * Throws an exception if the block's difficulty is not correct.
     *
     * @throws VerificationException
     *             if the block's difficulty is not correct.
     */
    // public abstract void checkDifficultyTransitions(StoredBlock storedPrev,
    // Block next, final BlockStore blockStore)
    // throws VerificationException, BlockStoreException;

    /**
     * Returns true if the block height is either not a checkpoint, or is a
     * checkpoint and the hash matches.
     */
    public boolean passesCheckpoint(long height, Sha256Hash hash) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash == null || checkpointHash.equals(hash);
    }

    /**
     * Returns true if the given height has a recorded checkpoint.
     */
    public boolean isCheckpoint(long height) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash != null;
    }

    public int getSubsidyDecreaseBlockCount() {
        return subsidyDecreaseBlockCount;
    }

    /**
     * Returns DNS names that when resolved, give IP addresses of active peers.
     */
    public String[] getDnsSeeds() {
        return dnsSeeds;
    }

    /** Returns IP address of active peers. */
    public int[] getAddrSeeds() {
        return addrSeeds;
    }

    /**
     * <p>
     * Genesis block for this chain.
     * </p>
     *
     * <p>
     * The first block in every chain is a well known constant shared between
     * all Bitcoin implemenetations. For a block to be valid, it must be
     * eventually possible to work backwards to the genesis block by following
     * the prevBlockHash pointers in the block headers.
     * </p>
     *
     * <p>
     * The genesis blocks for both test and main networks contain the timestamp
     * of when they were created, and a message in the coinbase transaction. It
     * says, <i>"The Times 03/Jan/2009 Chancellor on brink of second bailout for
     * banks"</i>.
     * </p>
     */
    public Block getGenesisBlock() {
        return genesisBlock;
    }

    /** Default TCP port on which to connect to nodes. */
    public int getPort() {
        return port;
    }

    /** The header bytes that identify the start of a packet on this network. */
    public long getPacketMagic() {
        return packetMagic;
    }

    /**
     * First byte of a base58 encoded address. See
     * {@link net.bigtangle.core.Address}. This is the same as
     * acceptableAddressCodes[0] and is the one used for "normal" addresses.
     * Other types of address may be encountered with version codes found in the
     * acceptableAddressCodes array.
     */
    public int getAddressHeader() {
        return addressHeader;
    }

    /**
     * First byte of a base58 encoded P2SH address. P2SH addresses are defined
     * as part of BIP0013.
     */
    public int getP2SHHeader() {
        return p2shHeader;
    }

    /**
     * First byte of a base58 encoded dumped private key. See
     * {@link net.bigtangle.core.DumpedPrivateKey}.
     */
    public int getDumpedPrivateKeyHeader() {
        return dumpedPrivateKeyHeader;
    }

    /**
     * How much time in seconds is supposed to pass between "interval" blocks.
     * If the actual elapsed time is significantly different from this value,
     * the network difficulty formula will produce a different value. Both test
     * and main Bitcoin networks use 2 weeks (1209600 seconds).
     */
    public int getTargetTimespan() {
        return targetTimespan;
    }

    /**
     * The version codes that prefix addresses which are acceptable on this
     * network. Although Satoshi intended these to be used for "versioning", in
     * fact they are today used to discriminate what kind of data is contained
     * in the address and to prevent accidentally sending coins across chains
     * which would destroy them.
     */
    public int[] getAcceptableAddressCodes() {
        return acceptableAddressCodes;
    }

    /**
     * If we are running in testnet-in-a-box mode, we allow connections to nodes
     * with 0 non-genesis blocks.
     */
    public boolean allowEmptyPeerChain() {
        return true;
    }

    /**
     * How many blocks pass between difficulty adjustment periods. Bitcoin
     * standardises this to be 2015.
     */
    public int getInterval() {
        return interval;
    }

    /** Maximum target represents the easiest allowable proof of work. */
    public BigInteger getMaxTarget() {
        return maxTarget;
    }

    /**
     * The key used to sign {@link net.bigtangle.core.AlertMessage}s. You can
     * use {@link net.bigtangle.core.ECKey#verify(byte[], byte[], byte[])} to
     * verify signatures using it.
     */
    public byte[] getAlertSigningKey() {
        return alertSigningKey;
    }

    /** Returns the 4 byte header for BIP32 (HD) wallet - public key part. */
    public int getBip32HeaderPub() {
        return bip32HeaderPub;
    }

    /** Returns the 4 byte header for BIP32 (HD) wallet - private key part. */
    public int getBip32HeaderPriv() {
        return bip32HeaderPriv;
    }

    /**
     * Returns the number of coins that will be produced in total, on this
     * network. Where not applicable, a very large number of coins is returned
     * instead (i.e. the main coin issue for Dogecoin).
     */
    // public abstract Coin getMaxMoney();

    /**
     * Any standard (ie pay-to-address) output smaller than this value will most
     * likely be rejected by the network.
     */
    public abstract Coin getMinNonDustOutput();

    /**
     * The monetary object for this currency.
     */
    public abstract MonetaryFormat getMonetaryFormat();

    /**
     * Scheme part for URIs, for example "bitcoin".
     */
    public abstract String getUriScheme();

    /**
     * Returns whether this network has a maximum number of coins (finite
     * supply) or not. Always returns true for Bitcoin, but exists to be
     * overriden for other networks.
     */
    public abstract boolean hasMaxMoney();

    /**
     * Return the default serializer for this network. This is a shared
     * serializer.
     * 
     * @return
     */
    public final MessageSerializer getDefaultSerializer() {
        // Construct a default serializer if we don't have one
        if (null == this.defaultSerializer) {
            // Don't grab a lock unless we absolutely need it
            synchronized (this) {
                // Now we have a lock, double check there's still no serializer
                // and create one if so.
                if (null == this.defaultSerializer) {
                    // As the serializers are intended to be immutable, creating
                    // two due to a race condition should not be a problem,
                    // however
                    // to be safe we ensure only one exists for each network.
                    this.defaultSerializer = getSerializer(false);
                }
            }
        }
        return defaultSerializer;
    }

    /**
     * Construct and return a custom serializer.
     */
    public abstract BitcoinSerializer getSerializer(boolean parseRetain);

    /**
     * The number of blocks in the last {@link getMajorityWindow()} blocks at
     * which to trigger a notice to the user to upgrade their client, where the
     * client does not understand those blocks.
     */
    public int getMajorityEnforceBlockUpgrade() {
        return majorityEnforceBlockUpgrade;
    }

    /**
     * The number of blocks in the last {@link getMajorityWindow()} blocks at
     * which to enforce the requirement that all new blocks are of the newer
     * type (i.e. outdated blocks are rejected).
     */
    public int getMajorityRejectBlockOutdated() {
        return majorityRejectBlockOutdated;
    }

    /**
     * The sampling window from which the version numbers of blocks are taken in
     * order to determine if a new block version is now the majority.
     */
    public int getMajorityWindow() {
        return majorityWindow;
    }

    /**
     * The flags indicating which block validation tests should be applied to
     * the given block. Enables support for alternative blockchains which enable
     * tests based on different criteria.
     * 
     * @param block
     *            block to determine flags for.
     * @param height
     *            height of the block, if known, null otherwise. Returned tests
     *            should be a safe subset if block height is unknown.
     */
    public EnumSet<Block.VerifyFlag> getBlockVerificationFlags(final Block block, final VersionTally tally,
            final long height) {
        final EnumSet<Block.VerifyFlag> flags = EnumSet.noneOf(Block.VerifyFlag.class);

        if (block.isBIP34()) {
            final Integer count = tally.getCountAtOrAbove(Block.BLOCK_VERSION_BIP34);
            if (null != count && count >= getMajorityEnforceBlockUpgrade()) {
                flags.add(Block.VerifyFlag.HEIGHT_IN_COINBASE);
            }
        }
        return flags;
    }

    /**
     * The flags indicating which script validation tests should be applied to
     * the given transaction. Enables support for alternative blockchains which
     * enable tests based on different criteria.
     *
     * @param block
     *            block the transaction belongs to.
     * @param transaction
     *            to determine flags for.
     * @param height
     *            height of the block, if known, null otherwise. Returned tests
     *            should be a safe subset if block height is unknown.
     */
    public EnumSet<Script.VerifyFlag> getTransactionVerificationFlags(final Block block, final Transaction transaction,
            final VersionTally tally) {
        final EnumSet<Script.VerifyFlag> verifyFlags = EnumSet.noneOf(Script.VerifyFlag.class);
        if (block.getTimeSeconds() >= NetworkParameters.BIP16_ENFORCE_TIME)
            verifyFlags.add(Script.VerifyFlag.P2SH);

        // Start enforcing CHECKLOCKTIMEVERIFY, (BIP65) for block.nVersion=4
        // blocks, when 75% of the network has upgraded:
        if (block.getVersion() >= Block.BLOCK_VERSION_BIP65
                && tally.getCountAtOrAbove(Block.BLOCK_VERSION_BIP65) > this.getMajorityEnforceBlockUpgrade()) {
            verifyFlags.add(Script.VerifyFlag.CHECKLOCKTIMEVERIFY);
        }

        return verifyFlags;
    }

    public abstract int getProtocolVersionNum(final ProtocolVersion version);

    public static enum ProtocolVersion {
        MINIMUM(70000), PONG(60001), BLOOM_FILTER(70000), CURRENT(70001);

        private final int bitcoinProtocol;

        ProtocolVersion(final int bitcoinProtocol) {
            this.bitcoinProtocol = bitcoinProtocol;
        }

        public int getBitcoinProtocolVersion() {
            return bitcoinProtocol;
        }
    }

    public long getRewardHeightInterval() {
        return REWARD_HEIGHT_INTERVAL;
    }
}
