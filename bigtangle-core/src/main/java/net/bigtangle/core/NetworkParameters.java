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

import static net.bigtangle.core.Utils.HEX;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.math.LongMath;

import net.bigtangle.equihash.EquihashProof;
import net.bigtangle.params.MainNetParams;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.utils.MonetaryFormat;

/**
 * <p>
 * NetworkParameters contains the data needed for working with an instantiation
 * of a BigTangle.
 * </p>
 *
 * <p>
 * This is an abstract class, concrete instantiations can be found in the params
 * package. There are two: one for the main network ({@link MainNetParams}), one
 * for the public test network. Although this class contains some aliases for
 * them, you are encouraged to call the static get() methods on each specific
 * params class directly.
 * </p>
 */
public abstract class NetworkParameters {

    // TODO Mainnet release

    /**
     * The string returned by getId() for the main, production network where
     * people trade things.
     */
    public static final String ID_MAINNET = "net.bigtangle";

    /** Unit test network. */
    public static final String ID_UNITTESTNET = "net.bigtangle.unittest";

    protected Block genesisBlock;
    protected BigInteger maxTarget;

    protected long packetMagic; // Indicates message origin network and is used
                                // to seek to the next message when stream state
                                // is unknown.
    protected int addressHeader;
    protected int p2shHeader;
    protected int dumpedPrivateKeyHeader;
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

    // Consensus settings
    public static final int MILESTONE_UPPER_THRESHOLD = 70;
    public static final int MILESTONE_LOWER_THRESHOLD = 67;
    public static final int NUMBER_RATING_TIPS = 100;
    public static final long ENTRYPOINT_RATING_UPPER_DEPTH_CUTOFF = 60;
    public static final long ENTRYPOINT_TIPSELECTION_DEPTH_CUTOFF = 20;

    // Token ID for System Coin
    public static final String BIGTANGLE_TOKENID_STRING = "bc";
    public static final byte[] BIGTANGLE_TOKENID = HEX.decode(BIGTANGLE_TOKENID_STRING);
    public static final String BIGTANGLE_TOKENNAME = "BIG";
    public static final int BIGTANGLE_DECIMAL = 6;
    // Use Equihash
    public static final boolean USE_EQUIHASH = false;
    protected int equihashN;
    protected int equihashK;

    /**
     * The version number at the start of the network.
     */
    public static final long BLOCK_VERSION_GENESIS = 1;

    /**
     * A value for difficultyTarget (nBits) that allows half of all possible
     * hash solutions. Used in unit testing.
     */
    public static final long EASIEST_DIFFICULTY_TARGET = 0x207fFFFFL;

    /**
     * A constant shared by the entire network: how large in bytes a block is
     * allowed to be. One day we may have to upgrade everyone to change this, so
     * Bitcoin can continue to grow. For now it exists as an anti-DoS measure to
     * avoid somebody creating a titanically huge but valid block and forcing
     * everyone to download/store it forever.
     */
    public static final int MAX_DEFAULT_BLOCK_SIZE = 300000;

    /**
     * A "sigop" is a signature verification operation. Because they're
     * expensive we also impose a separate limit on the number in a block to
     * prevent somebody mining a huge block that has way more sigops than
     * normal, so is very expensive/slow to verify.
     */
    public static final int MAX_BLOCK_SIGOPS = MAX_DEFAULT_BLOCK_SIZE / 50;

    /**
     * The maximum allowed time drift of blocks into the future in seconds.
     */
    public static final long ALLOWED_TIME_DRIFT = 5 * 60;

    
    /**
     * The maximum returned search blocks to the request.
     */
    public static final long ALLOWED_SEARCH_BLOCKS = 10000;

    
    /**
     * How many bytes are required to represent a block header WITHOUT the
     * trailing 00 length byte.
     */
    public static final int HEADER_SIZE = 88 // bitcoin
            + 32 // additional branch prev block
            + 2 * 4 // time and difftarget from int to long
            + 8 // sequence (lastMiningReward) long
            + 20 // miner address
            + 4 // blockType
            + 8 // heigth
            + (USE_EQUIHASH ? EquihashProof.BYTE_LENGTH : 0); // for Equihash

    // Transaction setting
    public static final int MAX_TRANSACTION_MEMO_SIZE = MAX_DEFAULT_BLOCK_SIZE / 5;

    // Reward and Difficulty Synchronization
    public static final long REWARD_INITIAL_TX_REWARD = 10L;
    public static final long REWARD_MIN_HEIGHT_DIFFERENCE = 2;
    public static final int REWARD_MIN_HEIGHT_INTERVAL = 10;
    public static final long REWARD_MIN_MILESTONE_PERCENTAGE = 97;
    public static final BigInteger MAX_TARGET = Utils.decodeCompactBits(0x207fFFFFL);

    public static final int TARGET_MAX_TPS = 100;
    public static final long REWARD_OVERRULE_TIME_MS = 1000;

