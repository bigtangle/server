/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2014-2016 the libsecp256k1 contributors
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.encoders.Base64;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.primitives.Ints;

import net.bigtangle.core.ECKey2.ECDSASignature;
import net.bigtangle.core.ECKey2.KeyIsEncryptedException;
import net.bigtangle.core.ECKey2.MissingPrivateKeyException;
import net.bigtangle.crypto.EncryptableItem;
import net.bigtangle.crypto.EncryptedData;
import net.bigtangle.crypto.KeyCrypter;
import net.bigtangle.crypto.KeyCrypterException;
import net.bigtangle.wallet.Protos;
import net.bigtangle.wallet.Wallet;
import net.bigtangle.wallet.Protos.Wallet.EncryptionType;
import net.thiim.dilithium.impl.PackingUtils;
import net.thiim.dilithium.interfaces.DilithiumParameterSpec;
import net.thiim.dilithium.interfaces.DilithiumPrivateKeySpec;
import net.thiim.dilithium.interfaces.DilithiumPublicKeySpec;
import net.thiim.dilithium.provider.DilithiumProvider;

// TODO: Move this class to tracking compression state itself.
// The Bouncy Castle developers are deprecating their own tracking of the compression state.

/**
 * <p>
 * Represents an elliptic curve public and (optionally) private key, usable for
 * digital signatures but not encryption. Creating a new ECKey with the empty
 * constructor will generate a new random keypair. Other static methods can be
 * used when you already have the public or private parts. If you create a key
 * with only the public part, you can check signatures but not create them.
 * </p>
 *
 * <p>
 * ECKey also provides access to Bitcoin Core compatible text message signing,
 * as accessible via the UI or JSON-RPC. This is slightly different to signing
 * raw bytes - if you want to sign your own data and it won't be exposed as text
 * to people, you don't want to use this. If in doubt, ask on the mailing list.
 * </p>
 *
 * <p>
 * The ECDSA algorithm supports <i>key recovery</i> in which a signature plus a
 * couple of discriminator bits can be reversed to find the public key used to
 * calculate it. This can be convenient when you have a message and a signature
 * and want to find out who signed it, rather than requiring the user to provide
 * the expected identity.
 * </p>
 *
 * <p>
 * This class supports a variety of serialization forms. The methods that
 * accept/return byte arrays serialize private keys as raw byte arrays and
 * public keys using the SEC standard byte encoding for public keys. Signatures
 * are encoded using ASN.1/DER inside the Bitcoin protocol.
 * </p>
 *
 * <p>
 * A key can be <i>compressed</i> or <i>uncompressed</i>. This refers to whether
 * the public key is represented when encoded into bytes as an (x, y) coordinate
 * on the elliptic curve, or whether it's represented as just an X co-ordinate
 * and an extra byte that carries a sign bit. With the latter form the Y
 * coordinate can be calculated dynamically, however, <b>because the binary
 * serialization is different the address of a key changes if its compression
 * status is changed</b>. If you deviate from the defaults it's important to
 * understand this: money sent to a compressed version of the key will have a
 * different address to the same key in uncompressed form. Whether a public key
 * is compressed or not is recorded in the SEC binary serialisation format, and
 * preserved in a flag in this class so round-tripping preserves state. Unless
 * you're working with old software or doing unusual things, you can usually
 * ignore the compressed/uncompressed distinction.
 * </p>
 */
public class ECKey implements EncryptableItem {
	 private static final Logger log = LoggerFactory.getLogger(ECKey .class);
	// The two parts of the key. If "priv" is set, "pub" can always be
	// calculated.
	// If "pub" is set but not "priv", we
	// can only verify signatures not make them.
	protected PrivateKey priv; // A field element.
	protected PublicKey pub;

	protected static DilithiumParameterSpec spec = DilithiumParameterSpec.LEVEL3;
	protected EncryptedData encryptedData;
	protected KeyCrypter keyCrypter;
	// this key for encryption
	/*
	 * KyberPublicKey kyberPublicKey; KyberPrivateKey kyberPrivateKey;
	 * Kyber1024KeyPairGenerator bobKeyGen1024 = new Kyber1024KeyPairGenerator();
	 * KeyPair bobKeyPair = bobKeyGen1024.generateKeyPair(); KyberPublicKey
	 * bobPublicKey = (KyberPublicKey) bobKeyPair.getPublic(); KyberPrivateKey
	 * bobPrivateKey = (KyberPrivateKey) bobKeyPair.getPrivate();
	 */

	// Creation time of the key in seconds since the epoch, or zero if the key was
	// deserialized from a version that did
	// not have this field.
	protected long creationTimeSeconds;

