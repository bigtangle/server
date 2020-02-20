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
 * Wrapper of an ElGamal Public Key
 * 
 * @author Chris Culnane
 *
 */
public class ElGamalPublicKey implements Storable {

	/**
	 * BigInteger containing public key
	 */
	private BigInteger publicKey = null;

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
	 */
	public ElGamalPublicKey(BigInteger publicKey) {
		this.publicKey = publicKey;
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
	 * Sets the public key of this key pair
	 * 
	 * @param publicKey
	 *            BigInteger containing the public key
	 */
	public void setPublicKey(BigInteger publicKey) {
		this.publicKey = publicKey;
	}

	/**
	 * Stores this key into a StorageObject
	 * 
	 * @param store
	 *            StorageObject to store key in
	 * @throws StorageException
	 */
	public ElGamalPublicKey(StorageObject<?> store) throws StorageException {
		if (store.has(PUBLIC_KEY)) {
			this.publicKey = store.getBigInteger(PUBLIC_KEY);
		}
	}

	/**
	 * Constructs an empty ElGamalKeyPair
	 */
	public ElGamalPublicKey() {

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
		store.set(PUBLIC_KEY, this.publicKey);
		return store;
	}

}
