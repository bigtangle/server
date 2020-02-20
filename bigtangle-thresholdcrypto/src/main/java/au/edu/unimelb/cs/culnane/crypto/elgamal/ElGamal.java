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
import java.security.SecureRandom;

import au.edu.unimelb.cs.culnane.crypto.Group;
import au.edu.unimelb.cs.culnane.crypto.exceptions.CryptoException;
import au.edu.unimelb.cs.culnane.storage.exceptions.StorageException;

/**
 * Class providing ElGamal utility methods
 * 
 * @author Chris Culnane
 *
 */
public class ElGamal {
	/**
	 * Encrypts a value and returns an ElGamalCipher object
	 * 
	 * @param group
	 *            Group to use for operations
	 * @param keyData
	 *            StorageObject with KeyData
	 * @param value
	 *            BigInteger value to encrypt
	 * @return ElGamalCipher containing c1 and c2 values
	 * @throws StorageException
	 *             - if the keyData does not contain appropriate fields
	 * @throws CryptoException
	 *             - if the key pair does not contain a public key
	 */
	public static final ElGamalCipher encrypt(Group group, ElGamalKeyPair keyPair, BigInteger value)
			throws StorageException, CryptoException {
		BigInteger h = keyPair.getPublicKey();
		SecureRandom rand = new SecureRandom();
		BigInteger y = group.getRandomValueInQ(rand);
		BigInteger c1 = group.get_g().modPow(y, group.get_p());
		BigInteger s = h.modPow(y, group.get_p());
		BigInteger m = value;
		BigInteger c2 = m.multiply(s).mod(group.get_p());
		return new ElGamalCipher(c1, c2);
	}

	/**
	 * Encrypts a value and returns an ElGamalCipher object
	 * 
	 * @param group
	 *            Group to use for operations
	 * @param keyData
	 *            StorageObject with KeyData
	 * @param value
	 *            BigInteger value to encrypt
	 * @return ElGamalCipher containing c1 and c2 values
	 * @throws StorageException
	 *             - if the keyData does not contain appropriate fields
	 * @throws CryptoException
	 *             - if the key pair does not contain a public key
	 */
	public static final ElGamalCipher encrypt(Group group, ElGamalPublicKey pubKey, BigInteger value)
			throws StorageException, CryptoException {
		BigInteger h = pubKey.getPublicKey();
		SecureRandom rand = new SecureRandom();
		BigInteger y = group.getRandomValueInQ(rand);
		BigInteger c1 = group.get_g().modPow(y, group.get_p());
		BigInteger s = h.modPow(y, group.get_p());
		BigInteger m = value;
		BigInteger c2 = m.multiply(s).mod(group.get_p());
		return new ElGamalCipher(c1, c2);
	}
}
