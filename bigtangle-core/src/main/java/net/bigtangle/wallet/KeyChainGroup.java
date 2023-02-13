/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2014 Mike Hearn
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

package net.bigtangle.wallet;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import net.bigtangle.core.Address;
import net.bigtangle.core.BloomFilter;
import net.bigtangle.core.ECKey;
import net.bigtangle.core.NetworkParameters;
import net.bigtangle.core.Utils;
import net.bigtangle.crypto.KeyCrypter;
import net.bigtangle.crypto.KeyCrypterScrypt;
import net.bigtangle.crypto.LinuxSecureRandom;
import net.bigtangle.utils.Threading;
import net.bigtangle.wallet.listeners.KeyChainEventListener;

/**
 * <p>
 * A KeyChainGroup is used by the {@link net.bigtangle.wallet.Wallet} and
 * manages: a {@link BasicKeyChain} object (which will normally be empty), and
 * zero or more {@link DeterministicKeyChain}s. A deterministic key chain will
 * be created lazily/on demand when a fresh or current key is requested,
 * possibly being initialized from the private key bytes of the earliest non
 * rotating key in the basic key chain if one is available, or from a fresh
 * random seed if not.
 * </p>
 *
 * <p>
 * If a key rotation time is set, it may be necessary to add a new
 * DeterministicKeyChain with a fresh seed and also preserve the old one, so
 * funds can be swept from the rotating keys. In this case, there may be more
 * than one deterministic chain. The latest chain is called the active chain and
 * is where new keys are served from.
 * </p>
 *
 * <p>
 * The wallet delegates most key management tasks to this class. It is
 * <b>not</b> thread safe and requires external locking, i.e. by the wallet
 * lock. The group then in turn delegates most operations to the key chain
 * objects, combining their responses together when necessary.
 * </p>
 *
 * <p>
 * Deterministic key chains have a concept of a lookahead size and threshold.
 * Please see the discussion in the class docs for {@link DeterministicKeyChain}
 * for more information on this topic.
 * </p>
 */
public class KeyChainGroup implements KeyBag {

	static {
		// Init proper random number generator, as some old Android installations have
		// bugs that make it unsecure.
		if (Utils.isAndroidRuntime())
			new LinuxSecureRandom();
	}

	private static final Logger log = LoggerFactory.getLogger(KeyChainGroup.class);

	private BasicKeyChain basic;
	private NetworkParameters params;

	// currentKeys is used for normal, non-multisig/married wallets.
	// currentAddresses is used when we're handing out
	// P2SH addresses. They're mutually exclusive.

	private final EnumMap<KeyChain.KeyPurpose, Address> currentAddresses;
	@Nullable
	private KeyCrypter keyCrypter;
	private int lookaheadSize = -1;
	private int lookaheadThreshold = -1;

	public KeyChainGroup(NetworkParameters params) {
		this.params = params;
		this.basic = new BasicKeyChain()  ;

		this.keyCrypter = new KeyCrypterScrypt(2);

		this.currentAddresses = new EnumMap<KeyChain.KeyPurpose, Address>(KeyChain.KeyPurpose.class);
// maybeLookaheadScripts();

	}

	// Used for deserialization.
	public KeyChainGroup(NetworkParameters params, @Nullable BasicKeyChain basicKeyChain,
			@Nullable KeyCrypter crypter) {
		this.params = params;
		this.basic = basicKeyChain == null ? new BasicKeyChain() : basicKeyChain;

		this.keyCrypter = crypter;

		this.currentAddresses = new EnumMap<KeyChain.KeyPurpose, Address>(KeyChain.KeyPurpose.class);
		// maybeLookaheadScripts();

	}

	/**
	 * Sets the lookahead buffer size for ALL deterministic key chains as well as
	 * for following key chains if any exist, see
	 * {@link DeterministicKeyChain#setLookaheadSize(int)} for more information.
	 */
	public void setLookaheadSize(int lookaheadSize) {
		this.lookaheadSize = lookaheadSize;

	}

