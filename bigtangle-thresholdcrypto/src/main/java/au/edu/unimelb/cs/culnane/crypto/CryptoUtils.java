/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * Utility class with some basic Crypto utility methods
 * 
 * @author Chris Culnane
 *
 */
public class CryptoUtils {
	/**
	 * Generates a random BigInteger of the appropriate size, checks it is not
	 * equal to zero. Use for generating a random integer in a group by passing
	 * the order of the group in as n
	 * 
	 * @param n
	 *            BitInteger of size to generate
	 * @param rand
	 *            SecureRandom to use
	 * @return BigInteger containing random value
	 */
	public static BigInteger getRandomInteger(BigInteger n, SecureRandom rand) {
		return getRandomInteger(n, rand, BigInteger.ZERO);
	}

	/**
	 * Generates a random BigInteger of the appropriate size, checks it is not
	 * equal to n. Use for generating a random integer in a group by passing the
	 * order of the group in as n
	 * 
	 * @param n
	 *            BitInteger of size to generate
	 * @param rand
	 *            SecureRandom to use
	 * @param notEqualTo
	 *            BigInteger to check the random value is not equal to (i.e.
	 *            zero or one)
	 * @return
	 */
	public static BigInteger getRandomInteger(BigInteger n, SecureRandom rand, BigInteger notEqualTo) {
		BigInteger randValue;
		int maxbits = n.bitLength();
		if (maxbits <= 1) {
			throw new IllegalArgumentException("The number of bits to generate cannot be less than 2");
		}
		do {
			randValue = new BigInteger(maxbits, rand);
		} while (randValue.compareTo(n) >= 0 || randValue.equals(notEqualTo));

		return randValue;
	}

}
