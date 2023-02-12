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

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import javax.annotation.Nullable;

import org.spongycastle.util.encoders.Base64;

import net.bigtangle.crypto.EncryptableItem;
import net.bigtangle.crypto.EncryptedData;
import net.bigtangle.crypto.TransactionSignature;
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

	// The two parts of the key. If "priv" is set, "pub" can always be
	// calculated.
	// If "pub" is set but not "priv", we
	// can only verify signatures not make them.
	protected PrivateKey priv; // A field element.
	protected PublicKey pub;

	protected DilithiumParameterSpec spec = DilithiumParameterSpec.LEVEL5;

	public ECKey() throws Exception {
		DilithiumProvider pv = new DilithiumProvider();
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("Dilithium", pv);
		kpg.initialize(spec);
		KeyPair kp = kpg.generateKeyPair();
		priv = kp.getPrivate();
		pub = kp.getPublic();

	}

	public ECKey(@Nullable PrivateKey priv, @Nullable PublicKey pub) {

		this.priv = priv;
		this.pub = pub;

	}

	@Override
	public boolean isEncrypted() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public byte[] getSecretBytes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EncryptedData getEncryptedData() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public EncryptionType getEncryptionType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public long getCreationTimeSeconds() {
		// TODO Auto-generated method stub
		return 0;
	}

	public String getPublicKeyString() {

		return Base64.toBase64String(getPubKey());
	}

	public String getPrivateKeyString() {

		return Base64.toBase64String(getPrivateKey());
	}

	public static ECKey fromPublicKeyString(String publickey) {
		DilithiumPublicKeySpec pubspec = new DilithiumPublicKeySpec(DilithiumParameterSpec.LEVEL5,
				Base64.decode(publickey));
		PublicKey publicKey = PackingUtils.unpackPublicKey(pubspec.getParameterSpec(), pubspec.getBytes());
		return new ECKey(null, publicKey);
	}

	public static ECKey fromPrivatekeyString(String privatekey) {
		DilithiumPrivateKeySpec prvspec = new DilithiumPrivateKeySpec(DilithiumParameterSpec.LEVEL5,
				Base64.decode(privatekey));
		PrivateKey privateKey = PackingUtils.unpackPrivateKey(prvspec.getParameterSpec(), prvspec.getBytes());
		return new ECKey(privateKey, null);
	}

	public static ECKey fromPublicOnly(byte[] publicHash) {
		return fromPublicKey(publicHash);
	}
	public static ECKey fromPublicKey(byte[] publicHash) {
		DilithiumPublicKeySpec pubspec = new DilithiumPublicKeySpec(DilithiumParameterSpec.LEVEL5, publicHash);
		PublicKey publicKey = PackingUtils.unpackPublicKey(pubspec.getParameterSpec(), pubspec.getBytes());
		return new ECKey(null, publicKey);
	}

	public static ECKey fromPrivatekey(byte[] privateHash) {
		DilithiumPrivateKeySpec prvspec = new DilithiumPrivateKeySpec(DilithiumParameterSpec.LEVEL5, privateHash);
		PrivateKey privateKey = PackingUtils.unpackPrivateKey(prvspec.getParameterSpec(), prvspec.getBytes());
		return new ECKey(privateKey, null);
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

	public   boolean verify(Sha256Hash hash, byte[] sig) {
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

	public ECKey.ECDSASignature sign(Sha256Hash hash)   {
		
		try{DilithiumProvider pv = new DilithiumProvider();
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
}