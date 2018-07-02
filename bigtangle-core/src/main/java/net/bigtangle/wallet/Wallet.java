/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.wallet;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;

import net.bigtangle.core.Address;
import net.bigtangle.core.Coin;
import net.bigtangle.core.Context;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.FilteredBlock;
import net.bigtangle.core.InsufficientMoneyException;
import net.bigtangle.core.Json;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.ScriptException;
import net.bigtangle.core.Sha256Hash;
import net.bigtangle.core.Transaction;
import net.bigtangle.core.TransactionBag;
import net.bigtangle.core.TransactionConfidence;
import net.bigtangle.core.TransactionConfidence.ConfidenceType;
import net.bigtangle.core.TransactionInput;
import net.bigtangle.core.TransactionOutPoint;
import net.bigtangle.core.TransactionOutput;
import net.bigtangle.core.UTXO;
import net.bigtangle.core.UTXOProvider;
import net.bigtangle.core.UTXOProviderException;
import net.bigtangle.core.Utils;
import net.bigtangle.core.VarInt;
import net.bigtangle.core.VerificationException;
import net.bigtangle.crypto.ChildNumber;
import net.bigtangle.crypto.DeterministicHierarchy;
import net.bigtangle.crypto.DeterministicKey;
import net.bigtangle.crypto.KeyCrypter;
import net.bigtangle.crypto.KeyCrypterException;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.script.Script;
import net.bigtangle.script.ScriptBuilder;
import net.bigtangle.signers.LocalTransactionSigner;
import net.bigtangle.signers.MissingSigResolutionSigner;
import net.bigtangle.signers.TransactionSigner;
import net.bigtangle.utils.BaseTaggableObject;
import net.bigtangle.utils.ListenerRegistration;
import net.bigtangle.utils.MapToBeanMapperUtil;
import net.bigtangle.utils.OkHttp3Util;
import net.bigtangle.utils.Threading;
import net.bigtangle.wallet.Protos.Wallet.EncryptionType;
import net.bigtangle.wallet.Wallet.BalanceType;
import net.bigtangle.wallet.WalletTransaction.Pool;
import net.bigtangle.wallet.listeners.KeyChainEventListener;
import net.bigtangle.wallet.listeners.ScriptsChangeEventListener;
import net.bigtangle.wallet.listeners.WalletChangeEventListener;
import net.bigtangle.wallet.listeners.WalletCoinsReceivedEventListener;
import net.bigtangle.wallet.listeners.WalletCoinsSentEventListener;
import net.bigtangle.wallet.listeners.WalletEventListener;
import net.bigtangle.wallet.listeners.WalletReorganizeEventListener;
import net.jcip.annotations.GuardedBy;

// To do list:
//
// - Take all wallet-relevant data out of Transaction and put it into WalletTransaction. Make Transaction immutable.
// - Only store relevant transaction outputs, don't bother storing the rest of the data. Big RAM saving.
// - Split block chain and tx output tracking into a superclass that doesn't have any key or spending related code.
// - Simplify how transactions are tracked and stored: in particular, have the wallet maintain positioning information
//   for transactions independent of the transactions themselves, so the timeline can be walked without having to
//   process and sort every single transaction.
// - Split data persistence out into a backend class and make the wallet transactional, so we can store a wallet
//   in a database not just in RAM.
// - Make clearing of transactions able to only rewind the wallet a certain distance instead of all blocks.
// - Make it scale:
//     - eliminate all the algorithms with quadratic complexity (or worse)
//     - don't require everything to be held in RAM at once
//     - consider allowing eviction of no longer re-orgable transactions or keys that were used up
//
// Finally, find more ways to break the class up and decompose it. Currently every time we move code out, other code
// fills up the lines saved!

/**
 * <p>
 * A Wallet stores keys and a record of transactions that send and receive value
 * from those keys. Using these, it is able to create new transactions that
 * spend the recorded transactions, and this is the fundamental operation of the
 * Bitcoin protocol.
 * </p>
 *
 * <p>
 * To learn more about this class, read
 * <b><a href="https://bitcoinj.github.io/working-with-the-wallet"> working with
 * the wallet.</a></b>
 * </p>
 *
 * <p>
 * To fill up a Wallet with transactions, you need to use it in combination with
 * a {@link BlockGraph} and various other objects, see the
 * <a href="https://bitcoinj.github.io/getting-started">Getting started</a>
 * tutorial on the website to learn more about how to set everything up.
 * </p>
 *
 * <p>
 * Wallets can be serialized using protocol buffers. You need to save the wallet
 * whenever it changes, there is an auto-save feature that simplifies this for
 * you although you're still responsible for manually triggering a save when
 * your app is about to quit because the auto-save feature waits a moment before
 * actually committing to disk to avoid IO thrashing when the wallet is changing
 * very fast (eg due to a block chain sync). See
 * {@link Wallet#autosaveToFile(java.io.File, long, java.util.concurrent.TimeUnit, net.bigtangle.wallet.WalletFiles.Listener)}
 * for more information about this.
 * </p>
 */
@SuppressWarnings("deprecation")
public class Wallet extends BaseTaggableObject implements KeyBag, TransactionBag {

    private static final Logger log = LoggerFactory.getLogger(Wallet.class);

    private static final int MINIMUM_BLOOM_DATA_LENGTH = 8;

    // Ordering: lock > keyChainGroupLock. KeyChainGroup is protected separately
    // to allow fast querying of current receive address
    // even if the wallet itself is busy e.g. saving or processing a big reorg.
    // Useful for reducing UI latency.
    protected final ReentrantLock lock = Threading.lock("wallet");
    protected final ReentrantLock keyChainGroupLock = Threading.lock("wallet-keychaingroup");

    // The various pools below give quick access to wallet-relevant transactions
    // by the state they're in:
    //
    // Pending: Transactions that didn't make it into the best chain yet.
    // Pending transactions can be killed if a
    // double spend against them appears in the best chain, in which case they
    // move to the dead pool.
    // If a double spend appears in the pending state as well, we update the
    // confidence type
    // of all txns in conflict to IN_CONFLICT and wait for the miners to resolve
    // the race.
    // Unspent: Transactions that appeared in the best chain and have outputs we
    // can spend. Note that we store the
    // entire transaction in memory even though for spending purposes we only
    // really need the outputs, the
    // reason being that this simplifies handling of re-orgs. It would be worth
    // fixing this in future.
    // Spent: Transactions that appeared in the best chain but don't have any
    // spendable outputs. They're stored here
    // for history browsing/auditing reasons only and in future will probably be
    // flushed out to some other
    // kind of cold storage or just removed.
    // Dead: Transactions that we believe will never confirm get moved here, out
    // of pending. Note that Bitcoin
    // Core has no notion of dead-ness: the assumption is that double spends
    // won't happen so there's no
    // need to notify the user about them. We take a more pessimistic approach
    // and try to track the fact that
    // transactions have been double spent so applications can do something
    // intelligent (cancel orders, show
    // to the user in the UI, etc). A transaction can leave dead and move into
    // spent/unspent if there is a
    // re-org to a chain that doesn't include the double spend.

    private final Map<Sha256Hash, Transaction> pending;
    private final Map<Sha256Hash, Transaction> unspent;
    private final Map<Sha256Hash, Transaction> spent;
    private final Map<Sha256Hash, Transaction> dead;

    // server url
    protected String serverurl;
    // All transactions together.
    protected final Map<Sha256Hash, Transaction> transactions;

    // All the TransactionOutput objects that we could spend (ignoring whether
    // we have the private key or not).
    // Used to speed up various calculations.
    protected final HashSet<TransactionOutput> myUnspents = Sets.newHashSet();