    // Order Matching Settings
    public static final long ORDER_MATCHING_MIN_HEIGHT_INTERVAL = 10;
    public static final long ORDER_MATCHING_OVERLAP_SIZE = 7;
    public static final long ORDER_MATCHING_MIN_MILESTONE_PERCENTAGE = 97;

    // Token config
    public static final long TOKEN_MAX_ISSUANCE_NUMBER = Integer.MAX_VALUE;
    public static final int TOKEN_MAX_NAME_LENGTH = 60;
    public static final int TOKEN_MAX_DESC_LENGTH = 500;
    public static final int TOKEN_MAX_URL_LENGTH = 100;
    public static final int TOKEN_MAX_ID_LENGTH = 100;
    public static final int TOKEN_MAX_LANGUAGE_LENGTH = 2;
    public static final int TOKEN_MAX_CLASSIFICATION_LENGTH = 100;
    // max time of an order in seconds
    public static final long ORDER_TIMEOUT_MAX = 8 * 60 * 60;

    public static String testPub = "02721b5eb0282e4bc86aab3380e2bba31d935cba386741c15447973432c61bc975";
    public static String testPriv = "ec1d240521f7f254c52aea69fca3f28d754d1b89f310f42b0fb094d16814317f";
    
    public static String genesisPub =testPub;
    
    // 100 billions  as Value
    public static long BigtangleCoinTotal = LongMath.pow(10, 11+BIGTANGLE_DECIMAL);
    public static final long TARGET_YEARLY_MINING_PAYOUT = BigtangleCoinTotal / 1000;

    protected NetworkParameters() {
    }

    public static Block createGenesis(NetworkParameters params) {
        Block genesisBlock = new Block(params, NetworkParameters.BLOCK_VERSION_GENESIS,
                Block.Type.BLOCKTYPE_INITIAL.ordinal());
        genesisBlock.setTime(1532896109L);

        // 1 in 4 blocks shall be correct
        BigInteger diff = Utils.decodeCompactBits(NetworkParameters.EASIEST_DIFFICULTY_TARGET);
        genesisBlock.setDifficultyTarget(Utils.encodeCompactBits(diff.divide(BigInteger.valueOf(2))));

        Transaction coinbase = new Transaction(params);
        final ScriptBuilder inputBuilder = new ScriptBuilder();
        coinbase.addInput(new TransactionInput(params, coinbase, inputBuilder.build().getProgram()));

        RewardInfo rewardInfo = new RewardInfo(-1l, 0l, Sha256Hash.ZERO_HASH);

        coinbase.setData(rewardInfo.toByteArray());
        add(params, BigtangleCoinTotal  ,  genesisPub, coinbase);
        genesisBlock.addTransaction(coinbase);
        genesisBlock.setNonce(0);
        genesisBlock.setHeigth(0);
        // genesisBlock.solve();

        return genesisBlock;

    }

    public static void add(NetworkParameters params, long amount, String account, Transaction coinbase) {
        //  amount, many public keys
        String[] list = account.split(",");
        Coin base = Coin.valueOf(amount, BIGTANGLE_TOKENID);
        List<ECKey> keys = new ArrayList<ECKey>();
        for (int i = 0; i < list.length; i++) {
            keys.add(ECKey.fromPublicOnly(Utils.HEX.decode(list[i].trim())));
        }
        if (keys.size() <= 1) {
            coinbase.addOutput(new TransactionOutput(params, coinbase, base,
                    ScriptBuilder.createOutputScript(ECKey.fromPublicOnly(keys.get(0).getPubKey())).getProgram()));
        } else {
            Script scriptPubKey = ScriptBuilder.createMultiSigOutputScript((int) keys.size() - 1, keys);
            coinbase.addOutput(new TransactionOutput(params, coinbase, base, scriptPubKey.getProgram()));
        }
    }

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
        } else if (id.equals(ID_UNITTESTNET)) {
            return MainNetParams.get();
        } else {
            return null;
        }
    }

    public int getSpendableCoinbaseDepth() {
        return spendableCoinbaseDepth;
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
     */
    public Block getGenesisBlock() {
        return genesisBlock;
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
    public EnumSet<Script.VerifyFlag> getTransactionVerificationFlags(final Block block,
            final Transaction transaction) {
        final EnumSet<Script.VerifyFlag> verifyFlags = EnumSet.noneOf(Script.VerifyFlag.class);
        // if (block.getTimeSeconds() >= NetworkParameters.BIP16_ENFORCE_TIME)
        verifyFlags.add(Script.VerifyFlag.P2SH);

        // Start enforcing CHECKLOCKTIMEVERIFY, (BIP65) for block.nVersion=4
        // blocks, when 75% of the network has upgraded:

        verifyFlags.add(Script.VerifyFlag.CHECKLOCKTIMEVERIFY);

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
}