	/**
	 * Gets the current lookahead size being used for ALL deterministic key chains.
	 * See {@link DeterministicKeyChain#setLookaheadSize(int)} for more information.
	 */
	public int getLookaheadSize() {

		return lookaheadSize;
	}

	/** Imports the given keys into the basic chain, creating it if necessary. */
	public int importKeys(List<ECKey> keys) {
		return basic.importKeys(keys);
	}

	/** Imports the given keys into the basic chain, creating it if necessary. */
	public int importKeys(ECKey... keys) {
		return importKeys(ImmutableList.copyOf(keys));
	}

	public boolean checkPassword(CharSequence password) {
		checkState(keyCrypter != null, "Not encrypted");
		return checkAESKey(keyCrypter.deriveKey(password));
	}

	public boolean checkAESKey(KeyParameter aesKey) {
		checkState(keyCrypter != null, "Not encrypted");
		if (basic.numKeys() > 0)
			return basic.checkAESKey(aesKey);
		return false;
	}

	/**
	 * Imports the given unencrypted keys into the basic chain, encrypting them
	 * along the way with the given key.
	 */
	public int importKeysAndEncrypt(final List<ECKey> keys, KeyParameter aesKey) {
		// TODO: Firstly check if the aes key can decrypt any of the existing keys
		// successfully.
		checkState(keyCrypter != null, "Not encrypted");
		LinkedList<ECKey> encryptedKeys = Lists.newLinkedList();
		for (ECKey key : keys) {
			if (key.isEncrypted())
				throw new IllegalArgumentException("Cannot provide already encrypted keys");
			encryptedKeys.add(key.encrypt(keyCrypter, aesKey));
		}
		return importKeys(encryptedKeys);
	}

	@Nullable
	@Override
	public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
		ECKey result;
		if ((result = basic.findKeyFromPubHash(pubkeyHash)) != null)
			return result;