    // Transactions that were dropped by the risk analysis system. These are not
    // in any pools and not serialized
    // to disk. We have to keep them around because if we ignore a tx because we
    // think it will never confirm, but
    // then it actually does confirm and does so within the same network
    // session, remote peers will not resend us
    // the tx data along with the Bloom filtered block, as they know we already
    // received it once before
    // (so it would be wasteful to repeat). Thus we keep them around here for a
    // while. If we drop our network
    // connections then the remote peers will forget that we were sent the tx
    // data previously and send it again
    // when relaying a filtered merkleblock.
    private final LinkedHashMap<Sha256Hash, Transaction> riskDropped = new LinkedHashMap<Sha256Hash, Transaction>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, Transaction> eldest) {
            return size() > 1000;
        }
    };

    // The key chain group is not thread safe, and generally the whole hierarchy
    // of objects should not be mutated
    // outside the wallet lock. So don't expose this object directly via any
    // accessors!
    @GuardedBy("keyChainGroupLock")
    private KeyChainGroup keyChainGroup;

    // A list of scripts watched by this wallet.
    @GuardedBy("keyChainGroupLock")
    private Set<Script> watchedScripts;

    protected final Context context;
    protected final NetworkParameters params;

    @Nullable
    private Sha256Hash lastBlockSeenHash;
    private int lastBlockSeenHeight;
    private long lastBlockSeenTimeSecs;

    private final CopyOnWriteArrayList<ListenerRegistration<WalletChangeEventListener>> changeListeners = new CopyOnWriteArrayList<ListenerRegistration<WalletChangeEventListener>>();
    private final CopyOnWriteArrayList<ListenerRegistration<WalletCoinsReceivedEventListener>> coinsReceivedListeners = new CopyOnWriteArrayList<ListenerRegistration<WalletCoinsReceivedEventListener>>();
    private final CopyOnWriteArrayList<ListenerRegistration<WalletCoinsSentEventListener>> coinsSentListeners = new CopyOnWriteArrayList<ListenerRegistration<WalletCoinsSentEventListener>>();
    private final CopyOnWriteArrayList<ListenerRegistration<WalletReorganizeEventListener>> reorganizeListeners = new CopyOnWriteArrayList<ListenerRegistration<WalletReorganizeEventListener>>();
    private final CopyOnWriteArrayList<ListenerRegistration<ScriptsChangeEventListener>> scriptChangeListeners = new CopyOnWriteArrayList<ListenerRegistration<ScriptsChangeEventListener>>();

    // A listener that relays confidence changes from the transaction confidence
    // object to the wallet event listener,
    // as a convenience to API users so they don't have to register on every
    // transaction themselves.
    private TransactionConfidence.Listener txConfidenceListener;

    // If a TX hash appears in this set then notifyNewBestBlock will ignore it,
    // as its confidence was already set up
    // in receive() via Transaction.setBlockAppearance(). As the BlockChain
    // always calls notifyNewBestBlock even if
    // it sent transactions to the wallet, without this we'd double count.
    private HashSet<Sha256Hash> ignoreNextNewBlock;
    // Whether or not to ignore pending transactions that are considered risky
    // by the configured risk analyzer.
    private boolean acceptRiskyTransactions;
    // Object that performs risk analysis of pending transactions. We might
    // reject transactions that seem like
    // a high risk of being a double spending attack.
    private RiskAnalysis.Analyzer riskAnalyzer = DefaultRiskAnalysis.FACTORY;

    // Stuff for notifying transaction objects that we changed their
    // confidences. The purpose of this is to avoid
    // spuriously sending lots of repeated notifications to listeners that API
    // users aren't really interested in as a
    // side effect of how the code is written (e.g. during re-orgs confidence
    // data gets adjusted multiple times).
    private int onWalletChangedSuppressions;
    private boolean insideReorg;
    private Map<Transaction, TransactionConfidence.Listener.ChangeReason> confidenceChanged;
    protected volatile WalletFiles vFileManager;
    // Object that is used to send transactions asynchronously when the wallet
    // requires it.

    // UNIX time in seconds. Money controlled by keys created before this time
    // will be automatically respent to a key
    // that was created after it. Useful when you believe some keys have been
    // compromised.
    private volatile long vKeyRotationTimestamp;

    protected CoinSelector coinSelector = new DefaultCoinSelector();

    // The wallet version. This is an int that can be used to track breaking
    // changes in the wallet format.
    // You can also use it to detect wallets that come from the future (ie they
    // contain features you
    // do not know how to deal with).
    private int version;
    // User-provided description that may help people keep track of what a
    // wallet is for.
    private String description;
    // Stores objects that know how to serialize/unserialize themselves to byte
    // streams and whether they're mandatory
    // or not. The string key comes from the extension itself.
    private final HashMap<String, WalletExtension> extensions;

    // Objects that perform transaction signing. Applied subsequently one after
    // another
    @GuardedBy("lock")
    private List<TransactionSigner> signers;

    // If this is set then the wallet selects spendable candidate outputs from a
    // UTXO provider.
    @Nullable
    private volatile UTXOProvider vUTXOProvider;

    /**
     * Creates a new, empty wallet with a randomly chosen seed and no
     * transactions. Make sure to provide for sufficient backup! Any keys will
     * be derived from the seed. If you want to restore a wallet from disk
     * instead, see {@link #loadFromFile}.
     */
    public Wallet(NetworkParameters params) {
        this(Context.getOrCreate(params));
    }

    public Wallet(NetworkParameters params, String url) {
        this(Context.getOrCreate(params), url);
    }

    /**
     * Creates a new, empty wallet with a randomly chosen seed and no
     * transactions. Make sure to provide for sufficient backup! Any keys will
     * be derived from the seed. If you want to restore a wallet from disk
     * instead, see {@link #loadFromFile}.
     */
    // TODO remove this
    public Wallet(Context context) {
        this(context, new KeyChainGroup(context.getParams()), null);
    }

    public Wallet(Context context, String url) {
        this(context, new KeyChainGroup(context.getParams()), url);
    }

    public static Wallet fromSeed(NetworkParameters params, DeterministicSeed seed) {
        return new Wallet(params, new KeyChainGroup(params, seed));
    }

    /**
     * Creates a wallet that tracks payments to and from the HD key hierarchy
     * rooted by the given watching key. A watching key corresponds to account
     * zero in the recommended BIP32 key hierarchy.
     */
    public static Wallet fromWatchingKey(NetworkParameters params, DeterministicKey watchKey) {
        return new Wallet(params, new KeyChainGroup(params, watchKey));
    }

    /**
     * Creates a wallet that tracks payments to and from the HD key hierarchy
     * rooted by the given watching key. A watching key corresponds to account
     * zero in the recommended BIP32 key hierarchy. The key is specified in
     * base58 notation and the creation time of the key. If you don't know the
     * creation time, you can pass
     * {@link DeterministicHierarchy#BIP32_STANDARDISATION_TIME_SECS}.
     */
    public static Wallet fromWatchingKeyB58(NetworkParameters params, String watchKeyB58, long creationTimeSeconds) {
        final DeterministicKey watchKey = DeterministicKey.deserializeB58(null, watchKeyB58, params);
        watchKey.setCreationTimeSeconds(creationTimeSeconds);
        return fromWatchingKey(params, watchKey);
    }

    /**
     * Creates a wallet containing a given set of keys. All further keys will be
     * derived from the oldest key.
     */
    public static Wallet fromKeys(NetworkParameters params, List<ECKey> keys) {
        for (ECKey key : keys)
            checkArgument(!(key instanceof DeterministicKey));

        KeyChainGroup group = new KeyChainGroup(params);
        group.importKeys(keys);
        return new Wallet(params, group);
    }

    public Wallet(NetworkParameters params, KeyChainGroup keyChainGroup) {
        this(Context.getOrCreate(params), keyChainGroup, null);
    }

    private Wallet(Context context, KeyChainGroup keyChainGroup, String url) {
        this.context = context;
        this.params = context.getParams();
        this.keyChainGroup = checkNotNull(keyChainGroup);
        if (params.getId().equals(NetworkParameters.ID_UNITTESTNET))
            this.keyChainGroup.setLookaheadSize(5); // Cut down excess
                                                    // computation for unit
                                                    // tests.
        // If this keyChainGroup was created fresh just now (new wallet), make
        // HD so a backup can be made immediately
        // without having to call current/freshReceiveKey. If there are already
        // keys in the chain of any kind then
        // we're probably being deserialized so leave things alone: the API user
        // can upgrade later.
        if (this.keyChainGroup.numKeys() == 0)
            this.keyChainGroup.createAndActivateNewHDChain();
        watchedScripts = Sets.newHashSet();
        unspent = new HashMap<Sha256Hash, Transaction>();
        spent = new HashMap<Sha256Hash, Transaction>();
        pending = new HashMap<Sha256Hash, Transaction>();
        dead = new HashMap<Sha256Hash, Transaction>();
        transactions = new HashMap<Sha256Hash, Transaction>();
        extensions = new HashMap<String, WalletExtension>();
        // Use a linked hash map to ensure ordering of event listeners is
        // correct.
        confidenceChanged = new LinkedHashMap<Transaction, TransactionConfidence.Listener.ChangeReason>();
        signers = new ArrayList<TransactionSigner>();
        addTransactionSigner(new LocalTransactionSigner());

        this.serverurl = url;
    }

    public NetworkParameters getNetworkParameters() {
        return params;
    }

    /**
     * Gets the active keychain via {@link KeyChainGroup#getActiveKeyChain()}
     */
    public DeterministicKeyChain getActiveKeyChain() {
        return keyChainGroup.getActiveKeyChain();
    }

    /**
     * <p>
     * Adds given transaction signer to the list of signers. It will be added to
     * the end of the signers list, so if this wallet already has some signers
     * added, given signer will be executed after all of them.
     * </p>
     * <p>
     * Transaction signer should be fully initialized before adding to the
     * wallet, otherwise {@link IllegalStateException} will be thrown
     * </p>
     */
    public final void addTransactionSigner(TransactionSigner signer) {
        lock.lock();
        try {
            if (signer.isReady())
                signers.add(signer);
            else
                throw new IllegalStateException(
                        "Signer instance is not ready to be added into Wallet: " + signer.getClass());
        } finally {
            lock.unlock();
        }
    }

    public List<TransactionSigner> getTransactionSigners() {
        lock.lock();
        try {
            return ImmutableList.copyOf(signers);
        } finally {
            lock.unlock();
        }
    }

    /******************************************************************************************************************/

    // region Key Management

    /**
     * Returns a key that hasn't been seen in a transaction yet, and which is
     * suitable for displaying in a wallet user interface as "a convenient key
     * to receive funds on" when the purpose parameter is
     * {@link net.bigtangle.wallet.KeyChain.KeyPurpose#RECEIVE_FUNDS}. The
     * returned key is stable until it's actually seen in a pending or confirmed
     * transaction, at which point this method will start returning a different
     * key (for each purpose independently).
     */
    public DeterministicKey currentKey(KeyChain.KeyPurpose purpose) {
        keyChainGroupLock.lock();
        try {
            maybeUpgradeToHD();
            return keyChainGroup.currentKey(purpose);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * An alias for calling
     * {@link #currentKey(net.bigtangle.wallet.KeyChain.KeyPurpose)} with
     * {@link net.bigtangle.wallet.KeyChain.KeyPurpose#RECEIVE_FUNDS} as the
     * parameter.
     */
    public DeterministicKey currentReceiveKey() {
        return currentKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    /**
     * Returns address for a
     * {@link #currentKey(net.bigtangle.wallet.KeyChain.KeyPurpose)}
     */
    public Address currentAddress(KeyChain.KeyPurpose purpose) {
        keyChainGroupLock.lock();
        try {
            maybeUpgradeToHD();
            return keyChainGroup.currentAddress(purpose);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * An alias for calling
     * {@link #currentAddress(net.bigtangle.wallet.KeyChain.KeyPurpose)} with
     * {@link net.bigtangle.wallet.KeyChain.KeyPurpose#RECEIVE_FUNDS} as the
     * parameter.
     */
    public Address currentReceiveAddress() {
        return currentAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    /**
     * Returns a key that has not been returned by this method before (fresh).
     * You can think of this as being a newly created key, although the notion
     * of "create" is not really valid for a
     * {@link net.bigtangle.wallet.DeterministicKeyChain}. When the parameter is
     * {@link net.bigtangle.wallet.KeyChain.KeyPurpose#RECEIVE_FUNDS} the
     * returned key is suitable for being put into a receive coins wizard type
     * UI. You should use this when the user is definitely going to hand this
     * key out to someone who wishes to send money.
     */
    public DeterministicKey freshKey(KeyChain.KeyPurpose purpose) {
        return freshKeys(purpose, 1).get(0);
    }

    /**
     * Returns a key/s that has not been returned by this method before (fresh).
     * You can think of this as being a newly created key/s, although the notion
     * of "create" is not really valid for a
     * {@link net.bigtangle.wallet.DeterministicKeyChain}. When the parameter is
     * {@link net.bigtangle.wallet.KeyChain.KeyPurpose#RECEIVE_FUNDS} the
     * returned key is suitable for being put into a receive coins wizard type
     * UI. You should use this when the user is definitely going to hand this
     * key/s out to someone who wishes to send money.
     */
    public List<DeterministicKey> freshKeys(KeyChain.KeyPurpose purpose, int numberOfKeys) {
        List<DeterministicKey> keys;
        keyChainGroupLock.lock();
        try {
            maybeUpgradeToHD();
            keys = keyChainGroup.freshKeys(purpose, numberOfKeys);
        } finally {
            keyChainGroupLock.unlock();
        }
        // Do we really need an immediate hard save? Arguably all this is doing
        // is saving the 'current' key
        // and that's not quite so important, so we could coalesce for more
        // performance.
        saveNow();
        return keys;
    }

    /**
     * An alias for calling
     * {@link #freshKey(net.bigtangle.wallet.KeyChain.KeyPurpose)} with
     * {@link net.bigtangle.wallet.KeyChain.KeyPurpose#RECEIVE_FUNDS} as the
     * parameter.
     */
    public DeterministicKey freshReceiveKey() {
        return freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    /**
     * Returns address for a
     * {@link #freshKey(net.bigtangle.wallet.KeyChain.KeyPurpose)}
     */
    public Address freshAddress(KeyChain.KeyPurpose purpose) {
        Address key;
        keyChainGroupLock.lock();
        try {
            key = keyChainGroup.freshAddress(purpose);
        } finally {
            keyChainGroupLock.unlock();
        }
        saveNow();
        return key;
    }

    /**
     * An alias for calling
     * {@link #freshAddress(net.bigtangle.wallet.KeyChain.KeyPurpose)} with
     * {@link net.bigtangle.wallet.KeyChain.KeyPurpose#RECEIVE_FUNDS} as the
     * parameter.
     */
    public Address freshReceiveAddress() {
        return freshAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    /**
     * Returns only the keys that have been issued by
     * {@link #freshReceiveKey()}, {@link #freshReceiveAddress()},
     * {@link #currentReceiveKey()} or {@link #currentReceiveAddress()}.
     */
    public List<ECKey> getIssuedReceiveKeys() {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.getActiveKeyChain().getIssuedReceiveKeys();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns only the addresses that have been issued by
     * {@link #freshReceiveKey()}, {@link #freshReceiveAddress()},
     * {@link #currentReceiveKey()} or {@link #currentReceiveAddress()}.
     */
    public List<Address> getIssuedReceiveAddresses() {
        final List<ECKey> keys = getIssuedReceiveKeys();
        List<Address> addresses = new ArrayList<Address>(keys.size());
        for (ECKey key : keys)
            addresses.add(key.toAddress(getParams()));
        return addresses;
    }

    /**
     * Upgrades the wallet to be deterministic (BIP32). You should call this,
     * possibly providing the users encryption key, after loading a wallet
     * produced by previous versions of bitcoinj. If the wallet is encrypted the
     * key <b>must</b> be provided, due to the way the seed is derived
     * deterministically from private key bytes: failing to do this will result
     * in an exception being thrown. For non-encrypted wallets, the upgrade will
     * be done for you automatically the first time a new key is requested (this
     * happens when spending due to the change address).
     */
    public void upgradeToDeterministic(@Nullable KeyParameter aesKey) throws DeterministicUpgradeRequiresPassword {
        keyChainGroupLock.lock();
        try {
            keyChainGroup.upgradeToDeterministic(vKeyRotationTimestamp, aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns true if the wallet contains random keys and no HD chains, in
     * which case you should call
     * {@link #upgradeToDeterministic(org.spongycastle.crypto.params.KeyParameter)}
     * before attempting to do anything that would require a new address or key.
     */
    public boolean isDeterministicUpgradeRequired() {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.isDeterministicUpgradeRequired();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    private void maybeUpgradeToHD() throws DeterministicUpgradeRequiresPassword {
        maybeUpgradeToHD(null);
    }

    @GuardedBy("keyChainGroupLock")
    private void maybeUpgradeToHD(@Nullable KeyParameter aesKey) throws DeterministicUpgradeRequiresPassword {
        checkState(keyChainGroupLock.isHeldByCurrentThread());
        if (keyChainGroup.isDeterministicUpgradeRequired()) {
            log.info("Upgrade to HD wallets is required, attempting to do so.");
            try {
                upgradeToDeterministic(aesKey);
            } catch (DeterministicUpgradeRequiresPassword e) {
                log.error("Failed to auto upgrade due to encryption. You should call wallet.upgradeToDeterministic "
                        + "with the users AES key to avoid this error.");
                throw e;
            }
        }
    }

    /**
     * Returns a snapshot of the watched scripts. This view is not live.
     */
    public List<Script> getWatchedScripts() {
        keyChainGroupLock.lock();
        try {
            return new ArrayList<Script>(watchedScripts);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Removes the given key from the basicKeyChain. Be very careful with this -
     * losing a private key <b>destroys the money associated with it</b>.
     * 
     * @return Whether the key was removed or not.
     */
    public boolean removeKey(ECKey key) {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.removeImportedKey(key);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns the number of keys in the key chain group, including lookahead
     * keys.
     */
    public int getKeyChainGroupSize() {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.numKeys();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @VisibleForTesting
    public int getKeyChainGroupCombinedKeyLookaheadEpochs() {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.getCombinedKeyLookaheadEpochs();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns a list of the non-deterministic keys that have been imported into
     * the wallet, or the empty list if none.
     */
    public List<ECKey> getImportedKeys() {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.getImportedKeys();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns the address used for change outputs. Note: this will probably go
     * away in future.
     */
    public Address currentChangeAddress() {
        return currentAddress(KeyChain.KeyPurpose.CHANGE);
    }

    /**
     * @deprecated use {@link #currentChangeAddress()} instead.
     */
    public Address getChangeAddress() {
        return currentChangeAddress();
    }

    /**
     * <p>
     * Deprecated alias for {@link #importKey(ECKey)}.
     * </p>
     *
     * <p>
     * <b>Replace with either {@link #freshReceiveKey()} if your call is
     * addKey(new ECKey()), or with {@link #importKey(ECKey)} which does the
     * same thing this method used to, but with a better name.</b>
     * </p>
     */
    @Deprecated
    public boolean addKey(ECKey key) {
        return importKey(key);
    }

    /**
     * <p>
     * Imports the given ECKey to the wallet.
     * </p>
     *
     * <p>
     * If the wallet is configured to auto save to a file, triggers a save
     * immediately. Runs the onKeysAdded event handler. If the key already
     * exists in the wallet, does nothing and returns false.
     * </p>
     */
    public boolean importKey(ECKey key) {
        return importKeys(Lists.newArrayList(key)) == 1;
    }

    /**
     * Replace with {@link #importKeys(java.util.List)}, which does the same
     * thing but with a better name.
     */
    @Deprecated
    public int addKeys(List<ECKey> keys) {
        return importKeys(keys);
    }

    /**
     * Imports the given keys to the wallet. If
     * {@link Wallet#autosaveToFile(java.io.File, long, java.util.concurrent.TimeUnit, net.bigtangle.wallet.WalletFiles.Listener)}
     * has been called, triggers an auto save bypassing the normal coalescing
     * delay and event handlers. Returns the number of keys added, after
     * duplicates are ignored. The onKeyAdded event will be called for each key
     * in the list that was not already present.
     */
    public int importKeys(final List<ECKey> keys) {
        // API usage check.
        checkNoDeterministicKeys(keys);
        int result;
        keyChainGroupLock.lock();
        try {
            result = keyChainGroup.importKeys(keys);
        } finally {
            keyChainGroupLock.unlock();
        }
        saveNow();
        return result;
    }

    private void checkNoDeterministicKeys(List<ECKey> keys) {
        // Watch out for someone doing
        // wallet.importKey(wallet.freshReceiveKey()); or equivalent: we never
        // tested this.
        for (ECKey key : keys)
            if (key instanceof DeterministicKey)
                throw new IllegalArgumentException("Cannot import HD keys back into the wallet");
    }

    /**
     * Takes a list of keys and a password, then encrypts and imports them in
     * one step using the current keycrypter.
     */
    public int importKeysAndEncrypt(final List<ECKey> keys, CharSequence password) {
        keyChainGroupLock.lock();
        int result;
        try {
            checkNotNull(getKeyCrypter(), "Wallet is not encrypted");
            result = importKeysAndEncrypt(keys, getKeyCrypter().deriveKey(password));
        } finally {
            keyChainGroupLock.unlock();
        }
        saveNow();
        return result;
    }

    /**
     * Takes a list of keys and an AES key, then encrypts and imports them in
     * one step using the current keycrypter.
     */
    public int importKeysAndEncrypt(final List<ECKey> keys, KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            checkNoDeterministicKeys(keys);
            return keyChainGroup.importKeysAndEncrypt(keys, aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Add a pre-configured keychain to the wallet. Useful for setting up a
     * complex keychain, such as for a married wallet. For example:
     * 
     * <pre>
     * MarriedKeyChain chain = MarriedKeyChain.builder() .random(new
     * SecureRandom()) .followingKeys(followingKeys) .threshold(2).build();
     * wallet.addAndActivateHDChain(chain);
     * </p>
     */
    public void addAndActivateHDChain(DeterministicKeyChain chain) {
        keyChainGroupLock.lock();
        try {
            keyChainGroup.addAndActivateHDChain(chain);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * See
     * {@link net.bigtangle.wallet.DeterministicKeyChain#setLookaheadSize(int)}
     * for more info on this.
     */
    public void setKeyChainGroupLookaheadSize(int lookaheadSize) {
        keyChainGroupLock.lock();
        try {
            keyChainGroup.setLookaheadSize(lookaheadSize);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * See
     * {@link net.bigtangle.wallet.DeterministicKeyChain#setLookaheadSize(int)}
     * for more info on this.
     */
    public int getKeyChainGroupLookaheadSize() {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.getLookaheadSize();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * See
     * {@link net.bigtangle.wallet.DeterministicKeyChain#setLookaheadThreshold(int)}
     * for more info on this.
     */
    public void setKeyChainGroupLookaheadThreshold(int num) {
        keyChainGroupLock.lock();
        try {
            maybeUpgradeToHD();
            keyChainGroup.setLookaheadThreshold(num);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * See
     * {@link net.bigtangle.wallet.DeterministicKeyChain#setLookaheadThreshold(int)}
     * for more info on this.
     */
    public int getKeyChainGroupLookaheadThreshold() {
        keyChainGroupLock.lock();
        try {
            maybeUpgradeToHD();
            return keyChainGroup.getLookaheadThreshold();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns a public-only DeterministicKey that can be used to set up a
     * watching wallet: that is, a wallet that can import transactions from the
     * block chain just as the normal wallet can, but which cannot spend.
     * Watching wallets are very useful for things like web servers that accept
     * payments. This key corresponds to the account zero key in the recommended
     * BIP32 hierarchy.
     */
    public DeterministicKey getWatchingKey() {
        keyChainGroupLock.lock();
        try {
            maybeUpgradeToHD();
            return keyChainGroup.getActiveKeyChain().getWatchingKey();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns whether this wallet consists entirely of watching keys
     * (unencrypted keys with no private part). Mixed wallets are forbidden.
     * 
     * @throws IllegalStateException
     *             if there are no keys, or if there is a mix between watching
     *             and non-watching keys.
     */
    public boolean isWatching() {
        keyChainGroupLock.lock();
        try {
            maybeUpgradeToHD();
            return keyChainGroup.isWatching();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Return true if we are watching this address.
     */
    public boolean isAddressWatched(Address address) {
        Script script = ScriptBuilder.createOutputScript(address);
        return isWatchedScript(script);
    }

    /**
     * Same as {@link #addWatchedAddress(Address, long)} with the current time
     * as the creation time.
     */
    public boolean addWatchedAddress(final Address address) {
        long now = Utils.currentTimeMillis() / 1000;
        return addWatchedAddresses(Lists.newArrayList(address), now) == 1;
    }

    /**
     * Adds the given address to the wallet to be watched. Outputs can be
     * retrieved by {@link #getWatchedOutputs(boolean)}.
     *
     * @param creationTime
     *            creation time in seconds since the epoch, for scanning the
     *            blockchain
     * @return whether the address was added successfully (not already present)
     */
    public boolean addWatchedAddress(final Address address, long creationTime) {
        return addWatchedAddresses(Lists.newArrayList(address), creationTime) == 1;
    }

    /**
     * Adds the given address to the wallet to be watched. Outputs can be
     * retrieved by {@link #getWatchedOutputs(boolean)}.
     *
     * @return how many addresses were added successfully
     */
    public int addWatchedAddresses(final List<Address> addresses, long creationTime) {
        List<Script> scripts = Lists.newArrayList();

        for (Address address : addresses) {
            Script script = ScriptBuilder.createOutputScript(address);
            script.setCreationTimeSeconds(creationTime);
            scripts.add(script);
        }

        return addWatchedScripts(scripts);
    }

    /**
     * Adds the given output scripts to the wallet to be watched. Outputs can be
     * retrieved by {@link #getWatchedOutputs(boolean)}. If a script is already
     * being watched, the object is replaced with the one in the given list. As
     * {@link Script} equality is defined in terms of program bytes only this
     * lets you update metadata such as creation time. Note that you should be
     * careful not to add scripts with a creation time of zero (the default!)
     * because otherwise it will disable the important wallet checkpointing
     * optimisation.
     *
     * @return how many scripts were added successfully
     */
    public int addWatchedScripts(final List<Script> scripts) {
        int added = 0;
        keyChainGroupLock.lock();
        try {
            for (final Script script : scripts) {
                // Script.equals/hashCode() only takes into account the program
                // bytes, so this step lets the user replace
                // a script in the wallet with an incorrect creation time.
                if (watchedScripts.contains(script))
                    watchedScripts.remove(script);
                if (script.getCreationTimeSeconds() == 0)
                    log.warn(
                            "Adding a script to the wallet with a creation time of zero, this will disable the checkpointing optimization!    {}",
                            script);
                watchedScripts.add(script);
                added++;
            }
        } finally {
            keyChainGroupLock.unlock();
        }
        if (added > 0) {
            queueOnScriptsChanged(scripts, true);
            saveNow();
        }
        return added;
    }

    /**
     * Removes the given output scripts from the wallet that were being watched.
     *
     * @return true if successful
     */
    public boolean removeWatchedAddress(final Address address) {
        return removeWatchedAddresses(ImmutableList.of(address));
    }

    /**
     * Removes the given output scripts from the wallet that were being watched.
     *
     * @return true if successful
     */
    public boolean removeWatchedAddresses(final List<Address> addresses) {
        List<Script> scripts = Lists.newArrayList();

        for (Address address : addresses) {
            Script script = ScriptBuilder.createOutputScript(address);
            scripts.add(script);
        }

        return removeWatchedScripts(scripts);
    }

    /**
     * Removes the given output scripts from the wallet that were being watched.
     *
     * @return true if successful
     */
    public boolean removeWatchedScripts(final List<Script> scripts) {
        lock.lock();
        try {
            for (final Script script : scripts) {
                if (!watchedScripts.contains(script))
                    continue;

                watchedScripts.remove(script);
            }

            queueOnScriptsChanged(scripts, false);
            saveNow();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns all addresses watched by this wallet.
     */
    public List<Address> getWatchedAddresses() {
        keyChainGroupLock.lock();
        try {
            List<Address> addresses = new LinkedList<Address>();
            for (Script script : watchedScripts)
                if (script.isSentToAddress())
                    addresses.add(script.getToAddress(params));
            return addresses;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Locates a keypair from the basicKeyChain given the hash of the public
     * key. This is needed when finding out which key we need to use to redeem a
     * transaction output.
     *
     * @return ECKey object or null if no such key was found.
     */
    @Override
    @Nullable
    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.findKeyFromPubHash(pubkeyHash);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns true if the given key is in the wallet, false otherwise.
     * Currently an O(N) operation.
     */
    public boolean hasKey(ECKey key) {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.hasKey(key);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPubKeyHashMine(byte[] pubkeyHash) {
        return findKeyFromPubHash(pubkeyHash) != null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isWatchedScript(Script script) {
        keyChainGroupLock.lock();
        try {
            return watchedScripts.contains(script);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Locates a keypair from the basicKeyChain given the raw public key bytes.
     * 
     * @return ECKey or null if no such key was found.
     */
    @Override
    @Nullable
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.findKeyFromPubKey(pubkey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPubKeyMine(byte[] pubkey) {
        return findKeyFromPubKey(pubkey) != null;
    }

    /**
     * Locates a redeem data (redeem script and keys) from the keyChainGroup
     * given the hash of the script. Returns RedeemData object or null if no
     * such data was found.
     */
    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.findRedeemDataFromScriptHash(payToScriptHash);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPayToScriptHashMine(byte[] payToScriptHash) {
        return findRedeemDataFromScriptHash(payToScriptHash) != null;
    }

    /**
     * Marks all keys used in the transaction output as used in the wallet. See
     * {@link net.bigtangle.wallet.DeterministicKeyChain#markKeyAsUsed(DeterministicKey)}
     * for more info on this.
     */
    private void markKeysAsUsed(Transaction tx) {
        keyChainGroupLock.lock();
        try {
            for (TransactionOutput o : tx.getOutputs()) {
                try {
                    Script script = o.getScriptPubKey();
                    if (script.isSentToRawPubKey()) {
                        byte[] pubkey = script.getPubKey();
                        keyChainGroup.markPubKeyAsUsed(pubkey);
                    } else if (script.isSentToAddress()) {
                        byte[] pubkeyHash = script.getPubKeyHash();
                        keyChainGroup.markPubKeyHashAsUsed(pubkeyHash);
                    } else if (script.isPayToScriptHash()) {
                        Address a = Address.fromP2SHScript(tx.getParams(), script);
                        keyChainGroup.markP2SHAddressAsUsed(a);
                    }
                } catch (ScriptException e) {
                    // Just means we didn't understand the output of this
                    // transaction: ignore it.
                    log.warn("Could not parse tx output script: {}", e.toString());
                }
            }
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns the immutable seed for the current active HD chain.
     * 
     * @throws net.bigtangle.core.ECKey.MissingPrivateKeyException
     *             if the seed is unavailable (watching wallet)
     */
    public DeterministicSeed getKeyChainSeed() {
        keyChainGroupLock.lock();
        try {
            DeterministicSeed seed = keyChainGroup.getActiveKeyChain().getSeed();
            if (seed == null)
                throw new ECKey.MissingPrivateKeyException();
            return seed;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns a key for the given HD path, assuming it's already been derived.
     * You normally shouldn't use this: use currentReceiveKey/freshReceiveKey
     * instead.
     */
    public DeterministicKey getKeyByPath(List<ChildNumber> path) {
        keyChainGroupLock.lock();
        try {
            maybeUpgradeToHD();
            return keyChainGroup.getActiveKeyChain().getKeyByPath(path, false);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Convenience wrapper around
     * {@link Wallet#encrypt(net.bigtangle.crypto.KeyCrypter, org.spongycastle.crypto.params.KeyParameter)}
     * which uses the default Scrypt key derivation algorithm and parameters to
     * derive a key from the given password.
     */
    public void encrypt(CharSequence password) {
        keyChainGroupLock.lock();
        try {
            final KeyCrypterScrypt scrypt = new KeyCrypterScrypt();
            keyChainGroup.encrypt(scrypt, scrypt.deriveKey(password));
        } finally {
            keyChainGroupLock.unlock();
        }
        saveNow();
    }

    /**
     * Encrypt the wallet using the KeyCrypter and the AES key. A good default
     * KeyCrypter to use is {@link net.bigtangle.crypto.KeyCrypterScrypt}.
     *
     * @param keyCrypter
     *            The KeyCrypter that specifies how to encrypt/ decrypt a key
     * @param aesKey
     *            AES key to use (normally created using KeyCrypter#deriveKey
     *            and cached as it is time consuming to create from a password)
     * @throws KeyCrypterException
     *             Thrown if the wallet encryption fails. If so, the wallet
     *             state is unchanged.
     */
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            keyChainGroup.encrypt(keyCrypter, aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
        saveNow();
    }

    /**
     * Decrypt the wallet with the wallets keyCrypter and password.
     * 
     * @throws KeyCrypterException
     *             Thrown if the wallet decryption fails. If so, the wallet
     *             state is unchanged.
     */
    public void decrypt(CharSequence password) {
        keyChainGroupLock.lock();
        try {
            final KeyCrypter crypter = keyChainGroup.getKeyCrypter();
            checkState(crypter != null, "Not encrypted");
            keyChainGroup.decrypt(crypter.deriveKey(password));
        } finally {
            keyChainGroupLock.unlock();
        }
        saveNow();
    }

    /**
     * Decrypt the wallet with the wallets keyCrypter and AES key.
     *
     * @param aesKey
     *            AES key to use (normally created using KeyCrypter#deriveKey
     *            and cached as it is time consuming to create from a password)
     * @throws KeyCrypterException
     *             Thrown if the wallet decryption fails. If so, the wallet
     *             state is unchanged.
     */
    public void decrypt(KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            keyChainGroup.decrypt(aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
        saveNow();
    }

    /**
     * Check whether the password can decrypt the first key in the wallet. This
     * can be used to check the validity of an entered password.
     *
     * @return boolean true if password supplied can decrypt the first private
     *         key in the wallet, false otherwise.
     * @throws IllegalStateException
     *             if the wallet is not encrypted.
     */
    public boolean checkPassword(CharSequence password) {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.checkPassword(password);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Check whether the AES key can decrypt the first encrypted key in the
     * wallet.
     *
     * @return boolean true if AES key supplied can decrypt the first encrypted
     *         private key in the wallet, false otherwise.
     */
    public boolean checkAESKey(KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.checkAESKey(aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Get the wallet's KeyCrypter, or null if the wallet is not encrypted.
     * (Used in encrypting/ decrypting an ECKey).
     */
    @Nullable
    public KeyCrypter getKeyCrypter() {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.getKeyCrypter();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Get the type of encryption used for this wallet.
     *
     * (This is a convenience method - the encryption type is actually stored in
     * the keyCrypter).
     */
    public EncryptionType getEncryptionType() {
        keyChainGroupLock.lock();
        try {
            KeyCrypter crypter = keyChainGroup.getKeyCrypter();
            if (crypter != null)
                return crypter.getUnderstoodEncryptionType();
            else
                return EncryptionType.UNENCRYPTED;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns true if the wallet is encrypted using any scheme, false if not.
     */
    public boolean isEncrypted() {
        return getEncryptionType() != EncryptionType.UNENCRYPTED;
    }

    /** Changes wallet encryption password, this is atomic operation. */
    public void changeEncryptionPassword(CharSequence currentPassword, CharSequence newPassword) {
        keyChainGroupLock.lock();
        try {
            decrypt(currentPassword);
            encrypt(newPassword);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /** Changes wallet AES encryption key, this is atomic operation. */
    public void changeEncryptionKey(KeyCrypter keyCrypter, KeyParameter currentAesKey, KeyParameter newAesKey) {
        keyChainGroupLock.lock();
        try {
            decrypt(currentAesKey);
            encrypt(keyCrypter, newAesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    // endregion

    /******************************************************************************************************************/

    // region Serialization support

    // TODO: Make this package private once the classes finish moving around.
    /** Internal use only. */
    public List<Protos.Key> serializeKeyChainGroupToProtobuf() {
        keyChainGroupLock.lock();
        try {
            return keyChainGroup.serializeToProtobuf();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Saves the wallet first to the given temp file, then renames to the dest
     * file.
     */
    public void saveToFile(File temp, File destFile) throws IOException {
        FileOutputStream stream = null;
        lock.lock();
        try {
            stream = new FileOutputStream(temp);
            saveToFileStream(stream);
            // Attempt to force the bits to hit the disk. In reality the OS or
            // hard disk itself may still decide
            // to not write through to physical media for at least a few
            // seconds, but this is the best we can do.
            stream.flush();
            stream.getFD().sync();
            stream.close();
            stream = null;
            if (Utils.isWindows()) {
                // Work around an issue on Windows whereby you can't rename over
                // existing files.
                File canonical = destFile.getCanonicalFile();
                if (canonical.exists() && !canonical.delete())
                    throw new IOException("Failed to delete canonical wallet file for replacement with autosave");
                if (temp.renameTo(canonical))
                    return; // else fall through.
                throw new IOException("Failed to rename " + temp + " to " + canonical);
            } else if (!temp.renameTo(destFile)) {
                throw new IOException("Failed to rename " + temp + " to " + destFile);
            }
        } catch (RuntimeException e) {
            log.error("Failed whilst saving wallet", e);
            throw e;
        } finally {
            lock.unlock();
            if (stream != null) {
                stream.close();
            }
            if (temp.exists()) {
                log.warn("Temp file still exists after failed save.");
            }
        }
    }

    /**
     * Uses protobuf serialization to save the wallet to the given file. To
     * learn more about this file format, see {@link WalletProtobufSerializer}.
     * Writes out first to a temporary file in the same directory and then
     * renames once written.
     */
    public void saveToFile(File f) throws IOException {
        File directory = f.getAbsoluteFile().getParentFile();
        File temp = File.createTempFile("wallet", null, directory);
        saveToFile(temp, f);
    }

    /**
     * <p>
     * Whether or not the wallet will ignore pending transactions that fail the
     * selected {@link RiskAnalysis}. By default, if a transaction is considered
     * risky then it won't enter the wallet and won't trigger any event
     * listeners. If you set this property to true, then all transactions will
     * be allowed in regardless of risk. For example, the
     * {@link DefaultRiskAnalysis} checks for non-finality of transactions.
     * </p>
     *
     * <p>
     * Note that this property is not serialized. You have to set it each time a
     * Wallet object is constructed, even if it's loaded from a protocol buffer.
     * </p>
     */
    public void setAcceptRiskyTransactions(boolean acceptRiskyTransactions) {
        lock.lock();
        try {
            this.acceptRiskyTransactions = acceptRiskyTransactions;
        } finally {
            lock.unlock();
        }
    }

    /**
     * See {@link Wallet#setAcceptRiskyTransactions(boolean)} for an explanation
     * of this property.
     */
    public boolean isAcceptRiskyTransactions() {
        lock.lock();
        try {
            return acceptRiskyTransactions;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the {@link RiskAnalysis} implementation to use for deciding whether
     * received pending transactions are risky or not. If the analyzer says a
     * transaction is risky, by default it will be dropped. You can customize
     * this behaviour with {@link #setAcceptRiskyTransactions(boolean)}.
     */
    public void setRiskAnalyzer(RiskAnalysis.Analyzer analyzer) {
        lock.lock();
        try {
            this.riskAnalyzer = checkNotNull(analyzer);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the current {@link RiskAnalysis} implementation. The default is
     * {@link DefaultRiskAnalysis}.
     */
    public RiskAnalysis.Analyzer getRiskAnalyzer() {
        lock.lock();
        try {
            return riskAnalyzer;
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>
     * Sets up the wallet to auto-save itself to the given file, using temp
     * files with atomic renames to ensure consistency. After connecting to a
     * file, you no longer need to save the wallet manually, it will do it
     * whenever necessary. Protocol buffer serialization will be used.
     * </p>
     *
     * <p>
     * If delayTime is set, a background thread will be created and the wallet
     * will only be saved to disk every so many time units. If no changes have
     * occurred for the given time period, nothing will be written. In this way
     * disk IO can be rate limited. It's a good idea to set this as otherwise
     * the wallet can change very frequently, eg if there are a lot of
     * transactions in it or during block sync, and there will be a lot of
     * redundant writes. Note that when a new key is added, that always results
     * in an immediate save regardless of delayTime. <b>You should still save
     * the wallet manually when your program is about to shut down as the JVM
     * will not wait for the background thread.</b>
     * </p>
     *
     * <p>
     * An event listener can be provided. If a delay >0 was specified, it will
     * be called on a background thread with the wallet locked when an auto-save
     * occurs. If delay is zero or you do something that always triggers an
     * immediate save, like adding a key, the event listener will be invoked on
     * the calling threads.
     * </p>
     *
     * @param f
     *            The destination file to save to.
     * @param delayTime
     *            How many time units to wait until saving the wallet on a
     *            background thread.
     * @param timeUnit
     *            the unit of measurement for delayTime.
     * @param eventListener
     *            callback to be informed when the auto-save thread does things,
     *            or null
     */
    public WalletFiles autosaveToFile(File f, long delayTime, TimeUnit timeUnit,
            @Nullable WalletFiles.Listener eventListener) {
        lock.lock();
        try {
            checkState(vFileManager == null, "Already auto saving this wallet.");
            WalletFiles manager = new WalletFiles(this, f, delayTime, timeUnit);
            if (eventListener != null)
                manager.setListener(eventListener);
            vFileManager = manager;
            return manager;
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>
     * Disables auto-saving, after it had been enabled with
     * {@link Wallet#autosaveToFile(java.io.File, long, java.util.concurrent.TimeUnit, net.bigtangle.wallet.WalletFiles.Listener)}
     * before. This method blocks until finished.
     * </p>
     */
    public void shutdownAutosaveAndWait() {
        lock.lock();
        try {
            WalletFiles files = vFileManager;
            vFileManager = null;
            checkState(files != null, "Auto saving not enabled.");
            files.shutdownAndWait();
        } finally {
            lock.unlock();
        }
    }

    /** Requests an asynchronous save on a background thread */
    protected void saveLater() {
        WalletFiles files = vFileManager;
        if (files != null)
            files.saveLater();
    }

    /**
     * If auto saving is enabled, do an immediate sync write to disk ignoring
     * any delays.
     */
    protected void saveNow() {
        WalletFiles files = vFileManager;
        if (files != null) {
            try {
                files.saveNow(); // This calls back into saveToFile().
            } catch (IOException e) {
                // Can't really do much at this point, just let the API user
                // know.
                log.error("Failed to save wallet to disk!", e);
                Thread.UncaughtExceptionHandler handler = Threading.uncaughtExceptionHandler;
                if (handler != null)
                    handler.uncaughtException(Thread.currentThread(), e);
            }
        }
    }

    /**
     * Uses protobuf serialization to save the wallet to the given file stream.
     * To learn more about this file format, see
     * {@link WalletProtobufSerializer}.
     */
    public void saveToFileStream(OutputStream f) throws IOException {
        lock.lock();
        try {
            new WalletProtobufSerializer().writeWallet(this, f);
        } finally {
            lock.unlock();
        }
    }

    /** Returns the parameters this wallet was created with. */
    public NetworkParameters getParams() {
        return params;
    }

    /** Returns the API context that this wallet was created with. */
    public Context getContext() {
        return context;
    }

    /**
     * <p>
     * Returns a wallet deserialized from the given file. Extensions previously
     * saved with the wallet can be deserialized by
     * calling @{@link WalletExtension#deserializeWalletExtension(Wallet, byte[])}}
     * </p>
     *
     * @param file
     *            the wallet file to read
     * @param walletExtensions
     *            extensions possibly added to the wallet.
     */
    public static Wallet loadFromFile(File file, @Nullable WalletExtension... walletExtensions)
            throws UnreadableWalletException {
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(file);
                return loadFromFileStream(stream, walletExtensions);
            } finally {
                if (stream != null)
                    stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    /**
     * Returns if this wallet is structurally consistent, so e.g. no duplicate
     * transactions. First inconsistency and a dump of the wallet will be
     * logged.
     */
    public boolean isConsistent() {
        try {
            isConsistentOrThrow();
            return true;
        } catch (IllegalStateException x) {
            log.error(x.getMessage());
            try {
                log.error(toString());
            } catch (RuntimeException x2) {
                log.error("Printing inconsistent wallet failed", x2);
            }
            return false;
        }
    }

    /**
     * Variant of {@link Wallet#isConsistent()} that throws an
     * {@link IllegalStateException} describing the first inconsistency.
     */
    public void isConsistentOrThrow() throws IllegalStateException {
        lock.lock();
        try {
            Set<Transaction> transactions = getTransactions(true);

            Set<Sha256Hash> hashes = new HashSet<Sha256Hash>();
            for (Transaction tx : transactions) {
                hashes.add(tx.getHash());
            }

            int size1 = transactions.size();
            if (size1 != hashes.size()) {
                throw new IllegalStateException("Two transactions with same hash");
            }

            int size2 = unspent.size() + spent.size() + pending.size() + dead.size();
            if (size1 != size2) {
                throw new IllegalStateException("Inconsistent wallet sizes: " + size1 + ", " + size2);
            }

            for (Transaction tx : unspent.values()) {
                if (!isTxConsistent(tx, false)) {
                    throw new IllegalStateException("Inconsistent unspent tx: " + tx.getHashAsString());
                }
            }

            for (Transaction tx : spent.values()) {
                if (!isTxConsistent(tx, true)) {
                    throw new IllegalStateException("Inconsistent spent tx: " + tx.getHashAsString());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /*
     * If isSpent - check that all my outputs spent, otherwise check that there
     * at least one unspent.
     */
    @VisibleForTesting
    boolean isTxConsistent(final Transaction tx, final boolean isSpent) {
        boolean isActuallySpent = true;
        for (TransactionOutput o : tx.getOutputs()) {
            if (o.isAvailableForSpending()) {
                if (o.isMineOrWatched(this))
                    isActuallySpent = false;
                if (o.getSpentBy() != null) {
                    log.error("isAvailableForSpending != spentBy");
                    return false;
                }
            } else {
                if (o.getSpentBy() == null) {
                    log.error("isAvailableForSpending != spentBy");
                    return false;
                }
            }
        }
        return isActuallySpent == isSpent;
    }

    /**
     * Returns a wallet deserialized from the given input stream and wallet
     * extensions.
     */
    public static Wallet loadFromFileStream(InputStream stream, @Nullable WalletExtension... walletExtensions)
            throws UnreadableWalletException {
        Wallet wallet = new WalletProtobufSerializer().readWallet(stream, walletExtensions);
        if (!wallet.isConsistent()) {
            log.error("Loaded an inconsistent wallet");
        }
        return wallet;
    }

    // endregion

    /**
     * Given a transaction and an optional list of dependencies
     * (recursive/flattened), returns true if the given transaction would be
     * rejected by the analyzer, or false otherwise. The result of this call is
     * independent of the value of {@link #isAcceptRiskyTransactions()}. Risky
     * transactions yield a logged warning. If you want to know the reason why a
     * transaction is risky, create an instance of the {@link RiskAnalysis}
     * yourself using the factory returned by {@link #getRiskAnalyzer()} and use
     * it directly.
     */
    public boolean isTransactionRisky(Transaction tx, @Nullable List<Transaction> dependencies) {
        lock.lock();
        try {
            if (dependencies == null)
                dependencies = ImmutableList.of();
            RiskAnalysis analysis = riskAnalyzer.create(this, tx, dependencies);
            RiskAnalysis.Result result = analysis.analyze();
            if (result != RiskAnalysis.Result.OK) {
                log.warn("Pending transaction was considered risky: {}\n{}", analysis, tx);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * This method is used by a {@link Peer} to find out if a transaction that
     * has been announced is interesting, that is, whether we should bother
     * downloading its dependencies and exploring the transaction to decide how
     * risky it is. If this method returns true then
     * {@link Wallet#receivePending(Transaction, java.util.List)} will soon be
     * called with the transactions dependencies as well.
     */
    public boolean isPendingTransactionRelevant(Transaction tx) throws ScriptException {
        lock.lock();
        try {
            // Ignore it if we already know about this transaction. Receiving a
            // pending transaction never moves it
            // between pools.
            EnumSet<Pool> containingPools = getContainingPools(tx);
            if (!containingPools.equals(EnumSet.noneOf(Pool.class))) {
                log.debug("Received tx we already saw in a block or created ourselves: " + tx.getHashAsString());
                return false;
            }
            // We only care about transactions that:
            // - Send us coins
            // - Spend our coins
            // - Double spend a tx in our wallet
            if (!isTransactionRelevant(tx)) {
                log.debug("Received tx that isn't relevant to this wallet, discarding.");
                return false;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>
     * Returns true if the given transaction sends coins to any of our keys, or
     * has inputs spending any of our outputs, and also returns true if tx has
     * inputs that are spending outputs which are not ours but which are spent
     * by pending transactions.
     * </p>
     *
     * <p>
     * Note that if the tx has inputs containing one of our keys, but the
     * connected transaction is not in the wallet, it will not be considered
     * relevant.
     * </p>
     */
    public boolean isTransactionRelevant(Transaction tx) throws ScriptException {
        lock.lock();
        try {
            return tx.getValueSentFromMe(this).signum() > 0 || tx.getValueSentToMe(this).signum() > 0
                    || !findDoubleSpendsAgainst(tx, transactions).isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finds transactions in the specified candidates that double spend "tx".
     * Not a general check, but it can work even if the double spent inputs are
     * not ours.
     * 
     * @return The set of transactions that double spend "tx".
     */
    private Set<Transaction> findDoubleSpendsAgainst(Transaction tx, Map<Sha256Hash, Transaction> candidates) {
        checkState(lock.isHeldByCurrentThread());
        if (tx.isCoinBase())
            return Sets.newHashSet();
        // Compile a set of outpoints that are spent by tx.
        HashSet<TransactionOutPoint> outpoints = new HashSet<TransactionOutPoint>();
        for (TransactionInput input : tx.getInputs()) {
            outpoints.add(input.getOutpoint());
        }
        // Now for each pending transaction, see if it shares any outpoints with
        // this tx.
        Set<Transaction> doubleSpendTxns = Sets.newHashSet();
        for (Transaction p : candidates.values()) {
            for (TransactionInput input : p.getInputs()) {
                // This relies on the fact that TransactionOutPoint equality is
                // defined at the protocol not object
                // level - outpoints from two different inputs that point to the
                // same output compare the same.
                TransactionOutPoint outpoint = input.getOutpoint();
                if (outpoints.contains(outpoint)) {
                    // It does, it's a double spend against the candidates,
                    // which makes it relevant.
                    doubleSpendTxns.add(p);
                }
            }
        }
        return doubleSpendTxns;
    }

    /**
     * Adds to txSet all the txns in txPool spending outputs of txns in txSet,
     * and all txns spending the outputs of those txns, recursively.
     */
    void addTransactionsDependingOn(Set<Transaction> txSet, Set<Transaction> txPool) {
        Map<Sha256Hash, Transaction> txQueue = new LinkedHashMap<Sha256Hash, Transaction>();
        for (Transaction tx : txSet) {
            txQueue.put(tx.getHash(), tx);
        }
        while (!txQueue.isEmpty()) {
            Transaction tx = txQueue.remove(txQueue.keySet().iterator().next());
            for (Transaction anotherTx : txPool) {
                if (anotherTx.equals(tx))
                    continue;
                for (TransactionInput input : anotherTx.getInputs()) {
                    if (input.getOutpoint().getHash().equals(tx.getHash())) {
                        if (txQueue.get(anotherTx.getHash()) == null) {
                            txQueue.put(anotherTx.getHash(), anotherTx);
                            txSet.add(anotherTx);
                        }
                    }
                }
            }
        }
    }

    // Whether to do a saveNow or saveLater when we are notified of the next
    // best block.
    private boolean hardSaveOnNextBlock = false;

    /**
     * Finds if tx is NOT spending other txns which are in the specified
     * confidence type
     */
    private boolean isNotSpendingTxnsInConfidenceType(Transaction tx, ConfidenceType confidenceType) {
        for (TransactionInput txInput : tx.getInputs()) {
            Transaction connectedTx = this.getTransaction(txInput.getOutpoint().getHash());
            if (connectedTx != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates and returns a new List with the same txns as inputSet but txns
     * are sorted by depencency (a topological sort). If tx B spends tx A, then
     * tx A should be before tx B on the returned List. Several invocations to
     * this method with the same inputSet could result in lists with txns in
     * different order, as there is no guarantee on the order of the returned
     * txns besides what was already stated.
     */
    List<Transaction> sortTxnsByDependency(Set<Transaction> inputSet) {
        ArrayList<Transaction> result = new ArrayList<Transaction>(inputSet);
        for (int i = 0; i < result.size() - 1; i++) {
            boolean txAtISpendsOtherTxInTheList;
            do {
                txAtISpendsOtherTxInTheList = false;
                for (int j = i + 1; j < result.size(); j++) {
                    if (spends(result.get(i), result.get(j))) {
                        Transaction transactionAtI = result.remove(i);
                        result.add(j, transactionAtI);
                        txAtISpendsOtherTxInTheList = true;
                        break;
                    }
                }
            } while (txAtISpendsOtherTxInTheList);
        }
        return result;
    }

    /** Finds whether txA spends txB */
    boolean spends(Transaction txA, Transaction txB) {
        for (TransactionInput txInput : txA.getInputs()) {
            if (txInput.getOutpoint().getHash().equals(txB.getHash())) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>
     * Updates the wallet by checking if this TX spends any of our outputs, and
     * marking them as spent if so. If fromChain is true, also checks to see if
     * any pending transaction spends outputs of this transaction and marks the
     * spent flags appropriately.
     * </p>
     *
     * <p>
     * It can be called in two contexts. One is when we receive a transaction on
     * the best chain but it wasn't pending, this most commonly happens when we
     * have a set of keys but the wallet transactions were wiped and we are
     * catching up with the block chain. It can also happen if a block includes
     * a transaction we never saw at broadcast time. If this tx double spends,
     * it takes precedence over our pending transactions and the pending tx goes
     * dead.
     * </p>
     *
     * <p>
     * The other context it can be called is from
     * {@link Wallet#receivePending(Transaction, java.util.List)}, ie we saw a
     * tx be broadcast or one was submitted directly that spends our own coins.
     * If this tx double spends it does NOT take precedence because the winner
     * will be resolved by the miners - we assume that our version will win, if
     * we are wrong then when a block appears the tx will go dead.
     * </p>
     *
     * @param tx
     *            The transaction which is being updated.
     * @param fromChain
     *            If true, the tx appeared on the current best chain, if false
     *            it was pending.
     */
    private void updateForSpends(Transaction tx, boolean fromChain) throws VerificationException {
        checkState(lock.isHeldByCurrentThread());
        if (fromChain)
            checkState(!pending.containsKey(tx.getHash()));
        for (TransactionInput input : tx.getInputs()) {
            TransactionInput.ConnectionResult result = input.connect(unspent,
                    TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
            if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
                // Not found in the unspent map. Try again with the spent map.
                result = input.connect(spent, TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
                if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
                    // Not found in the unspent and spent maps. Try again with
                    // the pending map.
                    result = input.connect(pending, TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
                    if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
                        // Doesn't spend any of our outputs or is coinbase.
                        continue;
                    }
                }
            }

            TransactionOutput output = checkNotNull(input.getConnectedOutput());
            if (result == TransactionInput.ConnectionResult.ALREADY_SPENT) {
                if (fromChain) {
                    // Can be:
                    // (1) We already marked this output as spent when we saw
                    // the pending transaction (most likely).
                    // Now it's being confirmed of course, we cannot mark it as
                    // spent again.
                    // (2) A double spend from chain: this will be handled later
                    // by findDoubleSpendsAgainst()/killTxns().
                    //
                    // In any case, nothing to do here.
                } else {
                    // We saw two pending transactions that double spend each
                    // other. We don't know which will win.
                    // This can happen in the case of bad network nodes that
                    // mutate transactions. Do a hex dump
                    // so the exact nature of the mutation can be examined.
                    log.warn("Saw two pending transactions double spend each other");
                    log.warn("  offending input is input {}", tx.getInputs().indexOf(input));
                    log.warn("{}: {}", tx.getHash(), Utils.HEX.encode(tx.unsafeBitcoinSerialize()));
                    Transaction other = output.getSpentBy().getParentTransaction();
                    log.warn("{}: {}", other.getHash(), Utils.HEX.encode(other.unsafeBitcoinSerialize()));
                }
            } else if (result == TransactionInput.ConnectionResult.SUCCESS) {
                // Otherwise we saw a transaction spend our coins, but we didn't
                // try and spend them ourselves yet.
                // The outputs are already marked as spent by the connect call
                // above, so check if there are any more for
                // us to use. Move if not.
                Transaction connected = checkNotNull(input.getConnectedTransaction());
                log.info("  marked {} as spent by {}", input.getOutpoint(), tx.getHashAsString());
                maybeMovePool(connected, "prevtx");
                // Just because it's connected doesn't mean it's actually ours:
                // sometimes we have total visibility.
                if (output.isMineOrWatched(this)) {
                    checkState(myUnspents.remove(output));
                }
            }
        }
        // Now check each output and see if there is a pending transaction which
        // spends it. This shouldn't normally
        // ever occur because we expect transactions to arrive in temporal
        // order, but this assumption can be violated
        // when we receive a pending transaction from the mempool that is
        // relevant to us, which spends coins that we
        // didn't see arrive on the best chain yet. For instance, because of a
        // chain replay or because of our keys were
        // used by another wallet somewhere else. Also, unconfirmed transactions
        // can arrive from the mempool in more or
        // less random order.
        for (Transaction pendingTx : pending.values()) {
            for (TransactionInput input : pendingTx.getInputs()) {
                TransactionInput.ConnectionResult result = input.connect(tx,
                        TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
                if (fromChain) {
                    // This TX is supposed to have just appeared on the best
                    // chain, so its outputs should not be marked
                    // as spent yet. If they are, it means something is
                    // happening out of order.
                    checkState(result != TransactionInput.ConnectionResult.ALREADY_SPENT);
                }
                if (result == TransactionInput.ConnectionResult.SUCCESS) {
                    log.info("Connected pending tx input {}:{}", pendingTx.getHashAsString(),
                            pendingTx.getInputs().indexOf(input));
                    // The unspents map might not have it if we never saw this
                    // tx until it was included in the chain
                    // and thus becomes spent the moment we become aware of it.
                    if (myUnspents.remove(input.getConnectedOutput()))
                        log.info("Removed from UNSPENTS: {}", input.getConnectedOutput());
                }
            }
        }
        if (!fromChain) {
            maybeMovePool(tx, "pendingtx");
        } else {
            // If the transactions outputs are now all spent, it will be moved
            // into the spent pool by the
            // processTxFromBestChain method.
        }
    }

    // Updates the wallet when a double spend occurs. overridingTx can be null
    // for the case of coinbases
    private void killTxns(Set<Transaction> txnsToKill, @Nullable Transaction overridingTx) {
        LinkedList<Transaction> work = new LinkedList<Transaction>(txnsToKill);
        while (!work.isEmpty()) {
            final Transaction tx = work.poll();
            log.warn("TX {} killed{}", tx.getHashAsString(),
                    overridingTx != null ? " by " + overridingTx.getHashAsString() : "");
            log.warn("Disconnecting each input and moving connected transactions.");
            // TX could be pending (finney attack), or in unspent/spent
            // (coinbase killed by reorg).
            pending.remove(tx.getHash());
            unspent.remove(tx.getHash());
            spent.remove(tx.getHash());
            addWalletTransaction(Pool.DEAD, tx);
            for (TransactionInput deadInput : tx.getInputs()) {
                Transaction connected = deadInput.getConnectedTransaction();
                if (connected == null)
                    continue;

                deadInput.disconnect();
                maybeMovePool(connected, "kill");
            }
            // tx.getConfidence().setOverridingTransaction(overridingTx);
            confidenceChanged.put(tx, TransactionConfidence.Listener.ChangeReason.TYPE);
            // Now kill any transactions we have that depended on this one.
            for (TransactionOutput deadOutput : tx.getOutputs()) {
                if (myUnspents.remove(deadOutput))
                    log.info("XX Removed from UNSPENTS: {}", deadOutput);
                TransactionInput connected = deadOutput.getSpentBy();
                if (connected == null)
                    continue;
                final Transaction parentTransaction = connected.getParentTransaction();
                log.info("This death invalidated dependent tx {}", parentTransaction.getHash());
                work.push(parentTransaction);
            }
        }
        if (overridingTx == null)
            return;
        log.warn("Now attempting to connect the inputs of the overriding transaction.");
        for (TransactionInput input : overridingTx.getInputs()) {
            TransactionInput.ConnectionResult result = input.connect(unspent,
                    TransactionInput.ConnectMode.DISCONNECT_ON_CONFLICT);
            if (result == TransactionInput.ConnectionResult.SUCCESS) {
                maybeMovePool(input.getConnectedTransaction(), "kill");
                myUnspents.remove(input.getConnectedOutput());
                log.info("Removing from UNSPENTS: {}", input.getConnectedOutput());
            } else {
                result = input.connect(spent, TransactionInput.ConnectMode.DISCONNECT_ON_CONFLICT);
                if (result == TransactionInput.ConnectionResult.SUCCESS) {
                    maybeMovePool(input.getConnectedTransaction(), "kill");
                    myUnspents.remove(input.getConnectedOutput());
                    log.info("Removing from UNSPENTS: {}", input.getConnectedOutput());
                }
            }
        }
    }

    /**
     * If the transactions outputs are all marked as spent, and it's in the
     * unspent map, move it. If the owned transactions outputs are not all
     * marked as spent, and it's in the spent map, move it.
     */
    private void maybeMovePool(Transaction tx, String context) {
        checkState(lock.isHeldByCurrentThread());
        if (tx.isEveryOwnedOutputSpent(this)) {
            // There's nothing left I can spend in this transaction.
            if (unspent.remove(tx.getHash()) != null) {
                if (log.isInfoEnabled()) {
                    log.info("  {} {} <-unspent ->spent", tx.getHashAsString(), context);
                }
                spent.put(tx.getHash(), tx);
            }
        } else {
            if (spent.remove(tx.getHash()) != null) {
                if (log.isInfoEnabled()) {
                    log.info("  {} {} <-spent ->unspent", tx.getHashAsString(), context);
                }
                unspent.put(tx.getHash(), tx);
            }
        }
    }

    // endregion

    /******************************************************************************************************************/

    // region Event listeners

    /**
     * Adds an event listener object. Methods on this object are called when
     * something interesting happens, like receiving money. Runs the listener
     * methods in the user thread.
     */
    public void addChangeEventListener(WalletChangeEventListener listener) {
        addChangeEventListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when
     * something interesting happens, like receiving money. The listener is
     * executed by the given executor.
     */
    public void addChangeEventListener(Executor executor, WalletChangeEventListener listener) {
        // This is thread safe, so we don't need to take the lock.
        changeListeners.add(new ListenerRegistration<WalletChangeEventListener>(listener, executor));
    }

    /**
     * Adds an event listener object called when coins are received. Runs the
     * listener methods in the user thread.
     */
    public void addCoinsReceivedEventListener(WalletCoinsReceivedEventListener listener) {
        addCoinsReceivedEventListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object called when coins are received. The
     * listener is executed by the given executor.
     */
    public void addCoinsReceivedEventListener(Executor executor, WalletCoinsReceivedEventListener listener) {
        // This is thread safe, so we don't need to take the lock.
        coinsReceivedListeners.add(new ListenerRegistration<WalletCoinsReceivedEventListener>(listener, executor));
    }

    /**
     * Adds an event listener object called when coins are sent. Runs the
     * listener methods in the user thread.
     */
    public void addCoinsSentEventListener(WalletCoinsSentEventListener listener) {
        addCoinsSentEventListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object called when coins are sent. The listener is
     * executed by the given executor.
     */
    public void addCoinsSentEventListener(Executor executor, WalletCoinsSentEventListener listener) {
        // This is thread safe, so we don't need to take the lock.
        coinsSentListeners.add(new ListenerRegistration<WalletCoinsSentEventListener>(listener, executor));
    }

    /**
     * Adds an event listener object. Methods on this object are called when
     * keys are added. The listener is executed in the user thread.
     */
    public void addKeyChainEventListener(KeyChainEventListener listener) {
        keyChainGroup.addEventListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when
     * keys are added. The listener is executed by the given executor.
     */
    public void addKeyChainEventListener(Executor executor, KeyChainEventListener listener) {
        keyChainGroup.addEventListener(listener, executor);
    }

    protected void maybeQueueOnWalletChanged() {
        // Don't invoke the callback in some circumstances, eg, whilst we are
        // re-organizing or fiddling with
        // transactions due to a new block arriving. It will be called later
        // instead.
        checkState(lock.isHeldByCurrentThread());
        checkState(onWalletChangedSuppressions >= 0);
        if (onWalletChangedSuppressions > 0)
            return;
        for (final ListenerRegistration<WalletChangeEventListener> registration : changeListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onWalletChanged(Wallet.this);
                }
            });
        }
    }

    protected void queueOnCoinsReceived(final Transaction tx, final Coin balance, final Coin newBalance) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<WalletCoinsReceivedEventListener> registration : coinsReceivedListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onCoinsReceived(Wallet.this, tx, balance, newBalance);
                }
            });
        }
    }

    protected void queueOnCoinsSent(final Transaction tx, final Coin prevBalance, final Coin newBalance) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<WalletCoinsSentEventListener> registration : coinsSentListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onCoinsSent(Wallet.this, tx, prevBalance, newBalance);
                }
            });
        }
    }

    protected void queueOnReorganize() {
        checkState(lock.isHeldByCurrentThread());
        checkState(insideReorg);
        for (final ListenerRegistration<WalletReorganizeEventListener> registration : reorganizeListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onReorganize(Wallet.this);
                }
            });
        }
    }

    protected void queueOnScriptsChanged(final List<Script> scripts, final boolean isAddingScripts) {
        for (final ListenerRegistration<ScriptsChangeEventListener> registration : scriptChangeListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onScriptsChanged(Wallet.this, scripts, isAddingScripts);
                }
            });
        }
    }

    // endregion

    /******************************************************************************************************************/

    // region Vending transactions and other internal state

    /**
     * Returns a set of all transactions in the wallet.
     * 
     * @param includeDead
     *            If true, transactions that were overridden by a double spend
     *            are included.
     */
    public Set<Transaction> getTransactions(boolean includeDead) {
        lock.lock();
        try {
            Set<Transaction> all = new HashSet<Transaction>();
            all.addAll(unspent.values());
            all.addAll(spent.values());
            all.addAll(pending.values());
            if (includeDead)
                all.addAll(dead.values());
            return all;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a set of all WalletTransactions in the wallet.
     */
    public Iterable<WalletTransaction> getWalletTransactions() {
        lock.lock();
        try {
            Set<WalletTransaction> all = new HashSet<WalletTransaction>();
            addWalletTransactionsToSet(all, Pool.UNSPENT, unspent.values());
            addWalletTransactionsToSet(all, Pool.SPENT, spent.values());
            addWalletTransactionsToSet(all, Pool.DEAD, dead.values());
            addWalletTransactionsToSet(all, Pool.PENDING, pending.values());
            return all;
        } finally {
            lock.unlock();
        }
    }

    private static void addWalletTransactionsToSet(Set<WalletTransaction> txns, Pool poolType,
            Collection<Transaction> pool) {
        for (Transaction tx : pool) {
            txns.add(new WalletTransaction(poolType, tx));
        }
    }

    /**
     * Adds a transaction that has been associated with a particular wallet
     * pool. This is intended for usage by deserialization code, such as the
     * {@link WalletProtobufSerializer} class. It isn't normally useful for
     * applications. It does not trigger auto saving.
     */
    public void addWalletTransaction(WalletTransaction wtx) {
        lock.lock();
        try {
            addWalletTransaction(wtx.getPool(), wtx.getTransaction());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds the given transaction to the given pools and registers a confidence
     * change listener on it.
     */
    private void addWalletTransaction(Pool pool, Transaction tx) {
        checkState(lock.isHeldByCurrentThread());
        transactions.put(tx.getHash(), tx);
        switch (pool) {
        case UNSPENT:
            checkState(unspent.put(tx.getHash(), tx) == null);
            break;
        case SPENT:
            checkState(spent.put(tx.getHash(), tx) == null);
            break;
        case PENDING:
            checkState(pending.put(tx.getHash(), tx) == null);
            break;
        case DEAD:
            checkState(dead.put(tx.getHash(), tx) == null);
            break;
        default:
            throw new RuntimeException("Unknown wallet transaction type " + pool);
        }
        if (pool == Pool.UNSPENT || pool == Pool.PENDING) {
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.isAvailableForSpending() && output.isMineOrWatched(this))
                    myUnspents.add(output);
            }
        }
        // This is safe even if the listener has been added before, as
        // TransactionConfidence ignores duplicate
        // registration requests. That makes the code in the wallet simpler.
        // CUI tx.getConfidence().addEventListener(Threading.SAME_THREAD,
        // txConfidenceListener);
    }

    /**
     * Returns all non-dead, active transactions ordered by recency.
     */
    public List<Transaction> getTransactionsByTime() {
        return getRecentTransactions(0, false);
    }

    /**
     * Returns an list of N transactions, ordered by increasing age.
     * Transactions on side chains are not included. Dead transactions
     * (overridden by double spends) are optionally included.
     * <p>
     * <p/>
     * Note: the current implementation is O(num transactions in wallet).
     * Regardless of how many transactions are requested, the cost is always the
     * same. In future, requesting smaller numbers of transactions may be faster
     * depending on how the wallet is implemented (eg if backed by a database).
     */
    public List<Transaction> getRecentTransactions(int numTransactions, boolean includeDead) {
        lock.lock();
        try {
            checkArgument(numTransactions >= 0);
            // Firstly, put all transactions into an array.
            int size = unspent.size() + spent.size() + pending.size();
            if (numTransactions > size || numTransactions == 0) {
                numTransactions = size;
            }
            ArrayList<Transaction> all = new ArrayList<Transaction>(getTransactions(includeDead));
            // Order by update time.
            Collections.sort(all, Transaction.SORT_TX_BY_UPDATE_TIME);
            if (numTransactions == all.size()) {
                return all;
            } else {
                all.subList(numTransactions, all.size()).clear();
                return all;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a transaction object given its hash, if it exists in this wallet,
     * or null otherwise.
     */
    @Nullable
    public Transaction getTransaction(Sha256Hash hash) {
        lock.lock();
        try {
            return transactions.get(hash);
        } finally {
            lock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<Sha256Hash, Transaction> getTransactionPool(Pool pool) {
        lock.lock();
        try {
            switch (pool) {
            case UNSPENT:
                return unspent;
            case SPENT:
                return spent;
            case PENDING:
                return pending;
            case DEAD:
                return dead;
            default:
                throw new RuntimeException("Unknown wallet transaction type " + pool);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Prepares the wallet for a blockchain replay. Removes all transactions (as
     * they would get in the way of the replay) and makes the wallet think it
     * has never seen a block. {@link WalletEventListener#onWalletChanged} will
     * be fired.
     */
    public void reset() {
        lock.lock();
        try {
            clearTransactions();
            lastBlockSeenHash = null;
            lastBlockSeenHeight = -1; // Magic value for 'never'.
            lastBlockSeenTimeSecs = 0;
            saveLater();
            maybeQueueOnWalletChanged();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deletes transactions which appeared above the given block height from the
     * wallet, but does not touch the keys. This is useful if you have some keys
     * and wish to replay the block chain into the wallet in order to pick them
     * up. Triggers auto saving.
     */
    public void clearTransactions(int fromHeight) {
        lock.lock();
        try {
            if (fromHeight == 0) {
                clearTransactions();
                saveLater();
            } else {
                throw new UnsupportedOperationException();
            }
        } finally {
            lock.unlock();
        }
    }

    private void clearTransactions() {
        unspent.clear();
        spent.clear();
        pending.clear();
        dead.clear();
        transactions.clear();
        myUnspents.clear();
    }

    /**
     * Returns all the outputs that match addresses or scripts added via
     * {@link #addWatchedAddress(Address)} or
     * {@link #addWatchedScripts(java.util.List)}.
     * 
     * @param excludeImmatureCoinbases
     *            Whether to ignore outputs that are unspendable due to being
     *            immature.
     */
    public List<TransactionOutput> getWatchedOutputs(boolean excludeImmatureCoinbases) {
        lock.lock();
        keyChainGroupLock.lock();
        try {
            LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
            for (Transaction tx : Iterables.concat(unspent.values(), pending.values())) {
                if (excludeImmatureCoinbases)
                    continue;
                for (TransactionOutput output : tx.getOutputs()) {
                    if (!output.isAvailableForSpending())
                        continue;
                    try {
                        Script scriptPubKey = output.getScriptPubKey();
                        if (!watchedScripts.contains(scriptPubKey))
                            continue;
                        candidates.add(output);
                    } catch (ScriptException e) {
                        // Ignore
                    }
                }
            }
            return candidates;
        } finally {
            keyChainGroupLock.unlock();
            lock.unlock();
        }
    }

    /**
     * Clean up the wallet. Currently, it only removes risky pending transaction
     * from the wallet and only if their outputs have not been spent.
     */
    public void cleanup() {
    }

    EnumSet<Pool> getContainingPools(Transaction tx) {
        lock.lock();
        try {
            EnumSet<Pool> result = EnumSet.noneOf(Pool.class);
            Sha256Hash txHash = tx.getHash();
            if (unspent.containsKey(txHash)) {
                result.add(Pool.UNSPENT);
            }
            if (spent.containsKey(txHash)) {
                result.add(Pool.SPENT);
            }
            if (pending.containsKey(txHash)) {
                result.add(Pool.PENDING);
            }
            if (dead.containsKey(txHash)) {
                result.add(Pool.DEAD);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    public int getPoolSize(WalletTransaction.Pool pool) {
        lock.lock();
        try {
            switch (pool) {
            case UNSPENT:
                return unspent.size();
            case SPENT:
                return spent.size();
            case PENDING:
                return pending.size();
            case DEAD:
                return dead.size();
            }
            throw new RuntimeException("Unreachable");
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting
    public boolean poolContainsTxHash(final WalletTransaction.Pool pool, final Sha256Hash txHash) {
        lock.lock();
        try {
            switch (pool) {
            case UNSPENT:
                return unspent.containsKey(txHash);
            case SPENT:
                return spent.containsKey(txHash);
            case PENDING:
                return pending.containsKey(txHash);
            case DEAD:
                return dead.containsKey(txHash);
            }
            throw new RuntimeException("Unreachable");
        } finally {
            lock.unlock();
        }
    }

    /** Returns a copy of the internal unspent outputs list */
    public List<TransactionOutput> getUnspents() {
        lock.lock();
        try {
            return new ArrayList<TransactionOutput>(myUnspents);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an immutable view of the transactions currently waiting for
     * network confirmations.
     */
    public Collection<Transaction> getPendingTransactions() {
        lock.lock();
        try {
            return Collections.unmodifiableCollection(pending.values());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the earliest creation time of keys or watched scripts in this
     * wallet, in seconds since the epoch, ie the min of
     * {@link net.bigtangle.core.ECKey#getCreationTimeSeconds()}. This can
     * return zero if at least one key does not have that data (was created
     * before key timestamping was implemented).
     * <p>
     *
     * This method is most often used in conjunction with
     * {@link PeerGroup#setFastCatchupTimeSecs(long)} in order to optimize chain
     * download for new users of wallet apps. Backwards compatibility notice: if
     * you get zero from this method, you can instead use the time of the first
     * release of your software, as it's guaranteed no users will have wallets
     * pre-dating this time.
     * <p>
     *
     * If there are no keys in the wallet, the current time is returned.
     */

    public long getEarliestKeyCreationTime() {
        keyChainGroupLock.lock();
        try {
            long earliestTime = keyChainGroup.getEarliestKeyCreationTime();
            for (Script script : watchedScripts)
                earliestTime = Math.min(script.getCreationTimeSeconds(), earliestTime);
            if (earliestTime == Long.MAX_VALUE)
                return Utils.currentTimeSeconds();
            return earliestTime;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Returns the hash of the last seen best-chain block, or null if the wallet
     * is too old to store this data.
     */
    @Nullable
    public Sha256Hash getLastBlockSeenHash() {
        lock.lock();
        try {
            return lastBlockSeenHash;
        } finally {
            lock.unlock();
        }
    }

    public void setLastBlockSeenHash(@Nullable Sha256Hash lastBlockSeenHash) {
        lock.lock();
        try {
            this.lastBlockSeenHash = lastBlockSeenHash;
        } finally {
            lock.unlock();
        }
    }

    public void setLastBlockSeenHeight(int lastBlockSeenHeight) {
        lock.lock();
        try {
            this.lastBlockSeenHeight = lastBlockSeenHeight;
        } finally {
            lock.unlock();
        }
    }

    public void setLastBlockSeenTimeSecs(long timeSecs) {
        lock.lock();
        try {
            lastBlockSeenTimeSecs = timeSecs;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the UNIX time in seconds since the epoch extracted from the last
     * best seen block header. This timestamp is <b>not</b> the local time at
     * which the block was first observed by this application but rather what
     * the block (i.e. miner) self declares. It is allowed to have some
     * significant drift from the real time at which the block was found,
     * although most miners do use accurate times. If this wallet is old and
     * does not have a recorded time then this method returns zero.
     */
    public long getLastBlockSeenTimeSecs() {
        lock.lock();
        try {
            return lastBlockSeenTimeSecs;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a {@link Date} representing the time extracted from the last best
     * seen block header. This timestamp is <b>not</b> the local time at which
     * the block was first observed by this application but rather what the
     * block (i.e. miner) self declares. It is allowed to have some significant
     * drift from the real time at which the block was found, although most
     * miners do use accurate times. If this wallet is old and does not have a
     * recorded time then this method returns null.
     */
    @Nullable
    public Date getLastBlockSeenTime() {
        final long secs = getLastBlockSeenTimeSecs();
        if (secs == 0)
            return null;
        else
            return new Date(secs * 1000);
    }

    /**
     * Returns the height of the last seen best-chain block. Can be 0 if a
     * wallet is brand new or -1 if the wallet is old and doesn't have that
     * data.
     */
    public int getLastBlockSeenHeight() {
        lock.lock();
        try {
            return lastBlockSeenHeight;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the version of the Wallet. This is an int you can use to indicate
     * which versions of wallets your code understands, and which come from the
     * future (and hence cannot be safely loaded).
     */
    public int getVersion() {
        return version;
    }

    /**
     * Set the version number of the wallet. See {@link Wallet#getVersion()}.
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Set the description of the wallet. This is a Unicode encoding string
     * typically entered by the user as descriptive text for the wallet.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Get the description of the wallet. See
     * {@link Wallet#setDescription(String))}
     */
    public String getDescription() {
        return description;
    }

    // endregion

    /******************************************************************************************************************/

    // region Balance and balance futures

    /**
     * <p>
     * It's possible to calculate a wallets balance from multiple points of
     * view. This enum selects which {@link #getBalance(BalanceType)} should
     * use.
     * </p>
     *
     * <p>
     * Consider a real-world example: you buy a snack costing $5 but you only
     * have a $10 bill. At the start you have $10 viewed from every possible
     * angle. After you order the snack you hand over your $10 bill. From the
     * perspective of your wallet you have zero dollars (AVAILABLE). But you
     * know in a few seconds the shopkeeper will give you back $5 change so most
     * people in practice would say they have $5 (ESTIMATED).
     * </p>
     *
     * <p>
     * The fact that the wallet can track transactions which are not spendable
     * by itself ("watching wallets") adds another type of balance to the mix.
     * Although the wallet won't do this by default, advanced use cases that
     * override the relevancy checks can end up with a mix of spendable and
     * unspendable transactions.
     * </p>
     */
    public enum BalanceType {
        /**
         * Balance calculated assuming all pending transactions are in fact
         * included into the best chain by miners. This includes the value of
         * immature coinbase transactions.
         */
        ESTIMATED,

        /**
         * Balance that could be safely used to create new spends, if we had all
         * the needed private keys. This is whatever the default coin selector
         * would make available, which by default means transaction outputs with
         * at least 1 confirmation and pending transactions created by our own
         * wallet which have been propagated across the network. Whether we
         * <i>actually</i> have the private keys or not is irrelevant for this
         * balance type.
         */
        AVAILABLE,

        /**
         * Same as ESTIMATED but only for outputs we have the private keys for
         * and can sign ourselves.
         */
        ESTIMATED_SPENDABLE,
        /**
         * Same as AVAILABLE but only for outputs we have the private keys for
         * and can sign ourselves.
         */
        AVAILABLE_SPENDABLE
    }

    private static class BalanceFutureRequest {
        public SettableFuture<Coin> future;
        public Coin value;
        public BalanceType type;
    }

    @GuardedBy("lock")
    private List<BalanceFutureRequest> balanceFutureRequests = Lists.newLinkedList();

    /**
     * Returns the amount of bitcoin ever received via output. <b>This is not
     * the balance!</b> If an output spends from a transaction whose inputs are
     * also to our wallet, the input amounts are deducted from the outputs
     * contribution, with a minimum of zero contribution. The idea behind this
     * is we avoid double counting money sent to us.
     * 
     * @return the total amount of satoshis received, regardless of whether it
     *         was spent or not.
     */
    public Coin getTotalReceived() {
        Coin total = Coin.ZERO;

        // Include outputs to us if they were not just change outputs, ie the
        // inputs to us summed to less
        // than the outputs to us.
        for (Transaction tx : transactions.values()) {
            Coin txTotal = Coin.ZERO;
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.isMine(this)) {
                    txTotal = txTotal.add(output.getValue());
                }
            }
            for (TransactionInput in : tx.getInputs()) {
                TransactionOutput prevOut = in.getConnectedOutput();
                if (prevOut != null && prevOut.isMine(this)) {
                    txTotal = txTotal.subtract(prevOut.getValue());
                }
            }
            if (txTotal.isPositive()) {
                total = total.add(txTotal);
            }
        }
        return total;
    }

    /**
     * Returns the amount of bitcoin ever sent via output. If an output is sent
     * to our own wallet, because of change or rotating keys or whatever, we do
     * not count it. If the wallet was involved in a shared transaction, i.e.
     * there is some input to the transaction that we don't have the key for,
     * then we multiply the sum of the output values by the proportion of
     * satoshi coming in to our inputs. Essentially we treat inputs as pooling
     * into the transaction, becoming fungible and being equally distributed to
     * all outputs.
     * 
     * @return the total amount of satoshis sent by us
     */
    public Coin getTotalSent() {
        Coin total = Coin.ZERO;

        for (Transaction tx : transactions.values()) {
            // Count spent outputs to only if they were not to us. This means we
            // don't count change outputs.
            Coin txOutputTotal = Coin.ZERO;
            for (TransactionOutput out : tx.getOutputs()) {
                if (out.isMine(this) == false) {
                    txOutputTotal = txOutputTotal.add(out.getValue());
                }
            }

            // Count the input values to us
            Coin txOwnedInputsTotal = Coin.ZERO;
            for (TransactionInput in : tx.getInputs()) {
                TransactionOutput prevOut = in.getConnectedOutput();
                if (prevOut != null && prevOut.isMine(this)) {
                    txOwnedInputsTotal = txOwnedInputsTotal.add(prevOut.getValue());
                }
            }

            // If there is an input that isn't from us, i.e. this is a shared
            // transaction
            Coin txInputsTotal = tx.getInputSum();
            if (txOwnedInputsTotal != txInputsTotal) {

                // multiply our output total by the appropriate proportion to
                // account for the inputs that we don't own
                BigInteger txOutputTotalNum = new BigInteger(txOutputTotal.toString());
                txOutputTotalNum = txOutputTotalNum.multiply(new BigInteger(txOwnedInputsTotal.toString()));
                txOutputTotalNum = txOutputTotalNum.divide(new BigInteger(txInputsTotal.toString()));
                txOutputTotal = Coin.valueOf(txOutputTotalNum.longValue(), NetworkParameters.BIGNETCOIN_TOKENID);
            }
            total = total.add(txOutputTotal);

        }
        return total;
    }

    // endregion

    /******************************************************************************************************************/

    // region Creating and sending transactions

    /**
     * A SendResult is returned to you as part of sending coins to a recipient.
     */
    public static class SendResult {
        /** The Bitcoin transaction message that moves the money. */
        public Transaction tx;
        /**
         * A future that will complete once the tx message has been successfully
         * broadcast to the network. This is just the result of calling
         * broadcast.future()
         */
        public ListenableFuture<Transaction> broadcastComplete;

    }

    /**
     * Enumerates possible resolutions for missing signatures.
     */
    public enum MissingSigsMode {
        /** Input script will have OP_0 instead of missing signatures */
        USE_OP_ZERO,
        /**
         * Missing signatures will be replaced by dummy sigs. This is useful
         * when you'd like to know the fee for a transaction without knowing the
         * user's password, as fee depends on size.
         */
        USE_DUMMY_SIG,
        /**
         * If signature is missing,
         * {@link org.bitcoinj.signers.TransactionSigner.MissingSignatureException}
         * will be thrown for P2SH and {@link ECKey.MissingPrivateKeyException}
         * for other tx types.
         */
        THROW
    }

    /**
     * <p>
     * Statelessly creates a transaction that sends the given value to address.
     * The change is sent to {@link Wallet#currentChangeAddress()}, so you must
     * have added at least one key.
     * </p>
     *
     * <p>
     * If you just want to send money quickly, you probably want
     * {@link Wallet#sendCoins(TransactionBroadcaster, Address, Coin)} instead.
     * That will create the sending transaction, commit to the wallet and
     * broadcast it to the network all in one go. This method is lower level and
     * lets you see the proposed transaction before anything is done with it.
     * </p>
     *
     * <p>
     * This is a helper method that is equivalent to using
     * {@link SendRequest#to(Address, Coin)} followed by
     * {@link Wallet#completeTx(Wallet.SendRequest)} and returning the requests
     * transaction object. Note that this means a fee may be automatically added
     * if required, if you want more control over the process, just do those two
     * steps yourself.
     * </p>
     *
     * <p>
     * IMPORTANT: This method does NOT update the wallet. If you call createSend
     * again you may get two transactions that spend the same coins. You have to
     * call {@link Wallet#commitTx(Transaction)} on the created transaction to
     * prevent this, but that should only occur once the transaction has been
     * accepted by the network. This implies you cannot have more than one
     * outstanding sending tx at once.
     * </p>
     *
     * <p>
     * You MUST ensure that the value is not smaller than
     * {@link Transaction#MIN_NONDUST_OUTPUT} or the transaction will almost
     * certainly be rejected by the network as dust.
     * </p>
     *
     * @param address
     *            The Bitcoin address to send the money to.
     * @param value
     *            How much currency to send.
     * @return either the created Transaction or null if there are insufficient
     *         coins.
     * @throws Exception
     * @throws JsonProcessingException
     * @throws UnsupportedEncodingException
     * @throws DustySendRequested
     *             if the resultant transaction would violate the dust rules.
     * @throws CouldNotAdjustDownwards
     *             if emptying the wallet was requested and the output can't be
     *             shrunk for fees without violating a protocol rule.
     * @throws ExceededMaxTransactionSize
     *             if the resultant transaction is too big for Bitcoin to
     *             process.
     * @throws MultipleOpReturnRequested
     *             if there is more than one OP_RETURN output for the resultant
     *             transaction.
     */
    public Transaction createSend(Address address, Coin value)
            throws UnsupportedEncodingException, JsonProcessingException, Exception {
        SendRequest req = SendRequest.to(address, value);
        if (params.getId().equals(NetworkParameters.ID_UNITTESTNET))
            req.shuffleOutputs = false;
        completeTx(req);
        return req.tx;
    }

    /**
     * Class of exceptions thrown in {@link Wallet#completeTx(SendRequest)}.
     */
    public static class CompletionException extends RuntimeException {
    }

    /**
     * Thrown if the resultant transaction would violate the dust rules (an
     * output that's too small to be worthwhile).
     */
    public static class DustySendRequested extends CompletionException {
    }

    /**
     * Thrown if there is more than one OP_RETURN output for the resultant
     * transaction.
     */
    public static class MultipleOpReturnRequested extends CompletionException {
    }

    /**
     * Thrown when we were trying to empty the wallet, and the total amount of
     * money we were trying to empty after being reduced for the fee was smaller
     * than the min payment. Note that the missing field will be null in this
     * case.
     */
    public static class CouldNotAdjustDownwards extends CompletionException {
    }

    /**
     * Thrown if the resultant transaction is too big for Bitcoin to process.
     * Try breaking up the amounts of value.
     */
    public static class ExceededMaxTransactionSize extends CompletionException {
    }

    /**
     * Given a spend request containing an incomplete transaction, makes it
     * valid by adding outputs and signed inputs according to the instructions
     * in the request. The transaction in the request is modified by this
     * method.
     *
     * @param req
     *            a SendRequest that contains the incomplete transaction and
     *            details for how to make it valid.
     * @throws InsufficientMoneyException
     *             if the request could not be completed due to not enough
     *             balance.
     * @throws IllegalArgumentException
     *             if you try and complete the same SendRequest twice
     * @throws DustySendRequested
     *             if the resultant transaction would violate the dust rules.
     * @throws CouldNotAdjustDownwards
     *             if emptying the wallet was requested and the output can't be
     *             shrunk for fees without violating a protocol rule.
     * @throws ExceededMaxTransactionSize
     *             if the resultant transaction is too big for Bitcoin to
     *             process.
     * @throws MultipleOpReturnRequested
     *             if there is more than one OP_RETURN output for the resultant
     *             transaction.
     */
    /*
     * public void completeTx(SendRequest req) throws InsufficientMoneyException
     * { lock.lock(); try { checkArgument(!req.completed,
     * "Given SendRequest has already been completed."); // Calculate the amount
     * of value we need to import. Coin value = Coin.ZERO; for
     * (TransactionOutput output : req.tx.getOutputs()) { value =
     * value.add(output.getValue()); }
     * 
     * log.
     * info("Completing send tx with {} outputs totalling {} and a fee of {}/kB"
     * , req.tx.getOutputs().size(), value.toFriendlyString(),
     * req.feePerKb.toFriendlyString());
     * 
     * // If any inputs have already been added, we don't need to get their
     * value from wallet Coin totalInput = Coin.ZERO; for (TransactionInput
     * input : req.tx.getInputs()) if (input.getConnectedOutput() != null)
     * totalInput = totalInput.add(input.getConnectedOutput().getValue()); else
     * log.
     * warn("SendRequest transaction already has inputs but we don't know how much they are worth - they will be added to fee."
     * ); value = value.subtract(totalInput);
     * 
     * List<TransactionInput> originalInputs = new
     * ArrayList<TransactionInput>(req.tx.getInputs());
     * 
     * // Check for dusty sends and the OP_RETURN limit. if
     * (req.ensureMinRequiredFee && !req.emptyWallet) { // Min fee checking is
     * handled later for emptyWallet. int opReturnCount = 0; for
     * (TransactionOutput output : req.tx.getOutputs()) { if (output.isDust())
     * throw new DustySendRequested(); if
     * (output.getScriptPubKey().isOpReturn()) ++opReturnCount; } if
     * (opReturnCount > 1) // Only 1 OP_RETURN per transaction allowed. throw
     * new MultipleOpReturnRequested(); }
     * 
     * // Calculate a list of ALL potential candidates for spending and then ask
     * a coin selector to provide us // with the actual outputs that'll be used
     * to gather the required amount of value. In this way, users // can
     * customize coin selection policies. The call below will ignore immature
     * coinbases and outputs // we don't have the keys for.
     * List<TransactionOutput> candidates = calculateAllSpendCandidates(true,
     * req.missingSigsMode == MissingSigsMode.THROW);
     * 
     * CoinSelection bestCoinSelection; TransactionOutput bestChangeOutput =
     * null; if (!req.emptyWallet) { // This can throw
     * InsufficientMoneyException. FeeCalculation feeCalculation =
     * calculateFee(req, value, originalInputs, req.ensureMinRequiredFee,
     * candidates); bestCoinSelection = feeCalculation.bestCoinSelection;
     * bestChangeOutput = feeCalculation.bestChangeOutput; } else { // We're
     * being asked to empty the wallet. What this means is ensuring "tx" has
     * only a single output // of the total value we can currently spend as
     * determined by the selector, and then subtracting the fee.
     * checkState(req.tx.getOutputs().size() == 1,
     * "Empty wallet TX must have a single output only."); CoinSelector selector
     * = req.coinSelector == null ? coinSelector : req.coinSelector;
     * bestCoinSelection = selector.select(params.getMaxMoney(), candidates);
     * candidates = null; // Selector took ownership and might have changed
     * candidates. Don't access again.
     * req.tx.getOutput(0).setValue(bestCoinSelection.valueGathered);
     * log.info("  emptying {}",
     * bestCoinSelection.valueGathered.toFriendlyString()); }
     * 
     * for (TransactionOutput output : bestCoinSelection.gathered)
     * req.tx.addInput(output);
     * 
     * if (req.emptyWallet) { final Coin feePerKb = req.feePerKb == null ?
     * Coin.ZERO : req.feePerKb; if (!adjustOutputDownwardsForFee(req.tx,
     * bestCoinSelection, feePerKb, req.ensureMinRequiredFee)) throw new
     * CouldNotAdjustDownwards(); }
     * 
     * if (bestChangeOutput != null) { req.tx.addOutput(bestChangeOutput);
     * log.info("  with {} change",
     * bestChangeOutput.getValue().toFriendlyString()); }
     * 
     * // Now shuffle the outputs to obfuscate which is the change. if
     * (req.shuffleOutputs) req.tx.shuffleOutputs();
     * 
     * // Now sign the inputs, thus proving that we are entitled to redeem the
     * connected outputs. if (req.signInputs) signTransaction(req);
     * 
     * // Check size. final int size = req.tx.unsafeBitcoinSerialize().length;
     * if (size > Transaction.MAX_STANDARD_TX_SIZE) throw new
     * ExceededMaxTransactionSize();
     * 
     * // Label the transaction as being self created. We can use this later to
     * spend its change output even before // the transaction is confirmed. We
     * deliberately won't bother notifying listeners here as there's not much //
     * point - the user isn't interested in a confidence transition they made
     * themselves.
     * req.tx.getConfidence().setSource(TransactionConfidence.Source.SELF); //
     * Label the transaction as being a user requested payment. This can be used
     * to render GUI wallet // transaction lists more appropriately, especially
     * when the wallet starts to generate transactions itself // for internal
     * purposes. req.tx.setPurpose(Transaction.Purpose.USER_PAYMENT); // Record
     * the exchange rate that was valid when the transaction was completed.
     * req.tx.setExchangeRate(req.exchangeRate); req.tx.setMemo(req.memo);
     * req.completed = true; log.info("  completed: {}", req.tx); } finally {
     * lock.unlock(); } }
     */
    /**
     * <p>
     * Given a send request containing transaction, attempts to sign it's
     * inputs. This method expects transaction to have all necessary inputs
     * connected or they will be ignored.
     * </p>
     * <p>
     * Actual signing is done by pluggable {@link #signers} and it's not
     * guaranteed that transaction will be complete in the end.
     * </p>
     */
    public void signTransaction(SendRequest req) {
        lock.lock();
        try {
            Transaction tx = req.tx;
            List<TransactionInput> inputs = tx.getInputs();
            List<TransactionOutput> outputs = tx.getOutputs();
            checkState(inputs.size() > 0);
            checkState(outputs.size() > 0);

            KeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(this, req.aesKey);

            int numInputs = tx.getInputs().size();
            for (int i = 0; i < numInputs; i++) {
                TransactionInput txIn = tx.getInput(i);
                if (txIn.getConnectedOutput() == null) {
                    // Missing connected output, assuming already signed.
                    continue;
                }

                Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
                RedeemData redeemData = txIn.getConnectedRedeemData(maybeDecryptingKeyBag);
                // checkNotNull(redeemData, "Transaction exists in wallet that
                // we cannot redeem: %s",
                // txIn.getOutpoint().getHash());
                if (redeemData != null)
                    txIn.setScriptSig(
                            scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript));
            }

            TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(tx);
            for (TransactionSigner signer : signers) {
                if (!signer.signInputs(proposal, maybeDecryptingKeyBag))
                    log.info("{} returned false for the tx", signer.getClass().getName());
            }

            // resolve missing sigs if any
            new MissingSigResolutionSigner(req.missingSigsMode).signInputs(proposal, maybeDecryptingKeyBag);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reduce the value of the first output of a transaction to pay the given
     * feePerKb as appropriate for its size.
     */
    protected boolean adjustOutputDownwardsForFee(Transaction tx, CoinSelection coinSelection, Coin feePerKb,
            boolean ensureMinRequiredFee) {
        final int size = tx.unsafeBitcoinSerialize().length + estimateBytesForSigning(coinSelection);
        Coin fee = feePerKb.multiply(size).divide(1000);
        if (ensureMinRequiredFee && fee.compareTo(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE) < 0)
            fee = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
        TransactionOutput output = tx.getOutput(0);
        output.setValue(output.getValue().subtract(fee));
        return !output.isDust();
    }

    /**
     * Returns true if this wallet has at least one of the private keys needed
     * to sign for this scriptPubKey. Returns false if the form of the script is
     * not known or if the script is OP_RETURN.
     */
    public boolean canSignFor(Script script) {
        if (script.isSentToRawPubKey()) {
            byte[] pubkey = script.getPubKey();
            ECKey key = findKeyFromPubKey(pubkey);
            return key != null && (key.isEncrypted() || key.hasPrivKey());
        }
        if (script.isPayToScriptHash()) {
            RedeemData data = findRedeemDataFromScriptHash(script.getPubKeyHash());
            return data != null && canSignFor(data.redeemScript);
        } else if (script.isSentToAddress()) {
            ECKey key = findKeyFromPubHash(script.getPubKeyHash());
            return key != null && (key.isEncrypted() || key.hasPrivKey());
        } else if (script.isSentToMultiSig()) {
            for (ECKey pubkey : script.getPubKeys()) {
                ECKey key = findKeyFromPubKey(pubkey.getPubKey());
                if (key != null && (key.isEncrypted() || key.hasPrivKey()))
                    return true;
            }
        } else if (script.isSentToCLTVPaymentChannel()) {
            // Any script for which we are the recipient or sender counts.
            byte[] sender = script.getCLTVPaymentChannelSenderPubKey();
            ECKey senderKey = findKeyFromPubKey(sender);
            if (senderKey != null && (senderKey.isEncrypted() || senderKey.hasPrivKey())) {
                return true;
            }
            byte[] recipient = script.getCLTVPaymentChannelRecipientPubKey();
            ECKey recipientKey = findKeyFromPubKey(sender);
            if (recipientKey != null && (recipientKey.isEncrypted() || recipientKey.hasPrivKey())) {
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Returns the spendable candidates from the {@link UTXOProvider} based on
     * keys that the wallet contains.
     * 
     * @return The list of candidates.
     */
    protected LinkedList<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(
            boolean excludeImmatureCoinbases) {
        checkState(lock.isHeldByCurrentThread());
        UTXOProvider utxoProvider = checkNotNull(vUTXOProvider, "No UTXO provider has been set");
        LinkedList<TransactionOutput> candidates = Lists.newLinkedList();
        try {
            // TODO fix this by putting depth into FreeStandingTransactionOutput
            long chainHeight = 0;
            for (UTXO output : getStoredOutputsFromUTXOProvider()) {
                boolean coinbase = output.isCoinbase();
                long depth = chainHeight - output.getHeight() + 1; // the
                                                                   // current
                                                                   // depth of
                                                                   // the output
                                                                   // (1 = same
                                                                   // as head).
                // Do not try and spend coinbases that were mined too recently,
                // the protocol forbids it.
                if (!excludeImmatureCoinbases || !coinbase || depth >= params.getSpendableCoinbaseDepth()) {
                    candidates.add(new FreeStandingTransactionOutput(params, output, chainHeight));
                }
            }
        } catch (UTXOProviderException e) {
            throw new RuntimeException("UTXO provider error", e);
        }
        // We need to handle the pending transactions that we know about.
        for (Transaction tx : pending.values()) {
            // Remove the spent outputs.
            for (TransactionInput input : tx.getInputs()) {
                if (input.getConnectedOutput().isMine(this)) {
                    candidates.remove(input.getConnectedOutput());
                }
            }
            // Add change outputs. Do not try and spend coinbases that were
            // mined too recently, the protocol forbids it.
            if (!excludeImmatureCoinbases) {
                for (TransactionOutput output : tx.getOutputs()) {
                    if (output.isAvailableForSpending() && output.isMine(this)) {
                        candidates.add(output);
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * Get all the {@link UTXO}'s from the {@link UTXOProvider} based on keys
     * that the wallet contains.
     * 
     * @return The list of stored outputs.
     */
    protected List<UTXO> getStoredOutputsFromUTXOProvider() throws UTXOProviderException {
        UTXOProvider utxoProvider = checkNotNull(vUTXOProvider, "No UTXO provider has been set");
        List<UTXO> candidates = new ArrayList<UTXO>();
        List<ECKey> keys = getImportedKeys();
        keys.addAll(getActiveKeyChain().getLeafKeys());
        List<Address> addresses = new ArrayList<Address>();
        for (ECKey key : keys) {
            Address address = new Address(params, key.getPubKeyHash());
            addresses.add(address);
        }
        candidates.addAll(utxoProvider.getOpenTransactionOutputs(addresses));
        return candidates;
    }

    /**
     * Returns the {@link CoinSelector} object which controls which outputs can
     * be spent by this wallet.
     */
    public CoinSelector getCoinSelector() {
        lock.lock();
        try {
            return coinSelector;
        } finally {
            lock.unlock();
        }
    }

    /**
     * A coin selector is responsible for choosing which outputs to spend when
     * creating transactions. The default selector implements a policy of
     * spending transactions that appeared in the best chain and pending
     * transactions that were created by this wallet, but not others. You can
     * override the coin selector for any given send operation by changing
     * {@link SendRequest#coinSelector}.
     */
    public void setCoinSelector(CoinSelector coinSelector) {
        lock.lock();
        try {
            this.coinSelector = checkNotNull(coinSelector);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the {@link UTXOProvider}.
     * 
     * @return The UTXO provider.
     */
    @Nullable
    public UTXOProvider getUTXOProvider() {
        lock.lock();
        try {
            return vUTXOProvider;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Set the {@link UTXOProvider}.
     *
     * <p>
     * The wallet will query the provider for spendable candidates, i.e. outputs
     * controlled exclusively by private keys contained in the wallet.
     * </p>
     *
     * <p>
     * Note that the associated provider must be reattached after a wallet is
     * loaded from disk. The association is not serialized.
     * </p>
     */
    public void setUTXOProvider(@Nullable UTXOProvider provider) {
        lock.lock();
        try {
            checkArgument(provider == null || provider.getParams().equals(params));
            this.vUTXOProvider = provider;
        } finally {
            lock.unlock();
        }
    }

    // endregion

    /******************************************************************************************************************/

    /**
     * A custom {@link TransactionOutput} that is free standing. This contains
     * all the information required for spending without actually having all the
     * linked data (i.e parent tx).
     *
     */
    public class FreeStandingTransactionOutput extends TransactionOutput {
        private UTXO output;
        private long chainHeight;

        /**
         * Construct a free standing Transaction Output.
         * 
         * @param params
         *            The network parameters.
         * @param output
         *            The stored output (free standing).
         */
        public FreeStandingTransactionOutput(NetworkParameters params, UTXO output, long chainHeight) {
            super(params, null, output.getValue(), output.getScript().getProgram());
            this.output = output;
            this.chainHeight = chainHeight;
        }

        /**
         * Get the {@link UTXO}.
         * 
         * @return The stored output.
         */
        public UTXO getUTXO() {
            return output;
        }

        @Override
        public int getIndex() {
            return (int) output.getIndex();
        }

        @Override
        public Sha256Hash getParentTransactionHash() {
            return output.getHash();
        }
    }

    /******************************************************************************************************************/

    /******************************************************************************************************************/

    private static class TxOffsetPair implements Comparable<TxOffsetPair> {
        public final Transaction tx;
        public final int offset;

        public TxOffsetPair(Transaction tx, int offset) {
            this.tx = tx;
            this.offset = offset;
        }

        @Override
        public int compareTo(TxOffsetPair o) {
            // note that in this implementation compareTo() is not consistent
            // with equals()
            return Ints.compare(offset, o.offset);
        }
    }

    // endregion

    /******************************************************************************************************************/

    // region Bloom filtering

    private final ArrayList<TransactionOutPoint> bloomOutPoints = Lists.newArrayList();
    // Used to track whether we must automatically begin/end a filter
    // calculation and calc outpoints/take the locks.
    private final AtomicInteger bloomFilterGuard = new AtomicInteger(0);

    private void calcBloomOutPointsLocked() {
        // TODO: This could be done once and then kept up to date.
        bloomOutPoints.clear();
        Set<Transaction> all = new HashSet<Transaction>();
        all.addAll(unspent.values());
        all.addAll(spent.values());
        all.addAll(pending.values());
        for (Transaction tx : all) {
            for (TransactionOutput out : tx.getOutputs()) {
                try {
                    if (isTxOutputBloomFilterable(out))
                        bloomOutPoints.add(out.getOutPointFor());
                } catch (ScriptException e) {
                    // If it is ours, we parsed the script correctly, so this
                    // shouldn't happen.
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // Returns true if the output is one that won't be selected by a data
    // element matching in the scriptSig.
    private boolean isTxOutputBloomFilterable(TransactionOutput out) {
        Script script = out.getScriptPubKey();
        boolean isScriptTypeSupported = script.isSentToRawPubKey() || script.isPayToScriptHash();
        return (isScriptTypeSupported && myUnspents.contains(out)) || watchedScripts.contains(script);
    }

    /**
     * Used by {@link Peer} to decide whether or not to discard this block and
     * any blocks building upon it, in case the Bloom filter used to request
     * them may be exhausted, that is, not have sufficient keys in the
     * deterministic sequence within it to reliably find relevant transactions.
     */
    public boolean checkForFilterExhaustion(FilteredBlock block) {
        keyChainGroupLock.lock();
        try {
            int epoch = keyChainGroup.getCombinedKeyLookaheadEpochs();
            for (Transaction tx : block.getAssociatedTransactions().values()) {
                markKeysAsUsed(tx);
            }
            int newEpoch = keyChainGroup.getCombinedKeyLookaheadEpochs();
            checkState(newEpoch >= epoch);
            // If the key lookahead epoch has advanced, there was a call to
            // addKeys and the PeerGroup already has a
            // pending request to recalculate the filter queued up on another
            // thread. The calling Peer should abandon
            // block at this point and await a new filter before restarting the
            // download.
            return newEpoch > epoch;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    // endregion

    /******************************************************************************************************************/

    // region Extensions to the wallet format.

    /**
     * By providing an object implementing the {@link WalletExtension}
     * interface, you can save and load arbitrary additional data that will be
     * stored with the wallet. Each extension is identified by an ID, so
     * attempting to add the same extension twice (or two different objects that
     * use the same ID) will throw an IllegalStateException.
     */
    public void addExtension(WalletExtension extension) {
        String id = checkNotNull(extension).getWalletExtensionID();
        lock.lock();
        try {
            if (extensions.containsKey(id))
                throw new IllegalStateException("Cannot add two extensions with the same ID: " + id);
            extensions.put(id, extension);
            saveNow();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically adds extension or returns an existing extension if there is
     * one with the same id already present.
     */
    public WalletExtension addOrGetExistingExtension(WalletExtension extension) {
        String id = checkNotNull(extension).getWalletExtensionID();
        lock.lock();
        try {
            WalletExtension previousExtension = extensions.get(id);
            if (previousExtension != null)
                return previousExtension;
            extensions.put(id, extension);
            saveNow();
            return extension;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Either adds extension as a new extension or replaces the existing
     * extension if one already exists with the same id. This also triggers
     * wallet auto-saving, so may be useful even when called with the same
     * extension as is already present.
     */
    public void addOrUpdateExtension(WalletExtension extension) {
        String id = checkNotNull(extension).getWalletExtensionID();
        lock.lock();
        try {
            extensions.put(id, extension);
            saveNow();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a snapshot of all registered extension objects. The extensions
     * themselves are not copied.
     */
    public Map<String, WalletExtension> getExtensions() {
        lock.lock();
        try {
            return ImmutableMap.copyOf(extensions);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Deserialize the wallet extension with the supplied data and then install
     * it, replacing any existing extension that may have existed with the same
     * ID. If an exception is thrown then the extension is removed from the
     * wallet, if already present.
     */
    public void deserializeExtension(WalletExtension extension, byte[] data) throws Exception {
        lock.lock();
        keyChainGroupLock.lock();
        try {
            // This method exists partly to establish a lock ordering of wallet
            // > extension.
            extension.deserializeWalletExtension(this, data);
            extensions.put(extension.getWalletExtensionID(), extension);
        } catch (Throwable throwable) {
            log.error("Error during extension deserialization", throwable);
            extensions.remove(extension.getWalletExtensionID());
            Throwables.propagate(throwable);
        } finally {
            keyChainGroupLock.unlock();
            lock.unlock();
        }
    }

    @Override
    public void setTag(String tag, ByteString value) {
        super.setTag(tag, value);
        saveNow();
    }

    // endregion

    /******************************************************************************************************************/

    protected static class FeeCalculation {
        public CoinSelection bestCoinSelection;
        public TransactionOutput bestChangeOutput;
    }

    public FeeCalculation calculateFee(SendRequest req, Coin value, List<TransactionInput> originalInputs,
            boolean needAtLeastReferenceFee, List<TransactionOutput> candidates) throws InsufficientMoneyException {
        return this.calculateFee(req, value, originalInputs, needAtLeastReferenceFee, candidates, req.changeAddress);
    }

    public FeeCalculation calculateFee(SendRequest req, Coin value, List<TransactionInput> originalInputs,
            boolean needAtLeastReferenceFee, List<TransactionOutput> candidates, Address changeAddress)
            throws InsufficientMoneyException {
        checkState(lock.isHeldByCurrentThread());
        // There are 3 possibilities for what adding change might do:
        // 1) No effect
        // 2) Causes increase in fee (change < 0.01 COINS)
        // 3) Causes the transaction to have a dust output or change < fee
        // increase (ie change will be thrown away)
        // If we get either of the last 2, we keep note of what the inputs
        // looked like at the time and try to
        // add inputs as we go up the list (keeping track of minimum inputs for
        // each category). At the end, we pick
        // the best input set as the one which generates the lowest total fee.
        Coin additionalValueForNextCategory = null;
        CoinSelection selection3 = null;
        CoinSelection selection2 = null;
        TransactionOutput selection2Change = null;
        CoinSelection selection1 = null;
        TransactionOutput selection1Change = null;
        // We keep track of the last size of the transaction we calculated.
        int lastCalculatedSize = 0;
        Coin valueNeeded, valueMissing = null;

        while (true) {
            resetTxInputs(req, originalInputs);

            valueNeeded = value;
            if (additionalValueForNextCategory != null)
                valueNeeded = valueNeeded.add(additionalValueForNextCategory);
            Coin additionalValueSelected = additionalValueForNextCategory;

            // Of the coins we could spend, pick some that we actually will
            // spend.
            CoinSelector selector = req.coinSelector == null ? coinSelector : req.coinSelector;
            // selector is allowed to modify candidates list.
            CoinSelection selection = selector.select(valueNeeded, new LinkedList<TransactionOutput>(candidates));
            // Can we afford this?
            if (selection.valueGathered.compareTo(valueNeeded) < 0) {
                valueMissing = valueNeeded.subtract(selection.valueGathered);
                break;
            }
            checkState(selection.gathered.size() > 0 || originalInputs.size() > 0);

            // We keep track of an upper bound on transaction size to
            // calculate
            // fees that need to be added.
            // Note that the difference between the upper bound and lower
            // bound
            // is usually small enough that it
            // will be very rare that we pay a fee we do not need to.
            //
            // We can't be sure a selection is valid until we check fee per
            // kb
            // at the end, so we just store
            // them here temporarily.
            boolean eitherCategory2Or3 = false;
            boolean isCategory3 = false;

            Coin change = selection.valueGathered.subtract(valueNeeded);
            if (additionalValueSelected != null)
                change = change.add(additionalValueSelected);

            int size = 0;
            TransactionOutput changeOutput = null;
            if (change.signum() > 0) {
                // The value of the inputs is greater than what we want to
                // send.
                // Just like in real life then,
                // we need to take back some coins ... this is called
                // "change".
                // Add another output that sends the change
                // back to us. The address comes either from the request or
                // currentChangeAddress() as a default.
                // Address changeAddress = req.changeAddress;
                if (changeAddress == null)
                    changeAddress = currentChangeAddress();
                changeOutput = new TransactionOutput(params, req.tx, change, changeAddress);
                // If the change output would result in this transaction
                // being
                // rejected as dust, just drop the change and make it a fee
                if (req.ensureMinRequiredFee && changeOutput.isDust()) {
                    // This solution definitely fits in category 3
                    isCategory3 = true;
                    additionalValueForNextCategory = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE
                            .add(changeOutput.getMinNonDustValue().add(Coin.SATOSHI));
                } else {
                    size += changeOutput.unsafeBitcoinSerialize().length + VarInt.sizeOf(req.tx.getOutputs().size())
                            - VarInt.sizeOf(req.tx.getOutputs().size() - 1);
                    // This solution is either category 1 or 2
                    if (!eitherCategory2Or3) // must be category 1
                        additionalValueForNextCategory = null;
                }
            } else {
                if (eitherCategory2Or3) {
                    // This solution definitely fits in category 3 (we threw
                    // away change because it was smaller than MIN_TX_FEE)
                    isCategory3 = true;
                    additionalValueForNextCategory = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.add(Coin.SATOSHI);
                }
            }

            // Now add unsigned inputs for the selected coins.
            for (TransactionOutput output : selection.gathered) {
                TransactionInput input = req.tx.addInput(output);
                // If the scriptBytes don't default to none, our size
                // calculations will be thrown off.
                checkState(input.getScriptBytes().length == 0);
            }

            // Estimate transaction size and loop again if we need more fee
            // per
            // kb. The serialized tx doesn't
            // include things we haven't added yet like input
            // signatures/scripts
            // or the change output.
            size += req.tx.unsafeBitcoinSerialize().length;
            size += estimateBytesForSigning(selection);
            if (size > lastCalculatedSize && req.feePerKb.signum() > 0) {
                lastCalculatedSize = size;
                // We need more fees anyway, just try again with the same
                // additional value
                additionalValueForNextCategory = additionalValueSelected;
                continue;
            }

            if (isCategory3) {
                if (selection3 == null)
                    selection3 = selection;
            } else if (eitherCategory2Or3) {
                // If we are in selection2, we will require at least CENT
                // additional. If we do that, there is no way
                // we can end up back here because CENT additional will
                // always
                // get us to 1
                checkState(selection2 == null);
                checkState(additionalValueForNextCategory.equals(Coin.CENT));
                selection2 = selection;
                selection2Change = checkNotNull(changeOutput); // If we get
                                                               // no
                                                               // change in
                                                               // category
                                                               // 2, we
                                                               // are
                                                               // actually
                                                               // in
                                                               // category 3
            } else {
                // Once we get a category 1 (change kept), we should break
                // out
                // of the loop because we can't do better
                // checkState(selection1 == null);
                checkState(additionalValueForNextCategory == null);
                selection1 = selection;
                selection1Change = changeOutput;
            }

            if (additionalValueForNextCategory != null) {
                if (additionalValueSelected != null)
                    checkState(additionalValueForNextCategory.compareTo(additionalValueSelected) > 0);
                continue;
            }
            break;
        }

        resetTxInputs(req, originalInputs);

        if (selection3 == null && selection2 == null && selection1 == null) {
            checkNotNull(valueMissing);
            log.warn("Insufficient value in wallet for send: needed {} more", valueMissing.toString());
            throw new InsufficientMoneyException(valueMissing);
        }

        Coin lowestFee = null;
        FeeCalculation result = new FeeCalculation();
        if (selection1 != null) {
            if (selection1Change != null)
                lowestFee = selection1.valueGathered.subtract(selection1Change.getValue());
            else
                lowestFee = selection1.valueGathered;
            result.bestCoinSelection = selection1;
            result.bestChangeOutput = selection1Change;
        }

        if (selection2 != null) {
            Coin fee = selection2.valueGathered.subtract(checkNotNull(selection2Change).getValue());
            if (lowestFee == null || fee.compareTo(lowestFee) < 0) {
                lowestFee = fee;
                result.bestCoinSelection = selection2;
                result.bestChangeOutput = selection2Change;
            }
        }

        if (selection3 != null) {
            if (lowestFee == null || selection3.valueGathered.compareTo(lowestFee) < 0) {
                result.bestCoinSelection = selection3;
                result.bestChangeOutput = null;
            }
        }
        return result;
    }

    private void resetTxInputs(SendRequest req, List<TransactionInput> originalInputs) {
        req.tx.clearInputs();
        for (TransactionInput input : originalInputs)
            req.tx.addInput(input);
    }

    private int estimateBytesForSigning(CoinSelection selection) {
        int size = 0;
        for (TransactionOutput output : selection.gathered) {
            try {
                Script script = output.getScriptPubKey();
                ECKey key = null;
                Script redeemScript = null;
                if (script.isSentToAddress()) {
                    key = findKeyFromPubHash(script.getPubKeyHash());
                    // Expected checkNotNull(key, "Coin selection includes
                    // unspendable outputs");
                } else if (script.isPayToScriptHash()) {
                    redeemScript = findRedeemDataFromScriptHash(script.getPubKeyHash()).redeemScript;
                    checkNotNull(redeemScript, "Coin selection includes unspendable outputs");
                }
                size += script.getNumberOfBytesRequiredToSpend(key, redeemScript);
            } catch (ScriptException e) {
                // If this happens it means an output script in a wallet tx
                // could not be understood. That should never
                // happen, if it does it means the wallet has got into an
                // inconsistent state.
                throw new IllegalStateException(e);
            }
        }
        return size;
    }

    // endregion

    /******************************************************************************************************************/

    // region Wallet maintenance transactions

    // Wallet maintenance transactions. These transactions may not be directly
    // connected to a payment the user is
    // making. They may be instead key rotation transactions for when old keys
    // are suspected to be compromised,
    // de/re-fragmentation transactions for when our output sizes are
    // inappropriate or suboptimal, privacy transactions
    // and so on. Because these transactions may require user intervention in
    // some way (e.g. entering their password)
    // the wallet application is expected to poll the Wallet class to get
    // SendRequests. Ideally security systems like
    // hardware wallets or risk analysis providers are programmed to
    // auto-approve transactions that send from our own
    // keys back to our own keys.

    /**
     * When a key rotation time is set, and money controlled by keys created
     * before the given timestamp T will be automatically respent to any key
     * that was created after T. This can be used to recover from a situation
     * where a set of keys is believed to be compromised. Once the time is set
     * transactions will be created and broadcast immediately. New coins that
     * come in after calling this method will be automatically respent
     * immediately. The rotation time is persisted to the wallet. You can stop
     * key rotation by calling this method again with zero as the argument.
     */
    public void setKeyRotationTime(Date time) {
        setKeyRotationTime(time.getTime() / 1000);
    }

    /**
     * Returns the key rotation time, or null if unconfigured. See
     * {@link #setKeyRotationTime(Date)} for a description of the field.
     */
    public @Nullable Date getKeyRotationTime() {
        final long keyRotationTimestamp = vKeyRotationTimestamp;
        if (keyRotationTimestamp != 0)
            return new Date(keyRotationTimestamp * 1000);
        else
            return null;
    }

    /**
     * <p>
     * When a key rotation time is set, any money controlled by keys created
     * before the given timestamp T will be automatically respent to any key
     * that was created after T. This can be used to recover from a situation
     * where a set of keys is believed to be compromised. You can stop key
     * rotation by calling this method again with zero as the argument. Once set
     * up, calling
     * {@link #doMaintenance(org.spongycastle.crypto.params.KeyParameter, boolean)}
     * will create and possibly send rotation transactions: but it won't be done
     * automatically (because you might have to ask for the users password).
     * </p>
     *
     * <p>
     * The given time cannot be in the future.
     * </p>
     */
    public void setKeyRotationTime(long unixTimeSeconds) {
        checkArgument(unixTimeSeconds <= Utils.currentTimeSeconds(), "Given time (%s) cannot be in the future.",
                Utils.dateTimeFormat(unixTimeSeconds * 1000));
        vKeyRotationTimestamp = unixTimeSeconds;
        saveNow();
    }

    /**
     * Returns whether the keys creation time is before the key rotation time,
     * if one was set.
     */
    public boolean isKeyRotating(ECKey key) {
        long time = vKeyRotationTimestamp;
        return time != 0 && key.getCreationTimeSeconds() < time;
    }

    // endregion

    // changes

    @SuppressWarnings("unchecked")

    public List<TransactionOutput> calculateAllSpendCandidates(boolean multisigns)
            throws UnsupportedEncodingException, JsonProcessingException, Exception {
        lock.lock();
        try {

            List<TransactionOutput> candidates = new ArrayList<TransactionOutput>();

            List<String> pubKeyHashs = new ArrayList<String>();

            for (ECKey ecKey : walletKeys(null)) {

                pubKeyHashs.add(Utils.HEX.encode(ecKey.getPubKeyHash()));
            }

            String response = OkHttp3Util.post(this.serverurl + "getOutputs",
                    Json.jsonmapper().writeValueAsString(pubKeyHashs).getBytes("UTF-8"));

            final Map<String, Object> data = Json.jsonmapper().readValue(response, Map.class);
            List<UTXO> outputs = new ArrayList<UTXO>();
            for (Map<String, Object> map : (List<Map<String, Object>>) data.get("outputs")) {
                UTXO utxo = MapToBeanMapperUtil.parseUTXO(map);
                outputs.add(utxo);
            }

            for (UTXO output : outputs) {
                if (multisigns) {
                    candidates.add(new FreeStandingTransactionOutput(this.params, output, 0));
                } else {
                    if (!output.isMultiSig()) {
                        candidates.add(new FreeStandingTransactionOutput(this.params, output, 0));
                    }
                }
            }

            return candidates;
        } finally {
            lock.unlock();
        }
    }

    public List<TransactionOutput> transforSpendCandidates(List<UTXO> outputs) {
        List<TransactionOutput> candidates = new ArrayList<TransactionOutput>();
        for (UTXO output : outputs) {
            candidates.add(new FreeStandingTransactionOutput(this.params, output, 0));
        }
        return candidates;
    }

    public void completeTx(SendRequest req) throws UnsupportedEncodingException, JsonProcessingException, Exception {
        // Calculate a list of ALL potential candidates for spending and
        // then ask a coin selector to provide us
        // with the actual outputs that'll be used to gather the required
        // amount of value. In this way, users
        // can customize coin selection policies. The call below will ignore
        // immature coinbases and outputs
        // we don't have the keys for.
        List<TransactionOutput> candidates = calculateAllSpendCandidates(false);
        completeTx(req, candidates, true);
    }

    public void completeTx(SendRequest req, List<TransactionOutput> candidates, boolean sign)
            throws InsufficientMoneyException {
        this.completeTx(req, candidates, sign, new HashMap<String, Address>());
    }

    public void completeTx(SendRequest req, List<TransactionOutput> candidates, boolean sign,
            HashMap<String, Address> addressResult) throws InsufficientMoneyException {
        lock.lock();
        try {
            checkArgument(!req.completed, "Given SendRequest has already been completed.");
            // Calculate the amount of value we need to import.
            Map<String, Coin> value = new HashMap<String, Coin>();
            for (TransactionOutput output : req.tx.getOutputs()) {
                if (value.containsKey(output.getValue().getTokenHex())) {
                    value.put(output.getValue().getTokenHex(), value.get(output.getValue().getTokenHex()))
                            .add(output.getValue());
                } else {
                    value.put(output.getValue().getTokenHex(), output.getValue());
                }
            }

            log.info("Completing send tx with {} outputs totalling {} and a fee of {}/kB", req.tx.getOutputs().size(),
                    value.toString(), req.feePerKb.toString());

            // If any inputs have already been added, we don't need to get their
            // value from wallet
            Map<String, Coin> valueIn = new HashMap<String, Coin>();

            for (TransactionInput input : req.tx.getInputs())
                if (input.getConnectedOutput() != null) {
                    if (valueIn.containsKey(input.getConnectedOutput().getValue().getTokenHex())) {
                        valueIn.put(input.getConnectedOutput().getValue().getTokenHex(),
                                valueIn.get(input.getConnectedOutput().getValue().getTokenHex()))
                                .add(input.getConnectedOutput().getValue());
                    } else {
                        valueIn.put(input.getConnectedOutput().getValue().getTokenHex(),
                                input.getConnectedOutput().getValue());
                    }
                } else
                    log.warn(
                            "SendRequest transaction already has inputs but we don't know how much they are worth - they will be added to fee.");
            substract(value, valueIn);

            List<TransactionInput> originalInputs = new ArrayList<TransactionInput>(req.tx.getInputs());

            // Check for dusty sends and the OP_RETURN limit.
            if (req.ensureMinRequiredFee && !req.emptyWallet) {
                // Min fee checking is handled later for emptyWallet.
                int opReturnCount = 0;
                for (TransactionOutput output : req.tx.getOutputs()) {
                    // if (output.isDust())
                    // throw new DustySendRequested();
                    if (output.getScriptPubKey().isOpReturn())
                        ++opReturnCount;
                }
                if (opReturnCount > 1) // Only 1 OP_RETURN per transaction
                                       // allowed.
                    throw new MultipleOpReturnRequested();
            }
            completeTxSelection(req, candidates, originalInputs, value, addressResult);

            // Now shuffle the outputs to obfuscate which is the change.
            if (req.shuffleOutputs)
                req.tx.shuffleOutputs();

            // Now sign the inputs, thus proving that we are entitled to redeem
            // the connected outputs.
            if (req.signInputs && sign)
                signTransaction(req);

            // Check size.
            final int size = req.tx.unsafeBitcoinSerialize().length;
            if (size > Transaction.MAX_STANDARD_TX_SIZE)
                throw new ExceededMaxTransactionSize();

            // Label the transaction as being self created. We can use this
            // later to spend its change output even before
            // the transaction is confirmed. We deliberately won't bother
            // notifying listeners here as there's not much
            // point - the user isn't interested in a confidence transition they
            // made themselves.
            // req.tx.getConfidence().setSource(TransactionConfidence.Source.SELF);
            // Label the transaction as being a user requested payment. This can
            // be used to render GUI wallet
            // transaction lists more appropriately, especially when the wallet
            // starts to generate transactions itself
            // for internal purposes.
            req.tx.setPurpose(Transaction.Purpose.USER_PAYMENT);
            // Record the exchange rate that was valid when the transaction was
            // completed.
            // req.tx.setMemo(req.memo);
            req.completed = true;
            log.info("  completed: {}", req.tx);
        } finally {
            lock.unlock();
        }
    }

    public void completeTxSelection(SendRequest req, List<TransactionOutput> candidates,
            List<TransactionInput> originalInputs, Map<String, Coin> value, HashMap<String, Address> addressResult)
            throws InsufficientMoneyException {
        List<TransactionInput> start = originalInputs;
        for (Map.Entry<String, Coin> entry : value.entrySet()) {
            CoinSelection bestCoinSelection;
            TransactionOutput bestChangeOutput = null;
            req.ensureMinRequiredFee = false;
            String tokenHex = entry.getKey();
            Address address = addressResult.get(tokenHex);
            FeeCalculation feeCalculation = calculateFee(req, entry.getValue(), start, req.ensureMinRequiredFee,
                    candidates, address);
            bestCoinSelection = feeCalculation.bestCoinSelection;
            bestChangeOutput = feeCalculation.bestChangeOutput;

            for (TransactionOutput output : bestCoinSelection.gathered) {
                req.tx.addInput(output);
                start.addAll(req.tx.getInputs());
            }

            if (bestChangeOutput != null) {
                req.tx.addOutput(bestChangeOutput);
                log.info("  with {} change", bestChangeOutput.getValue().toString());
            }
        }
    }

    public void substract(Map<String, Coin> valueInput, Map<String, Coin> valueOut) {

        for (Map.Entry<String, Coin> entry : valueInput.entrySet()) {

            Coin a = valueOut.get(entry.getKey());
            if (a != null) {
                valueInput.put(entry.getKey(), entry.getValue().subtract(a));
            }
        }
    }

    public void setServerURL(String url) {
        this.serverurl = url;
    }

    public KeyChainGroup getKeyChainGroup() {
        return this.keyChainGroup;
    }

    public boolean checkOutput(Map<String, Coin> valueOut) {

        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            // System.out.println(entry.getKey() + "/" + entry.getValue());
            if (entry.getValue().signum() < 0) {
                return false;
            }
        }
        return true;
    }

    public boolean checkInputOutput(Map<String, Coin> valueInput, Map<String, Coin> valueOut) {

        for (Map.Entry<String, Coin> entry : valueOut.entrySet()) {
            if (!valueInput.containsKey(entry.getKey())) {
                return false;
            } else {
                if (valueInput.get(entry.getKey()).compareTo(entry.getValue()) < 0)
                    return false;
            }
        }
        return true;
    }

    /*
     * get all keys in the wallet
     */
    public List<ECKey> walletKeys(@Nullable KeyParameter aesKey) throws Exception {
        DecryptingKeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(this, aesKey);
        List<ECKey> walletKeys = new ArrayList<ECKey>();
        for (ECKey key : getImportedKeys()) {
            ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
            walletKeys.add(ecKey);
        }
        for (DeterministicKeyChain chain : getKeyChainGroup().getDeterministicKeyChains()) {
            for (ECKey key : chain.getLeafKeys()) {
                ECKey ecKey = maybeDecryptingKeyBag.maybeDecrypt(key);
                walletKeys.add(ecKey);
            }
        }
        return walletKeys;
    }
}
