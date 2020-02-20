/*******************************************************************************
 * Copyright (c) 2016 University of Melbourne
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Public License v3.0 which accompanies
 * this distribution, and is available at http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *    Chris Culnane - initial API and implementation
 *******************************************************************************/
package au.edu.unimelb.cs.culnane.crypto.bls;

import it.unisa.dia.gas.jpbc.Element;

/**
 * BLS Key Pair
 * 
 * @author Chris Culnane
 *
 */
public class BLSKeyPair {
	/**
	 * BLS Private Key
	 */
	private BLSPrivateKey privKey;
	/**
	 * BLS Public Key
	 */
	private BLSPublicKey pubKey;

	/**
	 * Constructs a new Key Pair from a private and public key
	 * 
	 * @param privKey
	 *            BLSPrivateKey
	 * @param pubKey
	 *            BLSPublicKey
	 */
	public BLSKeyPair(BLSPrivateKey privKey, BLSPublicKey pubKey) {
		this.privKey = privKey;
		this.pubKey = pubKey;
	}

	/**
	 * Gets the BLSPrivateKey
	 * 
	 * @return BLSPrivateKey containing the private key
	 */
	public BLSPrivateKey getPrivateKey() {
		return this.privKey;
	}

	/**
	 * Gets the BLSPublicKey
	 * 
	 * @return BLSPublicKey containing the public key
	 */
	public BLSPublicKey getPublicKey() {
		return this.pubKey;
	}

	/**
	 * Generates a new key pair
	 * 
	 * @param sysParams
	 *            BLSSystemParameters containing pairing
	 * @return BLSKeyPair containing new private and public key
	 */
	public static BLSKeyPair generateKeyPair(BLSSystemParameters sysParams) {

		// Create a random PrivateKey;
		BLSPrivateKey privKey = new BLSPrivateKey(sysParams.getPairing().getZr().newRandomElement());

		// Using the generate and the private key to construct the public key
		Element publicKey = sysParams.getGenerator().powZn(privKey.getKey().getImmutable()).getImmutable();

		// Construct the public key from the variables
		BLSPublicKey pubKey = new BLSPublicKey(publicKey);
		return new BLSKeyPair(privKey, pubKey);
	}
}