		return null;
	}

	public boolean hasKey(ECKey key) {
		if (basic.hasKey(key))
			return true;

		return false;
	}

	@Nullable
	@Override
	public ECKey findKeyFromPubKey(byte[] pubkey) {
		ECKey result;
		if ((result = basic.findKeyFromPubKey(pubkey)) != null)
			return result;

		return null;
	}

	/**
	 * Returns the number of keys managed by this group, including the lookahead
	 * buffers.
	 */
	public int numKeys() {
		int result = basic.numKeys();

		return result;
	}

	/**
	 * Removes a key that was imported into the basic key chain. You cannot remove
	 * deterministic keys.
	 * 
	 * @throws java.lang.IllegalArgumentException if the key is deterministic.
	 */
	public boolean removeImportedKey(ECKey key) {
		checkNotNull(key);

		return basic.removeKey(key);
	}

	/**
	 * Encrypt the keys in the group using the KeyCrypter and the AES key. A good
	 * default KeyCrypter to use is {@link net.bigtangle.crypto.KeyCrypterScrypt}.
	 *
	 * @throws net.bigtangle.crypto.KeyCrypterException Thrown if the wallet
	 *                                                  encryption fails for some
	 *                                                  reason, leaving the group
	 *                                                  unchanged.
	 * @throws DeterministicUpgradeRequiredException    Thrown if there are random
	 *                                                  keys but no HD chain.
	 */
	public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
		checkNotNull(keyCrypter);
		checkNotNull(aesKey);
		// This code must be exception safe.
		BasicKeyChain newBasic = basic.toEncrypted(keyCrypter, aesKey);

		this.keyCrypter = keyCrypter;
		basic = newBasic;

	}

	/**
	 * Decrypt the keys in the group using the previously given key crypter and the
	 * AES key. A good default KeyCrypter to use is
	 * {@link net.bigtangle.crypto.KeyCrypterScrypt}.
	 *
	 * @throws net.bigtangle.crypto.KeyCrypterException Thrown if the wallet
	 *                                                  decryption fails for some
	 *                                                  reason, leaving the group
	 *                                                  unchanged.
	 */
	public void decrypt(KeyParameter aesKey) {
		// This code must be exception safe.
		checkNotNull(aesKey);
		BasicKeyChain newBasic = basic.toDecrypted(aesKey);

		this.keyCrypter = null;
		basic = newBasic;
	}

	/** Returns true if the group is encrypted. */
	public boolean isEncrypted() {
		return keyCrypter != null;
	}

	/**
	 * Returns whether this chain has only watching keys (unencrypted keys with no
	 * private part). Mixed chains are forbidden.
	 * 
	 * @throws IllegalStateException if there are no keys, or if there is a mix
	 *                               between watching and non-watching keys.
	 */
	public boolean isWatching() {
		BasicKeyChain.State basicState = basic.isWatching();
		BasicKeyChain.State activeState = BasicKeyChain.State.EMPTY;

		if (basicState == BasicKeyChain.State.EMPTY) {
			if (activeState == BasicKeyChain.State.EMPTY)
				throw new IllegalStateException("Empty key chain group: cannot answer isWatching() query");
			return activeState == BasicKeyChain.State.WATCHING;
		} else if (activeState == BasicKeyChain.State.EMPTY)
			return basicState == BasicKeyChain.State.WATCHING;
		else {
			if (activeState != basicState)
				throw new IllegalStateException("Mix of watching and non-watching keys in wallet");
			return activeState == BasicKeyChain.State.WATCHING;
		}
	}

	/** Returns the key crypter or null if the group is not encrypted. */
	@Nullable
	public KeyCrypter getKeyCrypter() {
		return keyCrypter;
	}

	/**
	 * Returns a list of the non-deterministic keys that have been imported into the
	 * wallet, or the empty list if none.
	 */
	public List<ECKey> getImportedKeys() {
		return basic.getKeys();
	}

	public long getEarliestKeyCreationTime() {
		long time = basic.getEarliestKeyCreationTime(); // Long.MAX_VALUE if empty.
		return time;
	}

	public int getBloomFilterElementCount() {
		int result = basic.numBloomFilterEntries();

		return result;
	}

	public BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak) {
		BloomFilter filter = new BloomFilter(size, falsePositiveRate, nTweak);
		if (basic.numKeys() > 0)
			filter.merge(basic.getFilter(size, falsePositiveRate, nTweak));

		return filter;
	}

	/** {@inheritDoc} */
	public boolean isRequiringUpdateAllBloomFilter() {
		throw new UnsupportedOperationException(); // Unused.
	}

	/**
	 * Adds a listener for events that are run when keys are added, on the user
	 * thread.
	 */
	public void addEventListener(KeyChainEventListener listener) {
		addEventListener(listener, Threading.USER_THREAD);
	}

	/**
	 * Adds a listener for events that are run when keys are added, on the given
	 * executor.
	 */
	public void addEventListener(KeyChainEventListener listener, Executor executor) {
		checkNotNull(listener);
		checkNotNull(executor);
		basic.addEventListener(listener, executor);

	}

	/** Removes a listener for events that are run when keys are added. */
	public boolean removeEventListener(KeyChainEventListener listener) {
		checkNotNull(listener);

		return basic.removeEventListener(listener);
	}

	/** Returns a list of key protobufs obtained by merging the chains. */
	public List<Protos.Key> serializeToProtobuf() {
		List<Protos.Key> result;
		if (basic != null)
			result = basic.serializeToProtobuf();
		else
			result = Lists.newArrayList();

		return result;
	}

	public static KeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys,
			KeyCrypter crypter) throws UnreadableWalletException {
		checkNotNull(crypter);
		BasicKeyChain basicKeyChain = BasicKeyChain.fromProtobufEncrypted(keys, crypter);

		return new KeyChainGroup(params, basicKeyChain, crypter);
	}

	public String toString(boolean includePrivateKeys) {
		final StringBuilder builder = new StringBuilder();
		if (basic != null) {
			List<ECKey> keys = basic.getKeys();
			// Collections.sort(keys, ECKey.AGE_COMPARATOR);
			for (ECKey key : keys)
				key.formatKeyWithAddress(includePrivateKeys, builder, params);
		}

		return builder.toString();
	}

	@Override
	public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
		// TODO Auto-generated method stub
		return null;
	}

}
