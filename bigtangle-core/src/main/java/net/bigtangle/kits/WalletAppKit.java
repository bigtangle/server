/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/

package net.bigtangle.kits;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractIdleService;

import net.bigtangle.core.BlockStore;
import net.bigtangle.core.BlockStoreException;
import net.bigtangle.core.Context;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.wallet.DeterministicSeed;
import net.bigtangle.wallet.KeyChainGroup;
import net.bigtangle.wallet.Protos;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.WalletExtension;
import net.bigtangle.wallet.WalletProtobufSerializer;

/**
 * <p>
 * Utility class that wraps the boilerplate needed to set up a new SPV bitcoinj
 * app. Instantiate it with a directory and file prefix, optionally configure a
 * few things, then use startAsync and optionally awaitRunning. The object will
 * construct and configure a {@link BlockGraph}, {@link SPVBlockStore},
 * {@link Wallet} and {@link PeerGroup}. Depending on the value of the
 * blockingStartup property, startup will be considered complete once the block
 * chain has fully synchronized, so it can take a while.
 * </p>
 *
 * <p>
 * To add listeners and modify the objects that are constructed, you can either
 * do that by overriding the {@link #onSetupCompleted()} method (which will run
 * on a background thread) and make your changes there, or by waiting for the
 * service to start and then accessing the objects from wherever you want.
 * However, you cannot access the objects this class creates until startup is
 * complete.
 * </p>
 *
 * <p>
 * The asynchronous design of this class may seem puzzling (just use
 * {@link #awaitRunning()} if you don't want that). It is to make it easier to
 * fit bitcoinj into GUI apps, which require a high degree of responsiveness on
 * their main thread which handles all the animation and user interaction. Even
 * when blockingStart is false, initializing bitcoinj means doing potentially
 * blocking file IO, generating keys and other potentially intensive operations.
 * By running it on a background thread, there's no risk of accidentally causing
 * UI lag.
 * </p>
 *
 * <p>
 * Note that {@link #awaitRunning()} can throw an unchecked
 * {@link java.lang.IllegalStateException} if anything goes wrong during startup
 * - you should probably handle it and use {@link Exception#getCause()} to
 * figure out what went wrong more precisely. Same thing if you just use the
 * {@link #startAsync()} method.
 * </p>
 */
public class WalletAppKit extends AbstractIdleService {
    protected static final Logger log = LoggerFactory.getLogger(WalletAppKit.class);

    protected final String filePrefix;
    protected final NetworkParameters params;

    protected volatile BlockStore vStore;
    protected volatile Wallet vWallet;

    protected final File directory;
    protected volatile File vWalletFile;

    protected boolean useAutoSave = true;
 

    protected boolean autoStop = true;
    protected InputStream checkpoints;
    protected boolean blockingStartup = true;
    protected boolean useTor = false; // Perhaps in future we can change this to
                                      // true.
    protected String userAgent, version;
    protected WalletProtobufSerializer.WalletFactory walletFactory;
    @Nullable
    protected DeterministicSeed restoreFromSeed;
 

    protected volatile Context context;

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files
     * will be stored in the given directory.
     */
    public WalletAppKit(NetworkParameters params, File directory, String filePrefix) {
        this(new Context(params), directory, filePrefix);
    }