	public ECKey() {
		try {
			DilithiumProvider pv = new DilithiumProvider();
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("Dilithium", pv);
			kpg.initialize(spec);
			KeyPair kp = kpg.generateKeyPair();
			priv = kp.getPrivate();
			pub = kp.getPublic();
			creationTimeSeconds = Utils.currentTimeSeconds();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public ECKey(@Nullable PrivateKey priv, @Nullable PublicKey pub, @Nullable EncryptedData encryptedKey) {

		this.priv = priv;
		this.pub = pub;
		this.encryptedData = encryptedKey;
	}

	@Override
	public boolean isEncrypted() {
		return keyCrypter != null && encryptedData != null && encryptedData.encryptedBytes.length > 0;
	}

	@Override
	public byte[] getSecretBytes() {
		if (hasPrivKey())
			return priv.getEncoded();
		else
			return null;
	}

	@Override
	public EncryptedData getEncryptedData() {

		return encryptedData;
	}

	@Nullable
	@Override
	public Protos.Wallet.EncryptionType getEncryptionType() {
		return keyCrypter != null ? keyCrypter.getUnderstoodEncryptionType() : Protos.Wallet.EncryptionType.UNENCRYPTED;
	}

	@Override
	public long getCreationTimeSeconds() {
		return creationTimeSeconds;
	}

	public String getPublicKeyString() {

		return Utils.HEX.encode(getPubKey());
	}

	public String getPrivateKeyString() {

		return Utils.HEX.encode(getPrivateKey());
	}

	public static ECKey fromPublicKeyString(String publickey) {
		DilithiumPublicKeySpec pubspec = new DilithiumPublicKeySpec(spec,
				Base64.decode(publickey));
		PublicKey publicKey = PackingUtils.unpackPublicKey(pubspec.getParameterSpec(), pubspec.getBytes());
		return new ECKey(null, publicKey, null);
	}

	public static ECKey fromPrivatekeyString(String privatekey) {
		DilithiumPrivateKeySpec prvspec = new DilithiumPrivateKeySpec(spec,
				Base64.decode(privatekey));
		PrivateKey privateKey = PackingUtils.unpackPrivateKey(prvspec.getParameterSpec(), prvspec.getBytes());
		return new ECKey(privateKey, null, null);
	}

	public static ECKey fromPublicOnly(byte[] publicHash) {
		return fromPublicKey(publicHash);
	}

	public static ECKey fromPublicKey(byte[] publicHash) {
		DilithiumPublicKeySpec pubspec = new DilithiumPublicKeySpec(spec, publicHash);
		PublicKey publicKey = PackingUtils.unpackPublicKey(pubspec.getParameterSpec(), pubspec.getBytes());
		return new ECKey(null, publicKey, null);
	}

	public static ECKey fromPrivatekey(byte[] privateHash) {
		DilithiumPrivateKeySpec prvspec = new DilithiumPrivateKeySpec(spec, privateHash);
		PrivateKey privateKey = PackingUtils.unpackPrivateKey(prvspec.getParameterSpec(), prvspec.getBytes());
		return new ECKey(privateKey, null, null);
	}
	public static ECKey fromPrivateAndPublic(byte[] privateHash, byte[] publicHash) {
		DilithiumPrivateKeySpec prvspec = new DilithiumPrivateKeySpec(spec, privateHash);
		PrivateKey privateKey = PackingUtils.unpackPrivateKey(prvspec.getParameterSpec(), prvspec.getBytes());
		DilithiumPublicKeySpec pubspec = new DilithiumPublicKeySpec(spec, publicHash);
		PublicKey publicKey = PackingUtils.unpackPublicKey(pubspec.getParameterSpec(), pubspec.getBytes());
		return new ECKey(privateKey, publicKey, null);
	}
	public static boolean verify(byte[] text, byte[] sig, byte[] pubKey) {
		try {
			DilithiumProvider pv = new DilithiumProvider();
			Signature signature = Signature.getInstance("Dilithium", pv);
			ECKey ec = ECKey.fromPublicKey(pubKey);
			signature.initVerify(ec.pub); //
			// alertSigningKey);
			signature.update(text);

			return signature.verify(sig);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] sign(byte[] text, byte[] privateKey)
			throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {

		DilithiumProvider pv = new DilithiumProvider();
		Signature signature = Signature.getInstance("Dilithium", pv);
		ECKey ec = ECKey.fromPrivatekey(privateKey);
		signature.initSign(ec.priv);
		signature.update(text);
		return signature.sign();
	}

	public byte[] getPubKey() {

		return pub.getEncoded();
	}

	public byte[] getPrivateKey() {

		return priv.getEncoded();
	}

	/** Gets the hash160 form of the public key (as seen in addresses). */
	public byte[] getPubKeyHash() {

		return Utils.sha256hash160(this.pub.getEncoded());
	}

	public byte[] getPrivateKeyHash() {

		return Utils.sha256hash160(this.priv.getEncoded());
	}

	public static class ECDSASignature {
		public ECDSASignature(byte[] sig2) {
			sig = sig2;
		}

		/** The two components of the signature. */
		public byte[] sig;

	}

	/**
	 * Returns the address that corresponds to the public part of this ECKey. Note
	 * that an address is derived from the RIPEMD-160 hash of the public key and is
	 * not the public key itself (which is too large to be convenient).
	 */
	public Address toAddress(NetworkParameters params) {
		return new Address(params, getPubKeyHash());
	}

	public boolean verify(Sha256Hash hash, byte[] sig) {
		try {
			DilithiumProvider pv = new DilithiumProvider();
			Signature signature = Signature.getInstance("Dilithium", pv);

			signature.initVerify(pub); //
			// alertSigningKey);
			signature.update(hash.getBytes());

			return signature.verify(sig);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public ECKey.ECDSASignature sign(Sha256Hash hash) {

		try {
			DilithiumProvider pv = new DilithiumProvider();
			Signature signature = Signature.getInstance("Dilithium", pv);

			signature.initSign(priv);
			signature.update(hash.getBytes());
			return new ECKey.ECDSASignature(signature.sign());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getPublicKeyAsHex() {

		return getPublicKeyString();
	}

	public boolean hasPrivKey() {
		return priv != null;
	}

	public ECKey encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
		checkNotNull(keyCrypter);
		final byte[] privKeyBytes = priv.getEncoded();
		EncryptedData encryptedPrivateKey = keyCrypter.encrypt(privKeyBytes, pub.getEncoded(), aesKey);
		ECKey result = ECKey.fromEncrypted(encryptedPrivateKey, keyCrypter, getPubKey());
		result.setCreationTimeSeconds(creationTimeSeconds);
		return result;
	}

	public static ECKey fromEncrypted(EncryptedData encrypted, KeyCrypter keyCrypter2, byte[] pubKey) {
		ECKey key = fromPublicKey( pubKey);
		key.encryptedData=encrypted;
        key.keyCrypter = checkNotNull(keyCrypter2);
        return key;
	}

 
	
	public ECKey decrypt(KeyParameter aesKey) throws KeyCrypterException {
		final KeyCrypter crypter = getKeyCrypter();
		if (crypter == null)
			throw new KeyCrypterException("No key crypter available");
		return decrypt(crypter, aesKey);
	}

	/**
	 * Returns the KeyCrypter that was used to encrypt to encrypt this ECKey2 . You
	 * need this to decrypt the ECKey2 .
	 */
	@Nullable
	public KeyCrypter getKeyCrypter() {
		return keyCrypter;
	}

	/**
	 * Create a decrypted private key with the keyCrypter and AES key supplied. Note
	 * that if the aesKey is wrong, this has some chance of throwing
	 * KeyCrypterException due to the corrupted padding that will result, but it can
	 * also just yield a garbage key.
	 *
	 * @param keyCrypter The keyCrypter that specifies exactly how the decrypted
	 *                   bytes are created.
	 * @param aesKey     The KeyParameter with the AES encryption key (usually
	 *                   constructed with keyCrypter#deriveKey and cached).
	 */
	public ECKey decrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
		checkNotNull(keyCrypter);
		// Check that the keyCrypter matches the one used to encrypt the keys, if set.
		if (this.keyCrypter != null && !this.keyCrypter.equals(keyCrypter))
			throw new KeyCrypterException(
					"The keyCrypter being used to decrypt the key is different to the one that was used to encrypt it");
		checkState(encryptedData != null, "This key is not encrypted");
		byte[] unencryptedPrivateKey = keyCrypter.decrypt(encryptedData, aesKey);
		ECKey key = ECKey.fromPrivatekey(unencryptedPrivateKey);
		// if (!isCompressed())
		// key = key.decompress();
		if (!Arrays.equals(key.getPubKey(), getPubKey()))
			throw new KeyCrypterException("Provided AES key is wrong");
		key.setCreationTimeSeconds(creationTimeSeconds);
		return key;
	}

	/**
	 * Signs the given hash and returns the R and S components as BigIntegers. In
	 * the Bitcoin protocol, they are usually encoded using DER format, so you want
	 * {@link net.bigtangle.core.ECKey2 .ECDSASignature#encodeToDER()} instead.
	 * However sometimes the independent components can be useful, for instance, if
	 * you're doing to do further EC maths on them.
	 *
	 * @param aesKey The AES key to use for decryption of the private key. If null
	 *               then no decryption is required.
	 * @throws KeyCrypterException if there's something wrong with aesKey.
	 * @throws ECKey2              .MissingPrivateKeyException if this key cannot
	 *                             sign because it's pubkey only.
	 */
	public ECDSASignature sign(Sha256Hash input, @Nullable KeyParameter aesKey) throws KeyCrypterException {
		KeyCrypter crypter = getKeyCrypter();
		if (crypter != null) {
			if (aesKey == null)
				throw new KeyIsEncryptedException();
			return decrypt(aesKey).sign(input);
		} else {
			// No decryption of private key required.
			if (priv == null)
				throw new MissingPrivateKeyException();
		}
		return sign(input);
	}

	/**
	 * Sets the creation time of this key. Zero is a convention to mean
	 * "unavailable". This method can be useful when you have a raw key you are
	 * importing from somewhere else.
	 */
	public void setCreationTimeSeconds(long newCreationTimeSeconds) {
		if (newCreationTimeSeconds < 0)
			throw new IllegalArgumentException("Cannot set creation time to negative value: " + newCreationTimeSeconds);
		creationTimeSeconds = newCreationTimeSeconds;
	}
	
    /**
     * <p>Check that it is possible to decrypt the key with the keyCrypter and that the original key is returned.</p>
     *
     * <p>Because it is a critical failure if the private keys cannot be decrypted successfully (resulting of loss of all
     * bitcoins controlled by the private key) you can use this method to check when you *encrypt* a wallet that
     * it can definitely be decrypted successfully.</p>
     *
     * <p>See {@link Wallet#encrypt(KeyCrypter keyCrypter, KeyParameter aesKey)} for example usage.</p>
     *
     * @return true if the encrypted key can be decrypted back to the original key successfully.
     */
    public static boolean encryptionIsReversible(ECKey originalKey, ECKey encryptedKey, KeyCrypter keyCrypter, KeyParameter aesKey) {
        try {
            ECKey rebornUnencryptedKey = encryptedKey.decrypt(keyCrypter, aesKey);
            byte[] originalPrivateKeyBytes = originalKey.getPrivateKey();
            byte[] rebornKeyBytes = rebornUnencryptedKey.getPrivateKey();
            if (!Arrays.equals(originalPrivateKeyBytes, rebornKeyBytes)) {
             //   log.error("The check that encryption could be reversed failed for {}", originalKey);
                return false;
            }
            return true;
        } catch (KeyCrypterException kce) {
            log.error(kce.getMessage());
            return false;
        }
    }
    
    public void formatKeyWithAddress(boolean includePrivateKeys, StringBuilder builder, NetworkParameters params) {
        final Address address = toAddress(params);
        builder.append("  addr:");
        builder.append(address.toString());
        builder.append("  hash160:");
        builder.append(Utils.HEX.encode(getPubKeyHash()));
        if (creationTimeSeconds > 0)
            builder.append("  creationTimeSeconds:").append(creationTimeSeconds);
        builder.append("\n");
        if (includePrivateKeys) {
            builder.append("  ");
            builder.append(toString(true, params));
            builder.append("\n");
        }
    }
    

    private String toString(boolean includePrivate, NetworkParameters params) {
        final MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
        helper.add("pub HEX", getPublicKeyAsHex());
        if (includePrivate) {
            try {
                helper.add("priv HEX", getPrivateKeyString());
 
            } catch (IllegalStateException e) {
                // TODO: Make hasPrivKey() work for deterministic keys and fix this.
            } catch (Exception e) {
                final String message = e.getMessage();
                helper.add("priv EXCEPTION", e.getClass().getName() + (message != null ? ": " + message : ""));
            }
        }
        if (creationTimeSeconds > 0)
            helper.add("creationTimeSeconds", creationTimeSeconds);
        helper.add("keyCrypter", keyCrypter);
        if (includePrivate)
            helper.add("encryptedPrivateKey", encryptedData);
        helper.add("isEncrypted", isEncrypted());
        helper.add("isPubKeyOnly", isPubKeyOnly());
        return helper.toString();
    }
    public boolean isPubKeyOnly() {
        return priv == null;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof ECKey)) return false;
        ECKey other = (ECKey) o;
        return Objects.equal(this.priv, other.priv)
                && Objects.equal(this.pub, other.pub)
                && Objects.equal(this.creationTimeSeconds, other.creationTimeSeconds)
                && Objects.equal(this.keyCrypter, other.keyCrypter)
                && Objects.equal(this.encryptedData, other.encryptedData);
    }
    @Override
    public int hashCode() {
        // Public keys are random already so we can just use a part of them as the hashcode. Read from the start to
        // avoid picking up the type code (compressed vs uncompressed) which is tacked on the end.
        byte[] bits = getPubKey();
        return Ints.fromBytes(bits[0], bits[1], bits[2], bits[3]);
    }
}