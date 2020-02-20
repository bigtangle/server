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

import au.edu.unimelb.cs.culnane.storage.Storable;
import au.edu.unimelb.cs.culnane.storage.StorageObject;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * ElGamalCipher class providers a wrapper around an ElGamal Cipher. It provides
 * a number of utility methods to allow easy operations to be performed on the
 * cipher. For example re-encryption and multiplication. Implements the Storable
 * interface to make it easy to load and store ciphers.
 * 
 * @author Chris Culnane
 *
 */
public class ElGamalCipher implements Storable {

	/**
	 * Field identifier for c1 component of cipher
	 */
	public static final String C1 = "c1";

	/**
	 * Field identifier for c2 component of cipher
	 */
	public static final String C2 = "c2";

	/**
	 * BigInteger to hold c1 value
	 */
	private BigInteger c1;
	/**
	 * BigInteger to hold c2 value
	 */
	private BigInteger c2;

	/**
	 * Constructs an ElGamal Cipher from raw BigInteger c1 and c2
	 * 
	 * @param c1
	 *            BigInteger c1 value
	 * @param c2
	 *            BigInteger c2 value
	 */
	public ElGamalCipher(BigInteger c1, BigInteger c2) {
		this.c1 = c1;
		this.c2 = c2;
	}

	/**
	 * Loads an ElGamalCipher from a StorgeObject. The passed in StorageObject
	 * must have a C1 and C2 element within it.
	 * 
	 * @param cipher
	 *            StorageObject containing ElGamalCipher elements
	 * @throws StorageException
	 *             if the StorageObject does not contain the appropriate fields
	 */
	public ElGamalCipher(StorageObject<?> cipher) throws StorageException {
		this.c1 = cipher.getBigInteger(C1);
		this.c2 = cipher.getBigInteger(C2);
	}

	/**
	 * Get the c1 value of the cipher
	 * 
	 * @return BigInteger containing c1 value
	 */
	public BigInteger getC1() {
		return this.c1;
	}

	/**
	 * Gets the c2 value of the cipher
	 * 
	 * @return BigInteger containing the c2 value
	 */
	public BigInteger getC2() {
		return this.c2;
	}

	/**
	 * Multiplies two ElGamalCiphers with each other, mod p. The two ciphers
	 * must be from the same group
	 * 
	 * @param cipher
	 *            ElGamalCipher to multiple this cipher with
	 * @param p
	 *            BigInteger containing p of group
	 */
	public void multiplyCipher(ElGamalCipher cipher, BigInteger p) {
		this.c1 = this.c1.multiply(cipher.c1).mod(p);
		this.c2 = this.c2.multiply(cipher.c2).mod(p);
	}

	/**
	 * Re-encrypts this cipher with the passed in randomness
	 * 
	 * @param r
	 *            BigInteger containing randomness to use for re-encryption
	 * @param p
	 *            BigInteger containing p of the group
	 * @return new ElGamalCipher that has been re-encrypted
	 */
	public ElGamalCipher reEncrypt(BigInteger r, BigInteger p) {

		return new ElGamalCipher(c1.modPow(r, p), c2.modPow(r, p));
	}

	@Override
	public StorageObject<?> storeInStorageObject(StorageObject<?> store) {
		store.set(C1, this.c1);
		store.set(C2, this.c2);
		return store;

	}

}