    /**
     * Creates a new WalletAppKit, with the given {@link Context}. Files will be
     * stored in the given directory.
     */
    public WalletAppKit(Context context, File directory, String filePrefix) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.directory = checkNotNull(directory);
        this.filePrefix = checkNotNull(filePrefix);
    }

 

    /**
     * If true, the wallet will save itself to disk automatically whenever it
     * changes.
     */
    public WalletAppKit setAutoSave(boolean value) {
        checkState(state() == State.NEW, "Cannot call after startup");
        useAutoSave = value;
        return this;
    }

    /**
     * If true, will register a shutdown hook to stop the library. Defaults to
     * true.
     */
    public WalletAppKit setAutoStop(boolean autoStop) {
        this.autoStop = autoStop;
        return this;
    }

    /**
     * If set, the file is expected to contain a checkpoints file calculated
     * with BuildCheckpoints. It makes initial block sync faster for new users -
     * please refer to the documentation on the bitcoinj website for further
     * details.
     */
    public WalletAppKit setCheckpoints(InputStream checkpoints) {
        if (this.checkpoints != null)
            Utils.closeUnchecked(this.checkpoints);
        this.checkpoints = checkNotNull(checkpoints);
        return this;
    }

    /**
     * If true (the default) then the startup of this service won't be
     * considered complete until the network has been brought up, peer
     * connections established and the block chain synchronised. Therefore
     * {@link #awaitRunning()} can potentially take a very long time. If false,
     * then startup is considered complete once the network activity begins and
     * peer connections/block chain sync will continue in the background.
     */
    public WalletAppKit setBlockingStartup(boolean blockingStartup) {
        this.blockingStartup = blockingStartup;
        return this;
    }

    /**
     * Sets the string that will appear in the subver field of the version
     * message.
     * 
     * @param userAgent
     *            A short string that should be the name of your app, e.g. "My
     *            Wallet"
     * @param version
     *            A short string that contains the version number, e.g.
     *            "1.0-BETA"
     */
    public WalletAppKit setUserAgent(String userAgent, String version) {
        this.userAgent = checkNotNull(userAgent);
        this.version = checkNotNull(version);
        return this;
    }

    /**
     * If called, then an embedded Tor client library will be used to connect to
     * the P2P network. The user does not need any additional software for this:
     * it's all pure Java. As of April 2014 <b>this mode is experimental</b>.
     */
    public WalletAppKit useTor() {
        this.useTor = true;
        return this;
    }

    /**
     * If a seed is set here then any existing wallet that matches the file name
     * will be renamed to a backup name, the chain file will be deleted, and the
     * wallet object will be instantiated with the given seed instead of a fresh
     * one being created. This is intended for restoring a wallet from the
     * original seed. To implement restore you would shut down the existing
     * appkit, if any, then recreate it with the seed given by the user, then
     * start up the new kit. The next time your app starts it should work as
     * normal (that is, don't keep calling this each time).
     */
    public WalletAppKit restoreWalletFromSeed(DeterministicSeed seed) {
        this.restoreFromSeed = seed;
        return this;
    }

 
    /**
     * <p>
     * Override this to return wallet extensions if any are necessary.
     * </p>
     *
     * <p>
     * When this is called, chain(), store(), and peerGroup() will return the
     * created objects, however they are not initialized/started.
     * </p>
     */
    protected List<WalletExtension> provideWalletExtensions() throws Exception {
        return ImmutableList.of();
    }

    /**
     * This method is invoked on a background thread after all objects are
     * initialised, but before the peer group or block chain download is
     * started. You can tweak the objects configuration here.
     */
    protected void onSetupCompleted() {
    }

    /**
     * Tests to see if the spvchain file has an operating system file lock on
     * it. Useful for checking if your app is already running. If another copy
     * of your app is running and you start the appkit anyway, an exception will
     * be thrown during the startup process. Returns false if the chain file
     * does not exist or is a directory.
     */
    public boolean isChainFileLocked() throws IOException {
        RandomAccessFile file2 = null;
        try {
            File file = new File(directory, filePrefix + ".spvchain");
            if (!file.exists())
                return false;
            if (file.isDirectory())
                return false;
            file2 = new RandomAccessFile(file, "rw");
            FileLock lock = file2.getChannel().tryLock();
            if (lock == null)
                return true;
            lock.release();
            return false;
        } finally {
            if (file2 != null)
                file2.close();
        }
    }

    @Override
    protected void startUp() throws Exception {
        // Runs in a separate thread.
        Context.propagate(context);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }
        log.info("Starting up with directory = {}", directory);

       
        
        vWalletFile = new File(directory, filePrefix + ".wallet");
        boolean shouldReplayWallet =  vWalletFile.exists()   ;
        vWallet = createOrLoadWallet(shouldReplayWallet);
    }

    private Wallet createOrLoadWallet(boolean shouldReplayWallet) throws Exception {
        Wallet wallet;

        maybeMoveOldWalletOutOfTheWay();

        if (vWalletFile.exists()) {
            wallet = loadWallet(shouldReplayWallet);
        } else {
            wallet = createWallet();
            wallet.freshReceiveKey();
            for (WalletExtension e : provideWalletExtensions()) {
                wallet.addExtension(e);
            }

            // Currently the only way we can be sure that an extension is aware
            // of its containing wallet is by
            // deserializing the extension (see
            // WalletExtension#deserializeWalletExtension(Wallet, byte[]))
            // Hence, we first save and then load wallet to ensure any
            // extensions are correctly initialized.
            wallet.saveToFile(vWalletFile);
            wallet = loadWallet(false);
        }

        if (useAutoSave) {
            this.setupAutoSave(wallet);
        }

        return wallet;
    }

    protected void setupAutoSave(Wallet wallet) {
        wallet.autosaveToFile(vWalletFile, 5, TimeUnit.SECONDS, null);
    }

    private Wallet loadWallet(boolean shouldReplayWallet) throws Exception {
        Wallet wallet;
        FileInputStream walletStream = new FileInputStream(vWalletFile);
        try {
            List<WalletExtension> extensions = provideWalletExtensions();
            WalletExtension[] extArray = extensions.toArray(new WalletExtension[extensions.size()]);
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer;
            if (walletFactory != null)
                serializer = new WalletProtobufSerializer(walletFactory);
            else
                serializer = new WalletProtobufSerializer();
            wallet = serializer.readWallet(params, extArray, proto);
            if (shouldReplayWallet)
                wallet.reset();
        } finally {
            walletStream.close();
        }
        return wallet;
    }

    protected Wallet createWallet() {
        KeyChainGroup kcg;
        if (restoreFromSeed != null)
            kcg = new KeyChainGroup(params, restoreFromSeed);
        else
            kcg = new KeyChainGroup(params);
        if (walletFactory != null) {
            return walletFactory.create(params, kcg);
        } else {
            return new Wallet(params, kcg); // default
        }
    }

    private void maybeMoveOldWalletOutOfTheWay() {
        if (restoreFromSeed == null)
            return;
        if (!vWalletFile.exists())
            return;
        int counter = 1;
        File newName;
        do {
            newName = new File(vWalletFile.getParent(), "Backup " + counter + " for " + vWalletFile.getName());
            counter++;
        } while (newName.exists());
        log.info("Renaming old wallet file {} to {}", vWalletFile, newName);
        if (!vWalletFile.renameTo(newName)) {
            // This should not happen unless something is really messed up.
            throw new RuntimeException("Failed to rename wallet for restore");
        }
    }

    private void installShutdownHook() {
        if (autoStop)
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        WalletAppKit.this.stopAsync();
                        WalletAppKit.this.awaitTerminated();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
    }

    @Override
    protected void shutDown() throws Exception {
        // Runs in a separate thread.
        try {
            Context.propagate(context);

            vWallet.saveToFile(vWalletFile);
            vStore.close();

            vWallet = null;
            vStore = null;

        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    public NetworkParameters params() {
        return params;
    }

    public BlockStore store() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vStore;
    }

    public Wallet wallet() {
        // checkState(state() == State.STARTING || state() == State.RUNNING,
        // "Cannot call until startup is complete");
        if (vWallet == null) {
            try {
                this.startUp();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return vWallet;
    }

    public File directory() {
        return directory;
    }
}
