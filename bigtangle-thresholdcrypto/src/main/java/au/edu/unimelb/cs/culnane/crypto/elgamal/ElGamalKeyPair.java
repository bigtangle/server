/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.elgamal;

import java.math.BigInteger;

import au.edu.unimelb.cs.culnane.crypto.exceptions.CryptoException;
import au.edu.unimelb.cs.culnane.storage.Storable;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Wrapper of an ElGamal Key Pair
 * 
 * @author Chris Culnane
 *
 */
public class ElGamalKeyPair implements Storable {

	/**
	 * BigInteger containing public key
	 */
	private BigInteger publicKey = null;
	/**
	 * BigInteger containing private key
	 */
	private BigInteger privateKey = null;

	/**
	 * String field identifier for secret key
	 */
	public static final String SECRET_KEY = "x";
	/**
	 * String field identifier for public key
	 */
	public static final String PUBLIC_KEY = "h";

	/**
	 * Constructs a new ElGamalKeyPair from a BigInteger public key and private
	 * key
	 * 
	 * @param publicKey
	 *            BigInteger containing public key
	 * @param privateKey
	 *            BigInteger containing private key
	 */
	public ElGamalKeyPair(BigInteger publicKey, BigInteger privateKey) {
		this.publicKey = publicKey;
		this.privateKey = privateKey;
	}

	/**
	 * Gets the public key
	 * 
	 * @return BigInteger containing the public key
	 * @throws CryptoException
	 *             if no public key has been set
	 */
	public BigInteger getPublicKey() throws CryptoException {
		if (!this.hasPublicKey()) {
			throw new CryptoException("No public key");
		}
		return this.publicKey;
	}

	/**
	 * Gets the private key
	 * 
	 * @return BigInteger containing the private key
	 * @throws CryptoException
	 *             if no private key has been set
	 */
	public BigInteger getPrivateKey() throws CryptoException {
		if (!this.hasPublicKey()) {
			throw new CryptoException("No private key");
		}
		return this.privateKey;
	}

	/**
	 * Sets the public key of this key pair
	 * 
	 * @param publicKey
	 *            BigInteger containing the public key
	 */
	public void setPublicKey(BigInteger publicKey) {
		this.publicKey = publicKey;
	}

	/**
	 * Sets the private key of this key pair
	 * 
	 * @param privateKey
	 *            BigInteger containing the private key
	 */
	public void setPrivateKey(BigInteger privateKey) {
		this.privateKey = privateKey;
	}

	/**
	 * Stores this key into a StorageObject
	 * 
	 * @param store
	 *            StorageObject to store key in
	 * @throws StorageException
	 */
	public ElGamalKeyPair(StorageObject<?> store) throws StorageException {
		if (store.has(SECRET_KEY)) {
			this.privateKey = store.getBigInteger(SECRET_KEY);
		}
		if (store.has(PUBLIC_KEY)) {
			this.publicKey = store.getBigInteger(PUBLIC_KEY);
		}
	}

	/**
	 * Constructs an empty ElGamalKeyPair
	 */
	public ElGamalKeyPair() {

	}

	/**
	 * Checks if a private key has been set
	 * 
	 * @return boolean true if private key set, false if not
	 */
	public boolean hasPrivateKey() {
		return !(this.privateKey == null);
	}

	/**
	 * Checks if a public key has been set
	 * 
	 * @return boolean true if a public key set, false if not
	 */
	public boolean hasPublicKey() {
		return !(this.publicKey == null);
	}

	@Override
	public StorageObject<?> storeInStorageObject(StorageObject<?> store) {
		store.set(SECRET_KEY, this.privateKey);
		store.set(PUBLIC_KEY, this.publicKey);
		return store;
	}

}
